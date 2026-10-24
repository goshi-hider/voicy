package net.voicechat.api.deafen;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public interface DeafenManager {
	boolean requestDeafen(@NotNull UUID target, @NotNull Identifier sourceMod);

	boolean requestUndeafen(@NotNull UUID target, @NotNull Identifier sourceMod);

	boolean isDeafened(@NotNull UUID playerId);

	Optional<Identifier> getDeafenSource(@NotNull UUID playerId);

	void setSelfDeafened(boolean deafened);

	boolean isSelfDeafened();
}
