package net.voicechat.api.config;

public interface VoiceChatConfigView {

	double proximityRadius();

	double proximityFalloff();

	int sampleRate();

	int frameDurationMs();

	ActivationMode activationMode();

	float activationThreshold();

	enum ActivationMode {
		VOICE_ACTIVATION,
		PUSH_TO_TALK,
		ALWAYS_ON
	}
}
