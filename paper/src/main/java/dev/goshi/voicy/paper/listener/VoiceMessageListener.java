package dev.goshi.voicy.paper.listener;

import dev.goshi.voicy.paper.VoicyPaperPlugin;
import dev.goshi.voicy.paper.net.VoicyByteBuf;
import dev.goshi.voicy.paper.net.VoicyChannels;
import dev.goshi.voicy.paper.net.payload.AnimationStylePayload;
import dev.goshi.voicy.paper.net.payload.ApiCommandPayload;
import dev.goshi.voicy.paper.net.payload.GroupActionPayload;
import dev.goshi.voicy.paper.net.payload.HandshakePayload;
import dev.goshi.voicy.paper.net.payload.VoiceFramePayload;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class VoiceMessageListener implements PluginMessageListener {
	private final VoicyPaperPlugin plugin;

	public VoiceMessageListener(VoicyPaperPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		VoicyByteBuf buf = VoicyByteBuf.wrap(message);
		switch (channel) {
			case VoicyChannels.VOICE_FRAME -> plugin.router().route(player, VoiceFramePayload.decode(buf));
			case VoicyChannels.API_COMMAND -> plugin.securityService().handleApiCommand(player, ApiCommandPayload.decode(buf));
			case VoicyChannels.HANDSHAKE -> {
				HandshakePayload.decode(buf);
				plugin.sendHandshake(player);
			}
			case VoicyChannels.GROUP_ACTION -> plugin.groupNetwork().handle(player, GroupActionPayload.decode(buf));
			case VoicyChannels.ANIMATION_STYLE -> plugin.animationStyles().handle(player, AnimationStylePayload.decode(buf));
			default -> plugin.getLogger().fine(() -> "Ignoring plugin message on unknown channel " + channel);
		}
	}
}
