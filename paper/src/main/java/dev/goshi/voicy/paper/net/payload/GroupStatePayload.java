package dev.goshi.voicy.paper.net.payload;

import dev.goshi.voicy.paper.net.VoicyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record GroupStatePayload(
		UUID groupId,
		boolean talkGroupOnly,
		int myColor,
		List<Request> requests,
		List<Invitation> invitations
) {
	public record Request(UUID player, String name) {
	}

	public record Invitation(UUID groupId, String groupName, String inviterName) {
	}

	public static final UUID NO_GROUP = new UUID(0L, 0L);
	public static final GroupStatePayload EMPTY = new GroupStatePayload(NO_GROUP, false, -1, List.of(), List.of());

	private static final int MAX_ENTRIES = 256;

	public void encode(VoicyByteBuf buf) {
		buf.writeUuid(groupId);
		buf.writeBoolean(talkGroupOnly);
		buf.writeInt(myColor);
		buf.writeVarInt(requests.size());
		for (Request request : requests) {
			buf.writeUuid(request.player());
			buf.writeString(request.name());
		}
		buf.writeVarInt(invitations.size());
		for (Invitation invitation : invitations) {
			buf.writeUuid(invitation.groupId());
			buf.writeString(invitation.groupName());
			buf.writeString(invitation.inviterName());
		}
	}

	public static GroupStatePayload decode(VoicyByteBuf buf) {
		UUID groupId = buf.readUuid();
		boolean groupOnly = buf.readBoolean();
		int color = buf.readInt();
		int requestCount = Math.min(buf.readVarInt(), MAX_ENTRIES);
		List<Request> requests = new ArrayList<>(requestCount);
		for (int i = 0; i < requestCount; i++) {
			requests.add(new Request(buf.readUuid(), buf.readString()));
		}
		int invitationCount = Math.min(buf.readVarInt(), MAX_ENTRIES);
		List<Invitation> invitations = new ArrayList<>(invitationCount);
		for (int i = 0; i < invitationCount; i++) {
			invitations.add(new Invitation(buf.readUuid(), buf.readString(), buf.readString()));
		}
		return new GroupStatePayload(groupId, groupOnly, color, List.copyOf(requests), List.copyOf(invitations));
	}

	public boolean inGroup() {
		return !NO_GROUP.equals(groupId);
	}
}
