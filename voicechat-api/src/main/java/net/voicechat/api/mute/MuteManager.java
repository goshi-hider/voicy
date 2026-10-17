package net.voicechat.api.mute;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public interface MuteManager {

	boolean requestMute(@NotNull UUID target, @NotNull Identifier sourceMod, @NotNull MuteReason reason);

	boolean requestUnmute(@NotNull UUID target, @NotNull Identifier sourceMod);

	boolean isMuted(@NotNull UUID playerId);

	Optional<Identifier> getMuteSource(@NotNull UUID playerId);

	void setSelfMuted(boolean muted);

	boolean isSelfMuted();
}
