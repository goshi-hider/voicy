package dev.goshi.voicy.network.payload;

import dev.goshi.voicy.Voicy;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.UUID;

public record GroupActionPayload(Action action, UUID id, String text, int number, boolean flag) implements CustomPayload {

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
	private static final int MAX_TEXT = 64;

	public static final CustomPayload.Id<GroupActionPayload> TYPE =
			new CustomPayload.Id<>(Voicy.id("group_action"));

	public static final PacketCodec<RegistryByteBuf, GroupActionPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeVarInt(payload.action.ordinal());
				buf.writeUuid(payload.id == null ? NO_ID : payload.id);
				buf.writeString(payload.text == null ? "" : payload.text, MAX_TEXT);
				buf.writeInt(payload.number);
				buf.writeBoolean(payload.flag);
			},
			buf -> new GroupActionPayload(
					Action.byIndex(buf.readVarInt()),
					buf.readUuid(),
					buf.readString(MAX_TEXT),
					buf.readInt(),
					buf.readBoolean())
	);

	public static GroupActionPayload of(Action action) {
		return new GroupActionPayload(action, NO_ID, "", 0, false);
	}

	public static GroupActionPayload withId(Action action, UUID id) {
		return new GroupActionPayload(action, id, "", 0, false);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return TYPE;
	}
}
