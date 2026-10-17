package dev.goshi.voicy.channel;

import net.minecraft.util.Identifier;
import net.voicechat.api.channel.ChannelManager;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServerChannelManagerTest {

	@Test
	void joiningAnotherChannelRemovesOldMembership() {
		ServerChannelManager manager = new ServerChannelManager();
		Identifier firstId = Identifier.of("test", "first");
		Identifier secondId = Identifier.of("test", "second");
		ChannelManager.VoiceChannel first = manager.createChannel(firstId,
				ChannelManager.ChannelType.GROUP, ChannelManager.ChannelSettings.defaults());
		ChannelManager.VoiceChannel second = manager.createChannel(secondId,
				ChannelManager.ChannelType.GROUP, ChannelManager.ChannelSettings.defaults());
		UUID player = UUID.randomUUID();

		assertTrue(manager.joinChannel(player, firstId));
		assertTrue(manager.joinChannel(player, secondId));

		assertFalse(first.members().contains(player));
		assertTrue(second.members().contains(player));
		assertEquals(secondId, manager.getActiveChannel(player));
	}

	@Test
	void leavingNonActiveChannelPreservesCurrentMembership() {
		ServerChannelManager manager = new ServerChannelManager();
		Identifier firstId = Identifier.of("test", "first");
		Identifier secondId = Identifier.of("test", "second");
		manager.createChannel(firstId, ChannelManager.ChannelType.GROUP, ChannelManager.ChannelSettings.defaults());
		manager.createChannel(secondId, ChannelManager.ChannelType.GROUP, ChannelManager.ChannelSettings.defaults());
		UUID player = UUID.randomUUID();
		manager.joinChannel(player, secondId);

		assertTrue(manager.leaveChannel(player, firstId));

		assertEquals(secondId, manager.getActiveChannel(player));
		assertTrue(manager.getChannel(secondId).orElseThrow().members().contains(player));
	}

	@Test
	void deletingChannelClearsMemberActiveState() {
		ServerChannelManager manager = new ServerChannelManager();
		Identifier channelId = Identifier.of("test", "group");
		manager.createChannel(channelId, ChannelManager.ChannelType.GROUP, ChannelManager.ChannelSettings.defaults());
		UUID player = UUID.randomUUID();
		manager.joinChannel(player, channelId);

		manager.deleteChannel(channelId);

		assertNull(manager.getActiveChannel(player));
		assertTrue(manager.getChannel(channelId).isEmpty());
	}
}