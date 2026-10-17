package net.voicechat.api;

import net.minecraft.util.Identifier;
import net.voicechat.api.audio.VolumeManager;
import net.voicechat.api.channel.ChannelManager;
import net.voicechat.api.deafen.DeafenManager;
import net.voicechat.api.events.VoiceChatEvents;
import net.voicechat.api.mute.MuteManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Global entry point for third-party Fabric mods integrating with Voicy proximity voice chat.
 * <p>
 * Obtain the singleton via {@link #getInstance()}. If Voicy is not installed, {@code getInstance()}
 * returns {@code null} on the logical side where the API is unavailable.
 */
public interface VoiceChatAPI {

	/**
	 * Retrieves the global singleton instance of the Voice Chat API.
	 *
	 * @return the API instance, or {@code null} when Voicy is not loaded on this side
	 */
	@Nullable
	static VoiceChatAPI getInstance() {
		return VoiceChatAPIHolder.get();
	}

	/** Access the mute management subsystem (server-authoritative). */
	@NotNull
	MuteManager getMuteManager();

	/** Access the deafen management subsystem (server-authoritative). */
	@NotNull
	DeafenManager getDeafenManager();

	/** Access volume controls and custom audio filters (client-local). */
	@NotNull
	VolumeManager getVolumeManager();

	/** Access group and private audio channels. */
	@NotNull
	ChannelManager getChannelManager();

	/** Accesses client-side voice state and talking transition events. */
	@NotNull
	VoiceChatEvents getEvents();

	/** Returns the latest voice state known to this runtime. */
	@NotNull
	VoiceState getVoiceState(@NotNull java.util.UUID playerId);

	/**
	 * Registers a third-party mod ID with the security context.
	 * Must be called during mod initialization on both client and server.
	 * Only registered namespaces may issue authoritative API commands on the server.
	 *
	 * @param modId the mod's root identifier (e.g. {@code Identifier.of("my_mod", "main")})
	 */
	void registerModSecurityContext(@NotNull Identifier modId);

	/** Immutable snapshot of a player's voice state. */
	record VoiceState(boolean muted, boolean deafened, @Nullable Identifier activeChannel) {
	}
}
