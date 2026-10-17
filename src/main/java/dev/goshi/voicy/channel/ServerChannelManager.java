package dev.goshi.voicy.channel;

import net.minecraft.util.Identifier;
import net.voicechat.api.channel.ChannelManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiConsumer;

public final class ServerChannelManager implements ChannelManager {

	private final Map<Identifier, VoiceChannelImpl> channels = new ConcurrentHashMap<>();
	private final Map<UUID, Identifier> activeChannel = new ConcurrentHashMap<>();
	private volatile BiConsumer<UUID, Identifier> activeChannelListener = (playerId, channelId) -> {};

	public void setActiveChannelListener(BiConsumer<UUID, Identifier> listener) {
		activeChannelListener = listener == null ? (playerId, channelId) -> {} : listener;
	}

	@Override
	public @NotNull VoiceChannel createChannel(@NotNull Identifier channelId, @NotNull ChannelType type, @NotNull ChannelSettings settings) {
		VoiceChannelImpl channel = new VoiceChannelImpl(channelId, type, settings);
		channels.put(channelId, channel);
		return channel;
	}

	@Override
	public Optional<VoiceChannel> getChannel(@NotNull Identifier channelId) {
		return Optional.ofNullable(channels.get(channelId));
	}

	@Override
	public void deleteChannel(@NotNull Identifier channelId) {
		VoiceChannelImpl removed = channels.remove(channelId);
		if (removed == null) {
			return;
		}
		for (UUID member : removed.members) {
			if (activeChannel.remove(member, channelId)) {
				activeChannelListener.accept(member, null);
			}
		}
	}

	@Override
	public @NotNull Collection<VoiceChannel> getChannels() {
		return java.util.List.copyOf(channels.values());
	}

	@Override
	public boolean joinChannel(@NotNull UUID playerId, @NotNull Identifier channelId) {
		VoiceChannelImpl channel = channels.get(channelId);
		if (channel == null) {
			return false;
		}
		Identifier previous = activeChannel.put(playerId, channelId);
		if (previous != null && !previous.equals(channelId)) {
			VoiceChannelImpl previousChannel = channels.get(previous);
			if (previousChannel != null) {
				previousChannel.members.remove(playerId);
			}
		}
		channel.members.add(playerId);
		activeChannelListener.accept(playerId, channelId);
		return true;
	}

	@Override
	public boolean leaveChannel(@NotNull UUID playerId, @NotNull Identifier channelId) {
		boolean wasActive = activeChannel.remove(playerId, channelId);
		VoiceChannelImpl ch = channels.get(channelId);
		if (ch != null) {
			ch.members.remove(playerId);
		}
		if (wasActive) {
			activeChannelListener.accept(playerId, null);
		}
		return true;
	}

	@Override
	public @Nullable Identifier getActiveChannel(@NotNull UUID playerId) {
		return activeChannel.get(playerId);
	}

	static final class VoiceChannelImpl implements VoiceChannel {
		private final Identifier id;
		private final ChannelType type;
		private final ChannelSettings settings;
		private final CopyOnWriteArraySet<UUID> members = new CopyOnWriteArraySet<>();

		VoiceChannelImpl(Identifier id, ChannelType type, ChannelSettings settings) {
			this.id = id;
			this.type = type;
			this.settings = settings;
		}

		@Override
		public @NotNull Identifier id() {
			return id;
		}

		@Override
		public @NotNull ChannelType type() {
			return type;
		}

		@Override
		public @NotNull Collection<UUID> members() {
			return java.util.Set.copyOf(members);
		}

		@Override
		public @NotNull ChannelSettings settings() {
			return settings;
		}
	}
}
