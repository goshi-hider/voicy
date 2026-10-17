package net.voicechat.api.audio;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Client-local volume overrides and optional per-stream audio filters.
 */
public interface VolumeManager {

	float getPlayerVolume(@NotNull UUID playerId);

	void setPlayerVolume(@NotNull UUID playerId, float volume);

	float getMasterVolume();

	void setMasterVolume(float volume);

	/** Applies the current master and per-player volume values. */
	float getEffectiveVolume(@NotNull UUID playerId);

	/** Runs registered PCM filters in registration order. */
	@NotNull
	short[] processAudio(@NotNull UUID speakerId, @NotNull short[] pcm, int sampleRate);

	/**
	 * Registers a filter invoked on decoded PCM before spatialization.
	 *
	 * @return a handle used to unregister the filter
	 */
	@NotNull
	AudioFilterHandle registerFilter(@NotNull Identifier filterId, @NotNull AudioFilter filter);

	void unregisterFilter(@NotNull AudioFilterHandle handle);

	interface AudioFilter {
		/**
		 * Transforms interleaved mono 16-bit PCM samples in-place or returns a new buffer.
		 *
		 * @param pcm       input samples
		 * @param sampleRate decode sample rate (24000 or 48000)
		 * @param speakerId  UUID of the remote speaker
		 * @return processed samples (may be {@code pcm})
		 */
		short[] process(@NotNull short[] pcm, int sampleRate, @NotNull UUID speakerId);
	}

	interface AudioFilterHandle {
		@NotNull
		Identifier filterId();
	}
}
