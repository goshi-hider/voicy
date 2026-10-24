package dev.goshi.voicy.client.music;

import dev.goshi.voicy.Voicy;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class MusicLibrary {
	public static final String FOLDER_NAME = "voicy-music";

	private List<MusicTrack> tracks = List.of();

	public Path folder() {
		return FabricLoader.getInstance().getGameDir().resolve(FOLDER_NAME);
	}

	public List<MusicTrack> tracks() {
		return tracks;
	}

	public void scan() {
		Path folder = folder();
		List<MusicTrack> found = new ArrayList<>();
		try {
			Files.createDirectories(folder);
			try (Stream<Path> files = Files.list(folder)) {
				files.filter(Files::isRegularFile)
						.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
						.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
						.forEach(path -> found.add(MusicTrack.fromFile(path)));
			}
		} catch (IOException e) {
			Voicy.LOGGER.warn("[Voicy] Could not read the music folder: {}", e.toString());
		}
		tracks = List.copyOf(found);
	}
}
