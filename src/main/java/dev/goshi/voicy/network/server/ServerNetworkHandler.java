package dev.goshi.voicy.network.server;

import dev.goshi.voicy.config.VoicyConfig;
import dev.goshi.voicy.network.payload.ApiCommandPayload;
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

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			securityService.onPlayerJoin(player.getUuid());
			sendHandshake(player);
			broadcastState(player);
			dev.goshi.voicy.Voicy.getInstance().speakerManager().syncToPlayer(player);
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				securityService.onPlayerLeave(handler.player.getUuid()));
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
				// Empty = "the address you connected to". The server can't reliably know its own public
				// address, and "127.0.0.1" points remote clients at their own machine.
				""
		));
	}

	private void broadcastState(ServerPlayerEntity player) {
		securityService.broadcastState(player.server, player.getUuid());
	}
}
