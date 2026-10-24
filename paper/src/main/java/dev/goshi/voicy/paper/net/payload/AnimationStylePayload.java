package dev.goshi.voicy.paper.net.payload;

import dev.goshi.voicy.paper.net.VoicyByteBuf;

import java.util.UUID;

public record AnimationStylePayload(UUID playerId, int style, float splitHeight) {
	public void encode(VoicyByteBuf buf) {
		buf.writeUuidAsString(playerId);
		buf.writeVarInt(style);
		buf.writeFloat(splitHeight);
	}

	public static AnimationStylePayload decode(VoicyByteBuf buf) {
		UUID playerId = buf.readUuidAsString();
		int style = buf.readVarInt();
		float splitHeight = buf.readFloat();
		return new AnimationStylePayload(playerId, style, splitHeight);
	}
}
