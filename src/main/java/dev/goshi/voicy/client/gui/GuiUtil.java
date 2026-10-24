package dev.goshi.voicy.client.gui;

import dev.goshi.voicy.group.GroupNameColors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

final class GuiUtil {
	private GuiUtil() {
	}

	static SkinTextures skinFor(UUID player) {
		var handler = MinecraftClient.getInstance().getNetworkHandler();
		PlayerListEntry entry = handler == null ? null : handler.getPlayerListEntry(player);
		return entry != null ? entry.getSkinTextures() : DefaultSkinHelper.getSkinTextures(player);
	}

	static void drawHead(DrawContext context, UUID player, int x, int y, int size) {
		PlayerSkinDrawer.draw(context, skinFor(player), x, y, size);
	}

	static MutableText coloredName(UUID player, String name) {
		MutableText text = Text.literal(name);
		Formatting color = GroupNameColors.get(player);
		return color == null ? text : text.formatted(color);
	}
}
