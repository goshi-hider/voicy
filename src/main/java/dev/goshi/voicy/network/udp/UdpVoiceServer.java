package dev.goshi.voicy.network.udp;

import dev.goshi.voicy.Voicy;
import dev.goshi.voicy.network.payload.VoiceFramePayload;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UdpVoiceServer implements AutoCloseable {

	private static final int MAX_PACKET = 2048;

	private final AtomicBoolean running = new AtomicBoolean(false);
	private DatagramSocket socket;
	private Thread readerThread;
	private int boundPort = -1;
	private VoiceFrameConsumer consumer = (sender, frame) -> {};
	private Function<UUID, byte[]> secretProvider = id -> null;

	@FunctionalInterface
	public interface VoiceFrameConsumer {
		void accept(UUID senderId, VoiceFramePayload frame);
	}

	public void start(int preferredPort) {
		if (running.get()) {
			return;
		}
		int bindPort = preferredPort;
		if (preferredPort <= 0 || preferredPort > 65535) {
			bindPort = 0;
			Voicy.LOGGER.debug("[Voicy] Invalid UDP fallback port {}, binding to ephemeral port instead", preferredPort);
		}
		try {
			socket = new DatagramSocket(bindPort);
			boundPort = socket.getLocalPort();
			running.set(true);
			readerThread = Thread.ofVirtual().name("voicy-udp-server").start(this::readLoop);
			Voicy.LOGGER.info("[Voicy] UDP fallback listening on port {}", boundPort);
		} catch (SocketException e) {
			Voicy.LOGGER.warn("[Voicy] UDP fallback unavailable on port {}: {}", bindPort, e.getMessage());
		}
	}

	public void setConsumer(VoiceFrameConsumer consumer) {
		this.consumer = consumer;
	}

	public void setSecretProvider(Function<UUID, byte[]> secretProvider) {
		this.secretProvider = secretProvider == null ? id -> null : secretProvider;
	}

	public boolean isRunning() {
		return running.get();
	}

	public int getBoundPort() {
		return boundPort;
	}

	private void readLoop() {
		byte[] buffer = new byte[MAX_PACKET];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		while (running.get()) {
			try {
				socket.receive(packet);
				VoiceFramePayload frame = decodeAuthenticated(buffer, packet.getLength(), secretProvider);
				if (frame != null) {
					consumer.accept(frame.speakerId(), frame);
				}
			} catch (IOException e) {
				if (running.get()) {
					Voicy.LOGGER.debug("[Voicy] UDP read error: {}", e.getMessage());
				}
			}
		}
	}

	public static VoiceFramePayload decodeAuthenticated(byte[] data, int length, Function<UUID, byte[]> secretProvider) {
		if (length < 32) {
			return null;
		}
		ByteBuffer buf = ByteBuffer.wrap(data, 0, length);
		long msb = buf.getLong();
		long lsb = buf.getLong();
		UUID speaker = new UUID(msb, lsb);
		long sequence = buf.getLong();
		float radius = buf.getFloat();
		int secretLength = buf.getInt();
		if (secretLength < 0 || secretLength > 4096 || buf.remaining() < secretLength) {
			return null;
		}
		byte[] suppliedSecret = new byte[secretLength];
		buf.get(suppliedSecret);
		byte[] expectedSecret = secretProvider == null ? null : secretProvider.apply(speaker);
		if (expectedSecret == null || !MessageDigest.isEqual(expectedSecret, suppliedSecret)) {
			return null;
		}
		byte[] opus = new byte[buf.remaining()];
		buf.get(opus);
		if (opus.length == 0) {
			return null;
		}
		return new VoiceFramePayload(speaker, sequence, radius, opus);
	}

	public static byte[] encode(VoiceFramePayload frame) {
		byte[] opus = frame.opusData();
		ByteBuffer buf = ByteBuffer.allocate(28 + opus.length);
		buf.putLong(frame.speakerId().getMostSignificantBits());
		buf.putLong(frame.speakerId().getLeastSignificantBits());
		buf.putLong(frame.sequence());
		buf.putFloat(frame.distanceRadius());
		buf.put(opus);
		return buf.array();
	}

	public void send(InetAddress address, int port, VoiceFramePayload frame) throws IOException {
		if (!running.get()) {
			return;
		}
		byte[] data = encode(frame);
		socket.send(new DatagramPacket(data, data.length, address, port));
	}

	@Override
	public void close() {
		running.set(false);
		if (socket != null && !socket.isClosed()) {
			socket.close();
		}
	}
}
