package dev.goshi.voicy.network.payload;

import dev.goshi.voicy.Voicy;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record GroupStatePayload(
		UUID groupId,
		boolean talkGroupOnly,
		int myColor,
		List<Request> requests,
		List<Invitation> invitations
) implements CustomPayload {

	public record Request(UUID player, String name) {
	}

	public record Invitation(UUID groupId, String groupName, String inviterName) {
	}

	public static final UUID NO_GROUP = new UUID(0L, 0L);
	public static final GroupStatePayload EMPTY = new GroupStatePayload(NO_GROUP, false, -1, List.of(), List.of());

	private static final int MAX_ENTRIES = 256;

	public static final CustomPayload.Id<GroupStatePayload> TYPE =
			new CustomPayload.Id<>(Voicy.id("group_state"));

	public static final PacketCodec<RegistryByteBuf, GroupStatePayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeUuid(payload.groupId);
				buf.writeBoolean(payload.talkGroupOnly);
				buf.writeInt(payload.myColor);
				buf.writeVarInt(payload.requests.size());
				for (Request request : payload.requests) {
					buf.writeUuid(request.player);
					buf.writeString(request.name, 32);
				}
				buf.writeVarInt(payload.invitations.size());
				for (Invitation invitation : payload.invitations) {
					buf.writeUuid(invitation.groupId);
					buf.writeString(invitation.groupName, 32);
					buf.writeString(invitation.inviterName, 32);
				}
			},
			buf -> {
				UUID groupId = buf.readUuid();
				boolean groupOnly = buf.readBoolean();
				int color = buf.readInt();
				int requestCount = Math.min(buf.readVarInt(), MAX_ENTRIES);
				List<Request> requests = new ArrayList<>(requestCount);
				for (int i = 0; i < requestCount; i++) {
					requests.add(new Request(buf.readUuid(), buf.readString(32)));
				}
				int invitationCount = Math.min(buf.readVarInt(), MAX_ENTRIES);
				List<Invitation> invitations = new ArrayList<>(invitationCount);
				for (int i = 0; i < invitationCount; i++) {
					invitations.add(new Invitation(buf.readUuid(), buf.readString(32), buf.readString(32)));
				}
				return new GroupStatePayload(groupId, groupOnly, color, List.copyOf(requests), List.copyOf(invitations));
			}
	);

	public boolean inGroup() {
		return !NO_GROUP.equals(groupId);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return TYPE;
	}
}
