package dev.goshi.voicy.paper.security;

import dev.goshi.voicy.paper.net.VoicyChannels;
import dev.goshi.voicy.paper.net.VoicyByteBuf;
import dev.goshi.voicy.paper.net.payload.ApiCommandPayload;
import dev.goshi.voicy.paper.net.payload.StateSyncPayload;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class VoiceSecurityService {

	public static final String OWN_NAMESPACE = "voicy";

	private final Logger logger;
	private final Plugin plugin;
	private final Map<UUID, PlayerVoiceState> states = new ConcurrentHashMap<>();
	private final Map<UUID, byte[]> sessionSecrets = new ConcurrentHashMap<>();
	private final Set<String> registeredNamespaces = ConcurrentHashMap.newKeySet();
	private final SecureRandom secureRandom = new SecureRandom();

	public VoiceSecurityService(Plugin plugin) {
		this.plugin = plugin;
		this.logger = plugin.getLogger();
		registeredNamespaces.add(OWN_NAMESPACE);
	}

	public void registerPluginContext(String namespace) {
		registeredNamespaces.add(namespace);
	}

	public PlayerVoiceState getState(UUID playerId) {
		return states.computeIfAbsent(playerId, PlayerVoiceState::new);
	}

	public void setActiveChannel(UUID playerId, String channelId) {
		getState(playerId).setActiveChannel(channelId);
	}

	public byte[] issueSessionSecret(UUID playerId) {
		byte[] secret = new byte[32];
		secureRandom.nextBytes(secret);
		sessionSecrets.put(playerId, secret);
		return secret;
	}

	public byte[] getSessionSecret(UUID playerId) {
		return sessionSecrets.get(playerId);
	}

	public void onPlayerJoin(UUID playerId) {
		states.computeIfAbsent(playerId, PlayerVoiceState::new);
	}

	public void onPlayerLeave(UUID playerId) {
		states.remove(playerId);
		sessionSecrets.remove(playerId);
	}

	public boolean canTransmit(UUID playerId) {
		return !getState(playerId).isMuted();
	}

	public boolean shareChannel(UUID a, UUID b) {
		PlayerVoiceState sa = getState(a);
		PlayerVoiceState sb = getState(b);
		if (sa.getActiveChannel() == null && sb.getActiveChannel() == null) {
			return true;
		}
		return Objects.equals(sa.getActiveChannel(), sb.getActiveChannel());
	}

	public void handleApiCommand(Player caller, ApiCommandPayload payload) {
		if (caller == null || payload == null) {
			return;
		}
		String namespace = namespaceOf(payload.sourceMod());
		UUID target = payload.targetPlayer();
		boolean selfTarget = caller.getUniqueId().equals(target);

		if (!selfTarget && !caller.isOp() && !caller.hasPermission("voicy.admin")) {
			logger.warning("Security: " + caller.getName() + " tried to target " + target + " without permission");
			return;
		}

		if (!registeredNamespaces.contains(namespace)) {
			if (!selfTarget) {
				logger.warning("EXPLOIT: untrusted namespace '" + namespace + "' in API command from " + caller.getUniqueId());
				return;
			}
		}

		PlayerVoiceState targetState = getState(target);
		switch (payload.commandType()) {
			case MUTE -> targetState.setMuted(payload.enabled());
			case UNMUTE -> targetState.setMuted(false);
			case DEAFEN -> targetState.setDeafened(payload.enabled());
			case UNDEAFEN -> targetState.setDeafened(false);
			case JOIN_CHANNEL -> targetState.setActiveChannel(payload.sourceMod());
			case LEAVE_CHANNEL -> targetState.setActiveChannel(null);
		}
		broadcastState(caller.getServer(), target);
	}

	public void setSelfMuted(Player player, boolean muted) {
		getState(player.getUniqueId()).setMuted(muted);
		broadcastState(player.getServer(), player.getUniqueId());
	}

	public void setSelfDeafened(Player player, boolean deafened) {
		getState(player.getUniqueId()).setDeafened(deafened);
		broadcastState(player.getServer(), player.getUniqueId());
	}

	public void broadcastState(Server server, UUID playerId) {
		PlayerVoiceState state = getState(playerId);
		StateSyncPayload payload = new StateSyncPayload(playerId, state.isMuted(), state.isDeafened());
		VoicyByteBuf buf = new VoicyByteBuf();
		payload.encode(buf);
		byte[] data = buf.toByteArray();
		for (Player player : server.getOnlinePlayers()) {
			player.sendPluginMessage(plugin, VoicyChannels.STATE_SYNC, data);
		}
	}

	private static String namespaceOf(String sourceMod) {
		if (sourceMod == null || sourceMod.isEmpty()) {
			return "";
		}
		int colon = sourceMod.indexOf(':');
		return colon < 0 ? sourceMod : sourceMod.substring(0, colon);
	}
}
