package dev.goshi.voicy.paper.net.payload;

import dev.goshi.voicy.paper.net.VoicyByteBuf;

import java.util.UUID;

public record ApiCommandPayload(
		ApiCommandType commandType,
		UUID targetPlayer,
		String sourceMod,
		boolean enabled
) {

	public enum ApiCommandType {
		MUTE,
		UNMUTE,
		DEAFEN,
		UNDEAFEN,
		JOIN_CHANNEL,
		LEAVE_CHANNEL
	}

	public void encode(VoicyByteBuf buf) {
		buf.writeString(commandType.name());
		buf.writeUuidAsString(targetPlayer);
		buf.writeString(sourceMod == null ? "" : sourceMod);
		buf.writeBoolean(enabled);
	}

	public static ApiCommandPayload decode(VoicyByteBuf buf) {
		ApiCommandType type = ApiCommandType.valueOf(buf.readString());
		UUID target = buf.readUuidAsString();
		String sourceMod = buf.readString();
		boolean enabled = buf.readBoolean();
		return new ApiCommandPayload(type, target, sourceMod, enabled);
	}
}
