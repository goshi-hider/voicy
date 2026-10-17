package net.voicechat.api.channel;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Group and private audio channels layered on top of proximity routing.
 */
public interface ChannelManager {

	@NotNull
	VoiceChannel createChannel(@NotNull Identifier channelId, @NotNull ChannelType type, @NotNull ChannelSettings settings);

	Optional<VoiceChannel> getChannel(@NotNull Identifier channelId);

	void deleteChannel(@NotNull Identifier channelId);

	@NotNull
	Collection<VoiceChannel> getChannels();

	/**
	 * Moves a player into a channel. Server validates membership changes.
	 */
	boolean joinChannel(@NotNull UUID playerId, @NotNull Identifier channelId);

	boolean leaveChannel(@NotNull UUID playerId, @NotNull Identifier channelId);

	@Nullable
	Identifier getActiveChannel(@NotNull UUID playerId);

	enum ChannelType {
		PROXIMITY,
		GROUP,
		PRIVATE
	}

	interface ChannelSettings {
		double maxDistance();

		boolean occluded();

		static ChannelSettings defaults() {
			return new ChannelSettings() {
				@Override
				public double maxDistance() {
					return 48.0;
				}

				@Override
				public boolean occluded() {
					return true;
				}
			};
		}
	}

	interface VoiceChannel {
		@NotNull
		Identifier id();

		@NotNull
		ChannelType type();

		@NotNull
		Collection<UUID> members();

		@NotNull
		ChannelSettings settings();
	}
}
