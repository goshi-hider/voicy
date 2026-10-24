package dev.goshi.voicy.client.music;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class IcyInputStream extends FilterInputStream {
	private static final Pattern TITLE = Pattern.compile("StreamTitle='(.*?)';", Pattern.DOTALL);

	private final int interval;
	private final Consumer<String> onTitle;
	private int remaining;

	IcyInputStream(InputStream in, int interval, Consumer<String> onTitle) {
		super(in);
		this.interval = interval;
		this.remaining = interval;
		this.onTitle = onTitle;
	}

	@Override
	public int read() throws IOException {
		byte[] one = new byte[1];
		int n = read(one, 0, 1);
		return n <= 0 ? -1 : one[0] & 0xFF;
	}

	@Override
	public int read(byte[] buffer, int offset, int length) throws IOException {
		if (length == 0) {
			return 0;
		}
		if (remaining == 0) {
			readMetadata();
			remaining = interval;
		}
		int n = in.read(buffer, offset, Math.min(length, remaining));
		if (n > 0) {
			remaining -= n;
		}
		return n;
	}

	@Override
	public long skip(long count) throws IOException {
		byte[] scratch = new byte[(int) Math.min(count, 4096)];
		long skipped = 0;
		while (skipped < count) {
			int n = read(scratch, 0, (int) Math.min(scratch.length, count - skipped));
			if (n <= 0) {
				break;
			}
			skipped += n;
		}
		return skipped;
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	private void readMetadata() throws IOException {
		int blocks = in.read();
		if (blocks < 0) {
			throw new IOException("Stream ended");
		}
		if (blocks == 0) {
			return;
		}
		byte[] meta = new byte[blocks * 16];
		int read = 0;
		while (read < meta.length) {
			int n = in.read(meta, read, meta.length - read);
			if (n < 0) {
				throw new IOException("Stream ended");
			}
			read += n;
		}
		Matcher matcher = TITLE.matcher(new String(meta, StandardCharsets.UTF_8));
		if (matcher.find()) {
			onTitle.accept(matcher.group(1).trim());
		}
	}
}
