package dev.goshi.voicy.audio.spatial;

import dev.goshi.voicy.config.VoicyConfig;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * OpenAL spatial source with logarithmic attenuation, occlusion stub, and EFX reverb send.
 */
public final class SpatialAudioSource implements AutoCloseable {

	private final int source;
	private final UUID ownerId;
	private final VoicyConfig config;
	private final Deque<Integer> queuedBuffers = new ArrayDeque<>();

	public SpatialAudioSource(UUID ownerId, VoicyConfig config) {
		this.ownerId = ownerId;
		this.config = config;
		this.source = AL10.alGenSources();

		AL10.alSourcef(source, AL10.AL_PITCH, 1.0f);
		AL10.alSourcef(source, AL10.AL_GAIN, 1.0f);
		AL10.alSourcef(source, AL10.AL_MIN_GAIN, 0.0f);
		AL10.alSourcef(source, AL10.AL_MAX_GAIN, 4.0f);
		AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
		AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
		AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0f);
		AL10.alDistanceModel(AL10.AL_NONE);
		checkError("create source");
	}

	public UUID ownerId() {
		return ownerId;
	}

	public int sourceId() {
		return source;
	}

	public void updatePosition(ClientPlayerEntity listener, OtherClientPlayerEntity speaker, boolean occluded) {
		Vec3d listenerPos = listener.getEyePos();
		Vec3d speakerPos = speaker.getEyePos();
		updatePosition(listenerPos, speakerPos, occluded);
	}

	public void updatePosition(ClientPlayerEntity listener, Vec3d speakerPos, boolean occluded) {
		Vec3d listenerPos = listener.getEyePos();
		updatePosition(listenerPos, speakerPos, occluded);
	}

	private void updatePosition(Vec3d listenerPos, Vec3d speakerPos, boolean occluded) {
		Vec3d delta = speakerPos.subtract(listenerPos);

		AL10.alListenerf(AL10.AL_GAIN, 1.0f);
		AL10.alSource3f(source, AL10.AL_POSITION, (float) delta.x, (float) delta.y, (float) delta.z);

		// Occlusion effects are optional. Basic voice playback must not depend on EFX support.
		if (occluded) {
			setGain(0.35f);
		}
		checkError("update source position");
	}

	public void setGain(float gain) {
		AL10.alSourcef(source, AL10.AL_GAIN, gain);
		checkError("set source gain");
	}

	public void queuePcm(int bufferId) {
		AL10.alSourceQueueBuffers(source, bufferId);
		checkError("queue buffer");
		queuedBuffers.addLast(bufferId);
		int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
		checkError("read source state");
		if (state != AL10.AL_PLAYING) {
			AL10.alSourcePlay(source);
			checkError("start source");
		}
	}

	public void recycleProcessedBuffers() {
		int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
		checkError("read processed buffers");
		if (processed <= 0) {
			return;
		}
		IntBuffer bufferIds = BufferUtils.createIntBuffer(processed);
		AL10.alSourceUnqueueBuffers(source, bufferIds);
		checkError("unqueue processed buffers");
		for (int i = 0; i < processed; i++) {
			int bufferId = bufferIds.get(i);
			queuedBuffers.remove(bufferId);
			AL10.alDeleteBuffers(bufferId);
		}
	}

	@Override
	public void close() {
		AL10.alSourceStop(source);
		checkError("stop source");
		recycleProcessedBuffers();
		while (!queuedBuffers.isEmpty()) {
			int bufferId = queuedBuffers.removeFirst();
			AL10.alSourceUnqueueBuffers(source);
			checkError("unqueue remaining buffer");
			AL10.alDeleteBuffers(bufferId);
			checkError("delete remaining buffer");
		}
		AL10.alDeleteSources(source);
		checkError("delete source");
	}

	private static void checkError(String operation) {
		int error = AL10.alGetError();
		if (error != AL10.AL_NO_ERROR) {
			throw new IllegalStateException("OpenAL error " + error + " during " + operation);
		}
	}
}
