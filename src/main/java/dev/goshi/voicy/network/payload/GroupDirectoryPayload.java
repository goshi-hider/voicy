package dev.goshi.voicy.network.payload;

import dev.goshi.voicy.Voicy;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record GroupDirectoryPayload(List<GroupInfo> groups) implements CustomPayload {
	public record Member(UUID id, String name, int color) {
	}

	public record GroupInfo(UUID id, String name, UUID owner, boolean isPublic, int maxPlayers, List<Member> members) {
		public boolean isFull() {
			return members.size() >= maxPlayers;
		}

		public boolean hasMember(UUID player) {
			for (Member member : members) {
				if (member.id().equals(player)) {
					return true;
				}
			}
			return false;
		}
	}

	public static final GroupDirectoryPayload EMPTY = new GroupDirectoryPayload(List.of());

	private static final int MAX_GROUPS = 1024;
	private static final int MAX_MEMBERS = 256;

	public static final CustomPayload.Id<GroupDirectoryPayload> TYPE =
			new CustomPayload.Id<>(Voicy.id("group_directory"));

	public static final PacketCodec<RegistryByteBuf, GroupDirectoryPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeVarInt(payload.groups.size());
				for (GroupInfo group : payload.groups) {
					buf.writeUuid(group.id);
					buf.writeString(group.name, 32);
					buf.writeUuid(group.owner);
					buf.writeBoolean(group.isPublic);
					buf.writeVarInt(group.maxPlayers);
					buf.writeVarInt(group.members.size());
					for (Member member : group.members) {
						buf.writeUuid(member.id);
						buf.writeString(member.name, 32);
						buf.writeInt(member.color);
					}
				}
			},
			buf -> {
				int groupCount = Math.min(buf.readVarInt(), MAX_GROUPS);
				List<GroupInfo> groups = new ArrayList<>(groupCount);
				for (int i = 0; i < groupCount; i++) {
					UUID id = buf.readUuid();
					String name = buf.readString(32);
					UUID owner = buf.readUuid();
					boolean isPublic = buf.readBoolean();
					int max = buf.readVarInt();
					int memberCount = Math.min(buf.readVarInt(), MAX_MEMBERS);
					List<Member> members = new ArrayList<>(memberCount);
					for (int j = 0; j < memberCount; j++) {
						members.add(new Member(buf.readUuid(), buf.readString(32), buf.readInt()));
					}
					groups.add(new GroupInfo(id, name, owner, isPublic, max, List.copyOf(members)));
				}
				return new GroupDirectoryPayload(List.copyOf(groups));
			}
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return TYPE;
	}
}
