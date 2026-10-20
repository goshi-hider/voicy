package dev.goshi.voicy.paper.net.payload;

import dev.goshi.voicy.paper.net.VoicyByteBuf;

public record GroupNotifyPayload(String key, String arg1, String arg2, boolean chat) {

	public void encode(VoicyByteBuf buf) {
		buf.writeString(key);
		buf.writeString(arg1);
		buf.writeString(arg2);
		buf.writeBoolean(chat);
	}

	public static GroupNotifyPayload decode(VoicyByteBuf buf) {
		String key = buf.readString();
		String arg1 = buf.readString();
		String arg2 = buf.readString();
		boolean chat = buf.readBoolean();
		return new GroupNotifyPayload(key, arg1, arg2, chat);
	}
}
