package dev.goshi.voicy.client.group;

import dev.goshi.voicy.api.VoicyVoiceChatAPI;
import dev.goshi.voicy.group.GroupNameColors;
import dev.goshi.voicy.network.payload.GroupActionPayload;
import dev.goshi.voicy.network.payload.GroupDirectoryPayload;
import dev.goshi.voicy.network.payload.GroupDirectoryPayload.GroupInfo;
import dev.goshi.voicy.network.payload.GroupNotifyPayload;
import dev.goshi.voicy.network.payload.GroupStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientGroupState {
	private static volatile GroupDirectoryPayload directory = GroupDirectoryPayload.EMPTY;
	private static volatile GroupStatePayload state = GroupStatePayload.EMPTY;
	private static volatile int version;

	private ClientGroupState() {
	}

	public static int version() {
		return version;
	}

	public static void applyDirectory(GroupDirectoryPayload payload) {
		publishMembershipDiff(directory, payload);
		directory = payload;
		Map<UUID, Formatting> colors = new HashMap<>();
		for (GroupInfo group : payload.groups()) {
			for (GroupDirectoryPayload.Member member : group.members()) {
				Formatting color = GroupNameColors.fromIndex(member.color());
				if (color != null) {
					colors.put(member.id(), color);
				}
			}
		}
		GroupNameColors.replaceAll(colors);
		version++;
	}

	public static void applyState(GroupStatePayload payload) {
		state = payload;
		version++;
	}

	public static void reset() {
		directory = GroupDirectoryPayload.EMPTY;
		state = GroupStatePayload.EMPTY;
		GroupNameColors.clear();
		version++;
	}

	public static List<GroupInfo> groups() {
		return directory.groups();
	}

	public static GroupInfo myGroup() {
		if (!state.inGroup()) {
			return null;
		}
		for (GroupInfo group : directory.groups()) {
			if (group.id().equals(state.groupId())) {
				return group;
			}
		}
		return null;
	}

	public static GroupInfo groupOf(UUID player) {
		for (GroupInfo group : directory.groups()) {
			if (group.hasMember(player)) {
				return group;
			}
		}
		return null;
	}

	public static boolean isOwner() {
		GroupInfo mine = myGroup();
		MinecraftClient client = MinecraftClient.getInstance();
		return mine != null && client.player != null && mine.owner().equals(client.player.getUuid());
	}

	public static boolean talksToGroupOnly() {
		return state.talkGroupOnly();
	}

	public static int myColor() {
		return state.myColor();
	}

	public static java.util.List<GroupStatePayload.Request> requests() {
		return state.requests();
	}

	public static java.util.List<GroupStatePayload.Invitation> invitations() {
		return state.invitations();
	}

	public static int pendingCount() {
		return state.requests().size() + state.invitations().size();
	}

	public static void send(GroupActionPayload payload) {
		if (ClientPlayNetworking.canSend(GroupActionPayload.TYPE)) {
			ClientPlayNetworking.send(payload);
		}
	}

	public static void send(GroupActionPayload.Action action) {
		send(GroupActionPayload.of(action));
	}

	public static void send(GroupActionPayload.Action action, UUID id) {
		send(GroupActionPayload.withId(action, id));
	}

	public static void showNotification(MinecraftClient client, GroupNotifyPayload payload) {
		Text message = Text.translatable(payload.key(), payload.arg1(), payload.arg2());
		SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
				Text.translatable("gui.voicy.toast_title"), message);
		if (payload.chat() && client.inGameHud != null) {
			client.inGameHud.getChatHud().addMessage(
					Text.translatable("gui.voicy.chat_prefix").append(message));
		}
	}

	private static void publishMembershipDiff(GroupDirectoryPayload oldDirectory, GroupDirectoryPayload newDirectory) {
		Map<UUID, GroupInfo> before = membershipOf(oldDirectory);
		Map<UUID, GroupInfo> after = membershipOf(newDirectory);
		for (Map.Entry<UUID, GroupInfo> entry : after.entrySet()) {
			GroupInfo previousGroup = before.get(entry.getKey());
			if (previousGroup == null || !previousGroup.id().equals(entry.getValue().id())) {
				VoicyVoiceChatAPI.publishGroupJoin(entry.getKey(), entry.getValue().id(), entry.getValue().name());
			}
		}
		for (Map.Entry<UUID, GroupInfo> entry : before.entrySet()) {
			GroupInfo currentGroup = after.get(entry.getKey());
			if (currentGroup == null || !currentGroup.id().equals(entry.getValue().id())) {
				VoicyVoiceChatAPI.publishGroupLeave(entry.getKey(), entry.getValue().id(), entry.getValue().name());
			}
		}
	}

	private static Map<UUID, GroupInfo> membershipOf(GroupDirectoryPayload payload) {
		Map<UUID, GroupInfo> membership = new HashMap<>();
		for (GroupInfo group : payload.groups()) {
			for (GroupDirectoryPayload.Member member : group.members()) {
				membership.put(member.id(), group);
			}
		}
		return membership;
	}
}
