package dev.goshi.voicy.client.music;

import dev.goshi.voicy.Voicy;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

final class PlaybackSession implements AutoCloseable {
	record Pcm(short[] data, int channels, int rate, float rms) {
		int frames() {
			return data.length / channels;
		}
	}

	private static final String USER_AGENT = "Voicy/1.0 (Minecraft mod)";

	final BlockingQueue<Pcm> queue = new ArrayBlockingQueue<>(10);
	final boolean live;
	final long startFrame;

	volatile boolean finished;
	volatile String error;
	volatile String liveTitle = "";
	volatile long totalFrames = -1;
	volatile int fileRate;

	private final Path file;
	private final String url;
	private volatile boolean stop;
	private volatile HttpURLConnection activeConnection;
	private Thread thread;

	private PlaybackSession(Path file, String url, long startFrame) {
		this.file = file;
		this.url = url;
		this.live = url != null;
		this.startFrame = startFrame;
	}

	static PlaybackSession local(Path file, long startFrame) {
		return new PlaybackSession(file, null, startFrame);
	}

	static PlaybackSession radio(String url) {
		return new PlaybackSession(null, url, 0L);
	}

	void start() {
		thread = Thread.ofPlatform().daemon().name("voicy-music").start(() -> {
			try {
				if (live) {
					runRadio();
				} else {
					runLocal();
				}
			} catch (Throwable t) {
				if (!stop) {
					Voicy.LOGGER.warn("[Voicy] Music playback failed: {}", t.toString());
					error = friendly(t);
				}
			} finally {
				finished = true;
			}
		});
	}

	@Override
	public void close() {
		stop = true;
		queue.clear();
		HttpURLConnection connection = activeConnection;
		if (connection != null) {
			connection.disconnect();
		}
		if (thread != null) {
			thread.interrupt();
		}
	}

	private static String friendly(Throwable t) {
		String message = t.getMessage();
		if (t instanceof UnsupportedStreamException || t instanceof PlaybackException) {
			return message;
		}
		if (t instanceof java.net.UnknownHostException) {
			return "Could not find that server";
		}
		if (t instanceof java.net.SocketTimeoutException) {
			return "The station took too long to respond";
		}
		return message == null || message.isBlank() ? "Playback failed" : message;
	}

	private boolean push(short[] data, int channels, int rate) throws InterruptedException {
		double sum = 0;
		for (short s : data) {
			sum += (double) s * s;
		}
		float rms = (float) Math.sqrt(sum / Math.max(1, data.length)) / 32768f;
		Pcm chunk = new Pcm(data, channels, rate, Math.min(1f, rms * 3.2f));
		while (!stop) {
			if (queue.offer(chunk, 100, TimeUnit.MILLISECONDS)) {
				return true;
			}
		}
		return false;
	}

	private void runLocal() throws Exception {
		byte[] bytes = Files.readAllBytes(file);
		ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
		long vorbis = 0L;
		ShortBuffer buffer = null;
		try {
			data.put(bytes).flip();
			try (MemoryStack stack = MemoryStack.stackPush()) {
				var errorCode = stack.mallocInt(1);
				vorbis = STBVorbis.stb_vorbis_open_memory(data, errorCode, null);
				if (vorbis == 0L) {
					throw new PlaybackException("Not a valid Ogg Vorbis file");
				}
				STBVorbisInfo info = STBVorbisInfo.malloc(stack);
				STBVorbis.stb_vorbis_get_info(vorbis, info);
				int sourceChannels = info.channels();
				int channels = Math.min(2, sourceChannels);
				int rate = info.sample_rate();
				fileRate = rate;
				totalFrames = STBVorbis.stb_vorbis_stream_length_in_samples(vorbis);
				if (startFrame > 0 && startFrame < totalFrames) {
					STBVorbis.stb_vorbis_seek(vorbis, (int) startFrame);
				}
				int chunkFrames = Math.max(2048, rate / 5);
				buffer = MemoryUtil.memAllocShort(chunkFrames * channels);
				while (!stop) {
					int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(vorbis, channels, buffer);
					if (frames <= 0) {
						break;
					}
					short[] pcm = new short[frames * channels];
					buffer.get(0, pcm);
					if (!push(pcm, channels, rate)) {
						return;
					}
				}
			}
		} finally {
			if (vorbis != 0L) {
				STBVorbis.stb_vorbis_close(vorbis);
			}
			if (buffer != null) {
				MemoryUtil.memFree(buffer);
			}
			MemoryUtil.memFree(data);
		}
	}

	private void runRadio() throws Exception {
		int failures = 0;
		while (!stop) {
			try {
				boolean gotAudio = streamOnce();
				if (stop) {
					return;
				}
				failures = gotAudio ? 0 : failures + 1;
			} catch (UnsupportedStreamException | PlaybackException e) {
				throw e;
			} catch (InterruptedException e) {
				return;
			} catch (IOException e) {
				if (stop) {
					return;
				}
				failures++;
				Voicy.LOGGER.debug("[Voicy] Radio connection problem: {}", e.toString());
				if (failures >= 4) {
					throw e;
				}
			}
			Thread.sleep(1500L);
		}
	}

	private boolean streamOnce() throws IOException, InterruptedException {
		HttpURLConnection connection = connect();
		activeConnection = connection;
		boolean gotAudio = false;
		try {
			int metaInterval = parseInt(connection.getHeaderField("icy-metaint"));
			InputStream raw = connection.getInputStream();
			InputStream in = new BufferedInputStream(
					metaInterval > 0 ? new IcyInputStream(raw, metaInterval, title -> liveTitle = title) : raw,
					32 * 1024);

			Bitstream bitstream = new Bitstream(in);
			Decoder decoder = new Decoder();
			short[] chunk = null;
			int filled = 0;
			int badFrames = 0;

			while (!stop) {
				SampleBuffer samples;
				try {
					Header header = bitstream.readFrame();
					if (header == null) {
						break;
					}
					samples = (SampleBuffer) decoder.decodeFrame(header, bitstream);
					bitstream.closeFrame();
				} catch (JavaLayerException e) {
					if (++badFrames > 60) {
						throw new IOException("The stream is not valid MP3 audio");
					}
					continue;
				}
				badFrames = 0;
				int channels = decoder.getOutputChannels();
				int rate = decoder.getOutputFrequency();
				int length = samples.getBufferLength();
				short[] pcm = samples.getBuffer();
				int chunkSize = Math.max(1, rate / 5) * channels;
				if (chunk == null || chunk.length != chunkSize) {
					if (filled > 0 && chunk != null) {
						push(java.util.Arrays.copyOf(chunk, filled), channels, rate);
					}
					chunk = new short[chunkSize];
					filled = 0;
				}
				int offset = 0;
				while (offset < length) {
					int n = Math.min(length - offset, chunk.length - filled);
					System.arraycopy(pcm, offset, chunk, filled, n);
					offset += n;
					filled += n;
					if (filled == chunk.length) {
						if (!push(chunk, channels, rate)) {
							return gotAudio;
						}
						gotAudio = true;
						chunk = new short[chunkSize];
						filled = 0;
					}
				}
			}
		} finally {
			connection.disconnect();
		}
		return gotAudio;
	}

	private HttpURLConnection connect() throws IOException {
		String target = url;
		for (int attempt = 0; attempt < 8; attempt++) {
			HttpURLConnection connection = open(target);
			String type = connection.getContentType() == null ? "" : connection.getContentType().toLowerCase(Locale.ROOT);
			String path = URI.create(target).getPath() == null ? "" : URI.create(target).getPath().toLowerCase(Locale.ROOT);

			if (path.endsWith(".m3u8")) {
				connection.disconnect();
				throw new UnsupportedStreamException("HLS (.m3u8) streams are not supported");
			}
			boolean playlist = path.endsWith(".pls") || path.endsWith(".m3u")
					|| type.contains("mpegurl") || type.contains("scpls") || type.contains("x-pls");
			if (playlist) {
				String body = readText(connection);
				connection.disconnect();
				String next = firstEntry(body);
				if (next == null) {
					throw new PlaybackException("That playlist has no stream in it");
				}
				target = next;
				continue;
			}
			if (type.contains("aac") || type.contains("mp4") || type.contains("aacp")) {
				connection.disconnect();
				throw new UnsupportedStreamException("AAC streams are not supported, use an MP3 stream");
			}
			if (type.contains("ogg") || type.contains("opus") || type.contains("flac")) {
				connection.disconnect();
				throw new UnsupportedStreamException("Only MP3 radio streams are supported");
			}
			if (type.startsWith("text/html")) {
				connection.disconnect();
				throw new UnsupportedStreamException("That link is a web page, not an audio stream");
			}
			return connection;
		}
		throw new PlaybackException("Too many playlist redirects");
	}

	private static HttpURLConnection open(String start) throws IOException {
		String current = start;
		for (int hop = 0; hop < 6; hop++) {
			URL target;
			try {
				target = URI.create(current).toURL();
			} catch (IllegalArgumentException e) {
				throw new PlaybackException("That is not a valid link");
			}
			HttpURLConnection connection = (HttpURLConnection) target.openConnection();
			connection.setInstanceFollowRedirects(false);
			connection.setConnectTimeout(10_000);
			connection.setReadTimeout(15_000);
			connection.setRequestProperty("Icy-MetaData", "1");
			connection.setRequestProperty("User-Agent", USER_AGENT);
			int code = connection.getResponseCode();
			if (code >= 300 && code < 400) {
				String location = connection.getHeaderField("Location");
				connection.disconnect();
				if (location == null) {
					throw new PlaybackException("The station redirected nowhere");
				}
				current = URI.create(current).resolve(location).toString();
				continue;
			}
			if (code != 200) {
				connection.disconnect();
				throw new PlaybackException("The station answered with HTTP " + code);
			}
			return connection;
		}
		throw new PlaybackException("Too many redirects");
	}

	private static String readText(HttpURLConnection connection) throws IOException {
		try (InputStream in = connection.getInputStream()) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int n;
			while ((n = in.read(buffer)) > 0 && out.size() < 65_536) {
				out.write(buffer, 0, n);
			}
			return out.toString(StandardCharsets.UTF_8);
		}
	}

	private static String firstEntry(String body) {
		for (String raw : body.split("\\R")) {
			String line = raw.trim();
			if (line.regionMatches(true, 0, "File", 0, 4) && line.indexOf('=') > 0) {
				line = line.substring(line.indexOf('=') + 1).trim();
			}
			if (line.startsWith("http://") || line.startsWith("https://")) {
				return line;
			}
		}
		return null;
	}

	private static int parseInt(String value) {
		try {
			return value == null ? 0 : Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	static final class UnsupportedStreamException extends IOException {
		UnsupportedStreamException(String message) {
			super(message);
		}
	}

	static final class PlaybackException extends IOException {
		PlaybackException(String message) {
			super(message);
		}
	}
}
