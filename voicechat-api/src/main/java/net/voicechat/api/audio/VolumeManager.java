package net.voicechat.api.audio;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface VolumeManager {

	float getPlayerVolume(@NotNull UUID playerId);

	void setPlayerVolume(@NotNull UUID playerId, float volume);

	float getMasterVolume();

	void setMasterVolume(float volume);

	float getEffectiveVolume(@NotNull UUID playerId);

	@NotNull
	short[] processAudio(@NotNull UUID speakerId, @NotNull short[] pcm, int sampleRate);

	@NotNull
	AudioFilterHandle registerFilter(@NotNull Identifier filterId, @NotNull AudioFilter filter);

	void unregisterFilter(@NotNull AudioFilterHandle handle);

	interface AudioFilter {

		short[] process(@NotNull short[] pcm, int sampleRate, @NotNull UUID speakerId);
	}

	interface AudioFilterHandle {
		@NotNull
		Identifier filterId();
	}
}
