package dev.goshi.voicy.audio.capture;

public final class AudioPreprocessor {

	private float agcGain = 1.0f;
	private float gateGain = 1.0f;
	private final float targetLevel = 0.22f;
	private final float noiseFloor;

	public AudioPreprocessor(float noiseFloor) {
		
		this.noiseFloor = Math.max(0.003f, Math.min(0.02f, noiseFloor * 0.08f));
	}

	public void process(short[] samples, int count) {
		float peak = 0f;
		for (int i = 0; i < count; i++) {
			peak = Math.max(peak, Math.abs(samples[i]) / 32768.0f);
		}

		if (peak >= noiseFloor && peak > 0.0001f) {
			float desired = targetLevel / peak;
			if (desired < agcGain) {
				
				agcGain = agcGain * 0.75f + desired * 0.25f;
			} else {
				
				agcGain = agcGain * 0.995f + desired * 0.005f;
			}
			agcGain = Math.max(0.5f, Math.min(6.0f, agcGain));
		}

		for (int i = 0; i < count; i++) {
			float targetGate = peak >= noiseFloor ? 1.0f : 0.0f;
			float smoothing = targetGate > gateGain ? 0.012f : 0.003f;
			gateGain += (targetGate - gateGain) * smoothing;
			float normalized = samples[i] / 32768.0f * agcGain * gateGain;
			float magnitude = Math.abs(normalized);
			if (magnitude > 0.82f) {
				float compressed = 0.82f + 0.18f * (1.0f - (float) Math.exp(-(magnitude - 0.82f) / 0.06f));
				normalized = Math.copySign(Math.min(1.0f, compressed), normalized);
			}
			samples[i] = (short) Math.round(normalized * 32767.0f);
		}
	}

	public float currentGain() {
		return agcGain;
	}
}
