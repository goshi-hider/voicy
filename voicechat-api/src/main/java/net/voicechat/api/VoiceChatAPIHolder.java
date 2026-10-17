package net.voicechat.api;

import org.jetbrains.annotations.Nullable;

/**
 * Holds the runtime {@link VoiceChatAPI} implementation registered by Voicy at startup.
 */
public final class VoiceChatAPIHolder {

	private static volatile VoiceChatAPI instance;

	private VoiceChatAPIHolder() {
	}

	public static @Nullable VoiceChatAPI get() {
		return instance;
	}

	public static void set(@Nullable VoiceChatAPI api) {
		instance = api;
	}
}
