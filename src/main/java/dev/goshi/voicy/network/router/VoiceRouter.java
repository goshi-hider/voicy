package dev.goshi.voicy.network.router;

import dev.goshi.voicy.Voicy;
import dev.goshi.voicy.SpeakerManager;
import dev.goshi.voicy.config.VoicyConfig;
import dev.goshi.voicy.group.GroupManager;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VoiceRouter {

	public static final float GROUP_FRAME_RADIUS = -1.0f;

	private final VoiceSecurityService securityService;
	private final VoicyConfig config;
	private final Map<UUID, Deque<TimestampedFrame>> recentFrames = new ConcurrentHashMap<>();
	private final Map<UUID, Deque<ReplayFrame>> replayQueues = new ConcurrentHashMap<>();

	private final Set<String> syncedRecipients = ConcurrentHashMap.newKeySet();
	private long replaySequence = 1_000_000_000L;

	private long lastTickNanos = -1L;
	private double replayAccumulatorMs = 0.0d;

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
		String activeChannelStr = senderState.getActiveChannel();
		Identifier activeChannelId = activeChannelStr == null ? null : Identifier.tryParse(activeChannelStr);
		var activeChannel = activeChannelId == null ? null
				: Voicy.getInstance().channelManager().getChannel(activeChannelId).orElse(null);
		boolean groupRoute = activeChannel != null
				&& activeChannel.type() != ChannelManager.ChannelType.PROXIMITY;
		GroupManager.Group talkGroup = Voicy.getInstance().groupManager().talkGroup(senderId);
		if (frame.opusData().length == 0 || (talkGroup == null && !groupRoute && maxRadius <= 0.0d)) {
			return;
		}
		recordRecentFrame(senderId, frame);

		if (talkGroup != null) {
			routeToGroup(sender, frame, talkGroup);
			return;
		}

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

	private void routeToGroup(ServerPlayerEntity sender, VoiceFramePayload frame, GroupManager.Group group) {
		VoiceFramePayload groupFrame = new VoiceFramePayload(
				frame.speakerId(), frame.sequence(), GROUP_FRAME_RADIUS, frame.opusData());
		for (UUID memberId : group.memberIds()) {
			if (memberId.equals(sender.getUuid())) {
				continue;
			}
			ServerPlayerEntity member = sender.server.getPlayerManager().getPlayer(memberId);
			if (member == null || securityService.getState(memberId).isDeafened()) {
				continue;
			}
			ServerPlayNetworking.send(member, groupFrame);
		}
		relayToSpeakers(sender, frame);
	}

	public void forgetSpeaker(int speakerId) {
		String prefix = speakerId + ":";
		syncedRecipients.removeIf(key -> key.startsWith(prefix));
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
		long now = System.nanoTime();
		if (lastTickNanos < 0) {
			lastTickNanos = now;
			return;
		}
		double elapsedMs = (now - lastTickNanos) / 1_000_000.0d;
		lastTickNanos = now;
		replayAccumulatorMs += elapsedMs;
		int framesToRelease = (int) (replayAccumulatorMs / Math.max(1, config.frameDurationMs()));
		if (framesToRelease <= 0) {
			return;
		}
		replayAccumulatorMs -= framesToRelease * Math.max(1, config.frameDurationMs());

		for (var entry : replayQueues.entrySet()) {
			Deque<ReplayFrame> replayQueue = entry.getValue();
			ServerPlayerEntity sender = null;
			for (int i = 0; i < framesToRelease; i++) {
				ReplayFrame replay;
				synchronized (replayQueue) {
					replay = replayQueue.pollFirst();
				}
				if (replay == null) {
					break;
				}
				if (sender == null) {
					sender = findPlayer(server, entry.getKey());
					if (sender == null) {
						break;
					}
				}
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
		GroupManager groups = Voicy.getInstance().groupManager();
		GroupManager.Group talkGroup = groups.talkGroup(sender.getUuid());
		for (var speakerEntry : Voicy.getInstance().speakerManager().speakers().entrySet()) {
			int speakerId = speakerEntry.getKey();
			var speaker = speakerEntry.getValue();
			UUID speakerGroup = groups.speakerGroup(speakerId);
			if (talkGroup != null) {

				if (!talkGroup.id().equals(speakerGroup)) {
					continue;
				}
			} else if (speakerGroup != null) {

				continue;
			}
			if (!speaker.dimension().equals(world.getRegistryKey())
					|| (talkGroup == null && speaker.position().distanceTo(senderPos) > maxRadius)) {
				continue;
			}
			VoiceFramePayload relay = new VoiceFramePayload(
					SpeakerManager.speakerUuid(speakerId),
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
				if (talkGroup != null) {

					if (!talkGroup.isMember(recipient.getUuid())) {
						continue;
					}
				} else if (!securityService.shareChannel(sender.getUuid(), recipient.getUuid())) {
					continue;
				}

				String syncKey = speakerId + ":" + recipient.getUuid();
				if (syncedRecipients.add(syncKey)) {
					Voicy.getInstance().speakerManager().syncToPlayer(recipient);
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
