package dev.goshi.voicy.paper.net.payload;

import dev.goshi.voicy.paper.net.VoicyByteBuf;

import java.util.UUID;

public record GroupActionPayload(Action action, UUID id, String text, int number, boolean flag) {
	public enum Action {
		CREATE,
		LEAVE,
		DISBAND,
		INVITE,
		ACCEPT_INVITE,
		DECLINE_INVITE,
		REQUEST_JOIN,
		ACCEPT_REQUEST,
		DECLINE_REQUEST,
		KICK,
		SET_TALK_GROUP_ONLY,
		SET_NAME_COLOR,
		UNKNOWN;

		static Action byIndex(int index) {
			Action[] values = values();
			return index >= 0 && index < values.length - 1 ? values[index] : UNKNOWN;
		}
	}

	public static final UUID NO_ID = new UUID(0L, 0L);

	public void encode(VoicyByteBuf buf) {
		buf.writeVarInt(action.ordinal());
		buf.writeUuid(id == null ? NO_ID : id);
		buf.writeString(text == null ? "" : text);
		buf.writeInt(number);
		buf.writeBoolean(flag);
	}

	public static GroupActionPayload decode(VoicyByteBuf buf) {
		Action action = Action.byIndex(buf.readVarInt());
		UUID id = buf.readUuid();
		String text = buf.readString();
		int number = buf.readInt();
		boolean flag = buf.readBoolean();
		return new GroupActionPayload(action, id, text, number, flag);
	}
}
