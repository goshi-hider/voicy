package dev.goshi.voicy.paper.listener;

import dev.goshi.voicy.paper.VoicyPaperPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerConnectionListener implements Listener {

	private final VoicyPaperPlugin plugin;

	public PlayerConnectionListener(VoicyPaperPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		var player = event.getPlayer();
		plugin.securityService().onPlayerJoin(player.getUniqueId());
		plugin.sendHandshake(player);
		plugin.securityService().broadcastState(plugin.getServer(), player.getUniqueId());
		plugin.speakerManager().syncToPlayer(player);
		plugin.groupNetwork().onJoin(player);
		plugin.animationStyles().sendAllTo(player);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		var player = event.getPlayer();
		plugin.securityService().onPlayerLeave(player.getUniqueId());
		plugin.groupNetwork().onDisconnect(player);
		plugin.animationStyles().remove(player.getUniqueId());
	}
}
