package dev.goshi.voicy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.voicechat.api.config.VoiceChatConfigView;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VoicyConfig implements VoiceChatConfigView {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final float ACTIVATION_THRESHOLD_MIN = 0.01f;
	public static final float ACTIVATION_THRESHOLD_MAX = 0.35f;
	public static final VoicyConfig INSTANCE = load();

	private double proximityRadius = 48.0;
	private double proximityFalloff = 1.0;
	private int sampleRate = 48000;
	private int frameDurationMs = 20;
	private ActivationMode activationMode = ActivationMode.VOICE_ACTIVATION;
	private float activationThreshold = 0.12f;
	private boolean preferUdpFallback = true;
	private boolean realisticOcclusion = true;
	private AnimationStyle animationStyle = AnimationStyle.SQUISH;
	private float splitHeight = 0.5f;
	private boolean echoGuard = false;
	private String micDevice = "";
	private String selectedInputDevice = "";
	private String selectedOutputDevice = "";
	private int maxOpusBitrate = 40000;
	private int jitterBufferMs = 120;
	private Map<UUID, Float> playerVolumes = new ConcurrentHashMap<>();

	private VoicyConfig() {
	}

	private static VoicyConfig load() {
		VoicyConfig config = new VoicyConfig();
		try {
			Path configPath = FabricLoader.getInstance().getConfigDir().resolve("voicy.json");
			if (Files.exists(configPath)) {
				try (Reader reader = Files.newBufferedReader(configPath)) {
					VoicyConfig loaded = GSON.fromJson(reader, VoicyConfig.class);
					if (loaded != null) {
						config = loaded;
					}
				}
			}
		} catch (Throwable ignored) {

		}

		config.activationThreshold = clampActivationThreshold(config.activationThreshold);
		return config;
	}

	private static float clampActivationThreshold(float value) {
		return Math.max(ACTIVATION_THRESHOLD_MIN, Math.min(ACTIVATION_THRESHOLD_MAX, value));
	}

	public void save() {
		try {
			Path configPath = FabricLoader.getInstance().getConfigDir().resolve("voicy.json");
			Files.createDirectories(configPath.getParent());
			try (Writer writer = Files.newBufferedWriter(configPath)) {
				GSON.toJson(this, writer);
			}
		} catch (Throwable ignored) {

		}
	}

	public double proximityRadius() {
		return proximityRadius;
	}

	public void setProximityRadius(double proximityRadius) {
		this.proximityRadius = proximityRadius;
		save();
	}

	public double proximityFalloff() {
		return proximityFalloff;
	}

	public void setProximityFalloff(double proximityFalloff) {
		this.proximityFalloff = proximityFalloff;
		save();
	}

	public int sampleRate() {
		return sampleRate;
	}

	public void setSampleRate(int sampleRate) {
		this.sampleRate = sampleRate;
		save();
	}

	public int frameDurationMs() {
		return frameDurationMs;
	}

	public void setFrameDurationMs(int frameDurationMs) {
		this.frameDurationMs = frameDurationMs;
		save();
	}

	public ActivationMode activationMode() {
		return activationMode;
	}

	public void setActivationMode(ActivationMode activationMode) {
		this.activationMode = activationMode;
		save();
	}

	public float activationThreshold() {
		return activationThreshold;
	}

	public void setActivationThreshold(float activationThreshold) {
		this.activationThreshold = clampActivationThreshold(activationThreshold);
		save();
	}

	public boolean preferUdpFallback() {
		return preferUdpFallback;
	}

	public boolean realisticOcclusion() {
		return realisticOcclusion;
	}

	public void setRealisticOcclusion(boolean realisticOcclusion) {
		this.realisticOcclusion = realisticOcclusion;
		save();
	}

	public String micDevice() {
		return micDevice == null ? "" : micDevice;
	}

	public void setMicDevice(String micDevice) {
		this.micDevice = micDevice == null ? "" : micDevice;
		save();
	}

	public boolean echoGuard() {
		return echoGuard;
	}

	public void setEchoGuard(boolean echoGuard) {
		this.echoGuard = echoGuard;
		save();
	}

	public AnimationStyle animationStyle() {
		return animationStyle == null ? AnimationStyle.SQUISH : animationStyle;
	}

	public void setAnimationStyle(AnimationStyle animationStyle) {
		this.animationStyle = animationStyle;
		save();
	}

	public float splitHeight() {
		return splitHeight < 0.15f || splitHeight > 0.85f ? 0.5f : splitHeight;
	}

	public void setSplitHeight(float splitHeight) {
		this.splitHeight = Math.max(0.15f, Math.min(0.85f, splitHeight));
		save();
	}

	public String selectedInputDevice() {
		return selectedInputDevice;
	}

	public void setSelectedInputDevice(String selectedInputDevice) {
		this.selectedInputDevice = selectedInputDevice == null ? "" : selectedInputDevice;
		save();
	}

	public String selectedOutputDevice() {
		return selectedOutputDevice;
	}

	public void setSelectedOutputDevice(String selectedOutputDevice) {
		this.selectedOutputDevice = selectedOutputDevice == null ? "" : selectedOutputDevice;
		save();
	}

	public int maxOpusBitrate() {
		return maxOpusBitrate;
	}

	public int jitterBufferMs() {
		return jitterBufferMs;
	}

	public float playerVolume(UUID playerId) {
		if (playerVolumes == null) {
			playerVolumes = new ConcurrentHashMap<>();
		}
		return playerVolumes.getOrDefault(playerId, 1.0f);
	}

	public void setPlayerVolume(UUID playerId, float volume) {
		if (playerVolumes == null) {
			playerVolumes = new ConcurrentHashMap<>();
		}
		playerVolumes.put(playerId, Math.max(0.0f, Math.min(2.0f, volume)));
		save();
	}

	public int frameSizeSamples() {
		return sampleRate * frameDurationMs / 1000;
	}
}
