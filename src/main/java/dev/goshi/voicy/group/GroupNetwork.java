package dev.goshi.voicy.group;

import dev.goshi.voicy.group.GroupManager.Group;
import dev.goshi.voicy.group.GroupManager.Result;
import dev.goshi.voicy.network.payload.GroupActionPayload;
import dev.goshi.voicy.network.payload.GroupDirectoryPayload;
import dev.goshi.voicy.network.payload.GroupNotifyPayload;
import dev.goshi.voicy.network.payload.GroupStatePayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class GroupNetwork {

	private final GroupManager manager;

	public GroupNetwork(GroupManager manager) {
		this.manager = manager;
	}

	public GroupManager manager() {
		return manager;
	}

	public void handle(ServerPlayerEntity player, GroupActionPayload payload) {
		MinecraftServer server = player.server;
		UUID me = player.getUuid();
		String myName = nameOf(player);
		UUID id = payload.id();

		switch (payload.action()) {
			case CREATE -> {
				Result result = manager.create(me, myName, payload.text(), payload.flag(), payload.number());
				if (result.isSuccess()) {
					tell(player, "gui.voicy.notify.group_created", payload.text().trim(), "", false);
				} else {
					error(player, result);
				}
			}
			case LEAVE -> {
				Group group = manager.groupOf(me);
				UUID oldOwner = group == null ? null : group.owner();
				Result result = manager.leave(me);
				if (result.isSuccess() && group != null) {
					tell(player, "gui.voicy.notify.you_left", group.name(), "", false);
					Group remaining = manager.groupById(group.id());
					if (remaining != null) {
						tellMembers(server, remaining, me, "gui.voicy.notify.member_left", myName, group.name());
						if (me.equals(oldOwner)) {
							tell(server.getPlayerManager().getPlayer(remaining.owner()),
									"gui.voicy.notify.now_owner", group.name(), "", true);
						}
					}
				} else if (!result.isSuccess()) {
					error(player, result);
				}
			}
			case DISBAND -> {
				Group group = manager.groupOf(me);
				List<UUID> members = group == null ? List.of() : group.memberIds();
				Result result = manager.disband(me);
				if (result.isSuccess() && group != null) {
					for (UUID member : members) {
						tell(server.getPlayerManager().getPlayer(member),
								"gui.voicy.notify.group_disbanded", group.name(), "", false);
					}
				} else if (!result.isSuccess()) {
					error(player, result);
				}
			}
			case INVITE -> {
				ServerPlayerEntity target = server.getPlayerManager().getPlayer(id);
				if (target == null) {
					tell(player, "gui.voicy.error.target_offline", "", "", false);
					break;
				}
				Result result = manager.invite(me, myName, id);
				if (result.isSuccess()) {
					Group group = manager.groupOf(me);
					tell(target, "gui.voicy.notify.invite_received", myName, group == null ? "" : group.name(), true);
					tell(player, "gui.voicy.notify.invite_sent", nameOf(target), "", false);
				} else {
					error(player, result);
				}
			}
			case ACCEPT_INVITE -> {
				Result result = manager.acceptInvite(me, myName, id);
				if (result == Result.JOINED) {
					announceJoin(server, manager.groupOf(me), player);
				} else {
					error(player, result);
				}
			}
			case DECLINE_INVITE -> {
				GroupManager.Invite invite = findInvite(me, id);
				Result result = manager.declineInvite(me, id);
				if (result.isSuccess() && invite != null) {
					tell(server.getPlayerManager().getPlayer(invite.inviter()),
							"gui.voicy.notify.invite_declined", myName, "", false);
				} else if (!result.isSuccess()) {
					error(player, result);
				}
			}
			case REQUEST_JOIN -> {
				Group targetGroup = manager.groupOf(id);
				Result result = manager.requestJoin(me, myName, id);
				if (result == Result.JOINED) {
					announceJoin(server, manager.groupOf(me), player);
				} else if (result == Result.REQUESTED && targetGroup != null) {
					tell(player, "gui.voicy.notify.request_sent", targetGroup.name(), "", false);
					tell(server.getPlayerManager().getPlayer(targetGroup.owner()),
							"gui.voicy.notify.request_received", myName, targetGroup.name(), true);
				} else {
					error(player, result);
				}
			}
			case ACCEPT_REQUEST -> {
				Group group = manager.groupOf(me);
				Result result = manager.respondRequest(me, id, true);
				ServerPlayerEntity requester = server.getPlayerManager().getPlayer(id);
				if (result == Result.JOINED && requester != null) {
					tell(requester, "gui.voicy.notify.request_accepted", group == null ? "" : group.name(), "", true);
					announceJoin(server, manager.groupOf(id), requester);
				} else if (!result.isSuccess()) {
					error(player, result);
				}
			}
			case DECLINE_REQUEST -> {
				Group group = manager.groupOf(me);
				Result result = manager.respondRequest(me, id, false);
				if (result.isSuccess()) {
					tell(server.getPlayerManager().getPlayer(id),
							"gui.voicy.notify.request_declined", group == null ? "" : group.name(), "", false);
				} else {
					error(player, result);
				}
			}
			case KICK -> {
				Group group = manager.groupOf(me);
				String targetName = group == null ? "" : group.memberName(id);
				Result result = manager.kick(me, id);
				if (result.isSuccess() && group != null) {
					tell(server.getPlayerManager().getPlayer(id), "gui.voicy.notify.kicked", group.name(), "", true);
					Group remaining = manager.groupById(group.id());
					if (remaining != null) {
						tellMembers(server, remaining, me, "gui.voicy.notify.member_kicked",
								targetName == null ? "" : targetName, group.name());
					}
				} else if (!result.isSuccess()) {
					error(player, result);
				}
			}
			case SET_TALK_GROUP_ONLY -> {
				Result result = manager.setTalkToGroupOnly(me, payload.flag());
				if (!result.isSuccess()) {
					error(player, result);
				}
			}
			case SET_NAME_COLOR -> {
				Result result = manager.setNameColor(me, payload.number());
				if (!result.isSuccess()) {
					error(player, result);
				}
			}
			case UNKNOWN -> {
				return;
			}
		}
		syncAll(server, null);
	}

	public void onJoin(ServerPlayerEntity player) {
		syncAll(player.server, null);
	}

	public void onDisconnect(ServerPlayerEntity player) {
		MinecraftServer server = player.server;
		UUID me = player.getUuid();
		Group group = manager.groupOf(me);
		UUID oldOwner = group == null ? null : group.owner();
		manager.removePlayer(me);
		if (group != null) {
			Group remaining = manager.groupById(group.id());
			if (remaining != null) {
				tellMembers(server, remaining, me, "gui.voicy.notify.member_left", nameOf(player), group.name());
				if (me.equals(oldOwner)) {
					tell(server.getPlayerManager().getPlayer(remaining.owner()),
							"gui.voicy.notify.now_owner", group.name(), "", true);
				}
			}
		}
		syncAll(server, me);
	}

	public void syncAll(MinecraftServer server, UUID skip) {
		GroupDirectoryPayload directory = buildDirectory();
		for (ServerPlayerEntity recipient : PlayerLookup.all(server)) {
			if (recipient.getUuid().equals(skip)) {
				continue;
			}
			send(recipient, directory);
			send(recipient, buildState(recipient.getUuid()));
		}
	}

	private GroupDirectoryPayload buildDirectory() {
		List<GroupDirectoryPayload.GroupInfo> groups = new ArrayList<>();
		for (Group group : manager.groups()) {
			List<GroupDirectoryPayload.Member> members = new ArrayList<>();
			for (UUID memberId : group.memberIds()) {
				String name = group.memberName(memberId);
				members.add(new GroupDirectoryPayload.Member(memberId, name == null ? "" : name,
						manager.visibleNameColor(memberId)));
			}
			groups.add(new GroupDirectoryPayload.GroupInfo(group.id(), group.name(), group.owner(),
					group.isPublic(), group.maxPlayers(), List.copyOf(members)));
		}
		return new GroupDirectoryPayload(List.copyOf(groups));
	}

	private GroupStatePayload buildState(UUID player) {
		Group group = manager.groupOf(player);
		List<GroupStatePayload.Request> requests = new ArrayList<>();
		List<GroupStatePayload.Invitation> invitations = new ArrayList<>();
		if (group != null) {
			if (player.equals(group.owner())) {
				for (GroupManager.JoinRequest request : manager.requestsFor(group.id())) {
					requests.add(new GroupStatePayload.Request(request.player(), request.name()));
				}
			}
		} else {
			for (GroupManager.Invite invite : manager.invitesFor(player)) {
				Group invited = manager.groupById(invite.groupId());
				if (invited != null) {
					invitations.add(new GroupStatePayload.Invitation(invited.id(), invited.name(), invite.inviterName()));
				}
			}
		}
		return new GroupStatePayload(
				group == null ? GroupStatePayload.NO_GROUP : group.id(),
				manager.talksToGroupOnly(player),
				manager.visibleNameColor(player),
				List.copyOf(requests),
				List.copyOf(invitations));
	}

	private void announceJoin(MinecraftServer server, Group group, ServerPlayerEntity joiner) {
		if (group == null) {
			return;
		}
		tell(joiner, "gui.voicy.notify.you_joined", group.name(), "", false);
		tellMembers(server, group, joiner.getUuid(), "gui.voicy.notify.member_joined", nameOf(joiner), group.name());
	}

	private void tellMembers(MinecraftServer server, Group group, UUID except, String key, String arg1, String arg2) {
		for (UUID memberId : group.memberIds()) {
			if (!memberId.equals(except)) {
				tell(server.getPlayerManager().getPlayer(memberId), key, arg1, arg2, false);
			}
		}
	}

	private GroupManager.Invite findInvite(UUID player, UUID groupId) {
		for (GroupManager.Invite invite : manager.invitesFor(player)) {
			if (invite.groupId().equals(groupId)) {
				return invite;
			}
		}
		return null;
	}

	private void error(ServerPlayerEntity player, Result result) {
		tell(player, "gui.voicy.error." + result.name().toLowerCase(Locale.ROOT), "", "", false);
	}

	private void tell(ServerPlayerEntity target, String key, String arg1, String arg2, boolean chat) {
		if (target != null) {
			send(target, new GroupNotifyPayload(key, arg1, arg2, chat));
		}
	}

	private static void send(ServerPlayerEntity player, CustomPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.getId())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static String nameOf(ServerPlayerEntity player) {
		return player.getGameProfile().getName();
	}
}
