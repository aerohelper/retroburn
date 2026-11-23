// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.reel;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * The reel's sound, mixed from the session theme's own sounds (ADR-0017): spin-up at the spin-up
 * scene, the burning loop under the progress scene, the success sound at the complete scene, faded
 * out on the end card. 22 050 Hz mono 16-bit PCM.
 */
public final class ReelSoundtrack {

    public static final int RATE = 22_050;

    private static final Logger LOG = Logger.getLogger(ReelSoundtrack.class.getName());

    private ReelSoundtrack() {}

    /** The mixed samples, -1 to 1, as long as the timeline. */
    public static double[] mix(ReelTimeline timeline, String theme) {
        double[] out = new double[(int) Math.round(timeline.seconds() * RATE)];
        String folder = "/themes/" + theme + "/sounds/";
        ReelTimeline.Span spinUp = timeline.span("spin-up");
        ReelTimeline.Span progress = timeline.span("progress");
        ReelTimeline.Span complete = timeline.span("complete");
        ReelTimeline.Span end = timeline.span("end");

        add(out, read(folder + "spin-up.wav"), spinUp.start(), Double.MAX_VALUE, 1);
        double[] loop = read(folder + "burning-loop.wav");
        if (loop.length > 0) {
            for (double at = progress.start(); at < progress.end(); at += loop.length / (double) RATE) {
                add(out, loop, at, progress.end(), 1.6);
            }
        }
        add(out, read(folder + "success.wav"), complete.start(), Double.MAX_VALUE, 1);
        // Fade everything out over the end card.
        int from = (int) (end.start() * RATE);
        for (int index = from; index < out.length; index++) {
            out[index] *= 1 - (index - from) / (double) (out.length - from);
        }
        return out;
    }

    /** Writes the mix as a WAV file. */
    public static void write(double[] samples, Path file) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (double sample : samples) {
            data.putShort((short) Math.round(Math.clamp(sample, -1, 1) * 32_767));
        }
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put(ascii("RIFF")).putInt(36 + data.capacity()).put(ascii("WAVE"));
        header.put(ascii("fmt ")).putInt(16).putShort((short) 1).putShort((short) 1);
        header.putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
        header.put(ascii("data")).putInt(data.capacity());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(44 + data.capacity());
        bytes.write(header.array());
        bytes.write(data.array());
        Files.write(file, bytes.toByteArray());
    }

    /** Adds {@code sound} at {@code startSeconds}, cut at {@code endSeconds} with a short fade. */
    private static void add(double[] out, double[] sound, double startSeconds, double endSeconds, double gain) {
        int start = (int) Math.round(startSeconds * RATE);
        int stop = (int) Math.min(out.length, Math.min(start + (long) sound.length, Math.round(endSeconds * RATE)));
        int fade = RATE / 20;
        for (int index = start; index < stop; index++) {
            double edge = Math.min(1, (stop - index) / (double) fade);
            out[index] += sound[index - start] * gain * edge;
        }
    }

    /** A theme sound as samples, or none if it is missing or not 16-bit mono PCM at 22 050 Hz. */
    static double[] read(String resource) {
        try (InputStream raw = ReelSoundtrack.class.getResourceAsStream(resource)) {
            if (raw == null) {
                return new double[0];
            }
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                AudioFormat format = audio.getFormat();
                if (format.getSampleRate() != RATE
                        || format.getChannels() != 1
                        || format.getSampleSizeInBits() != 16
                        || format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
                    LOG.info(() -> "Leaving out " + resource + ": format " + format);
                    return new double[0];
                }
                ByteBuffer bytes = ByteBuffer.wrap(audio.readAllBytes())
                        .order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
                double[] samples = new double[bytes.remaining() / 2];
                for (int index = 0; index < samples.length; index++) {
                    samples[index] = bytes.getShort() / 32_768.0;
                }
                return samples;
            }
        } catch (IOException | UnsupportedAudioFileException e) {
            LOG.log(Level.INFO, "Leaving out " + resource, e);
            return new double[0];
        }
    }

    private static byte[] ascii(String text) {
        return text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
}
