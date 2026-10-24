package dev.goshi.voicy.config;

public enum DuckPriority {
	NONE,
	PLAYERS,
	GAME,
	BOTH;

	public boolean includesPlayers() {
		return this == PLAYERS || this == BOTH;
	}

	public boolean includesGame() {
		return this == GAME || this == BOTH;
	}

	public DuckPriority next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
