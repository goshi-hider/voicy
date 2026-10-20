package dev.goshi.voicy.audio.playback;

import dev.goshi.voicy.audio.codec.OpusCodec;
import dev.goshi.voicy.network.payload.VoiceFramePayload;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public final class JitterBuffer {

	private final int maxLatencyMs;
	private final int frameDurationMs;
	private final ReentrantLock lock = new ReentrantLock();
	private final PriorityQueue<BufferedFrame> queue = new PriorityQueue<>(
			Comparator.comparingLong(BufferedFrame::sequence)
	);
	private long nextExpectedSequence = -1;

	private long lastAdvanceNanos = -1L;

	public JitterBuffer(int maxLatencyMs, int frameDurationMs) {
		this.maxLatencyMs = maxLatencyMs;
		this.frameDurationMs = frameDurationMs;
	}

	public void push(VoiceFramePayload frame) {
		lock.lock();
		try {
			if (nextExpectedSequence < 0) {
				nextExpectedSequence = frame.sequence();
				lastAdvanceNanos = System.nanoTime();
			}
			if (frame.sequence() < nextExpectedSequence) {
				return;
			}
			queue.offer(new BufferedFrame(frame.sequence(), frame));
			while (queue.size() > Math.max(1, maxLatencyMs / Math.max(1, frameDurationMs))) {
				BufferedFrame dropped = queue.poll();
				if (dropped != null && dropped.sequence() >= nextExpectedSequence) {
					nextExpectedSequence = dropped.sequence() + 1;
					lastAdvanceNanos = System.nanoTime();
				}
			}
		} finally {
			lock.unlock();
		}
	}

	public VoiceFramePayload pollReady() {
		lock.lock();
		try {
			while (!queue.isEmpty()) {
				BufferedFrame head = queue.peek();
				if (head == null) {
					return null;
				}
				if (head.sequence() < nextExpectedSequence) {
					queue.poll();
					continue;
				}
				if (head.sequence() == nextExpectedSequence) {
					queue.poll();
					nextExpectedSequence = head.sequence() + 1;
					lastAdvanceNanos = System.nanoTime();
					return head.frame();
				}
				long gapWaitNanos = Math.max(1L, frameDurationMs) * 1_000_000L;
				if (System.nanoTime() - lastAdvanceNanos >= gapWaitNanos) {

					long lostSequence = nextExpectedSequence++;
					lastAdvanceNanos = System.nanoTime();
					VoiceFramePayload nextFrame = head.frame();
					return new VoiceFramePayload(nextFrame.speakerId(), lostSequence,
							nextFrame.distanceRadius(), new byte[0]);
				}
				return null;
			}
			return null;
		} finally {
			lock.unlock();
		}
	}

	public short[] decodeNext(OpusCodec codec) throws Exception {
		VoiceFramePayload frame = pollReady();
		if (frame != null) {
			return codec.decode(frame.opusData(), false);
		}
		return codec.decode(null, true);
	}

	private record BufferedFrame(long sequence, VoiceFramePayload frame) {
	}
}
