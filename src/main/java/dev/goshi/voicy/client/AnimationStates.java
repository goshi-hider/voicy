package dev.goshi.voicy.client;

import dev.goshi.voicy.config.AnimationStyle;
import dev.goshi.voicy.config.VoicyConfig;
import net.minecraft.client.MinecraftClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AnimationStates {

	private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

	private AnimationStates() {
	}

	public record Entry(AnimationStyle style, float splitHeight) {
	}

	public static void put(UUID id, int style, float splitHeight) {
		ENTRIES.put(id, new Entry(AnimationStyle.byIndex(style), splitHeight));
	}

	public static void clear() {
		ENTRIES.clear();
	}

	public static Entry of(UUID id) {
		VoicyConfig config = VoicyConfig.INSTANCE;
		Entry own = new Entry(config.animationStyle(), config.splitHeight());
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && client.player.getUuid().equals(id)) {
			return own;
		}
		Entry synced = ENTRIES.get(id);
		return synced != null ? synced : own;
	}
}
