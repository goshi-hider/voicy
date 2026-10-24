package dev.goshi.voicy.audio.spatial;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class OpenALAudioEngine implements AutoCloseable {
	private final int format;
	private final int sampleRate;

	public OpenALAudioEngine(int sampleRate) {
		this.sampleRate = sampleRate;
		this.format = AL10.AL_FORMAT_MONO16;
	}

	public int uploadPcm(short[] pcm) {
		if (pcm == null || pcm.length == 0) {
			throw new IllegalArgumentException("Cannot upload empty PCM audio");
		}
		AL10.alGetError();
		int buffer = AL10.alGenBuffers();
		checkError("create buffer");
		ByteBuffer data = MemoryUtil.memAlloc(pcm.length * 2).order(ByteOrder.nativeOrder());
		try {
			for (short s : pcm) {
				data.putShort(s);
			}
			data.flip();
			AL10.alBufferData(buffer, format, data, sampleRate);
			checkError("upload PCM");
			return buffer;
		} catch (RuntimeException error) {
			AL10.alDeleteBuffers(buffer);
			throw error;
		} finally {
			MemoryUtil.memFree(data);
		}
	}

	private static void checkError(String operation) {
		int error = AL10.alGetError();
		if (error != AL10.AL_NO_ERROR) {
			throw new IllegalStateException("OpenAL error " + error + " during " + operation);
		}
	}

	public void setOutputDevice(String deviceName) {
		long current = ALC10.alcGetContextsDevice(ALC10.alcGetCurrentContext());

		if (deviceName != null && !deviceName.isEmpty()) {
			ALC10.alcGetString(current, ALC11.ALC_ALL_DEVICES_SPECIFIER);
		}
	}

	@Override
	public void close() {
	}
}
