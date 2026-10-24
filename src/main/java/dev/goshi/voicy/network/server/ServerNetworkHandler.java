package dev.goshi.voicy.network.server;

import dev.goshi.voicy.config.VoicyConfig;
import dev.goshi.voicy.network.payload.ApiCommandPayload;
import dev.goshi.voicy.network.payload.GroupActionPayload;
import dev.goshi.voicy.network.payload.HandshakePayload;
import dev.goshi.voicy.network.payload.StateSyncPayload;
import dev.goshi.voicy.network.payload.VoiceFramePayload;
import dev.goshi.voicy.network.router.VoiceRouter;
import dev.goshi.voicy.network.udp.UdpVoiceServer;
import dev.goshi.voicy.security.VoiceSecurityService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ServerNetworkHandler {
	private final VoiceRouter router;
	private final VoiceSecurityService securityService;
	private final UdpVoiceServer udpServer;
	private final VoicyConfig config;
	private final java.util.Map<java.util.UUID, dev.goshi.voicy.network.payload.AnimationStylePayload> animationStyles =
			new java.util.concurrent.ConcurrentHashMap<>();

	public ServerNetworkHandler(
			VoiceRouter router,
			VoiceSecurityService securityService,
			UdpVoiceServer udpServer,
			VoicyConfig config
	) {
		this.router = router;
		this.securityService = securityService;
		this.udpServer = udpServer;
		this.config = config;
	}

	public void register() {
		ServerPlayNetworking.registerGlobalReceiver(VoiceFramePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> router.route(context.player(), payload));
		});

		ServerPlayNetworking.registerGlobalReceiver(ApiCommandPayload.TYPE, (payload, context) -> {
			context.server().execute(() ->
					securityService.handleApiCommand(context.player(), payload));
		});

		ServerPlayNetworking.registerGlobalReceiver(HandshakePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> sendHandshake(context.player()));
		});

		ServerPlayNetworking.registerGlobalReceiver(GroupActionPayload.TYPE, (payload, context) -> {
			context.server().execute(() ->
					dev.goshi.voicy.Voicy.getInstance().groupNetwork().handle(context.player(), payload));
		});

		ServerPlayNetworking.registerGlobalReceiver(dev.goshi.voicy.network.payload.AnimationStylePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> handleAnimationStyle(context.player(), payload));
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			securityService.onPlayerJoin(player.getUuid());
			sendHandshake(player);
			broadcastState(player);
			dev.goshi.voicy.Voicy.getInstance().speakerManager().syncToPlayer(player);
			dev.goshi.voicy.Voicy.getInstance().groupNetwork().onJoin(player);
			sendAnimationStyles(player);
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			securityService.onPlayerLeave(handler.player.getUuid());
			animationStyles.remove(handler.player.getUuid());
			dev.goshi.voicy.Voicy.getInstance().groupNetwork().onDisconnect(handler.player);
		});
	}

	private void sendHandshake(ServerPlayerEntity player) {
		byte[] secret = securityService.issueSessionSecret(player.getUuid());
		HandshakePayload.TransportMode mode = config.preferUdpFallback() && udpServer.isRunning()
				? HandshakePayload.TransportMode.UDP_FALLBACK
				: HandshakePayload.TransportMode.FABRIC_PAYLOAD;
		dev.goshi.voicy.Voicy.LOGGER.info("[Voicy] Negotiating {} voice transport with {} (udpPort={})", mode, player.getGameProfile().getName(), udpServer.getBoundPort());

		ServerPlayNetworking.send(player, new HandshakePayload(
				mode,
				udpServer.getBoundPort(),
				config.sampleRate(),
				config.frameDurationMs(),
				secret,

				""
		));
	}

	private void handleAnimationStyle(ServerPlayerEntity player, dev.goshi.voicy.network.payload.AnimationStylePayload payload) {
		int style = Math.max(0, Math.min(6, payload.style()));
		float split = Float.isNaN(payload.splitHeight()) ? 0.5f : Math.max(0.15f, Math.min(0.85f, payload.splitHeight()));
		dev.goshi.voicy.network.payload.AnimationStylePayload clean =
				new dev.goshi.voicy.network.payload.AnimationStylePayload(player.getUuid(), style, split);
		animationStyles.put(player.getUuid(), clean);
		for (ServerPlayerEntity other : net.fabricmc.fabric.api.networking.v1.PlayerLookup.all(player.server)) {
			if (ServerPlayNetworking.canSend(other, dev.goshi.voicy.network.payload.AnimationStylePayload.TYPE)) {
				ServerPlayNetworking.send(other, clean);
			}
		}
	}

	private void sendAnimationStyles(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, dev.goshi.voicy.network.payload.AnimationStylePayload.TYPE)) {
			return;
		}
		for (dev.goshi.voicy.network.payload.AnimationStylePayload payload : animationStyles.values()) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private void broadcastState(ServerPlayerEntity player) {
		securityService.broadcastState(player.server, player.getUuid());
	}
}
