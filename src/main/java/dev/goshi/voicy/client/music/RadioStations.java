package dev.goshi.voicy.client.music;

import dev.goshi.voicy.config.CustomStation;
import dev.goshi.voicy.config.VoicyConfig;

import java.util.ArrayList;
import java.util.List;

public final class RadioStations {
	private static final String SOMA = "https://ice1.somafm.com/";

	private static final List<RadioStation> PRESETS = List.of(
			soma("Groove Salad", "groovesalad", "Ambient and downtempo beats"),
			soma("Drone Zone", "dronezone", "Atmospheric textures, no beats"),
			soma("Lush", "lush", "Sensuous vocals, mellow electronica"),
			soma("Space Station Soma", "spacestation", "Spaced-out ambient and mid-tempo"),
			soma("Secret Agent", "secretagent", "Lounge for spies and fugitives"),
			soma("Beat Blender", "beatblender", "Deep-house and downtempo chill"),
			soma("Fluid", "fluid", "Instrumental hip-hop and future soul"),
			soma("Indie Pop Rocks", "indiepop", "Indie pop and alt favorites"),
			soma("Illinois Street Lounge", "illstreet", "Classic bachelor pad exotica"),
			soma("Seven Inch Soul", "7soul", "Vintage soul on 45s"),
			soma("Folk Forward", "folkfwd", "Indie folk and alt-folk"),
			soma("Metal Detector", "metal", "Heavy metal, all of it"),
			new RadioStation("Radio Paradise", "https://stream.radioparadise.com/mp3-192",
					"Eclectic mix, picked by humans", false),
			new RadioStation("Radio Swiss Jazz", "http://stream.srg-ssr.ch/m/rsj/mp3_128",
					"Jazz around the clock", false),
			new RadioStation("Radio Swiss Classic", "http://stream.srg-ssr.ch/m/rsc_de/mp3_128",
					"Classical, lightly presented", false),
			new RadioStation("Radio Swiss Pop", "http://stream.srg-ssr.ch/m/rsp/mp3_128",
					"Pop and rock, 24 hours", false)
	);

	private RadioStations() {
	}

	private static RadioStation soma(String name, String id, String tagline) {
		return new RadioStation("SomaFM " + name, SOMA + id + "-128-mp3", tagline, false);
	}

	public static List<RadioStation> presets() {
		return PRESETS;
	}

	public static List<RadioStation> all() {
		List<RadioStation> all = new ArrayList<>();
		for (CustomStation custom : VoicyConfig.INSTANCE.customStations()) {
			if (custom.url != null && !custom.url.isBlank()) {
				String name = custom.name == null || custom.name.isBlank() ? hostOf(custom.url) : custom.name;
				all.add(new RadioStation(name, custom.url, hostOf(custom.url), true));
			}
		}
		all.addAll(PRESETS);
		return all;
	}

	public static String hostOf(String url) {
		try {
			String host = java.net.URI.create(url).getHost();
			return host == null ? url : host;
		} catch (IllegalArgumentException e) {
			return url;
		}
	}

	public static boolean isValidUrl(String url) {
		if (url == null || url.length() > 512) {
			return false;
		}
		try {
			java.net.URI uri = java.net.URI.create(url.trim());
			String scheme = uri.getScheme();
			return uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
