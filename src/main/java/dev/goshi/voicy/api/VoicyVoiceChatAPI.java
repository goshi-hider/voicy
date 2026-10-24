package dev.goshi.voicy.api;

import dev.goshi.voicy.Voicy;
import dev.goshi.voicy.channel.ServerChannelManager;
import dev.goshi.voicy.client.ClientVoiceManager;
import dev.goshi.voicy.client.group.ClientGroupState;
import dev.goshi.voicy.network.payload.ApiCommandPayload;
import dev.goshi.voicy.network.payload.GroupActionPayload;
import dev.goshi.voicy.network.payload.GroupDirectoryPayload;
import dev.goshi.voicy.network.payload.GroupStatePayload;
import dev.goshi.voicy.security.VoiceSecurityService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import net.voicechat.api.VoiceChatAPI;
import net.voicechat.api.VoiceChatAPIHolder;
import net.voicechat.api.audio.VolumeManager;
import net.voicechat.api.channel.ChannelManager;
import net.voicechat.api.deafen.DeafenManager;
import net.voicechat.api.events.VoiceChatEvents;
import net.voicechat.api.group.GroupManager;
import net.voicechat.api.mute.MuteManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class VoicyVoiceChatAPI implements VoiceChatAPI {
	private final MuteManagerImpl muteManager;
	private final DeafenManagerImpl deafenManager;
	private final VolumeManagerImpl volumeManager;
	private final ServerChannelManager channelManager;
	private final ClientVoiceManager clientVoiceManager;
	private final GroupManagerImpl groupManager = new GroupManagerImpl();
	private final VoicyVoiceChatEvents events = new VoicyVoiceChatEvents();

	public VoicyVoiceChatAPI(
			VoiceSecurityService securityService,
			ClientVoiceManager clientVoiceManager,
			ServerChannelManager channelManager
	) {
		this.muteManager = new MuteManagerImpl(securityService, clientVoiceManager);
		this.deafenManager = new DeafenManagerImpl(securityService, clientVoiceManager);
		this.volumeManager = new VolumeManagerImpl();
		this.channelManager = channelManager;
		this.clientVoiceManager = clientVoiceManager;
		this.volumeManager.config = dev.goshi.voicy.config.VoicyConfig.INSTANCE;
		VoiceChatAPIHolder.set(this);
		registerModSecurityContext(Voicy.id("core"));
	}

	public static void init(
			VoiceSecurityService securityService,
			ClientVoiceManager clientVoiceManager,
			ServerChannelManager channelManager
	) {
		VoicyVoiceChatAPI api = new VoicyVoiceChatAPI(securityService, clientVoiceManager, channelManager);
		api.registerModSecurityContext(Voicy.id("core"));
	}

	@Override
	public @NotNull MuteManager getMuteManager() {
		return muteManager;
	}

	@Override
	public @NotNull DeafenManager getDeafenManager() {
		return deafenManager;
	}

	@Override
	public @NotNull VolumeManager getVolumeManager() {
		return volumeManager;
	}

	@Override
	public @NotNull ChannelManager getChannelManager() {
		return channelManager;
	}

	@Override
	public @NotNull GroupManager getGroupManager() {
		return groupManager;
	}

	@Override
	public @NotNull VoiceChatEvents getEvents() {
		return events;
	}

	public static void publishVoiceState(UUID playerId, boolean wasMuted, boolean mutedNow, boolean wasDeafened, boolean deafenedNow) {
		if (VoiceChatAPIHolder.get() instanceof VoicyVoiceChatAPI api) {
			try {
				api.events.publishState(playerId, wasMuted, mutedNow, wasDeafened, deafenedNow);
			} catch (RuntimeException | LinkageError e) {
				dev.goshi.voicy.Voicy.LOGGER.error("[Voicy] Failed to publish voice state event", e);
			}
		}
	}

	public static void publishTalking(UUID playerId, boolean start) {
		if (VoiceChatAPIHolder.get() instanceof VoicyVoiceChatAPI api) {
			try {
				api.events.publishTalking(playerId, start);
			} catch (RuntimeException | LinkageError e) {
				dev.goshi.voicy.Voicy.LOGGER.error("[Voicy] Failed to publish talking event", e);
			}
		}
	}

	public static void publishGroupJoin(UUID playerId, UUID groupId, String groupName) {
		if (VoiceChatAPIHolder.get() instanceof VoicyVoiceChatAPI api) {
			try {
				api.events.publishGroupJoin(playerId, groupId, groupName);
			} catch (RuntimeException | LinkageError e) {
				dev.goshi.voicy.Voicy.LOGGER.error("[Voicy] Failed to publish group join event", e);
			}
		}
	}

	public static void publishGroupLeave(UUID playerId, UUID groupId, String groupName) {
		if (VoiceChatAPIHolder.get() instanceof VoicyVoiceChatAPI api) {
			try {
				api.events.publishGroupLeave(playerId, groupId, groupName);
			} catch (RuntimeException | LinkageError e) {
				dev.goshi.voicy.Voicy.LOGGER.error("[Voicy] Failed to publish group leave event", e);
			}
		}
	}

	@Override
	public @NotNull VoiceState getVoiceState(@NotNull UUID playerId) {
		ClientVoiceManager.SyncedMuteState synchronizedState = clientVoiceManager.getSynchronizedVoiceState(playerId);
		if (synchronizedState != null) {
			return new VoiceState(synchronizedState.muted(), synchronizedState.deafened(), null);
		}
		var state = muteManager.securityService.getState(playerId);
		Identifier channel = state.getActiveChannel() == null ? null : Identifier.tryParse(state.getActiveChannel());
		return new VoiceState(state.isMuted(), state.isDeafened(), channel);
	}

	@Override
	public void registerModSecurityContext(@NotNull Identifier modId) {
		if (Voicy.getInstance() != null) {
			Voicy.getInstance().registerModSecurityContext(modId);
		}
	}

	static boolean sendApiCommand(ApiCommandPayload payload) {
		if (ClientPlayNetworking.canSend(ApiCommandPayload.TYPE)) {
			ClientPlayNetworking.send(payload);
			return true;
		}
		return false;
	}

	static boolean sendGroupAction(GroupActionPayload payload) {
		if (ClientPlayNetworking.canSend(GroupActionPayload.TYPE)) {
			ClientPlayNetworking.send(payload);
			return true;
		}
		return false;
	}

	static final class MuteManagerImpl implements MuteManager {
		private final VoiceSecurityService securityService;
		private final ClientVoiceManager clientVoiceManager;

		MuteManagerImpl(VoiceSecurityService securityService, ClientVoiceManager clientVoiceManager) {
			this.securityService = securityService;
			this.clientVoiceManager = clientVoiceManager;
		}

		@Override
		public boolean requestMute(@NotNull UUID target, @NotNull Identifier sourceMod, @NotNull net.voicechat.api.mute.MuteReason reason) {
			return VoicyVoiceChatAPI.sendApiCommand(new ApiCommandPayload(
					ApiCommandPayload.ApiCommandType.MUTE, target, sourceMod, true));
		}

		@Override
		public boolean requestUnmute(@NotNull UUID target, @NotNull Identifier sourceMod) {
			return VoicyVoiceChatAPI.sendApiCommand(new ApiCommandPayload(
					ApiCommandPayload.ApiCommandType.UNMUTE, target, sourceMod, false));
		}

		@Override
		public boolean isMuted(@NotNull UUID playerId) {
			ClientVoiceManager.SyncedMuteState synchronizedState = clientVoiceManager.getSynchronizedVoiceState(playerId);
			if (synchronizedState != null) {
				return synchronizedState.muted();
			}
			return securityService.getState(playerId).isMuted();
		}

		@Override
		public java.util.Optional<Identifier> getMuteSource(@NotNull UUID playerId) {
			return java.util.Optional.empty();
		}

		@Override
		public void setSelfMuted(boolean muted) {
			clientVoiceManager.setSelfMuted(muted);
		}

		@Override
		public boolean isSelfMuted() {
			return clientVoiceManager.isSelfMuted();
		}
	}

	static final class DeafenManagerImpl implements DeafenManager {
		private final VoiceSecurityService securityService;
		private final ClientVoiceManager clientVoiceManager;

		DeafenManagerImpl(VoiceSecurityService securityService, ClientVoiceManager clientVoiceManager) {
			this.securityService = securityService;
			this.clientVoiceManager = clientVoiceManager;
		}

		@Override
		public boolean requestDeafen(@NotNull UUID target, @NotNull Identifier sourceMod) {
			return VoicyVoiceChatAPI.sendApiCommand(new ApiCommandPayload(
					ApiCommandPayload.ApiCommandType.DEAFEN, target, sourceMod, true));
		}

		@Override
		public boolean requestUndeafen(@NotNull UUID target, @NotNull Identifier sourceMod) {
			return VoicyVoiceChatAPI.sendApiCommand(new ApiCommandPayload(
					ApiCommandPayload.ApiCommandType.UNDEAFEN, target, sourceMod, false));
		}

		@Override
		public boolean isDeafened(@NotNull UUID playerId) {
			ClientVoiceManager.SyncedMuteState synchronizedState = clientVoiceManager.getSynchronizedVoiceState(playerId);
			if (synchronizedState != null) {
				return synchronizedState.deafened();
			}
			return securityService.getState(playerId).isDeafened();
		}

		@Override
		public java.util.Optional<Identifier> getDeafenSource(@NotNull UUID playerId) {
			return java.util.Optional.empty();
		}

		@Override
		public void setSelfDeafened(boolean deafened) {
			clientVoiceManager.setSelfDeafened(deafened);
		}

		@Override
		public boolean isSelfDeafened() {
			return clientVoiceManager.isSelfDeafened();
		}
	}

	static final class VolumeManagerImpl implements VolumeManager {
		private final java.util.Map<UUID, Float> playerVolumes = new java.util.concurrent.ConcurrentHashMap<>();
		private dev.goshi.voicy.config.VoicyConfig config;
		private volatile float master = 1.0f;
		private final java.util.List<RegisteredFilter> filters = new java.util.concurrent.CopyOnWriteArrayList<>();

		@Override
		public float getPlayerVolume(@NotNull UUID playerId) {
			return playerVolumes.getOrDefault(playerId, config == null ? 1.0f : config.playerVolume(playerId));
		}

		@Override
		public void setPlayerVolume(@NotNull UUID playerId, float volume) {
			float clamped = Math.max(0f, Math.min(2f, volume));
			playerVolumes.put(playerId, clamped);
			if (config != null) {
				config.setPlayerVolume(playerId, clamped);
			}
		}

		@Override
		public float getMasterVolume() {
			return master;
		}

		@Override
		public void setMasterVolume(float volume) {
			master = Math.max(0f, Math.min(2f, volume));
		}

		@Override
		public float getEffectiveVolume(@NotNull UUID playerId) {
			return master * getPlayerVolume(playerId);
		}

		@Override
		public short[] processAudio(@NotNull UUID speakerId, @NotNull short[] pcm, int sampleRate) {
			short[] processed = pcm;
			for (RegisteredFilter registered : filters) {
				try {
					short[] result = registered.filter().process(processed.clone(), sampleRate, speakerId);
					if (result != null && result.length > 0) {
						processed = result;
					}
				} catch (RuntimeException ignored) {
				}
			}
			return processed;
		}

		@Override
		public @NotNull AudioFilterHandle registerFilter(@NotNull Identifier filterId, @NotNull AudioFilter filter) {
			RegisteredFilter handle = new RegisteredFilter(filterId, filter);
			filters.add(handle);
			return handle;
		}

		@Override
		public void unregisterFilter(@NotNull AudioFilterHandle handle) {
			if (handle instanceof RegisteredFilter rf) {
				filters.remove(rf);
			}
		}

		record RegisteredFilter(Identifier filterId, AudioFilter filter) implements AudioFilterHandle {
			@Override
			public @NotNull Identifier filterId() {
				return filterId;
			}
		}
	}

	static final class GroupManagerImpl implements GroupManager {
		@Override
		public @NotNull Collection<VoiceGroup> getGroups() {
			List<VoiceGroup> groups = new ArrayList<>();
			for (GroupDirectoryPayload.GroupInfo info : ClientGroupState.groups()) {
				groups.add(new GroupInfoAdapter(info));
			}
			return List.copyOf(groups);
		}

		@Override
		public @NotNull Optional<VoiceGroup> getGroup(@NotNull UUID playerId) {
			GroupDirectoryPayload.GroupInfo info = ClientGroupState.groupOf(playerId);
			return info == null ? Optional.empty() : Optional.of(new GroupInfoAdapter(info));
		}

		@Override
		public @NotNull Optional<VoiceGroup> getMyGroup() {
			GroupDirectoryPayload.GroupInfo info = ClientGroupState.myGroup();
			return info == null ? Optional.empty() : Optional.of(new GroupInfoAdapter(info));
		}

		@Override
		public boolean isInGroup(@NotNull UUID playerId) {
			return ClientGroupState.groupOf(playerId) != null;
		}

		@Override
		public boolean isMyGroupOwner() {
			return ClientGroupState.isOwner();
		}

		@Override
		public boolean talksToGroupOnly() {
			return ClientGroupState.talksToGroupOnly();
		}

		@Override
		public int myNameColor() {
			return ClientGroupState.myColor();
		}

		@Override
		public @NotNull Collection<GroupInvitation> getInvitations() {
			List<GroupInvitation> invitations = new ArrayList<>();
			for (GroupStatePayload.Invitation invitation : ClientGroupState.invitations()) {
				invitations.add(new GroupInvitation(invitation.groupId(), invitation.groupName(), invitation.inviterName()));
			}
			return List.copyOf(invitations);
		}

		@Override
		public @NotNull Collection<GroupJoinRequest> getJoinRequests() {
			List<GroupJoinRequest> requests = new ArrayList<>();
			for (GroupStatePayload.Request request : ClientGroupState.requests()) {
				requests.add(new GroupJoinRequest(request.player(), request.name()));
			}
			return List.copyOf(requests);
		}

		@Override
		public boolean createGroup(@NotNull String name, boolean isPublic, int maxPlayers) {
			return VoicyVoiceChatAPI.sendGroupAction(new GroupActionPayload(
					GroupActionPayload.Action.CREATE, GroupActionPayload.NO_ID, name, maxPlayers, isPublic));
		}

		@Override
		public boolean leaveGroup() {
			return VoicyVoiceChatAPI.sendGroupAction(GroupActionPayload.of(GroupActionPayload.Action.LEAVE));
		}

		@Override
		public boolean disbandGroup() {
			return VoicyVoiceChatAPI.sendGroupAction(GroupActionPayload.of(GroupActionPayload.Action.DISBAND));
		}

		@Override
		public boolean invite(@NotNull UUID target) {
			return VoicyVoiceChatAPI.sendGroupAction(GroupActionPayload.withId(GroupActionPayload.Action.INVITE, target));
		}

		@Override
		public boolean kick(@NotNull UUID target) {
			return VoicyVoiceChatAPI.sendGroupAction(GroupActionPayload.withId(GroupActionPayload.Action.KICK, target));
		}

		@Override
		public boolean acceptInvite(@NotNull UUID groupId) {
			return VoicyVoiceChatAPI.sendGroupAction(GroupActionPayload.withId(GroupActionPayload.Action.ACCEPT_INVITE, groupId));
		}

		@Override
		public boolean declineInvite(@NotNull UUID groupId) {
			return VoicyVoiceChatAPI.sendGroupAction(GroupActionPayload.withId(GroupActionPayload.Action.DECLINE_INVITE, groupId));
		}

		@Override
		public boolean requestJoin(@NotNull UUID targetPlayer) {
			return VoicyVoiceChatAPI.sendGroupAction(GroupActionPayload.withId(GroupActionPayload.Action.REQUEST_JOIN, targetPlayer));
		}

		@Override
		public boolean acceptJoinRequest(@NotNull UUID requester) {
			return VoicyVoiceChatAPI.sendGroupAction(GroupActionPayload.withId(GroupActionPayload.Action.ACCEPT_REQUEST, requester));
		}

		@Override
		public boolean declineJoinRequest(@NotNull UUID requester) {
			return VoicyVoiceChatAPI.sendGroupAction(GroupActionPayload.withId(GroupActionPayload.Action.DECLINE_REQUEST, requester));
		}

		@Override
		public boolean setTalkToGroupOnly(boolean groupOnly) {
			return VoicyVoiceChatAPI.sendGroupAction(new GroupActionPayload(
					GroupActionPayload.Action.SET_TALK_GROUP_ONLY, GroupActionPayload.NO_ID, "", 0, groupOnly));
		}

		@Override
		public boolean setNameColor(int colorIndex) {
			return VoicyVoiceChatAPI.sendGroupAction(new GroupActionPayload(
					GroupActionPayload.Action.SET_NAME_COLOR, GroupActionPayload.NO_ID, "", colorIndex, false));
		}

		record GroupInfoAdapter(GroupDirectoryPayload.GroupInfo info) implements VoiceGroup {
			@Override
			public @NotNull UUID id() {
				return info.id();
			}

			@Override
			public @NotNull String name() {
				return info.name();
			}

			@Override
			public @NotNull UUID owner() {
				return info.owner();
			}

			@Override
			public boolean isPublic() {
				return info.isPublic();
			}

			@Override
			public int maxPlayers() {
				return info.maxPlayers();
			}

			@Override
			public @NotNull Collection<GroupMember> members() {
				List<GroupMember> members = new ArrayList<>();
				for (GroupDirectoryPayload.Member member : info.members()) {
					members.add(new GroupMemberAdapter(member));
				}
				return List.copyOf(members);
			}

			@Override
			public boolean isFull() {
				return info.isFull();
			}

			@Override
			public boolean hasMember(@NotNull UUID player) {
				return info.hasMember(player);
			}
		}

		record GroupMemberAdapter(GroupDirectoryPayload.Member member) implements GroupMember {
			@Override
			public @NotNull UUID id() {
				return member.id();
			}

			@Override
			public @NotNull String name() {
				return member.name();
			}

			@Override
			public int colorIndex() {
				return member.color();
			}
		}
	}
}
