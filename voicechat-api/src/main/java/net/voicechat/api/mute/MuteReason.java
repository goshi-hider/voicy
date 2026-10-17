package net.voicechat.api.mute;

/**
 * Reason codes attached to authoritative mute requests for auditing and UI display.
 */
public enum MuteReason {
	ADMIN,
	MOD_EFFECT,
	PLAYER_SELF,
	API,
	SECURITY_VIOLATION
}
