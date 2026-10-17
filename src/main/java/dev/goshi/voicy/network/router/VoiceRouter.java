package dev.goshi.voicy.network.router;

import dev.goshi.voicy.Voicy;
import dev.goshi.voicy.SpeakerManager;
import dev.goshi.voicy.config.VoicyConfig;
import dev.goshi.voicy.network.payload.VoiceFramePayload;
import dev.goshi.voicy.security.PlayerVoiceState;
import dev.goshi.voicy.security.VoiceSecurityService;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import net.voicechat.api.channel.ChannelManager;

import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zero-overhead server routing engine: validates sender state, computes proximity, fans out frames.
 */
public final class VoiceRouter {

	private final VoiceSecurityService securityService;
	private final VoicyConfig config;
	private final Map<UUID, Deque<TimestampedFrame>> recentFrames = new ConcurrentHashMap<>();
	private final Map<UUID, Deque<ReplayFrame>> replayQueues = new ConcurrentHashMap<>();
	private long replaySequence = 1_000_000_000L;

	public VoiceRouter(VoiceSecurityService securityService, VoicyConfig config) {
		this.securityService = securityService;
		this.config = config;
	}

	public void route(ServerPlayerEntity sender, VoiceFramePayload frame) {
		UUID senderId = sender.getUuid();

		if (!securityService.canTransmit(senderId)) {
			return;
		}

		if (!frame.speakerId().equals(senderId)) {
			Voicy.LOGGER.warn("[Voicy] Dropped spoofed voice frame from {} claiming to be {}",
					senderId, frame.speakerId());
			return;
		}

		PlayerVoiceState senderState = securityService.getState(senderId);
		if (senderState.isMuted() || !sender.isAlive()) {
			return;
		}

		ServerWorld world = sender.getServerWorld();
		Vec3d senderPos = sender.getPos();
		double maxRadius = Math.min(Math.max(0.0d, frame.distanceRadius()), config.proximityRadius());
		Identifier activeChannelId = Identifier.tryParse(senderState.getActiveChannel());
		var activeChannel = activeChannelId == null ? null
				: Voicy.getInstance().channelManager().getChannel(activeChannelId).orElse(null);
		boolean groupRoute = activeChannel != null
				&& activeChannel.type() != ChannelManager.ChannelType.PROXIMITY;
		if ((!groupRoute && maxRadius <= 0.0d) || frame.opusData().length == 0) {
			return;
		}
		recordRecentFrame(senderId, frame);

		for (ServerPlayerEntity recipient : PlayerLookup.world(world)) {
			if (recipient.getUuid().equals(senderId)) {
				continue;
			}

			PlayerVoiceState recipientState = securityService.getState(recipient.getUuid());
			if (recipientState.isDeafened()) {
				continue;
			}

			if (groupRoute) {
				if (!activeChannel.members().contains(recipient.getUuid())) {
					continue;
				}
			} else {
				double distance = recipient.getPos().distanceTo(senderPos);
				if (distance > maxRadius || !securityService.shareChannel(senderId, recipient.getUuid())) {
					continue;
				}
			}

			ServerPlayNetworking.send(recipient, frame);
		}

		relayToSpeakers(sender, frame);
	}

	public int replayRecentToSpeakers(ServerPlayerEntity sender) {
		Deque<TimestampedFrame> frames = recentFrames.get(sender.getUuid());
		if (frames == null || frames.isEmpty()) {
			return 0;
		}
		long cutoff = System.nanoTime() - 5_000_000_000L;
		int replayed = 0;
		Deque<ReplayFrame> replayQueue = replayQueues.computeIfAbsent(sender.getUuid(), ignored -> new ArrayDeque<>());
		synchronized (replayQueue) {
			replayQueue.clear();
			for (TimestampedFrame recent : frames) {
				if (recent.timestampNanos() >= cutoff) {
					replayQueue.addLast(new ReplayFrame(replaySequence++, recent.frame()));
					replayed++;
				}
			}
		}
		return replayed;
	}

	public void tick(MinecraftServer server) {
		for (var entry : replayQueues.entrySet()) {
			Deque<ReplayFrame> replayQueue = entry.getValue();
			ReplayFrame replay;
			synchronized (replayQueue) {
				replay = replayQueue.pollFirst();
			}
			if (replay == null) {
				continue;
			}
			ServerPlayerEntity sender = findPlayer(server, entry.getKey());
			if (sender != null) {
				relayToSpeakers(sender, replay.frame(), replay.sequence());
			}
		}
	}

	private ServerPlayerEntity findPlayer(MinecraftServer server, UUID playerId) {
		for (var player : PlayerLookup.all(server)) {
			if (player.getUuid().equals(playerId)) {
				return player;
			}
		}
		return null;
	}

	private void recordRecentFrame(UUID senderId, VoiceFramePayload frame) {
		Deque<TimestampedFrame> frames = recentFrames.computeIfAbsent(senderId, ignored -> new ArrayDeque<>());
		synchronized (frames) {
			frames.addLast(new TimestampedFrame(System.nanoTime(), frame));
			long cutoff = System.nanoTime() - 5_000_000_000L;
			while (!frames.isEmpty() && frames.peekFirst().timestampNanos() < cutoff) {
				frames.removeFirst();
			}
			while (frames.size() > 300) {
				frames.removeFirst();
			}
		}
	}

	private void relayToSpeakers(ServerPlayerEntity sender, VoiceFramePayload frame) {
		relayToSpeakers(sender, frame, frame.sequence());
	}

	private void relayToSpeakers(ServerPlayerEntity sender, VoiceFramePayload frame, long sequence) {
		ServerWorld world = sender.getServerWorld();
		Vec3d senderPos = sender.getPos();
		double maxRadius = Math.min(Math.max(0.0d, frame.distanceRadius()), config.proximityRadius());
		for (var speakerEntry : Voicy.getInstance().speakerManager().speakers().entrySet()) {
			var speaker = speakerEntry.getValue();
			if (!speaker.dimension().equals(world.getRegistryKey())
					|| speaker.position().distanceTo(senderPos) > maxRadius) {
				continue;
			}
			VoiceFramePayload relay = new VoiceFramePayload(
					SpeakerManager.speakerUuid(speakerEntry.getKey()),
					sequence,
					frame.distanceRadius(),
					frame.opusData()
			);
			for (ServerPlayerEntity recipient : PlayerLookup.world(world)) {
				if (recipient.getPos().distanceTo(speaker.position()) > maxRadius) {
					continue;
				}
				PlayerVoiceState recipientState = securityService.getState(recipient.getUuid());
				if (recipientState.isDeafened()) {
					continue;
				}
				if (!securityService.shareChannel(sender.getUuid(), recipient.getUuid())) {
					continue;
				}
				ServerPlayNetworking.send(recipient, relay);
			}
		}
	}

	private record TimestampedFrame(long timestampNanos, VoiceFramePayload frame) {
	}

	private record ReplayFrame(long sequence, VoiceFramePayload frame) {
	}

	public static double attenuationFactor(double distance, double maxRadius, double falloff) {
		if (distance >= maxRadius) {
			return 0.0;
		}
		double normalized = distance / maxRadius;
		return Math.pow(1.0 - normalized, falloff);
	}
}
