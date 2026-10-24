package net.voicechat.api.events;

import net.minecraft.util.Identifier;
import net.voicechat.api.mute.MuteReason;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Consumer;

public interface VoiceChatEvents {
	void onPlayerMuted(@NotNull Consumer<PlayerMuteEvent> listener);

	void onPlayerUnmuted(@NotNull Consumer<PlayerUnmuteEvent> listener);

	void onPlayerDeafened(@NotNull Consumer<PlayerDeafenEvent> listener);

	void onPlayerUndeafened(@NotNull Consumer<PlayerUndeafenEvent> listener);

	void onPlayerStartTalking(@NotNull Consumer<PlayerTalkingEvent> listener);

	void onPlayerStopTalking(@NotNull Consumer<PlayerTalkingEvent> listener);

	void onPlayerJoinedGroup(@NotNull Consumer<PlayerJoinedGroupEvent> listener);

	void onPlayerLeftGroup(@NotNull Consumer<PlayerLeftGroupEvent> listener);

	void unregisterListener(@NotNull Consumer<?> listener);

	record PlayerMuteEvent(@NotNull UUID playerId, @NotNull MuteReason reason, @NotNull Identifier sourceMod) {
	}

	record PlayerUnmuteEvent(@NotNull UUID playerId, @NotNull Identifier sourceMod) {
	}

	record PlayerDeafenEvent(@NotNull UUID playerId, @NotNull Identifier sourceMod) {
	}

	record PlayerUndeafenEvent(@NotNull UUID playerId, @NotNull Identifier sourceMod) {
	}

	record PlayerTalkingEvent(@NotNull UUID playerId) {
	}

	record PlayerJoinedGroupEvent(@NotNull UUID playerId, @NotNull UUID groupId, @NotNull String groupName) {
	}

	record PlayerLeftGroupEvent(@NotNull UUID playerId, @NotNull UUID groupId, @NotNull String groupName) {
	}
}
