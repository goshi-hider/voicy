package dev.goshi.voicy.client.music;

import dev.goshi.voicy.Voicy;
import dev.goshi.voicy.VoicyClient;
import dev.goshi.voicy.client.ClientVoiceManager;
import dev.goshi.voicy.config.DuckPriority;
import dev.goshi.voicy.config.MusicMode;
import dev.goshi.voicy.config.RepeatMode;
import dev.goshi.voicy.config.VoicyConfig;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public final class MusicPlayer {
	public enum State {
		IDLE,
		LOADING,
		PLAYING,
		PAUSED,
		ERROR
	}

	public static final MusicPlayer INSTANCE = new MusicPlayer();

	private static final int MAX_QUEUED = 6;

	private record Queued(int buffer, int frames, float level) {
	}

	private final VoicyConfig config = VoicyConfig.INSTANCE;
	private final MusicLibrary library = new MusicLibrary();
	private final Random random = new Random();
	private final Deque<Integer> history = new ArrayDeque<>();
	private final Deque<Queued> queued = new ArrayDeque<>();

	private PlaybackSession session;
	private int alSource;
	private int formatRate;
	private int formatChannels;
	private long playedFrames;
	private long positionFrames;

	private State state = State.IDLE;
	private boolean userPaused;
	private String errorMessage = "";
	private int failStreak;

	private int currentIndex = -1;
	private RadioStation currentStation;
	private float level;
	private float duck = 1f;
	private float appliedGain = -1f;

	private MusicPlayer() {
	}

	public State state() {
		return state;
	}

	public boolean hasMedia() {
		return state != State.IDLE;
	}

	public boolean isPlaying() {
		return state == State.PLAYING || state == State.LOADING && !userPaused;
	}

	public boolean isLive() {
		return config.musicMode() == MusicMode.RADIO;
	}

	public String errorMessage() {
		return errorMessage;
	}

	public float level() {
		return level;
	}

	public MusicLibrary library() {
		return library;
	}

	public int currentIndex() {
		return config.musicMode() == MusicMode.LOCAL ? currentIndex : -1;
	}

	public String currentStationUrl() {
		return config.musicMode() == MusicMode.RADIO && currentStation != null && state != State.IDLE
				? currentStation.url() : "";
	}

	public String title() {
		if (state == State.ERROR) {
			return errorMessage;
		}
		if (isLive()) {
			if (currentStation == null) {
				return "";
			}
			String song = session == null ? "" : session.liveTitle;
			return song.isBlank() ? currentStation.name() : song;
		}
		MusicTrack track = currentTrack();
		return track == null ? "" : track.title();
	}

	public String subtitle() {
		if (state == State.ERROR) {
			return isLive() && currentStation != null ? currentStation.name() : "";
		}
		if (isLive()) {
			if (currentStation == null) {
				return "";
			}
			String song = session == null ? "" : session.liveTitle;
			return song.isBlank() ? currentStation.tagline() : currentStation.name();
		}
		MusicTrack track = currentTrack();
		if (track == null) {
			return "";
		}
		return track.artist().isBlank() ? library.tracks().size() == 0 ? "" : "Track " + (currentIndex + 1) + " of "
				+ library.tracks().size() : track.artist();
	}

	public double durationSeconds() {
		PlaybackSession s = session;
		if (s == null || s.live || s.totalFrames <= 0 || s.fileRate <= 0) {
			return -1;
		}
		return s.totalFrames / (double) s.fileRate;
	}

	public double positionSeconds() {
		PlaybackSession s = session;
		if (s == null || s.live || s.fileRate <= 0) {
			return 0;
		}
		return Math.min(positionFrames, Math.max(0, s.totalFrames)) / (double) s.fileRate;
	}

	private MusicTrack currentTrack() {
		List<MusicTrack> tracks = library.tracks();
		return currentIndex >= 0 && currentIndex < tracks.size() ? tracks.get(currentIndex) : null;
	}

	public void refreshLibrary() {
		library.scan();
		if (config.musicMode() == MusicMode.LOCAL && currentIndex >= library.tracks().size()) {
			stop();
		}
	}

	public void setMode(MusicMode mode) {
		if (config.musicMode() == mode) {
			return;
		}
		stop();
		currentIndex = -1;
		currentStation = null;
		config.setMusicMode(mode);
	}

	public void setVolume(float volume) {
		config.setMusicVolume(volume);
	}

	public void playIndex(int index) {
		List<MusicTrack> tracks = library.tracks();
		if (index < 0 || index >= tracks.size()) {
			return;
		}
		if (config.musicMode() != MusicMode.LOCAL) {
			setMode(MusicMode.LOCAL);
		}
		if (currentIndex >= 0 && currentIndex != index) {
			history.push(currentIndex);
			while (history.size() > 50) {
				history.removeLast();
			}
		}
		currentIndex = index;
		startSession(PlaybackSession.local(tracks.get(index).path(), 0L));
	}

	public void playStation(RadioStation station) {
		if (config.musicMode() != MusicMode.RADIO) {
			setMode(MusicMode.RADIO);
		}
		currentStation = station;
		startSession(PlaybackSession.radio(station.url()));
	}

	public void togglePause() {
		if (state == State.IDLE || state == State.ERROR) {
			resumeSelection();
			return;
		}
		if (isLive()) {
			if (userPaused) {
				if (currentStation != null) {
					playStation(currentStation);
				}
			} else {
				RadioStation keep = currentStation;
				stopSession();
				currentStation = keep;
				userPaused = true;
				state = State.PAUSED;
			}
			return;
		}
		if (session == null || alSource == 0) {
			return;
		}
		if (userPaused) {
			userPaused = false;
			state = State.LOADING;
			AL10.alSourcePlay(alSource);
		} else {
			userPaused = true;
			state = State.PAUSED;
			AL10.alSourcePause(alSource);
		}
	}

	private void resumeSelection() {
		if (config.musicMode() == MusicMode.RADIO) {
			RadioStation station = currentStation;
			if (station == null) {
				List<RadioStation> all = RadioStations.all();
				station = all.isEmpty() ? null : all.get(0);
			}
			if (station != null) {
				playStation(station);
			}
			return;
		}
		if (library.tracks().isEmpty()) {
			library.scan();
		}
		int total = library.tracks().size();
		if (total == 0) {
			return;
		}
		int index = currentIndex >= 0 && currentIndex < total ? currentIndex
				: config.musicShuffle() ? random.nextInt(total) : 0;
		playIndex(index);
	}

	public void next() {
		if (isLive()) {
			stepStation(1);
			return;
		}
		int target = pickNext(false);
		if (target >= 0) {
			playIndex(target);
		}
	}

	public void previous() {
		if (isLive()) {
			stepStation(-1);
			return;
		}
		if (positionSeconds() > 3.0 && session != null) {
			startSession(PlaybackSession.local(library.tracks().get(currentIndex).path(), 0L));
			return;
		}
		List<MusicTrack> tracks = library.tracks();
		if (tracks.isEmpty()) {
			return;
		}
		if (!history.isEmpty()) {
			int target = history.pop();
			if (target < tracks.size()) {
				currentIndex = target;
				startSession(PlaybackSession.local(tracks.get(target).path(), 0L));
				return;
			}
		}
		int target = currentIndex <= 0 ? tracks.size() - 1 : currentIndex - 1;
		playIndex(target);
	}

	private void stepStation(int delta) {
		List<RadioStation> all = RadioStations.all();
		if (all.isEmpty()) {
			return;
		}
		int at = -1;
		for (int i = 0; i < all.size(); i++) {
			if (currentStation != null && all.get(i).url().equalsIgnoreCase(currentStation.url())) {
				at = i;
				break;
			}
		}
		playStation(all.get(Math.floorMod(at + delta, all.size())));
	}

	public void seek(double fraction) {
		PlaybackSession s = session;
		if (s == null || s.live || s.totalFrames <= 0 || currentIndex < 0) {
			return;
		}
		MusicTrack track = currentTrack();
		if (track == null) {
			return;
		}
		long frame = (long) (Math.max(0, Math.min(0.999, fraction)) * s.totalFrames);
		boolean wasPaused = userPaused;
		startSession(PlaybackSession.local(track.path(), frame));
		positionFrames = frame;
		if (wasPaused) {
			userPaused = true;
			state = State.PAUSED;
		}
	}

	public void stop() {
		stopSession();
		currentIndex = config.musicMode() == MusicMode.LOCAL ? currentIndex : -1;
		state = State.IDLE;
		userPaused = false;
		errorMessage = "";
		failStreak = 0;
		level = 0;
	}

	public void shutdown() {
		stopSession();
		if (alSource != 0) {
			try {
				AL10.alDeleteSources(alSource);
			} catch (RuntimeException ignored) {
			}
			alSource = 0;
		}
		state = State.IDLE;
	}

	private int pickNext(boolean automatic) {
		int total = library.tracks().size();
		if (total == 0) {
			return -1;
		}
		RepeatMode repeat = config.musicRepeat();
		if (automatic && repeat == RepeatMode.ONE && currentIndex >= 0) {
			return currentIndex;
		}
		if (config.musicShuffle()) {
			if (total == 1) {
				return automatic && repeat == RepeatMode.OFF ? -1 : 0;
			}
			int pick;
			do {
				pick = random.nextInt(total);
			} while (pick == currentIndex);
			return pick;
		}
		int pick = currentIndex + 1;
		if (pick >= total) {
			if (automatic && repeat == RepeatMode.OFF) {
				return -1;
			}
			pick = 0;
		}
		return pick;
	}

	private void startSession(PlaybackSession next) {
		stopSession();
		if (!ensureSource()) {
			return;
		}
		session = next;
		userPaused = false;
		state = State.LOADING;
		errorMessage = "";
		playedFrames = 0;
		positionFrames = next.startFrame;
		next.start();
	}

	private boolean ensureSource() {
		if (alSource != 0) {
			return true;
		}
		try {
			AL10.alGetError();
			int source = AL10.alGenSources();
			if (AL10.alGetError() != AL10.AL_NO_ERROR || source == 0) {
				throw new IllegalStateException("could not create an audio source");
			}
			AL10.alSourcef(source, AL10.AL_PITCH, 1.0f);
			AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
			AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
			AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0f);
			AL10.alSourcef(source, AL10.AL_GAIN, config.musicVolume());
			alSource = source;
			appliedGain = -1f;
			return true;
		} catch (RuntimeException e) {
			Voicy.LOGGER.warn("[Voicy] Music audio setup failed: {}", e.toString());
			fail("Audio device unavailable");
			return false;
		}
	}

	private void stopSession() {
		if (session != null) {
			session.close();
			session = null;
		}
		if (alSource != 0) {
			try {
				AL10.alSourceStop(alSource);
				AL10.alSourcei(alSource, AL10.AL_BUFFER, 0);
				for (Queued entry : queued) {
					AL10.alDeleteBuffers(entry.buffer());
				}
				AL10.alGetError();
			} catch (RuntimeException ignored) {
			}
		}
		queued.clear();
		playedFrames = 0;
		positionFrames = 0;
		formatRate = 0;
		formatChannels = 0;
	}

	private void fail(String message) {
		stopSession();
		errorMessage = message == null || message.isBlank() ? "Playback failed" : message;
		state = State.ERROR;
		userPaused = false;
	}

	public void tick() {
		PlaybackSession s = session;
		if (s == null || alSource == 0) {
			level *= 0.8f;
			return;
		}
		try {
			AL10.alGetError();
			updateDuck();
			applyGain();
			recycleBuffers();

			if (s.error != null && s.queue.isEmpty() && queued.isEmpty()) {
				onSessionError(s);
				return;
			}

			while (queued.size() < MAX_QUEUED) {
				PlaybackSession.Pcm chunk = s.queue.peek();
				if (chunk == null) {
					break;
				}
				if (!queued.isEmpty() && (chunk.rate() != formatRate || chunk.channels() != formatChannels)) {
					break;
				}
				s.queue.poll();
				upload(chunk);
			}

			if (!queued.isEmpty() && !userPaused) {
				if (AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
					AL10.alSourcePlay(alSource);
				}
				state = State.PLAYING;
			} else if (queued.isEmpty() && !userPaused && state == State.PLAYING && !s.finished) {
				state = State.LOADING;
			}

			if (!s.live && !queued.isEmpty()) {
				positionFrames = s.startFrame + playedFrames + Math.max(0, AL10.alGetSourcei(alSource, AL11.AL_SAMPLE_OFFSET));
			}

			if (s.finished && s.queue.isEmpty() && queued.isEmpty() && s.error == null) {
				onSessionEnded(s);
				return;
			}

			float target = !queued.isEmpty() && state == State.PLAYING ? queued.peekFirst().level() : 0f;
			level += (target - level) * 0.35f;
		} catch (RuntimeException e) {
			Voicy.LOGGER.warn("[Voicy] Music tick failed: {}", e.toString());
			fail("Audio error");
		}
	}

	private void updateDuck() {
		DuckPriority priority = config.musicPriority();
		float pressure = 0f;
		if (priority.includesPlayers()) {
			ClientVoiceManager voice = VoicyClient.voiceManager();
			if (voice != null && voice.isAnyoneTalking()) {
				pressure = 1f;
			}
		}
		if (priority.includesGame()) {
			pressure = Math.max(pressure, MusicDucker.gamePressure());
		}
		float target = 1f - config.musicDuckDepth() * pressure;

		duck += (target - duck) * (target < duck ? 0.3f : 0.07f);
	}

	private void applyGain() {
		float gain = config.musicVolume() * duck;
		if (Math.abs(gain - appliedGain) > 0.002f) {
			AL10.alSourcef(alSource, AL10.AL_GAIN, gain);
			appliedGain = gain;
		}
	}

	private void recycleBuffers() {
		int processed = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_PROCESSED);
		for (int i = 0; i < processed && !queued.isEmpty(); i++) {
			int buffer = AL10.alSourceUnqueueBuffers(alSource);
			Queued entry = queued.pollFirst();
			playedFrames += entry.frames();
			AL10.alDeleteBuffers(buffer);
		}
	}

	private void upload(PlaybackSession.Pcm chunk) {
		int buffer = AL10.alGenBuffers();
		ShortBuffer data = MemoryUtil.memAllocShort(chunk.data().length);
		try {
			data.put(chunk.data()).flip();
			int format = chunk.channels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
			AL10.alBufferData(buffer, format, data, chunk.rate());
		} finally {
			MemoryUtil.memFree(data);
		}
		AL10.alSourceQueueBuffers(alSource, buffer);
		if (AL10.alGetError() != AL10.AL_NO_ERROR) {
			AL10.alDeleteBuffers(buffer);
			throw new IllegalStateException("could not queue audio");
		}
		formatRate = chunk.rate();
		formatChannels = chunk.channels();
		queued.addLast(new Queued(buffer, chunk.frames(), chunk.rms()));
	}

	private void onSessionError(PlaybackSession s) {
		String message = s.error;
		if (!s.live) {
			failStreak++;
			if (failStreak < library.tracks().size()) {
				int target = pickNext(true);
				if (target >= 0 && target != currentIndex) {
					currentIndex = target;
					startSession(PlaybackSession.local(library.tracks().get(target).path(), 0L));
					return;
				}
			}
		}
		fail(message);
	}

	private void onSessionEnded(PlaybackSession s) {
		failStreak = 0;
		if (s.live) {
			fail("The station closed the connection");
			return;
		}
		int target = pickNext(true);
		if (target < 0) {
			stopSession();
			state = State.IDLE;
			level = 0;
			return;
		}
		history.push(currentIndex);
		currentIndex = target;
		startSession(PlaybackSession.local(library.tracks().get(target).path(), 0L));
	}
}
