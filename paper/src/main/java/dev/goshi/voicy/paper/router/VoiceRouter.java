package dev.goshi.voicy.paper.router;

import dev.goshi.voicy.paper.config.PaperVoicyConfig;
import dev.goshi.voicy.paper.group.GroupManager;
import dev.goshi.voicy.paper.net.VoicyByteBuf;
import dev.goshi.voicy.paper.net.VoicyChannels;
import dev.goshi.voicy.paper.net.payload.VoiceFramePayload;
import dev.goshi.voicy.paper.security.PlayerVoiceState;
import dev.goshi.voicy.paper.security.VoiceSecurityService;
import dev.goshi.voicy.paper.speaker.SpeakerManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class VoiceRouter {

	public static final float GROUP_FRAME_RADIUS = -1.0f;

	private final Plugin plugin;
	private final Logger logger;
	private final VoiceSecurityService securityService;
	private final GroupManager groupManager;
	private final SpeakerManager speakerManager;
	private final PaperVoicyConfig config;

	private final Map<UUID, Deque<TimestampedFrame>> recentFrames = new ConcurrentHashMap<>();
	private final Map<UUID, Deque<ReplayFrame>> replayQueues = new ConcurrentHashMap<>();
	private final Set<String> syncedRecipients = ConcurrentHashMap.newKeySet();
	private long replaySequence = 1_000_000_000L;
	private final Map<Integer, java.util.concurrent.atomic.AtomicLong> speakerSequences = new ConcurrentHashMap<>();

	private long lastTickNanos = -1L;
	private double replayAccumulatorMs = 0.0d;

	public VoiceRouter(Plugin plugin, VoiceSecurityService securityService, GroupManager groupManager,
			SpeakerManager speakerManager, PaperVoicyConfig config) {
		this.plugin = plugin;
		this.logger = plugin.getLogger();
		this.securityService = securityService;
		this.groupManager = groupManager;
		this.speakerManager = speakerManager;
		this.config = config;
	}

	public void route(Player sender, VoiceFramePayload frame) {
		UUID senderId = sender.getUniqueId();

		if (!securityService.canTransmit(senderId)) {
			return;
		}

		if (!frame.speakerId().equals(senderId)) {
			logger.warning("Dropped spoofed voice frame from " + senderId + " claiming to be " + frame.speakerId());
			return;
		}

		PlayerVoiceState senderState = securityService.getState(senderId);
		if (senderState.isMuted() || !sender.isOnline() || sender.isDead()) {
			return;
		}

		World world = sender.getWorld();
		Location senderPos = sender.getLocation();
		double maxRadius = Math.min(Math.max(0.0d, frame.distanceRadius()), config.proximityRadius());
		GroupManager.Group talkGroup = groupManager.talkGroup(senderId);
		if (frame.opusData().length == 0 || (talkGroup == null && maxRadius <= 0.0d)) {
			return;
		}
		recordRecentFrame(senderId, frame);

		if (talkGroup != null) {
			routeToGroup(sender, frame, talkGroup);
			return;
		}

		byte[] encodedFrame = encode(frame);
		for (Player recipient : world.getPlayers()) {
			if (recipient.getUniqueId().equals(senderId)) {
				continue;
			}
			PlayerVoiceState recipientState = securityService.getState(recipient.getUniqueId());
			if (recipientState.isDeafened()) {
				continue;
			}
			double distance = recipient.getLocation().distance(senderPos);
			if (distance > maxRadius || !securityService.shareChannel(senderId, recipient.getUniqueId())) {
				continue;
			}
			try {
				recipient.sendPluginMessage(plugin, VoicyChannels.VOICE_FRAME, encodedFrame);
			} catch (RuntimeException e) {
				logger.fine(() -> "Voice send failed: " + e.getMessage());
			}
		}

		relayToSpeakers(sender, frame);
	}

	private void routeToGroup(Player sender, VoiceFramePayload frame, GroupManager.Group group) {
		VoiceFramePayload groupFrame = new VoiceFramePayload(
				frame.speakerId(), frame.sequence(), GROUP_FRAME_RADIUS, frame.opusData());
		byte[] encodedFrame = encode(groupFrame);
		for (UUID memberId : group.memberIds()) {
			if (memberId.equals(sender.getUniqueId())) {
				continue;
			}
			Player member = plugin.getServer().getPlayer(memberId);
			if (member == null || securityService.getState(memberId).isDeafened()) {
				continue;
			}
			member.sendPluginMessage(plugin, VoicyChannels.VOICE_FRAME, encodedFrame);
		}
		relayToSpeakers(sender, frame);
	}

	public void forgetSpeaker(int speakerId) {
		String prefix = speakerId + ":";
		syncedRecipients.removeIf(key -> key.startsWith(prefix));
	}

	public int replayRecentToSpeakers(Player sender) {
		Deque<TimestampedFrame> frames = recentFrames.get(sender.getUniqueId());
		if (frames == null || frames.isEmpty()) {
			return 0;
		}
		long cutoff = System.nanoTime() - 5_000_000_000L;
		int replayed = 0;
		Deque<ReplayFrame> replayQueue = replayQueues.computeIfAbsent(sender.getUniqueId(), ignored -> new ArrayDeque<>());
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

	public void tick() {
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
			Player sender = null;
			for (int i = 0; i < framesToRelease; i++) {
				ReplayFrame replay;
				synchronized (replayQueue) {
					replay = replayQueue.pollFirst();
				}
				if (replay == null) {
					break;
				}
				if (sender == null) {
					sender = plugin.getServer().getPlayer(entry.getKey());
					if (sender == null) {
						break;
					}
				}
				relayToSpeakers(sender, replay.frame(), replay.sequence());
			}
		}
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

	private void relayToSpeakers(Player sender, VoiceFramePayload frame) {
		relayToSpeakers(sender, frame, frame.sequence());
	}

	private void relayToSpeakers(Player sender, VoiceFramePayload frame, long sequence) {
		World world = sender.getWorld();
		Location senderPos = sender.getLocation();
		double maxRadius = Math.min(Math.max(0.0d, frame.distanceRadius()), config.proximityRadius());
		GroupManager.Group talkGroup = groupManager.talkGroup(sender.getUniqueId());
		for (var speakerEntry : speakerManager.speakers().entrySet()) {
			int speakerId = speakerEntry.getKey();
			SpeakerManager.SpeakerEntry speaker = speakerEntry.getValue();
			UUID speakerGroup = groupManager.speakerGroup(speakerId);
			if (talkGroup != null) {
				if (!talkGroup.id().equals(speakerGroup)) {
					continue;
				}
			} else if (speakerGroup != null) {
				continue;
			}
			if (!speaker.world().equals(world.getName())
					|| (talkGroup == null && speaker.distanceTo(senderPos.getX(), senderPos.getY(), senderPos.getZ()) > maxRadius)) {
				continue;
			}
			VoiceFramePayload relay = new VoiceFramePayload(
					SpeakerManager.speakerUuid(speakerId),
					speakerSequences.computeIfAbsent(speakerId, id -> new java.util.concurrent.atomic.AtomicLong(1_000_000_000L)).incrementAndGet(),
					frame.distanceRadius(), frame.opusData());
			byte[] encodedRelay = encode(relay);
			for (Player recipient : world.getPlayers()) {
				Location recipientPos = recipient.getLocation();
				double dx = recipientPos.getX() - speaker.x();
				double dy = recipientPos.getY() - speaker.y();
				double dz = recipientPos.getZ() - speaker.z();
				if (Math.sqrt(dx * dx + dy * dy + dz * dz) > maxRadius) {
					continue;
				}
				PlayerVoiceState recipientState = securityService.getState(recipient.getUniqueId());
				if (recipientState.isDeafened()) {
					continue;
				}
				if (talkGroup != null) {
					if (!talkGroup.isMember(recipient.getUniqueId())) {
						continue;
					}
				} else if (!securityService.shareChannel(sender.getUniqueId(), recipient.getUniqueId())) {
					continue;
				}
				String syncKey = speakerId + ":" + recipient.getUniqueId();
				if (syncedRecipients.add(syncKey)) {
					speakerManager.syncToPlayer(recipient);
				}
				recipient.sendPluginMessage(plugin, VoicyChannels.VOICE_FRAME, encodedRelay);
			}
		}
	}

	private static byte[] encode(VoiceFramePayload frame) {
		VoicyByteBuf buf = new VoicyByteBuf();
		frame.encode(buf);
		return buf.toByteArray();
	}

	private record TimestampedFrame(long timestampNanos, VoiceFramePayload frame) {
	}

	private record ReplayFrame(long sequence, VoiceFramePayload frame) {
	}
}
