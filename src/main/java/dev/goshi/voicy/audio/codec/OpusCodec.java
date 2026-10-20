package dev.goshi.voicy.audio.codec;

import dev.goshi.voicy.config.VoicyConfig;
import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusSignal;

public final class OpusCodec implements AutoCloseable {

	private final OpusEncoder encoder;
	private final OpusDecoder decoder;
	private final int frameSize;
	private final int sampleRate;

	public OpusCodec(VoicyConfig config) throws Exception {
		this.sampleRate = config.sampleRate();
		this.frameSize = config.frameSizeSamples();
		this.encoder = new OpusEncoder(sampleRate, 1, OpusApplication.OPUS_APPLICATION_VOIP);
		this.encoder.setBitrate(config.maxOpusBitrate());
		this.encoder.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
		this.encoder.setUseInbandFEC(true);
		this.encoder.setPacketLossPercent(5);
		this.decoder = new OpusDecoder(sampleRate, 1);
	}

	public byte[] encode(short[] pcm) throws Exception {
		byte[] out = new byte[1275];
		int len = encoder.encode(pcm, 0, frameSize, out, 0, out.length);
		byte[] frame = new byte[len];
		System.arraycopy(out, 0, frame, 0, len);
		return frame;
	}

	public short[] decode(byte[] opus, boolean decodeFec) throws Exception {
		return decode(opus, decodeFec, decoder);
	}

	public short[] decode(byte[] opus, boolean decodeFec, OpusDecoder decoder) throws Exception {
		short[] pcm = new short[frameSize];
		if (opus == null || opus.length == 0) {
			int samples = decoder.decode(null, 0, 0, pcm, 0, frameSize, true);
			if (samples < frameSize) {
				short[] trimmed = new short[samples];
				System.arraycopy(pcm, 0, trimmed, 0, samples);
				return trimmed;
			}
			return pcm;
		}
		int samples = decoder.decode(opus, 0, opus.length, pcm, 0, frameSize, decodeFec);
		if (samples < frameSize) {
			short[] trimmed = new short[samples];
			System.arraycopy(pcm, 0, trimmed, 0, samples);
			return trimmed;
		}
		return pcm;
	}

	public short[] decode(byte[] opus) throws Exception {
		return decode(opus, false);
	}

	public int frameSize() {
		return frameSize;
	}

	public int sampleRate() {
		return sampleRate;
	}

	public OpusDecoder createDecoder() throws Exception {
		return new OpusDecoder(sampleRate, 1);
	}

	@Override
	public void close() {

	}
}
