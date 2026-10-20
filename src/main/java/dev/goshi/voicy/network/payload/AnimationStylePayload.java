package dev.goshi.voicy.network.payload;

import dev.goshi.voicy.Voicy;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.UUID;

public record AnimationStylePayload(
		UUID playerId,
		int style,
		float splitHeight
) implements CustomPayload {

	private static final PacketCodec<RegistryByteBuf, UUID> UUID_CODEC =
			PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString).cast();

	public static final CustomPayload.Id<AnimationStylePayload> TYPE =
			new CustomPayload.Id<>(Voicy.id("animation_style"));

	public static final PacketCodec<RegistryByteBuf, AnimationStylePayload> CODEC =
			PacketCodec.tuple(
					UUID_CODEC, AnimationStylePayload::playerId,
					PacketCodecs.VAR_INT, AnimationStylePayload::style,
					PacketCodecs.FLOAT, AnimationStylePayload::splitHeight,
					AnimationStylePayload::new
			);

	@Override
	public Id<? extends CustomPayload> getId() {
		return TYPE;
	}
}
