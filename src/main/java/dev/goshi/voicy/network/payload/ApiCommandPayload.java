package dev.goshi.voicy.network.payload;

import dev.goshi.voicy.Voicy;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record ApiCommandPayload(
		ApiCommandType commandType,
		UUID targetPlayer,
		Identifier sourceMod,
		boolean enabled
) implements CustomPayload {
	private static final PacketCodec<RegistryByteBuf, UUID> UUID_CODEC =
			PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString).cast();

	public enum ApiCommandType {
		MUTE,
		UNMUTE,
		DEAFEN,
		UNDEAFEN,
		JOIN_CHANNEL,
		LEAVE_CHANNEL
	}

	public static final CustomPayload.Id<ApiCommandPayload> TYPE =
			new CustomPayload.Id<>(Voicy.id("api_command"));

	public static final PacketCodec<RegistryByteBuf, ApiCommandPayload> CODEC =
			PacketCodec.tuple(
					PacketCodecs.STRING.xmap(ApiCommandPayload::parseType, ApiCommandType::name),
							ApiCommandPayload::commandType,
					UUID_CODEC, ApiCommandPayload::targetPlayer,
					Identifier.PACKET_CODEC, ApiCommandPayload::sourceMod,
					PacketCodecs.BOOL, ApiCommandPayload::enabled,
					ApiCommandPayload::new
			);

	private static ApiCommandType parseType(String name) {
		return ApiCommandType.valueOf(name);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return TYPE;
	}
}
