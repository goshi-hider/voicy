package net.voicechat.api.mute;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative mute management. All state changes initiated by third-party mods
 * are validated against registered security contexts before taking effect.
 */
public interface MuteManager {

	/**
	 * Requests that the server mute {@code target}. The call is attributed to {@code sourceMod}.
	 *
	 * @return {@code true} if the request was accepted for processing
	 */
	boolean requestMute(@NotNull UUID target, @NotNull Identifier sourceMod, @NotNull MuteReason reason);

	/**
	 * Requests that the server unmute {@code target}.
	 */
	boolean requestUnmute(@NotNull UUID target, @NotNull Identifier sourceMod);

	/** Returns whether the player is currently muted according to server state. */
	boolean isMuted(@NotNull UUID playerId);

	/** Returns the mod that last applied the active mute, if any. */
	Optional<Identifier> getMuteSource(@NotNull UUID playerId);

	/** Locally toggled self-mute (client sends intent; server confirms). */
	void setSelfMuted(boolean muted);

	boolean isSelfMuted();
}
