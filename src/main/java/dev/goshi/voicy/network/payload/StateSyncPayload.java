package dev.goshi.voicy.network.payload;

import dev.goshi.voicy.Voicy;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.UUID;

/**
 * Authoritative mute/deafen state pushed from server to clients.
 */
public record StateSyncPayload(
		UUID playerId,
		boolean muted,
		boolean deafened
) implements CustomPayload {

	private static final PacketCodec<RegistryByteBuf, UUID> UUID_CODEC =
			PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString).cast();

	public static final CustomPayload.Id<StateSyncPayload> TYPE =
			new CustomPayload.Id<>(Voicy.id("state_sync"));

	public static final PacketCodec<RegistryByteBuf, StateSyncPayload> CODEC =
			PacketCodec.tuple(
					UUID_CODEC, StateSyncPayload::playerId,
					PacketCodecs.BOOL, StateSyncPayload::muted,
					PacketCodecs.BOOL, StateSyncPayload::deafened,
					StateSyncPayload::new
			);

	@Override
	public Id<? extends CustomPayload> getId() {
		return TYPE;
	}
}
