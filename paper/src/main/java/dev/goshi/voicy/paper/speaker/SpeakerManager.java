package dev.goshi.voicy.paper.speaker;

import dev.goshi.voicy.paper.net.VoicyByteBuf;
import dev.goshi.voicy.paper.net.VoicyChannels;
import dev.goshi.voicy.paper.net.payload.SpeakerSyncPayload;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SpeakerManager {
	private static final long SPEAKER_MAGIC = 0x564F4953564F4E45L;

	private final Plugin plugin;
	private final File storageFile;
	private final Map<Integer, SpeakerEntry> speakers = new LinkedHashMap<>();

	public SpeakerManager(Plugin plugin) {
		this.plugin = plugin;
		this.storageFile = new File(plugin.getDataFolder(), "speakers.yml");
		load();
	}

	public static UUID speakerUuid(int speakerId) {
		return new UUID(SPEAKER_MAGIC, (long) speakerId & 0xffffffffL);
	}

	public static int decodeSpeakerId(UUID speakerId) {
		if (speakerId == null || speakerId.getMostSignificantBits() != SPEAKER_MAGIC) {
			return -1;
		}
		return (int) (speakerId.getLeastSignificantBits() & 0xffffffffL);
	}

	public static boolean isSpeakerUuid(UUID speakerId) {
		return decodeSpeakerId(speakerId) >= 0;
	}

	public void syncToPlayers() {
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			syncToPlayer(player);
		}
	}

	public void syncToPlayer(Player player) {
		String worldName = player.getWorld().getName();
		for (var entry : speakers.entrySet()) {
			SpeakerEntry speaker = entry.getValue();
			if (!speaker.world().equals(worldName)) {
				continue;
			}
			SpeakerSyncPayload payload = new SpeakerSyncPayload(
					entry.getKey(), speaker.x(), speaker.y(), speaker.z(), 48.0d, 48.0d);
			VoicyByteBuf buf = new VoicyByteBuf();
			payload.encode(buf);
			player.sendPluginMessage(plugin, VoicyChannels.SPEAKER_SYNC, buf.toByteArray());
		}
	}

	public SpeakerEntry lookupSpeaker(int id) {
		return speakers.get(id);
	}

	public int createSpeaker(World world, double x, double y, double z) {
		int id = nextSpeakerId();
		SpeakerEntry speaker = new SpeakerEntry(id, world.getName(), x, y, z);
		speakers.put(id, speaker);
		save();
		plugin.getLogger().info("Created speaker " + id + " at " + x + ", " + y + ", " + z + " in " + world.getName());
		return id;
	}

	private int nextSpeakerId() {
		int nextId = 1;
		for (int existingId : speakers.keySet()) {
			if (existingId >= nextId) {
				nextId = existingId + 1;
			}
		}
		return nextId;
	}

	public boolean removeSpeaker(int id) {
		SpeakerEntry removed = speakers.remove(id);
		if (removed == null) {
			return false;
		}
		save();
		plugin.getLogger().info("Removed speaker " + id);
		return true;
	}

	public boolean showSpeaker(int id) {
		SpeakerEntry speaker = speakers.get(id);
		if (speaker == null) {
			return false;
		}
		World world = plugin.getServer().getWorld(speaker.world());
		if (world == null) {
			return false;
		}
		spawnOutline(world, speaker.x(), speaker.y(), speaker.z(), 0.42d, 0.3d);
		return true;
	}

	public Map<Integer, SpeakerEntry> speakers() {
		return Collections.unmodifiableMap(speakers);
	}

	private void spawnOutline(World world, double cx, double cy, double cz, double halfSize, double step) {
		double[][] corners = {
				{cx - halfSize, cy - halfSize, cz - halfSize},
				{cx + halfSize, cy - halfSize, cz - halfSize},
				{cx + halfSize, cy + halfSize, cz - halfSize},
				{cx - halfSize, cy + halfSize, cz - halfSize},
				{cx - halfSize, cy - halfSize, cz + halfSize},
				{cx + halfSize, cy - halfSize, cz + halfSize},
				{cx + halfSize, cy + halfSize, cz + halfSize},
				{cx - halfSize, cy + halfSize, cz + halfSize}
		};
		int[][] edges = {
				{0, 1}, {1, 2}, {2, 3}, {3, 0},
				{4, 5}, {5, 6}, {6, 7}, {7, 4},
				{0, 4}, {1, 5}, {2, 6}, {3, 7}
		};
		Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1.2f);
		for (int[] edge : edges) {
			double[] start = corners[edge[0]];
			double[] end = corners[edge[1]];
			int samples = 12;
			for (int i = 0; i <= samples; i++) {
				double t = i / (double) samples;
				double px = start[0] * (1.0d - t) + end[0] * t;
				double py = start[1] * (1.0d - t) + end[1] * t;
				double pz = start[2] * (1.0d - t) + end[2] * t;
				world.spawnParticle(Particle.DUST, new Location(world, px, py, pz), 1, 0.0d, 0.0d, 0.0d, 0.0d, dust);
			}
		}
	}

	private void load() {
		if (!storageFile.exists()) {
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
		for (String key : yaml.getKeys(false)) {
			try {
				int id = Integer.parseInt(key);
				String path = key + ".";
				String world = yaml.getString(path + "world");
				double x = yaml.getDouble(path + "x");
				double y = yaml.getDouble(path + "y");
				double z = yaml.getDouble(path + "z");
				if (world != null) {
					speakers.put(id, new SpeakerEntry(id, world, x, y, z));
				}
			} catch (NumberFormatException ignored) {
			}
		}
	}

	private void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		for (SpeakerEntry speaker : speakers.values()) {
			String path = speaker.id() + ".";
			yaml.set(path + "world", speaker.world());
			yaml.set(path + "x", speaker.x());
			yaml.set(path + "y", speaker.y());
			yaml.set(path + "z", speaker.z());
		}
		try {
			File parent = storageFile.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			yaml.save(storageFile);
		} catch (IOException e) {
			plugin.getLogger().warning("Failed to save speakers.yml: " + e.getMessage());
		}
	}

	public record SpeakerEntry(int id, String world, double x, double y, double z) {
		public double distanceTo(double ox, double oy, double oz) {
			double dx = x - ox;
			double dy = y - oy;
			double dz = z - oz;
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}
	}
}
