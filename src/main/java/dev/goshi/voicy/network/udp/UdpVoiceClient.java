package dev.goshi.voicy.network.udp;

import dev.goshi.voicy.Voicy;
import dev.goshi.voicy.network.payload.VoiceFramePayload;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UdpVoiceClient implements AutoCloseable {

	private static final int MAX_PACKET = 2048;

	private final AtomicBoolean running = new AtomicBoolean(false);
	private DatagramSocket socket;
	private InetAddress serverAddress;
	private int serverPort = -1;
	private String username = "";
	private byte[] sessionSecret = new byte[0];
	private Thread readerThread;
	private VoiceFrameConsumer consumer = frame -> {};

	@FunctionalInterface
	public interface VoiceFrameConsumer {
		void accept(VoiceFramePayload frame);
	}

	public void setConsumer(VoiceFrameConsumer consumer) {
		this.consumer = consumer == null ? frame -> {} : consumer;
	}

	public void startSession(String username, String host, int port, byte[] secret) {
		close();
		try {
			this.username = username == null ? "" : username;
			this.serverAddress = InetAddress.getByName(host == null || host.isBlank() ? "127.0.0.1" : host);
			this.serverPort = port > 0 ? port : 24454;
			this.sessionSecret = secret == null ? new byte[0] : Arrays.copyOf(secret, secret.length);
			socket = new DatagramSocket();
			socket.setSoTimeout(1000);
			running.set(true);
			readerThread = Thread.ofVirtual().name("voicy-udp-client").start(this::readLoop);
			Voicy.LOGGER.info("[Voicy] UDP client connected to {}:{}", this.serverAddress.getHostAddress(), this.serverPort);
		} catch (Exception e) {
			Voicy.LOGGER.warn("[Voicy] UDP client session failed: {}", e.getMessage());
			running.set(false);
		}
	}

	public boolean isRunning() {
		return running.get() && socket != null && !socket.isClosed();
	}

	public void sendFrame(VoiceFramePayload frame) throws IOException {
		if (!isRunning()) {
			throw new IOException("UDP session is not active");
		}
		byte[] data = encode(frame, sessionSecret);
		socket.send(new DatagramPacket(data, data.length, serverAddress, serverPort));
	}

	private void readLoop() {
		byte[] buffer = new byte[MAX_PACKET];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		while (running.get()) {
			try {
				socket.receive(packet);
			} catch (IOException ignored) {
				continue;
			}
			VoiceFramePayload frame = decode(packet.getData(), packet.getLength());
			if (frame != null) {
				consumer.accept(frame);
			}
		}
	}

	public static byte[] encode(VoiceFramePayload frame, byte[] secret) {
		byte[] opus = frame.opusData();
		ByteBuffer buf = ByteBuffer.allocate(16 + 8 + 4 + 4 + Math.max(1, secret.length) + opus.length);
		buf.putLong(frame.speakerId().getMostSignificantBits());
		buf.putLong(frame.speakerId().getLeastSignificantBits());
		buf.putLong(frame.sequence());
		buf.putFloat(frame.distanceRadius());
		buf.putInt(secret.length);
		if (secret.length > 0) {
			buf.put(secret);
		}
		buf.put(opus);
		return buf.array();
	}

	/**
	 * Decodes an S2C packet as written by UdpVoiceServer.encode(): [uuid:16][seq:8][radius:4][opus].
	 * No secret field here - the client already trusts whatever its own socket is bound to the
	 * negotiated server address/port for. (This is a distinct wire format from the C2S packets this
	 * class sends via encode(frame, secret), which UdpVoiceServer.decodeAuthenticated() parses.)
	 */
	public static VoiceFramePayload decode(byte[] data, int length) {
		if (length < 16 + 8 + 4) {
			return null;
		}
		ByteBuffer buf = ByteBuffer.wrap(data, 0, length);
		long msb = buf.getLong();
		long lsb = buf.getLong();
		UUID speaker = new UUID(msb, lsb);
		long sequence = buf.getLong();
		float radius = buf.getFloat();
		byte[] opus = new byte[buf.remaining()];
		buf.get(opus);
		if (opus.length == 0) {
			return null;
		}
		return new VoiceFramePayload(speaker, sequence, radius, opus);
	}

	@Override
	public void close() {
		running.set(false);
		if (readerThread != null) {
			readerThread.interrupt();
		}
		if (socket != null && !socket.isClosed()) {
			socket.close();
		}
		serverAddress = null;
		serverPort = -1;
		sessionSecret = new byte[0];
	}
}