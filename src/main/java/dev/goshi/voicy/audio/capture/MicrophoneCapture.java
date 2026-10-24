package dev.goshi.voicy.audio.capture;

import dev.goshi.voicy.Voicy;
import dev.goshi.voicy.config.VoicyConfig;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class MicrophoneCapture implements AutoCloseable {
	public static final String DEFAULT_DEVICE = "System Default";

	private final VoicyConfig config;
	private volatile TargetDataLine line;
	private volatile String selectedDevice;

	public MicrophoneCapture(VoicyConfig config) {
		this.config = config;
	}

	public List<String> listInputDevices() {
		LinkedHashSet<String> devices = new LinkedHashSet<>();
		devices.add(DEFAULT_DEVICE);
		DataLine.Info probe = new DataLine.Info(TargetDataLine.class, new AudioFormat(48000, 16, 1, true, false));
		for (var info : AudioSystem.getMixerInfo()) {
			try {
				if (AudioSystem.getMixer(info).isLineSupported(probe)) {
					devices.add(info.getName());
				}
			} catch (RuntimeException ignored) {
			}
		}
		return new ArrayList<>(devices);
	}

	public List<String> listOutputDevices() {
		List<String> devices = new ArrayList<>();
		devices.add("Default System Output");
		for (var info : AudioSystem.getMixerInfo()) {
			try {
				if (AudioSystem.getMixer(info).getSourceLineInfo().length > 0 && !devices.contains(info.getName())) {
					devices.add(info.getName());
				}
			} catch (RuntimeException ignored) {
			}
		}
		return devices;
	}

	public String getSelectedDevice() {
		String name = selectedDevice != null ? selectedDevice : config.micDevice();
		return name == null || name.isBlank() ? DEFAULT_DEVICE : name;
	}

	public void selectDevice(String deviceName) {
		String stored = deviceName == null || DEFAULT_DEVICE.equals(deviceName) ? "" : deviceName;
		this.selectedDevice = stored;
		config.setMicDevice(stored);
		reopen();
	}

	public void selectOutputDevice(String deviceName) {
		config.setSelectedOutputDevice(deviceName);
	}

	public boolean reopen() {
		try {
			open();
			return true;
		} catch (LineUnavailableException | RuntimeException e) {
			Voicy.LOGGER.warn("[Voicy] Could not (re)open microphone: {}", e.getMessage());
			return false;
		}
	}

	public synchronized void open() throws LineUnavailableException {
		closeLine();
		AudioFormat format = new AudioFormat(config.sampleRate(), 16, 1, true, false);
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
		int lineBufferBytes = config.frameSizeSamples() * 2 * 8;

		String wanted = selectedDevice != null ? selectedDevice : config.micDevice();
		TargetDataLine opened = null;
		if (wanted != null && !wanted.isBlank()) {
			for (var mixerInfo : AudioSystem.getMixerInfo()) {
				if (!mixerInfo.getName().equals(wanted)) {
					continue;
				}
				try {
					var mixer = AudioSystem.getMixer(mixerInfo);
					if (!mixer.isLineSupported(info)) {
						continue;
					}
					TargetDataLine candidate = (TargetDataLine) mixer.getLine(info);
					candidate.open(format, lineBufferBytes);
					opened = candidate;
					break;
				} catch (LineUnavailableException | RuntimeException e) {
					opened = null;
				}
			}
			if (opened == null) {
				Voicy.LOGGER.warn("[Voicy] Microphone '{}' unavailable, using the system default instead", wanted);
			}
		}
		if (opened == null) {
			opened = (TargetDataLine) AudioSystem.getLine(info);
			opened.open(format, lineBufferBytes);
		}
		opened.start();
		line = opened;
		Voicy.LOGGER.info("[Voicy] Microphone opened: {} (format={}, buffer={} bytes)", wanted == null || wanted.isBlank() ? "system default" : wanted, opened.getFormat(), opened.getBufferSize());
	}

	public int read(short[] buffer) {
		TargetDataLine current = line;
		if (current == null) {
			return 0;
		}
		int needed = buffer.length * 2;
		byte[] raw = new byte[needed];
		int read;
		try {
			long deadline = System.nanoTime() + 750_000_000L;
			while (current.isOpen() && current.available() < needed) {
				if (System.nanoTime() > deadline) {
					return 0;
				}
				Thread.sleep(2);
			}
			if (!current.isOpen()) {
				return 0;
			}
			read = current.read(raw, 0, needed);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return 0;
		} catch (RuntimeException e) {
			return 0;
		}
		if (read <= 0) {
			return 0;
		}
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

	public String deviceSignature() {
		try {
			StringBuilder builder = new StringBuilder();
			for (var info : AudioSystem.getMixerInfo()) {
				builder.append(info.getName()).append('|');
			}
			return builder.toString();
		} catch (RuntimeException e) {
			return "";
		}
	}

	private void closeLine() {
		TargetDataLine current = line;
		line = null;
		if (current != null) {
			try {
				current.stop();
				current.close();
			} catch (RuntimeException ignored) {
			}
		}
	}

	@Override
	public synchronized void close() {
		closeLine();
	}
}
