package dev.goshi.voicy.paper;

import dev.goshi.voicy.paper.command.SpeakerCommand;
import dev.goshi.voicy.paper.config.PaperVoicyConfig;
import dev.goshi.voicy.paper.group.GroupManager;
import dev.goshi.voicy.paper.group.GroupNetwork;
import dev.goshi.voicy.paper.listener.PlayerConnectionListener;
import dev.goshi.voicy.paper.listener.VoiceMessageListener;
import dev.goshi.voicy.paper.animation.AnimationStyleService;
import dev.goshi.voicy.paper.net.UdpVoiceServer;
import dev.goshi.voicy.paper.net.VoicyByteBuf;
import dev.goshi.voicy.paper.net.VoicyChannels;
import dev.goshi.voicy.paper.net.payload.HandshakePayload;
import dev.goshi.voicy.paper.router.VoiceRouter;
import dev.goshi.voicy.paper.security.VoiceSecurityService;
import dev.goshi.voicy.paper.speaker.SpeakerManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoicyPaperPlugin extends JavaPlugin {

	private PaperVoicyConfig config;
	private VoiceSecurityService securityService;
	private GroupManager groupManager;
	private GroupNetwork groupNetwork;
	private SpeakerManager speakerManager;
	private AnimationStyleService animationStyles;
	private VoiceRouter router;
	private UdpVoiceServer udpServer;

	@Override
	public void onEnable() {
		config = new PaperVoicyConfig(this);

		securityService = new VoiceSecurityService(this);
		groupManager = new GroupManager();
		groupNetwork = new GroupNetwork(this, groupManager);
		speakerManager = new SpeakerManager(this);
		animationStyles = new AnimationStyleService(this);
		router = new VoiceRouter(this, securityService, groupManager, speakerManager, config);

		udpServer = new UdpVoiceServer(getLogger());
		udpServer.setSecretProvider(securityService::getSessionSecret);
		udpServer.setConsumer((senderId, frame) -> getServer().getScheduler().runTask(this, () -> {
			Player sender = getServer().getPlayer(senderId);
			if (sender != null) {
				router.route(sender, frame);
			}
		}));
		if (config.preferUdpFallback()) {
			int port = config.udpPort() > 0 ? config.udpPort() : PaperVoicyConfig.DEFAULT_UDP_PORT;
			udpServer.start(port);
		}

		registerChannels();
		getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

		SpeakerCommand speakerCommand = new SpeakerCommand(this);
		var command = getCommand("speaker");
		if (command != null) {
			command.setExecutor(speakerCommand);
			command.setTabCompleter(speakerCommand);
		}

		getServer().getScheduler().runTaskTimer(this, router::tick, 1L, 1L);
		getServer().getScheduler().runTaskTimer(this, speakerManager::syncToPlayers, 100L, 100L);

		getLogger().info("Voice pipeline initialized (radius=" + config.proximityRadius()
				+ " blocks, udpFallback=" + udpServer.isRunning() + ")");
	}

	@Override
	public void onDisable() {
		if (udpServer != null) {
			udpServer.close();
		}
		if (groupManager != null) {
			groupManager.clear();
		}
		getServer().getMessenger().unregisterIncomingPluginChannel(this);
		getServer().getMessenger().unregisterOutgoingPluginChannel(this);
	}

	private void registerChannels() {
		VoiceMessageListener listener = new VoiceMessageListener(this);
		for (String channel : VoicyChannels.INCOMING) {
			getServer().getMessenger().registerIncomingPluginChannel(this, channel, listener);
		}
		for (String channel : VoicyChannels.OUTGOING) {
			getServer().getMessenger().registerOutgoingPluginChannel(this, channel);
		}
	}

	public void sendHandshake(Player player) {
		byte[] secret = securityService.issueSessionSecret(player.getUniqueId());
		HandshakePayload.TransportMode mode = config.preferUdpFallback() && udpServer.isRunning()
				? HandshakePayload.TransportMode.UDP_FALLBACK
				: HandshakePayload.TransportMode.FABRIC_PAYLOAD;
		getLogger().info("Negotiating " + mode + " voice transport with " + player.getName()
				+ " (udpPort=" + udpServer.getBoundPort() + ")");

		HandshakePayload payload = new HandshakePayload(
				mode,
				udpServer.getBoundPort(),
				config.sampleRate(),
				config.frameDurationMs(),
				secret,

				""
		);
		VoicyByteBuf buf = new VoicyByteBuf();
		payload.encode(buf);
		player.sendPluginMessage(this, VoicyChannels.HANDSHAKE, buf.toByteArray());
	}

	public PaperVoicyConfig config() {
		return config;
	}

	public VoiceSecurityService securityService() {
		return securityService;
	}

	public GroupManager groupManager() {
		return groupManager;
	}

	public GroupNetwork groupNetwork() {
		return groupNetwork;
	}

	public SpeakerManager speakerManager() {
		return speakerManager;
	}

	public AnimationStyleService animationStyles() {
		return animationStyles;
	}

	public VoiceRouter router() {
		return router;
	}
}
