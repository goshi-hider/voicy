package dev.goshi.voicy;

import dev.goshi.voicy.config.VoicyConfig;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SpeakerManager {
	private static final long SPEAKER_MAGIC = 0x564F4953564F4E45L;

	private final Map<Integer, SpeakerEntry> speakers = new LinkedHashMap<>();
	private ServerWorld world;
	private SpeakerState persistentState;

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

	public void syncToPlayers(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (var player : PlayerLookup.all(server)) {
			syncToPlayer(player);
		}
	}

	public void syncToPlayer(ServerPlayerEntity player) {
		for (var entry : speakers.entrySet()) {
			SpeakerEntry speaker = entry.getValue();
			if (!speaker.dimension().equals(player.getWorld().getRegistryKey())) {
				continue;
			}
			ServerPlayNetworking.send(player, new dev.goshi.voicy.network.payload.SpeakerSyncPayload(
					entry.getKey(),
					speaker.position().x,
					speaker.position().y,
					speaker.position().z,
					48.0d,
					48.0d));
		}
	}

	public SpeakerEntry lookupSpeaker(int id) {
		return speakers.get(id);
	}

	public void bind(ServerWorld serverWorld) {
		this.world = serverWorld;
		this.persistentState = serverWorld.getPersistentStateManager().getOrCreate(SpeakerState.TYPE, "voicy_speakers");
		this.speakers.clear();
		this.speakers.putAll(this.persistentState.speakers());
	}

	private void saveState() {
		if (this.persistentState == null && this.world != null) {
			this.persistentState = this.world.getPersistentStateManager().getOrCreate(SpeakerState.TYPE, "voicy_speakers");
		}
		if (this.persistentState != null) {
			this.persistentState.setSpeakers(new LinkedHashMap<>(this.speakers));
			this.persistentState.markDirty();
		}
	}

	public int createSpeaker(ServerWorld world, Vec3d position) {
		int id = nextSpeakerId();
		SpeakerEntry speaker = new SpeakerEntry(id, world.getRegistryKey(), position);
		speakers.put(id, speaker);
		saveState();
		Voicy.LOGGER.info("[Voicy:{}] Created speaker {} at {} in {}", Voicy.MOD_ID, id, position, world.getRegistryKey().getValue());
		return id;
	}

	private int nextSpeakerId() {
		int nextId = 1;
		for (int existingId : speakers.keySet()) {
			if (existingId >= nextId) {
				nextId = existingId + 1;
			}
		}
		if (persistentState != null) {
			for (int existingId : persistentState.speakers().keySet()) {
				if (existingId >= nextId) {
					nextId = existingId + 1;
				}
			}
		}
		return nextId;
	}

	public boolean removeSpeaker(int id) {
		SpeakerEntry removed = speakers.remove(id);
		if (removed == null) {
			return false;
		}
		saveState();
		Voicy.LOGGER.info("[Voicy:{}] Removed speaker {} from {}", Voicy.MOD_ID, id, removed.position());
		return true;
	}

	public boolean showSpeaker(ServerWorld world, int id) {
		SpeakerEntry speaker = speakers.get(id);
		if (speaker == null) {
			return false;
		}
		spawnOutline(world, speaker.position(), 0.42d, 0.3d);
		Voicy.LOGGER.info("[Voicy:{}] Showing speaker {} at {}", Voicy.MOD_ID, id, speaker.position());
		return true;
	}

	public Map<Integer, SpeakerEntry> speakers() {
		return Collections.unmodifiableMap(speakers);
	}

	private static boolean isOccluded(ServerWorld world, Vec3d start, Vec3d end) {
		var hit = world.raycast(new RaycastContext(
				start,
				end,
				RaycastContext.ShapeType.VISUAL,
				RaycastContext.FluidHandling.NONE,
				net.minecraft.block.ShapeContext.absent()
		));
		return hit.getType() != HitResult.Type.MISS;
	}

	private static float computeVoiceEnergy(short[] samples) {
		if (samples == null || samples.length == 0) {
			return 0.0F;
		}
		double sumSquares = 0.0d;
		for (short sample : samples) {
			double normalized = sample / 32768.0d;
			sumSquares += normalized * normalized;
		}
		double rms = Math.sqrt(sumSquares / samples.length);
		return (float) Math.min(1.0d, rms * 3.2d);
	}

	private void spawnOutline(ServerWorld world, Vec3d center, double halfSize, double step) {
		Vec3d[] corners = {
				new Vec3d(center.x - halfSize, center.y - halfSize, center.z - halfSize),
				new Vec3d(center.x + halfSize, center.y - halfSize, center.z - halfSize),
				new Vec3d(center.x + halfSize, center.y + halfSize, center.z - halfSize),
				new Vec3d(center.x - halfSize, center.y + halfSize, center.z - halfSize),
				new Vec3d(center.x - halfSize, center.y - halfSize, center.z + halfSize),
				new Vec3d(center.x + halfSize, center.y - halfSize, center.z + halfSize),
				new Vec3d(center.x + halfSize, center.y + halfSize, center.z + halfSize),
				new Vec3d(center.x - halfSize, center.y + halfSize, center.z + halfSize)
		};
		int[][] edges = {
				{0, 1}, {1, 2}, {2, 3}, {3, 0},
				{4, 5}, {5, 6}, {6, 7}, {7, 4},
				{0, 4}, {1, 5}, {2, 6}, {3, 7}
		};
		DustParticleEffect effect = new DustParticleEffect(new Vector3f(1.0f, 0.0f, 0.0f), 1.2f);
		for (int[] edge : edges) {
			Vec3d start = corners[edge[0]];
			Vec3d end = corners[edge[1]];
			int samples = 12;
			for (int i = 0; i <= samples; i++) {
				double t = i / (double) samples;
				Vec3d p = start.multiply(1.0d - t).add(end.multiply(t));
				world.spawnParticles(effect, p.x, p.y, p.z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
			}
		}
	}

	public record SpeakerEntry(int id, RegistryKey<?> dimension, Vec3d position) {
		public String label() {
			return "Speaker @ " + position.x + ", " + position.y + ", " + position.z;
		}
	}

	public static final class SpeakerState extends PersistentState {
		public static final PersistentState.Type<SpeakerState> TYPE = new PersistentState.Type<>(
				SpeakerState::new,
				SpeakerState::fromNbt,
				DataFixTypes.LEVEL
		);
		private final Map<Integer, SpeakerEntry> speakers = new LinkedHashMap<>();

		public SpeakerState() {
			super();
		}

		public Map<Integer, SpeakerEntry> speakers() {
			return Collections.unmodifiableMap(speakers);
		}

		public void setSpeakers(Map<Integer, SpeakerEntry> newSpeakers) {
			speakers.clear();
			speakers.putAll(newSpeakers);
			markDirty();
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
			NbtList list = new NbtList();
			for (SpeakerEntry speaker : speakers.values()) {
				NbtCompound entry = new NbtCompound();
				entry.putInt("id", speaker.id());
				entry.putString("dimension", speaker.dimension().getValue().toString());
				entry.putDouble("x", speaker.position().x);
				entry.putDouble("y", speaker.position().y);
				entry.putDouble("z", speaker.position().z);
				list.add(entry);
			}
			nbt.put("speakers", list);
			return nbt;
		}

		public static SpeakerState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
			SpeakerState state = new SpeakerState();
			if (!nbt.contains("speakers")) {
				return state;
			}
			NbtList list = nbt.getList("speakers", 10);
			for (int i = 0; i < list.size(); i++) {
				NbtCompound entry = list.getCompound(i);
				int id = entry.getInt("id");
				String dimId = entry.getString("dimension");
				RegistryKey<?> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.tryParse(dimId));
				Vec3d pos = new Vec3d(entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"));
				state.speakers.put(id, new SpeakerEntry(id, dimension, pos));
			}
			return state;
		}
	}
}
