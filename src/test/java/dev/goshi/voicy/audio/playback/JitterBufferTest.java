package dev.goshi.voicy.audio.playback;

import dev.goshi.voicy.SpeakerManager;
import dev.goshi.voicy.audio.capture.AudioPreprocessor;
import dev.goshi.voicy.audio.capture.VoiceActivityGate;
import dev.goshi.voicy.audio.codec.OpusCodec;
import dev.goshi.voicy.config.VoicyConfig;
import dev.goshi.voicy.network.payload.VoiceFramePayload;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JitterBufferTest {

    @Test
    void packetLossShouldNotBlockPlaybackForever() throws Exception {
        JitterBuffer buffer = new JitterBuffer(120, 20);
        UUID speaker = UUID.randomUUID();
        OpusCodec codec = new OpusCodec(VoicyConfig.INSTANCE);

        buffer.push(new VoiceFramePayload(speaker, 10, 12.0f, new byte[] {1, 2, 3}));
        buffer.push(new VoiceFramePayload(speaker, 12, 12.0f, new byte[] {7, 8, 9}));

        assertNotNull(buffer.pollReady());
        assertNull(buffer.pollReady());
        assertNotNull(buffer.decodeNext(codec));
    }

    @Test
    void sequenceGapShouldAdvancePastMissingFrames() throws Exception {
        JitterBuffer buffer = new JitterBuffer(120, 20);
        UUID speaker = UUID.randomUUID();
        OpusCodec codec = new OpusCodec(VoicyConfig.INSTANCE);

        buffer.push(new VoiceFramePayload(speaker, 5, 12.0f, new byte[] {1}));
        buffer.push(new VoiceFramePayload(speaker, 7, 12.0f, new byte[] {2}));

        assertNotNull(buffer.pollReady());
        Thread.sleep(3L);
        assertNotNull(buffer.decodeNext(codec));
    }

    @Test
    void speakerUuidShouldRoundTripToSpeakerId() {
    }

    @Test
    void burstReplayShouldAdvancePastDroppedFrames() {
        JitterBuffer buffer = new JitterBuffer(120, 20);
        UUID speaker = SpeakerManager.speakerUuid(1);

        for (long sequence = 100; sequence < 120; sequence++) {
            buffer.push(new VoiceFramePayload(speaker, sequence, 48.0f, new byte[] {(byte) sequence}));
        }

        assertNotNull(buffer.pollReady());
    }

    @Test
    void speechPcmShouldNotBeErasedByActivationThreshold() {
        AudioPreprocessor preprocessor = new AudioPreprocessor(0.12f);
        short[] samples = new short[960];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) (Math.sin(i * 0.15d) * 700);
        }

        preprocessor.process(samples, samples.length);

        boolean hasSignal = false;
        for (short sample : samples) {
            if (sample != 0) {
                hasSignal = true;
                break;
            }
        }
        assertTrue(hasSignal);
    }

    @Test
    void loudSpeechShouldBeSoftLimitedInsteadOfHardClipped() {
        AudioPreprocessor preprocessor = new AudioPreprocessor(0.12f);
        short[] samples = new short[960];
        java.util.Arrays.fill(samples, (short) 30000);

        preprocessor.process(samples, samples.length);

        for (short sample : samples) {
            assertTrue(Math.abs((int) sample) < Short.MAX_VALUE);
        }
    }

    @Test
    void noiseGateShouldCloseSmoothlyAcrossFrames() {
        AudioPreprocessor preprocessor = new AudioPreprocessor(0.12f);
        short[] speech = new short[960];
        java.util.Arrays.fill(speech, (short) 500);
        preprocessor.process(speech, speech.length);

        short[] noise = new short[960];
        java.util.Arrays.fill(noise, (short) 20);
        preprocessor.process(noise, noise.length);

        assertTrue(Math.abs((int) noise[0]) < 500);
        assertTrue(Math.abs((int) noise[noise.length - 1]) < Math.abs((int) noise[0]));
    }

    @Test
    void voiceGateKeepsQuietSyllablesAndWordEndings() {
        VoiceActivityGate gate = new VoiceActivityGate(0.12f, 0.55f, 400_000_000L);

        assertTrue(gate.update(0.14f, 0L));
        assertTrue(gate.update(0.08f, 250_000_000L));
        assertTrue(gate.update(0.0f, 500_000_000L));
        assertFalse(gate.update(0.0f, 901_000_000L));
    }

    @Test
    void missingPacketShouldNotStallTheStream() throws Exception {
        JitterBuffer buffer = new JitterBuffer(120, 1);
        UUID speaker = UUID.randomUUID();
        buffer.push(new VoiceFramePayload(speaker, 10, 48.0f, new byte[] {1}));
        buffer.push(new VoiceFramePayload(speaker, 12, 48.0f, new byte[] {2}));

        assertNotNull(buffer.pollReady());
        Thread.sleep(3L);
        VoiceFramePayload lossFrame = buffer.pollReady();
        assertNotNull(lossFrame);
        assertEquals(11L, lossFrame.sequence());
        assertEquals(0, lossFrame.opusData().length);
        assertEquals(12L, buffer.pollReady().sequence());
    }

    @Test
    void senderRestartShouldNotBeDroppedAsOldFrames() {
        JitterBuffer buffer = new JitterBuffer(120, 20);
        UUID speaker = UUID.randomUUID();

        for (long sequence = 5000; sequence < 5010; sequence++) {
            buffer.push(new VoiceFramePayload(speaker, sequence, 48.0f, new byte[] {1}));
            assertNotNull(buffer.pollReady());
        }
        // sender restarted its game: counter starts again from 1
        buffer.push(new VoiceFramePayload(speaker, 1, 48.0f, new byte[] {2}));
        assertNotNull(buffer.pollReady());
    }
}
