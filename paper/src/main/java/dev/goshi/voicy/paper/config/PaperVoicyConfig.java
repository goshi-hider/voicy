package dev.goshi.voicy.paper.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public final class PaperVoicyConfig {
	public static final int DEFAULT_UDP_PORT = 25555;

	private final double proximityRadius;
	private final int sampleRate;
	private final int frameDurationMs;
	private final boolean preferUdpFallback;
	private final int udpPort;

	public PaperVoicyConfig(Plugin plugin) {
		plugin.saveDefaultConfig();
		plugin.reloadConfig();
		FileConfiguration config = plugin.getConfig();
		this.proximityRadius = config.getDouble("proximity-radius", 48.0d);
		this.sampleRate = config.getInt("sample-rate", 48000);
		this.frameDurationMs = config.getInt("frame-duration-ms", 20);
		this.preferUdpFallback = config.getBoolean("prefer-udp-fallback", true);
		this.udpPort = config.getInt("udp-port", DEFAULT_UDP_PORT);
	}

	public double proximityRadius() {
		return proximityRadius;
	}

	public int sampleRate() {
		return sampleRate;
	}

	public int frameDurationMs() {
		return frameDurationMs;
	}

	public boolean preferUdpFallback() {
		return preferUdpFallback;
	}

	public int udpPort() {
		return udpPort;
	}
}
