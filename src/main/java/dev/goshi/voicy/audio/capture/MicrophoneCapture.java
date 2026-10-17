package dev.goshi.voicy.audio.capture;

import dev.goshi.voicy.config.VoicyConfig;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;

/**
 * Microphone capture via Java Sound API with optional device selection.
 */
public final class MicrophoneCapture implements AutoCloseable {

	private final VoicyConfig config;
	private TargetDataLine line;
	private String selectedDevice;

	public MicrophoneCapture(VoicyConfig config) {
		this.config = config;
	}

	public List<String> listInputDevices() {
		List<String> devices = new ArrayList<>();
		javax.sound.sampled.Mixer.Info[] mixers = AudioSystem.getMixerInfo();
		for (var info : mixers) {
			if (AudioSystem.getMixer(info).isLineSupported(new DataLine.Info(TargetDataLine.class, new AudioFormat(48000, 16, 1, true, false)))) {
				devices.add(info.getName());
			}
		}
		return devices;
	}

	public List<String> listOutputDevices() {
		List<String> devices = new ArrayList<>();
		devices.add("Default System Output");
		for (var info : AudioSystem.getMixerInfo()) {
			if (AudioSystem.getMixer(info).getSourceLineInfo().length > 0) {
				devices.add(info.getName());
			}
		}
		return devices;
	}

	public String getSelectedDevice() {
		return selectedDevice != null ? selectedDevice : config.selectedInputDevice();
	}

	public void selectDevice(String deviceName) {
		this.selectedDevice = deviceName;
		config.setSelectedInputDevice(deviceName);
		close();
		if (deviceName != null && !deviceName.isBlank()) {
			try {
				open();
			} catch (LineUnavailableException e) {
				throw new IllegalStateException("Unable to reopen microphone device: " + deviceName, e);
			}
		}
	}

	public void selectOutputDevice(String deviceName) {
		config.setSelectedOutputDevice(deviceName);
	}

	public void open() throws LineUnavailableException {
		AudioFormat format = new AudioFormat(
				config.sampleRate(),
				16,
				1,
				true,
				false
		);
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
		if (selectedDevice != null) {
			for (var mixerInfo : AudioSystem.getMixerInfo()) {
				if (mixerInfo.getName().equals(selectedDevice)) {
					var mixer = AudioSystem.getMixer(mixerInfo);
					line = (TargetDataLine) mixer.getLine(info);
					break;
				}
			}
		}
		if (line == null) {
			line = (TargetDataLine) AudioSystem.getLine(info);
		}
		// Buffer sized to exactly one Opus frame leaves zero headroom: any tiny scheduling
		// delay on the capture thread (GC pause, OS jitter, virtual thread scheduling) causes
		// the line to overflow and corrupt/drop samples, which is heard as static/crackling
		// once the noise gate opens for real speech. Give it several frames of slack instead.
		int lineBufferBytes = config.frameSizeSamples() * 2 * 8;
		line.open(format, lineBufferBytes);
		line.start();
	}

	public int read(short[] buffer) {
		if (line == null) {
			return 0;
		}
		byte[] raw = new byte[buffer.length * 2];
		int read = line.read(raw, 0, raw.length);
		for (int i = 0; i < read / 2; i++) {
			int lo = raw[i * 2] & 0xFF;
			int hi = raw[i * 2 + 1];
			buffer[i] = (short) ((hi << 8) | lo);
		}
		return read / 2;
	}

	public float peakLevel(short[] buffer, int samples) {
		if (samples <= 0) {
			return 0.0f;
		}
		double peak = 0.0d;
		double sumSquares = 0.0d;
		for (int i = 0; i < samples; i++) {
			double sample = buffer[i] / 32768.0d;
			double abs = Math.abs(sample);
			peak = Math.max(peak, abs);
			sumSquares += sample * sample;
		}
		double rms = Math.sqrt(sumSquares / samples);
		double signal = Math.max(rms, peak * 0.72d);
		double db = 20.0d * Math.log10(Math.max(signal, 1.0e-6d));
		double normalized = Math.max(0.0d, (db + 42.0d) / 42.0d);
		return (float) Math.min(1.0d, normalized);
	}

	@Override
	public void close() {
		if (line != null) {
			line.stop();
			line.close();
			line = null;
		}
	}
}