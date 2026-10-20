package dev.goshi.voicy.paper.animation;

import dev.goshi.voicy.paper.net.VoicyByteBuf;
import dev.goshi.voicy.paper.net.VoicyChannels;
import dev.goshi.voicy.paper.net.payload.AnimationStylePayload;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AnimationStyleService {

	private final Plugin plugin;
	private final Map<UUID, AnimationStylePayload> styles = new ConcurrentHashMap<>();

	public AnimationStyleService(Plugin plugin) {
		this.plugin = plugin;
	}

	public void handle(Player sender, AnimationStylePayload payload) {
		int style = Math.max(0, Math.min(6, payload.style()));
		float split = Float.isNaN(payload.splitHeight()) ? 0.5f : Math.max(0.15f, Math.min(0.85f, payload.splitHeight()));
		AnimationStylePayload clean = new AnimationStylePayload(sender.getUniqueId(), style, split);
		styles.put(sender.getUniqueId(), clean);
		for (Player other : plugin.getServer().getOnlinePlayers()) {
			send(other, clean);
		}
	}

	public void sendAllTo(Player player) {
		for (AnimationStylePayload payload : styles.values()) {
			send(player, payload);
		}
	}

	public void remove(UUID playerId) {
		styles.remove(playerId);
	}

	private void send(Player to, AnimationStylePayload payload) {
		VoicyByteBuf buf = new VoicyByteBuf();
		payload.encode(buf);
		to.sendPluginMessage(plugin, VoicyChannels.ANIMATION_STYLE, buf.toByteArray());
	}
}
