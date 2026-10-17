package net.voicechat.api.config;

/**
 * Read-only view of proximity voice configuration exposed to integrating mods.
 */
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
