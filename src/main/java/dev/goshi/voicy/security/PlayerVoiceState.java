package dev.goshi.voicy.security;

import java.util.UUID;

public final class PlayerVoiceState {

	private final UUID playerId;
	private volatile boolean muted;
	private volatile boolean deafened;
	private volatile String activeChannel;

	public PlayerVoiceState(UUID playerId) {
		this.playerId = playerId;
	}

	public UUID playerId() {
		return playerId;
	}

	public boolean isMuted() {
		return muted;
	}

	public void setMuted(boolean muted) {
		this.muted = muted;
	}

	public boolean isDeafened() {
		return deafened;
	}

	public void setDeafened(boolean deafened) {
		this.deafened = deafened;
	}

	public String getActiveChannel() {
		return activeChannel;
	}

	public void setActiveChannel(String activeChannel) {
		this.activeChannel = activeChannel;
	}
}
