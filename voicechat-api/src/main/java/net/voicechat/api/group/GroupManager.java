package net.voicechat.api.group;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public interface GroupManager {
	int MIN_NAME_LENGTH = 2;
	int MAX_NAME_LENGTH = 16;
	int MIN_PLAYERS = 2;
	int MAX_PLAYERS = 32;
	int COLOR_COUNT = 16;
	int NO_COLOR = -1;

	Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{" + MIN_NAME_LENGTH + "," + MAX_NAME_LENGTH + "}");

	static boolean isValidGroupName(String name) {
		return name != null && NAME_PATTERN.matcher(name).matches();
	}

	@NotNull
	Collection<VoiceGroup> getGroups();

	@NotNull
	Optional<VoiceGroup> getGroup(@NotNull UUID playerId);

	@NotNull
	Optional<VoiceGroup> getMyGroup();

	boolean isInGroup(@NotNull UUID playerId);

	boolean isMyGroupOwner();

	boolean talksToGroupOnly();

	int myNameColor();

	@NotNull
	Collection<GroupInvitation> getInvitations();

	@NotNull
	Collection<GroupJoinRequest> getJoinRequests();

	boolean createGroup(@NotNull String name, boolean isPublic, int maxPlayers);

	boolean leaveGroup();

	boolean disbandGroup();

	boolean invite(@NotNull UUID target);

	boolean kick(@NotNull UUID target);

	boolean acceptInvite(@NotNull UUID groupId);

	boolean declineInvite(@NotNull UUID groupId);

	boolean requestJoin(@NotNull UUID targetPlayer);

	boolean acceptJoinRequest(@NotNull UUID requester);

	boolean declineJoinRequest(@NotNull UUID requester);

	boolean setTalkToGroupOnly(boolean groupOnly);

	boolean setNameColor(int colorIndex);

	interface GroupMember {
		@NotNull UUID id();
		@NotNull String name();
		int colorIndex();
	}

	interface VoiceGroup {
		@NotNull UUID id();
		@NotNull String name();
		@NotNull UUID owner();
		boolean isPublic();
		int maxPlayers();
		@NotNull Collection<GroupMember> members();
		boolean isFull();
		boolean hasMember(@NotNull UUID player);
	}

	record GroupInvitation(@NotNull UUID groupId, @NotNull String groupName, @NotNull String inviterName) {
	}

	record GroupJoinRequest(@NotNull UUID playerId, @NotNull String playerName) {
	}
}
