package dev.goshi.voicy.paper.net;

public final class VoicyChannels {

	public static final String VOICE_FRAME = "voicy:voice_frame";
	public static final String API_COMMAND = "voicy:api_command";
	public static final String STATE_SYNC = "voicy:state_sync";
	public static final String HANDSHAKE = "voicy:handshake";
	public static final String SPEAKER_SYNC = "voicy:speaker_sync";
	public static final String GROUP_ACTION = "voicy:group_action";
	public static final String GROUP_DIRECTORY = "voicy:group_directory";
	public static final String GROUP_STATE = "voicy:group_state";
	public static final String GROUP_NOTIFY = "voicy:group_notify";
	public static final String ANIMATION_STYLE = "voicy:animation_style";

	public static final String[] INCOMING = {VOICE_FRAME, API_COMMAND, HANDSHAKE, GROUP_ACTION, ANIMATION_STYLE};

	public static final String[] OUTGOING = {
			VOICE_FRAME, STATE_SYNC, HANDSHAKE, SPEAKER_SYNC, GROUP_DIRECTORY, GROUP_STATE, GROUP_NOTIFY, ANIMATION_STYLE
	};

	private VoicyChannels() {
	}
}
