package dev.goshi.voicy.client.music;

import java.nio.file.Path;

public record MusicTrack(Path path, String title, String artist) {
	public static MusicTrack fromFile(Path path) {
		String name = path.getFileName().toString();
		int dot = name.lastIndexOf('.');
		if (dot > 0) {
			name = name.substring(0, dot);
		}
		name = name.replace('_', ' ').trim();
		int split = name.indexOf(" - ");
		if (split > 0 && split < name.length() - 3) {
			return new MusicTrack(path, name.substring(split + 3).trim(), name.substring(0, split).trim());
		}
		return new MusicTrack(path, name, "");
	}
}
