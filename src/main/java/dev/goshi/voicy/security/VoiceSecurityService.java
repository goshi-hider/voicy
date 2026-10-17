package dev.goshi.voicy.security;

import dev.goshi.voicy.Voicy;
import dev.goshi.voicy.network.payload.ApiCommandPayload;
import dev.goshi.voicy.network.payload.StateSyncPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative verification protocol: validates mod namespaces, enforces mute/deafen, drops exploit attempts.
 */
public final class VoiceSecurityService {

	private final Map<UUID, PlayerVoiceState> states = new ConcurrentHashMap<>();
	private final Map<UUID, byte[]> sessionSecrets = new ConcurrentHashMap<>();
	private final Set<String> registeredNamespaces = ConcurrentHashMap.newKeySet();
	private final Set<String> serverModNamespaces = ConcurrentHashMap.newKeySet();
	private final SecureRandom secureRandom = new SecureRandom();

	public VoiceSecurityService() {
		FabricLoader.getInstance().getAllMods().forEach(mod ->
				serverModNamespaces.add(mod.getMetadata().getId()));
		registeredNamespaces.add(Voicy.MOD_ID);
	}

	public void registerModContext(Identifier modId) {
		registeredNamespaces.add(modId.getNamespace());
	}

	public PlayerVoiceState getState(UUID playerId) {
		return states.computeIfAbsent(playerId, PlayerVoiceState::new);
	}

	public void setActiveChannel(UUID playerId, Identifier channelId) {
		getState(playerId).setActiveChannel(channelId == null ? null : channelId.toString());
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

	public void handleApiCommand(ServerPlayerEntity caller, ApiCommandPayload payload) {
		if (caller == null || payload == null) {
			return;
		}
		String namespace = payload.sourceMod() == null ? "" : payload.sourceMod().getNamespace();
		UUID target = payload.targetPlayer();
		boolean selfTarget = caller.getUuid().equals(target);

		if (!selfTarget && !caller.hasPermissionLevel(2)) {
			Voicy.LOGGER.warn("[Voicy] Security: {} tried to target {} without permission", caller.getGameProfile().getName(), target);
			return;
		}

		if (!registeredNamespaces.contains(namespace) || !serverModNamespaces.contains(namespace)) {
			if (!selfTarget) {
				Voicy.LOGGER.warn("[Voicy] EXPLOIT: untrusted namespace '{}' in API command from {}", namespace, caller.getUuid());
				return;
			}
		}

		PlayerVoiceState targetState = getState(target);
		switch (payload.commandType()) {
			case MUTE -> targetState.setMuted(payload.enabled());
			case UNMUTE -> targetState.setMuted(false);
			case DEAFEN -> targetState.setDeafened(payload.enabled());
			case UNDEAFEN -> targetState.setDeafened(false);
			case JOIN_CHANNEL -> targetState.setActiveChannel(payload.sourceMod() == null ? "" : payload.sourceMod().toString());
			case LEAVE_CHANNEL -> targetState.setActiveChannel(null);
		}
		broadcastState(caller.server, target);
	}

	public void setSelfMuted(ServerPlayerEntity player, boolean muted) {
		PlayerVoiceState state = getState(player.getUuid());
		state.setMuted(muted);
		broadcastState(player.server, player.getUuid());
	}

	public void setSelfDeafened(ServerPlayerEntity player, boolean deafened) {
		PlayerVoiceState state = getState(player.getUuid());
		state.setDeafened(deafened);
		broadcastState(player.server, player.getUuid());
	}

	public void broadcastState(net.minecraft.server.MinecraftServer server, UUID playerId) {
		PlayerVoiceState state = getState(playerId);
		StateSyncPayload payload = new StateSyncPayload(playerId, state.isMuted(), state.isDeafened());
		for (ServerPlayerEntity player : PlayerLookup.all(server)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
