package dev.goshi.voicy.api;

import dev.goshi.voicy.Voicy;
import dev.goshi.voicy.channel.ServerChannelManager;
import dev.goshi.voicy.client.ClientVoiceManager;
import dev.goshi.voicy.network.payload.ApiCommandPayload;
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
import net.voicechat.api.mute.MuteManager;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class VoicyVoiceChatAPI implements VoiceChatAPI {

	private final MuteManagerImpl muteManager;
	private final DeafenManagerImpl deafenManager;
	private final VolumeManagerImpl volumeManager;
	private final ServerChannelManager channelManager;
	private final ClientVoiceManager clientVoiceManager;
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
	public @NotNull VoiceChatEvents getEvents() {
		return events;
	}

	public static void publishVoiceState(UUID playerId, boolean wasMuted, boolean mutedNow, boolean wasDeafened, boolean deafenedNow) {
		if (VoiceChatAPIHolder.get() instanceof VoicyVoiceChatAPI api) {
			try {
				api.events.publishState(playerId, wasMuted, mutedNow, wasDeafened, deafenedNow);
			} catch (RuntimeException | LinkageError e) {
				// Event delivery is best-effort: a broken listener or API jar mismatch must never
				// crash the render thread from a button click.
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
}