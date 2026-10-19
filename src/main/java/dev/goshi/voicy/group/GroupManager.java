package dev.goshi.voicy.group;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

public final class GroupManager {

	public static final int MIN_NAME_LENGTH = 2;
	public static final int MAX_NAME_LENGTH = 16;
	public static final int MIN_PLAYERS = 2;
	public static final int MAX_PLAYERS = 32;
	public static final int DEFAULT_PLAYERS = 8;
	public static final int COLOR_COUNT = 16;
	public static final int NO_COLOR = -1;

	public static final long REQUEST_TTL_MS = 5L * 60L * 1000L;

	private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{" + MIN_NAME_LENGTH + "," + MAX_NAME_LENGTH + "}");

	public enum Result {
		OK,
		JOINED,
		REQUESTED,
		ALREADY_IN_GROUP,
		NOT_IN_GROUP,
		INVALID_NAME,
		NAME_TAKEN,
		INVALID_SIZE,
		NOT_OWNER,
		TARGET_IN_GROUP,
		TARGET_NOT_IN_GROUP,
		GROUP_FULL,
		NO_SUCH_INVITE,
		NO_SUCH_REQUEST,
		SELF_TARGET,
		ALREADY_INVITED,
		ALREADY_REQUESTED,
		NOT_IN_YOUR_GROUP,
		BAD_COLOR;

		public boolean isSuccess() {
			return this == OK || this == JOINED || this == REQUESTED;
		}
	}

	public record Invite(UUID groupId, UUID inviter, String inviterName, long createdAt) {
	}

	public record JoinRequest(UUID player, String name, long createdAt) {
	}

	public static final class Group {
		private final UUID id;
		private final String name;
		private final boolean isPublic;
		private final int maxPlayers;
		private final LinkedHashMap<UUID, String> members = new LinkedHashMap<>();
		private volatile UUID owner;
		private volatile List<UUID> memberSnapshot = List.of();

		private Group(UUID id, String name, UUID owner, boolean isPublic, int maxPlayers) {
			this.id = id;
			this.name = name;
			this.owner = owner;
			this.isPublic = isPublic;
			this.maxPlayers = maxPlayers;
		}

		public UUID id() {
			return id;
		}

		public String name() {
			return name;
		}

		public UUID owner() {
			return owner;
		}

		public boolean isPublic() {
			return isPublic;
		}

		public int maxPlayers() {
			return maxPlayers;
		}

		public int size() {
			return memberSnapshot.size();
		}

		public boolean isFull() {
			return size() >= maxPlayers;
		}

		public boolean isMember(UUID player) {
			return memberSnapshot.contains(player);
		}

		public List<UUID> memberIds() {
			return memberSnapshot;
		}

		public String memberName(UUID player) {
			synchronized (members) {
				return members.get(player);
			}
		}

		private void add(UUID player, String playerName) {
			synchronized (members) {
				members.put(player, playerName);
				memberSnapshot = List.copyOf(members.keySet());
			}
		}

		private void remove(UUID player) {
			synchronized (members) {
				members.remove(player);
				memberSnapshot = List.copyOf(members.keySet());
			}
		}
	}

	private final LongSupplier clock;
	private final Map<UUID, Group> groupsById = new LinkedHashMap<>();
	private final Map<String, UUID> idsByLowerName = new HashMap<>();
	private final Map<UUID, UUID> groupOfPlayer = new HashMap<>();
	private final Set<UUID> talkGroupOnly = new HashSet<>();
	private final Map<UUID, Integer> colorPrefs = new HashMap<>();
	private final Map<UUID, Map<UUID, Invite>> invitesByTarget = new HashMap<>();
	private final Map<UUID, Map<UUID, JoinRequest>> requestsByGroup = new HashMap<>();
	private final Map<Integer, UUID> speakerGroups = new HashMap<>();

	public GroupManager() {
		this(System::currentTimeMillis);
	}

	public GroupManager(LongSupplier clock) {
		this.clock = clock;
	}

	public static boolean isValidName(String name) {
		return name != null && NAME_PATTERN.matcher(name).matches();
	}

	public synchronized Result create(UUID owner, String ownerName, String rawName, boolean isPublic, int maxPlayers) {
		if (groupOfPlayer.containsKey(owner)) {
			return Result.ALREADY_IN_GROUP;
		}
		String name = rawName == null ? "" : rawName.trim();
		if (!isValidName(name)) {
			return Result.INVALID_NAME;
		}
		if (maxPlayers < MIN_PLAYERS || maxPlayers > MAX_PLAYERS) {
			return Result.INVALID_SIZE;
		}
		if (idsByLowerName.containsKey(name.toLowerCase(Locale.ROOT))) {
			return Result.NAME_TAKEN;
		}
		Group group = new Group(UUID.randomUUID(), name, owner, isPublic, maxPlayers);
		groupsById.put(group.id, group);
		idsByLowerName.put(name.toLowerCase(Locale.ROOT), group.id);
		addMember(group, owner, ownerName);
		return Result.OK;
	}

	public synchronized Result leave(UUID player) {
		Group group = groupOf(player);
		if (group == null) {
			return Result.NOT_IN_GROUP;
		}
		removeMember(group, player);
		return Result.OK;
	}

	public synchronized Result disband(UUID owner) {
		Group group = groupOf(owner);
		if (group == null) {
			return Result.NOT_IN_GROUP;
		}
		if (!owner.equals(group.owner)) {
			return Result.NOT_OWNER;
		}
		deleteGroup(group);
		return Result.OK;
	}

	public synchronized void removePlayer(UUID player) {
		Group group = groupOf(player);
		if (group != null) {
			removeMember(group, player);
		}
		invitesByTarget.remove(player);
		for (Map<UUID, JoinRequest> requests : requestsByGroup.values()) {
			requests.remove(player);
		}
		talkGroupOnly.remove(player);
		colorPrefs.remove(player);
	}

	public synchronized Result invite(UUID owner, String ownerName, UUID target) {
		if (owner.equals(target)) {
			return Result.SELF_TARGET;
		}
		Group group = groupOf(owner);
		if (group == null) {
			return Result.NOT_IN_GROUP;
		}
		if (!owner.equals(group.owner)) {
			return Result.NOT_OWNER;
		}
		if (groupOfPlayer.containsKey(target)) {
			return Result.TARGET_IN_GROUP;
		}
		if (group.isFull()) {
			return Result.GROUP_FULL;
		}
		Map<UUID, Invite> invites = invitesByTarget.computeIfAbsent(target, ignored -> new HashMap<>());
		Invite existing = invites.get(group.id);
		if (existing != null && !expired(existing.createdAt())) {
			return Result.ALREADY_INVITED;
		}
		invites.put(group.id, new Invite(group.id, owner, ownerName, clock.getAsLong()));
		return Result.OK;
	}

	public synchronized Result acceptInvite(UUID player, String playerName, UUID groupId) {
		if (groupOfPlayer.containsKey(player)) {
			return Result.ALREADY_IN_GROUP;
		}
		Invite invite = takeInvite(player, groupId);
		Group group = groupsById.get(groupId);
		if (invite == null || expired(invite.createdAt()) || group == null) {
			return Result.NO_SUCH_INVITE;
		}
		if (group.isFull()) {
			return Result.GROUP_FULL;
		}
		addMember(group, player, playerName);
		return Result.JOINED;
	}

	public synchronized Result declineInvite(UUID player, UUID groupId) {
		Invite invite = takeInvite(player, groupId);
		return invite == null ? Result.NO_SUCH_INVITE : Result.OK;
	}

	public synchronized Result requestJoin(UUID player, String playerName, UUID targetPlayer) {
		if (player.equals(targetPlayer)) {
			return Result.SELF_TARGET;
		}
		if (groupOfPlayer.containsKey(player)) {
			return Result.ALREADY_IN_GROUP;
		}
		Group group = groupOf(targetPlayer);
		if (group == null) {
			return Result.TARGET_NOT_IN_GROUP;
		}
		if (group.isFull()) {
			return Result.GROUP_FULL;
		}
		if (group.isPublic) {
			addMember(group, player, playerName);
			return Result.JOINED;
		}
		Map<UUID, JoinRequest> requests = requestsByGroup.computeIfAbsent(group.id, ignored -> new LinkedHashMap<>());
		JoinRequest existing = requests.get(player);
		if (existing != null && !expired(existing.createdAt())) {
			return Result.ALREADY_REQUESTED;
		}
		requests.put(player, new JoinRequest(player, playerName, clock.getAsLong()));
		return Result.REQUESTED;
	}

	public synchronized Result respondRequest(UUID owner, UUID requester, boolean accept) {
		Group group = groupOf(owner);
		if (group == null) {
			return Result.NOT_IN_GROUP;
		}
		if (!owner.equals(group.owner)) {
			return Result.NOT_OWNER;
		}
		Map<UUID, JoinRequest> requests = requestsByGroup.get(group.id);
		JoinRequest request = requests == null ? null : requests.remove(requester);
		if (request == null || expired(request.createdAt())) {
			return Result.NO_SUCH_REQUEST;
		}
		if (!accept) {
			return Result.OK;
		}
		if (groupOfPlayer.containsKey(requester)) {
			return Result.ALREADY_IN_GROUP;
		}
		if (group.isFull()) {
			return Result.GROUP_FULL;
		}
		addMember(group, requester, request.name());
		return Result.JOINED;
	}

	public synchronized Result kick(UUID owner, UUID target) {
		Group group = groupOf(owner);
		if (group == null) {
			return Result.NOT_IN_GROUP;
		}
		if (!owner.equals(group.owner)) {
			return Result.NOT_OWNER;
		}
		if (owner.equals(target)) {
			return Result.SELF_TARGET;
		}
		if (!group.isMember(target)) {
			return Result.NOT_IN_YOUR_GROUP;
		}
		removeMember(group, target);
		return Result.OK;
	}

	public synchronized Result setTalkToGroupOnly(UUID player, boolean groupOnly) {
		if (!groupOfPlayer.containsKey(player)) {
			return Result.NOT_IN_GROUP;
		}
		if (groupOnly) {
			talkGroupOnly.add(player);
		} else {
			talkGroupOnly.remove(player);
		}
		return Result.OK;
	}

	public synchronized boolean talksToGroupOnly(UUID player) {
		return groupOfPlayer.containsKey(player) && talkGroupOnly.contains(player);
	}

	public synchronized Group talkGroup(UUID player) {
		UUID groupId = groupOfPlayer.get(player);
		if (groupId == null || !talkGroupOnly.contains(player)) {
			return null;
		}
		return groupsById.get(groupId);
	}

	public synchronized Result setNameColor(UUID player, int colorIndex) {
		if (colorIndex < NO_COLOR || colorIndex >= COLOR_COUNT) {
			return Result.BAD_COLOR;
		}
		if (!groupOfPlayer.containsKey(player)) {
			return Result.NOT_IN_GROUP;
		}
		if (colorIndex == NO_COLOR) {
			colorPrefs.remove(player);
		} else {
			colorPrefs.put(player, colorIndex);
		}
		return Result.OK;
	}

	public synchronized int visibleNameColor(UUID player) {
		if (!groupOfPlayer.containsKey(player)) {
			return NO_COLOR;
		}
		return colorPrefs.getOrDefault(player, NO_COLOR);
	}

	public synchronized Group groupOf(UUID player) {
		UUID groupId = groupOfPlayer.get(player);
		return groupId == null ? null : groupsById.get(groupId);
	}

	public synchronized Group groupById(UUID groupId) {
		return groupsById.get(groupId);
	}

	public synchronized Group findByName(String name) {
		if (name == null) {
			return null;
		}
		UUID id = idsByLowerName.get(name.toLowerCase(Locale.ROOT));
		return id == null ? null : groupsById.get(id);
	}

	public synchronized List<Group> groups() {
		return List.copyOf(groupsById.values());
	}

	public synchronized List<String> groupNames() {
		List<String> names = new ArrayList<>();
		for (Group group : groupsById.values()) {
			names.add(group.name);
		}
		return names;
	}

	public synchronized List<Invite> invitesFor(UUID player) {
		Map<UUID, Invite> invites = invitesByTarget.get(player);
		if (invites == null) {
			return List.of();
		}
		invites.values().removeIf(invite -> expired(invite.createdAt()) || !groupsById.containsKey(invite.groupId()));
		return List.copyOf(invites.values());
	}

	public synchronized List<JoinRequest> requestsFor(UUID groupId) {
		Map<UUID, JoinRequest> requests = requestsByGroup.get(groupId);
		if (requests == null) {
			return List.of();
		}
		requests.values().removeIf(request -> expired(request.createdAt()));
		return List.copyOf(requests.values());
	}

	public synchronized void joinSpeaker(int speakerId, UUID groupId) {
		speakerGroups.put(speakerId, groupId);
	}

	public synchronized boolean leaveSpeaker(int speakerId) {
		return speakerGroups.remove(speakerId) != null;
	}

	public synchronized UUID speakerGroup(int speakerId) {
		return speakerGroups.get(speakerId);
	}

	public synchronized Map<Integer, UUID> speakerGroups() {
		return Collections.unmodifiableMap(new HashMap<>(speakerGroups));
	}

	public synchronized void forgetSpeaker(int speakerId) {
		speakerGroups.remove(speakerId);
	}

	public synchronized void clear() {
		groupsById.clear();
		idsByLowerName.clear();
		groupOfPlayer.clear();
		talkGroupOnly.clear();
		colorPrefs.clear();
		invitesByTarget.clear();
		requestsByGroup.clear();
		speakerGroups.clear();
	}

	private boolean expired(long createdAt) {
		return clock.getAsLong() - createdAt > REQUEST_TTL_MS;
	}

	private Invite takeInvite(UUID player, UUID groupId) {
		Map<UUID, Invite> invites = invitesByTarget.get(player);
		return invites == null ? null : invites.remove(groupId);
	}

	private void addMember(Group group, UUID player, String playerName) {
		group.add(player, playerName);
		groupOfPlayer.put(player, group.id);
		talkGroupOnly.add(player);
		invitesByTarget.remove(player);
		for (Map<UUID, JoinRequest> requests : requestsByGroup.values()) {
			requests.remove(player);
		}
	}

	private void removeMember(Group group, UUID player) {
		group.remove(player);
		groupOfPlayer.remove(player);
		talkGroupOnly.remove(player);
		if (group.memberSnapshot.isEmpty()) {
			deleteGroup(group);
		} else if (player.equals(group.owner)) {
			group.owner = group.memberSnapshot.get(0);
		}
	}

	private void deleteGroup(Group group) {
		groupsById.remove(group.id);
		idsByLowerName.remove(group.name.toLowerCase(Locale.ROOT));
		for (UUID member : group.memberSnapshot) {
			groupOfPlayer.remove(member);
			talkGroupOnly.remove(member);
		}
		requestsByGroup.remove(group.id);
		for (Map<UUID, Invite> invites : invitesByTarget.values()) {
			invites.remove(group.id);
		}
		speakerGroups.values().removeIf(group.id::equals);
	}
}
