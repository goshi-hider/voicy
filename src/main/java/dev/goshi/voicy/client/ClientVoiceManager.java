package dev.goshi.voicy.client;

import dev.goshi.voicy.SpeakerManager;
import dev.goshi.voicy.Voicy;
import dev.goshi.voicy.api.VoicyVoiceChatAPI;
import dev.goshi.voicy.audio.capture.AudioPreprocessor;
import dev.goshi.voicy.audio.capture.MicrophoneCapture;
import dev.goshi.voicy.audio.capture.VoiceActivityGate;
import dev.goshi.voicy.audio.codec.OpusCodec;
import dev.goshi.voicy.network.payload.ApiCommandPayload;
import dev.goshi.voicy.audio.playback.JitterBuffer;
import dev.goshi.voicy.audio.spatial.OpenALAudioEngine;
import dev.goshi.voicy.audio.spatial.SpatialAudioSource;
import dev.goshi.voicy.config.VoicyConfig;
import net.voicechat.api.VoiceChatAPI;
import net.voicechat.api.VoiceChatAPI;
import dev.goshi.voicy.network.payload.HandshakePayload;
import dev.goshi.voicy.network.payload.SpeakerSyncPayload;
import dev.goshi.voicy.network.payload.VoiceFramePayload;
import dev.goshi.voicy.network.udp.UdpVoiceClient;
import io.github.jaredmdobson.concentus.OpusDecoder;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.openal.AL10;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client-side orchestrator: capture → encode → transmit, receive → jitter → decode → spatial playback.
 */
public final class ClientVoiceManager implements AutoCloseable {

	private final VoicyConfig config;
	private final MicrophoneCapture capture;
	private final AudioPreprocessor preprocessor;
	private final OpusCodec codec;
	private final OpenALAudioEngine audioEngine;
	private final Map<UUID, JitterBuffer> jitterBuffers = new ConcurrentHashMap<>();
	private final Map<UUID, OpusDecoder> playbackDecoders = new ConcurrentHashMap<>();
	private final Map<UUID, SpatialAudioSource> spatialSources = new ConcurrentHashMap<>();
	private final Map<UUID, Vec3d> speakerPositions = new ConcurrentHashMap<>();
	private final Map<UUID, VoiceChatAPI.VoiceState> synchronizedVoiceStates = new ConcurrentHashMap<>();
	private final Map<UUID, Long> lastVoiceFrameNanos = new ConcurrentHashMap<>();
	private final Set<UUID> talkingSources = ConcurrentHashMap.newKeySet();
	private static final long TALKING_TIMEOUT_NANOS = 400_000_000L;

	// Bounds for receive-side packet-loss concealment: only conceal while a speaker has
	// been sending recently (mid-utterance network jitter) and only for a few frames in a
	// row, so a speaker who has actually stopped talking goes quiet instead of hissing
	// forever on synthesized concealment audio.

	private final AtomicBoolean selfMuted = new AtomicBoolean(false);
	private final AtomicBoolean selfDeafened = new AtomicBoolean(false);
	private final AtomicBoolean pttHeld = new AtomicBoolean(false);
	private final AtomicLong sequence = new AtomicLong();

	private final VoiceActivityGate voiceActivityGate;
	private static final int FADE_SAMPLES = 240; // ~5ms at 48kHz
	private volatile boolean sendingActive = false;

	private Thread captureThread;
	private HandshakePayload.TransportMode transportMode = HandshakePayload.TransportMode.FABRIC_PAYLOAD;
	private UdpVoiceClient udpClient;
	private volatile float inputLevel;
	private final AtomicLong receivedSpeakerFrames = new AtomicLong();
	private final AtomicLong queuedSpeakerFrames = new AtomicLong();
	private final AtomicBoolean loggedSpeakerPcm = new AtomicBoolean();

	public ClientVoiceManager(VoicyConfig config) throws Exception {
		this.config = config;
		this.capture = new MicrophoneCapture(config);
		this.preprocessor = new AudioPreprocessor(config.activationThreshold());
		this.voiceActivityGate = new VoiceActivityGate(config.activationThreshold(), 0.55f, 400_000_000L);
		this.codec = new OpusCodec(config);
		this.audioEngine = new OpenALAudioEngine(config.sampleRate());
	}

	public void setTransport(HandshakePayload handshake) {
		this.transportMode = handshake.transportMode();
		if (handshake.transportMode() == HandshakePayload.TransportMode.UDP_FALLBACK) {
			if (udpClient == null) {
				udpClient = new UdpVoiceClient();
			}
			udpClient.startSession(MinecraftClient.getInstance().getSession().getUsername(), handshake.udpHost(), handshake.udpPort(), handshake.sessionSecret());
		} else if (udpClient != null) {
			udpClient.close();
			udpClient = null;
		}
		Voicy.LOGGER.info("[Voicy] Voice transport negotiated: {} (sampleRate={}, frameMs={}, udpHost={})",
				handshake.transportMode(), handshake.sampleRate(), handshake.frameDurationMs(), handshake.udpHost());
	}

	public void startCapture() {
		if (captureThread != null) {
			return;
		}
		try {
			capture.open();
		} catch (Exception e) {
			Voicy.LOGGER.error("[Voicy] Failed to open microphone", e);
			return;
		}
		captureThread = Thread.ofVirtual().name("voicy-capture").start(this::captureLoop);
	}

	public void stopCapture() {
		if (captureThread != null) {
			captureThread.interrupt();
			captureThread = null;
		}
		try {
			capture.close();
		} catch (Exception ignored) {
		}
	}

	private void captureLoop() {
		short[] buffer = new short[config.frameSizeSamples()];
		while (!Thread.currentThread().isInterrupted()) {
			int samples = capture.read(buffer);
			if (samples <= 0) {
				inputLevel = 0.0f;
				continue;
			}
			if (samples < buffer.length) {
				Arrays.fill(buffer, samples, buffer.length, (short) 0);
			}
			float level = capture.peakLevel(buffer, samples);
			inputLevel = inputLevel * 0.68f + level * 0.32f;
			if (inputLevel <= 0.001f) {
				continue;
			}
			preprocessor.process(buffer, samples);

			boolean effectiveTransmit = resolveTransmitState(level);
			if (!effectiveTransmit) {
				if (sendingActive) {
					// Fade the final frame out instead of just stopping mid-amplitude.
					fadeOut(buffer, samples, Math.min(FADE_SAMPLES, samples));
					encodeAndSend(buffer);
					sendingActive = false;
				}
				continue;
			}
			if (!sendingActive) {
				// Fade the first frame in instead of starting at full amplitude.
				fadeIn(buffer, samples, Math.min(FADE_SAMPLES, samples));
				sendingActive = true;
			}
			encodeAndSend(buffer);
		}
	}

	private boolean resolveTransmitState(float level) {
		if (selfMuted.get()) {
			sendingActive = false;
			voiceActivityGate.reset();
			return false;
		}
		return switch (config.activationMode()) {
			case ALWAYS_ON -> true;
			case PUSH_TO_TALK -> pttHeld.get();
			case VOICE_ACTIVATION -> voiceActivityGate.update(level, System.nanoTime());
		};
	}

	private void encodeAndSend(short[] buffer) {
		try {
			byte[] opus = codec.encode(buffer);
			sendFrame(opus);
		} catch (Exception e) {
			Voicy.LOGGER.debug("[Voicy] encode error: {}", e.getMessage());
		}
	}

	private static void fadeIn(short[] samples, int count, int fadeSamples) {
		if (fadeSamples <= 0) {
			return;
		}
		for (int i = 0; i < fadeSamples && i < count; i++) {
			float gain = i / (float) fadeSamples;
			samples[i] = (short) Math.round(samples[i] * gain);
		}
	}

	private static void fadeOut(short[] samples, int count, int fadeSamples) {
		if (fadeSamples <= 0) {
			return;
		}
		int start = Math.max(0, count - fadeSamples);
		for (int i = start; i < count; i++) {
			float gain = (count - i) / (float) fadeSamples;
			samples[i] = (short) Math.round(samples[i] * gain);
		}
	}

	private void sendFrame(byte[] opus) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		VoiceFramePayload payload = new VoiceFramePayload(
				client.player.getUuid(),
				sequence.incrementAndGet(),
				(float) config.proximityRadius(),
				opus
		);
		if (transportMode == HandshakePayload.TransportMode.UDP_FALLBACK && udpClient != null && udpClient.isRunning()) {
			try {
				udpClient.sendFrame(payload);
				return;
			} catch (Exception e) {
				Voicy.LOGGER.warn("[Voicy] UDP send failed, falling back to fabric payload: {}", e.getMessage());
			}
		}
		ClientPlayNetworking.send(payload);
	}

	public void onVoiceFrame(VoiceFramePayload frame) {
		if (selfDeafened.get()) {
			return;
		}
		if (frame.opusData() == null || frame.opusData().length == 0) {
			return;
		}
		if (SpeakerManager.isSpeakerUuid(frame.speakerId())) {
			long received = receivedSpeakerFrames.incrementAndGet();
			if (received == 1 || received % 25 == 0) {
				Voicy.LOGGER.info("[Voicy] Received {} speaker voice frames for {}", received, frame.speakerId());
			}
		}
		lastVoiceFrameNanos.put(frame.speakerId(), System.nanoTime());
		if (talkingSources.add(frame.speakerId())) {
			VoicyVoiceChatAPI.publishTalking(frame.speakerId(), true);
		}
		jitterBuffers.computeIfAbsent(frame.speakerId(), id ->
				new JitterBuffer(config.jitterBufferMs(), config.frameDurationMs())
		).push(frame);
	}

	public void onSpeakerSync(SpeakerSyncPayload payload) {
		UUID speakerUuid = SpeakerManager.speakerUuid(payload.speakerId());
		speakerPositions.put(speakerUuid, new Vec3d(payload.x(), payload.y(), payload.z()));
	}

	public void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return;
		}
		long now = System.nanoTime();
		for (UUID sourceId : talkingSources) {
			Long lastFrame = lastVoiceFrameNanos.get(sourceId);
			if (lastFrame == null || now - lastFrame > TALKING_TIMEOUT_NANOS) {
				if (talkingSources.remove(sourceId)) {
					VoicyVoiceChatAPI.publishTalking(sourceId, false);
				}
			}
		}
		for (var entry : jitterBuffers.entrySet()) {
			UUID speakerId = entry.getKey();
			JitterBuffer buffer = entry.getValue();
			try {
				if (SpeakerManager.isSpeakerUuid(speakerId)) {
					Vec3d speakerPos = speakerPositions.get(speakerId);
					if (speakerPos == null) {
						continue;
					}
					SpatialAudioSource source = spatialSources.computeIfAbsent(speakerId,
							id -> new SpatialAudioSource(id, config));
					boolean occluded = false;
					source.updatePosition(client.player, speakerPos, occluded);
					double distance = client.player.getEyePos().distanceTo(speakerPos);
					double gain = Math.max(0.35, 1.0 / (1.0 + (distance / 12.0)));
						source.setGain((float) (gain * playbackVolume(speakerId) * (occluded ? 0.35 : 1.0)));
					VoiceFramePayload readyFrame;
					while ((readyFrame = buffer.pollReady()) != null) {
						boolean concealment = readyFrame.opusData().length == 0;
						short[] pcm = codec.decode(concealment ? null : readyFrame.opusData(), concealment, decoderFor(speakerId));
						pcm = processPlaybackAudio(speakerId, pcm);
						if (loggedSpeakerPcm.compareAndSet(false, true)) {
							Voicy.LOGGER.info("[Voicy] First speaker PCM: samples={}, peak={}", pcm.length, peakLevel(pcm));
						}
						int alBuffer = audioEngine.uploadPcm(pcm);
						source.queuePcm(alBuffer);
						long queued = queuedSpeakerFrames.incrementAndGet();
						if (queued == 1 || queued % 25 == 0) {
							Voicy.LOGGER.info("[Voicy] Queued {} speaker PCM frames for {}", queued, speakerId);
						}
					}
					source.recycleProcessedBuffers();
					continue;
				}
				if (!(client.world.getPlayerByUuid(speakerId) instanceof OtherClientPlayerEntity speaker)) {
					continue;
				}
				SpatialAudioSource source = spatialSources.computeIfAbsent(speakerId,
						id -> new SpatialAudioSource(id, config));
				boolean occluded = config.realisticOcclusion() && isOccluded(client, speaker);
				source.updatePosition(client.player, speaker, occluded);
				double distance = client.player.getEyePos().distanceTo(speaker.getEyePos());
				double gain = Math.max(0.35, 1.0 / (1.0 + (distance / 12.0)));
				source.setGain((float) (gain * playbackVolume(speakerId) * (occluded ? 0.35 : 1.0)));
				VoiceFramePayload readyFrame;
				while ((readyFrame = buffer.pollReady()) != null) {
					boolean concealment = readyFrame.opusData().length == 0;
					short[] pcm = codec.decode(concealment ? null : readyFrame.opusData(), concealment, decoderFor(speakerId));
					pcm = processPlaybackAudio(speakerId, pcm);
					int alBuffer = audioEngine.uploadPcm(pcm);
					source.queuePcm(alBuffer);
				}
				source.recycleProcessedBuffers();
			} catch (Exception e) {
				Voicy.LOGGER.error("[Voicy] playback error for {}", speakerId, e);
			}
		}
	}

	private OpusDecoder decoderFor(UUID sourceId) throws Exception {
		OpusDecoder decoder = playbackDecoders.get(sourceId);
		if (decoder != null) {
			return decoder;
		}
		OpusDecoder created = codec.createDecoder();
		OpusDecoder existing = playbackDecoders.putIfAbsent(sourceId, created);
		return existing == null ? created : existing;
	}

	private float playbackVolume(UUID sourceId) {
		VoiceChatAPI api = VoiceChatAPI.getInstance();
		return api == null ? 1.0f : api.getVolumeManager().getEffectiveVolume(sourceId);
	}

	private short[] processPlaybackAudio(UUID sourceId, short[] pcm) {
		VoiceChatAPI api = VoiceChatAPI.getInstance();
		return api == null ? pcm : api.getVolumeManager().processAudio(sourceId, pcm, config.sampleRate());
	}

	private static int peakLevel(short[] pcm) {
		int peak = 0;
		for (short sample : pcm) {
			peak = Math.max(peak, Math.abs((int) sample));
		}
		return peak;
	}

	private boolean isOccluded(MinecraftClient client, OtherClientPlayerEntity speaker) {
		var start = client.player.getEyePos();
		var end = speaker.getEyePos();
		var hit = client.world.raycast(new net.minecraft.world.RaycastContext(
				start,
				end,
				net.minecraft.world.RaycastContext.ShapeType.VISUAL,
				net.minecraft.world.RaycastContext.FluidHandling.NONE,
				client.player
		));
		return hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS;
	}

	public float getInputLevel() {
		return Math.min(1.0f, Math.max(0.0f, inputLevel));
	}

	public void setSelfMuted(boolean muted) {
		selfMuted.set(muted);
		sendSelfStateUpdate();
	}

	public boolean isSelfMuted() {
		return selfMuted.get();
	}

	public void setSelfDeafened(boolean deafened) {
		selfDeafened.set(deafened);
		sendSelfStateUpdate();
	}

	public boolean isSelfDeafened() {
		return selfDeafened.get();
	}

	public void applyServerVoiceState(boolean muted, boolean deafened) {
		selfMuted.set(muted);
		selfDeafened.set(deafened);
	}

	public void applyServerVoiceState(UUID playerId, boolean muted, boolean deafened) {
		VoiceChatAPI.VoiceState previous = synchronizedVoiceStates.put(
				playerId, new VoiceChatAPI.VoiceState(muted, deafened, null));
		VoicyVoiceChatAPI.publishVoiceState(playerId,
				previous != null && previous.muted(), muted,
				previous != null && previous.deafened(), deafened);
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && client.player.getUuid().equals(playerId)) {
			applyServerVoiceState(muted, deafened);
		}
	}

	public VoiceChatAPI.VoiceState getSynchronizedVoiceState(UUID playerId) {
		return synchronizedVoiceStates.get(playerId);
	}

	private void sendSelfStateUpdate() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !ClientPlayNetworking.canSend(ApiCommandPayload.TYPE)) {
			return;
		}
		UUID playerId = client.player.getUuid();
		ClientPlayNetworking.send(new ApiCommandPayload(
				selfMuted.get() ? ApiCommandPayload.ApiCommandType.MUTE : ApiCommandPayload.ApiCommandType.UNMUTE,
				playerId,
				Voicy.id("client"),
				selfMuted.get()));
		ClientPlayNetworking.send(new ApiCommandPayload(
				selfDeafened.get() ? ApiCommandPayload.ApiCommandType.DEAFEN : ApiCommandPayload.ApiCommandType.UNDEAFEN,
				playerId,
				Voicy.id("client"),
				selfDeafened.get()));
	}

	public void setPushToTalkHeld(boolean held) {
		pttHeld.set(held);
	}

	public MicrophoneCapture capture() {
		return capture;
	}

	@Override
	public void close() {
		if (captureThread != null) {
			captureThread.interrupt();
		}
		spatialSources.values().forEach(SpatialAudioSource::close);
		spatialSources.clear();
		playbackDecoders.clear();
		capture.close();
		try {
			codec.close();
		} catch (Exception ignored) {
		}
		audioEngine.close();
	}
}