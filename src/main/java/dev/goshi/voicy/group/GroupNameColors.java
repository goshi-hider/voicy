package dev.goshi.voicy.group;

import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.UUID;

public final class GroupNameColors {

	private static volatile Map<UUID, Formatting> colors = Map.of();

	private GroupNameColors() {
	}

	public static Formatting fromIndex(int index) {
		if (index < 0 || index >= GroupManager.COLOR_COUNT) {
			return null;
		}
		return Formatting.byColorIndex(index);
	}

	public static Formatting get(UUID player) {
		return colors.get(player);
	}

	public static void replaceAll(Map<UUID, Formatting> newColors) {
		colors = Map.copyOf(newColors);
	}

	public static void clear() {
		colors = Map.of();
	}
}
