package dev.goshi.voicy.network.payload;

import dev.goshi.voicy.Voicy;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record GroupNotifyPayload(String key, String arg1, String arg2, boolean chat) implements CustomPayload {

	public static final CustomPayload.Id<GroupNotifyPayload> TYPE =
			new CustomPayload.Id<>(Voicy.id("group_notify"));

	public static final PacketCodec<RegistryByteBuf, GroupNotifyPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeString(payload.key, 128);
				buf.writeString(payload.arg1, 64);
				buf.writeString(payload.arg2, 64);
				buf.writeBoolean(payload.chat);
			},
			buf -> new GroupNotifyPayload(buf.readString(128), buf.readString(64), buf.readString(64), buf.readBoolean())
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return TYPE;
	}
}
