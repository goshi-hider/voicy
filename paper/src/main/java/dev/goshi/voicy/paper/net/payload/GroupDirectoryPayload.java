package dev.goshi.voicy.paper.net.payload;

import dev.goshi.voicy.paper.net.VoicyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record GroupDirectoryPayload(List<GroupInfo> groups) {
	public record Member(UUID id, String name, int color) {
	}

	public record GroupInfo(UUID id, String name, UUID owner, boolean isPublic, int maxPlayers, List<Member> members) {
	}

	public static final GroupDirectoryPayload EMPTY = new GroupDirectoryPayload(List.of());

	private static final int MAX_GROUPS = 1024;
	private static final int MAX_MEMBERS = 256;

	public void encode(VoicyByteBuf buf) {
		buf.writeVarInt(groups.size());
		for (GroupInfo group : groups) {
			buf.writeUuid(group.id());
			buf.writeString(group.name());
			buf.writeUuid(group.owner());
			buf.writeBoolean(group.isPublic());
			buf.writeVarInt(group.maxPlayers());
			buf.writeVarInt(group.members().size());
			for (Member member : group.members()) {
				buf.writeUuid(member.id());
				buf.writeString(member.name());
				buf.writeInt(member.color());
			}
		}
	}

	public static GroupDirectoryPayload decode(VoicyByteBuf buf) {
		int groupCount = Math.min(buf.readVarInt(), MAX_GROUPS);
		List<GroupInfo> groups = new ArrayList<>(groupCount);
		for (int i = 0; i < groupCount; i++) {
			UUID id = buf.readUuid();
			String name = buf.readString();
			UUID owner = buf.readUuid();
			boolean isPublic = buf.readBoolean();
			int max = buf.readVarInt();
			int memberCount = Math.min(buf.readVarInt(), MAX_MEMBERS);
			List<Member> members = new ArrayList<>(memberCount);
			for (int j = 0; j < memberCount; j++) {
				members.add(new Member(buf.readUuid(), buf.readString(), buf.readInt()));
			}
			groups.add(new GroupInfo(id, name, owner, isPublic, max, List.copyOf(members)));
		}
		return new GroupDirectoryPayload(List.copyOf(groups));
	}
}
