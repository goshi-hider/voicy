package net.voicechat.api;

import net.minecraft.util.Identifier;
import net.voicechat.api.audio.VolumeManager;
import net.voicechat.api.channel.ChannelManager;
import net.voicechat.api.deafen.DeafenManager;
import net.voicechat.api.events.VoiceChatEvents;
import net.voicechat.api.group.GroupManager;
import net.voicechat.api.mute.MuteManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface VoiceChatAPI {
	@Nullable
	static VoiceChatAPI getInstance() {
		return VoiceChatAPIHolder.get();
	}

	@NotNull
	MuteManager getMuteManager();

	@NotNull
	DeafenManager getDeafenManager();

	@NotNull
	VolumeManager getVolumeManager();

	@NotNull
	ChannelManager getChannelManager();

	@NotNull
	GroupManager getGroupManager();

	@NotNull
	VoiceChatEvents getEvents();

	@NotNull
	VoiceState getVoiceState(@NotNull java.util.UUID playerId);

	void registerModSecurityContext(@NotNull Identifier modId);

	final class VoiceState {
		private final boolean muted;
		private final boolean deafened;
		private final Identifier activeChannel;

		public VoiceState(boolean muted, boolean deafened, @Nullable Identifier activeChannel) {
			this.muted = muted;
			this.deafened = deafened;
			this.activeChannel = activeChannel;
		}

		public boolean muted() {
			return muted;
		}

		public boolean deafened() {
			return deafened;
		}

		@Nullable
		public Identifier activeChannel() {
			return activeChannel;
		}
	}
}
