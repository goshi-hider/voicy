package dev.goshi.voicy.audio.capture;

public final class VoiceActivityGate {

	private final float activationThreshold;
	private final float releaseThreshold;
	private final long hangoverNanos;
	private long lastSpeechNanos = Long.MIN_VALUE;
	private boolean active;

	public VoiceActivityGate(float activationThreshold, float releaseRatio, long hangoverNanos) {
		this.activationThreshold = Math.max(0.0f, activationThreshold);
		this.releaseThreshold = this.activationThreshold * Math.max(0.0f, Math.min(1.0f, releaseRatio));
		this.hangoverNanos = Math.max(0L, hangoverNanos);
	}

	public boolean update(float level, long nowNanos) {
		if (level >= activationThreshold || (active && level >= releaseThreshold)) {
			lastSpeechNanos = nowNanos;
			active = true;
		} else if (active && nowNanos - lastSpeechNanos > hangoverNanos) {
			active = false;
		}
		return active;
	}

	public void reset() {
		active = false;
		lastSpeechNanos = Long.MIN_VALUE;
	}
}
