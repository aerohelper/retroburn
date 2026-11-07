// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

/**
 * Writes the theme sounds (ADR-0017): spin-up, burning loop, success and error for the classic and
 * y2k themes, as 22 050 Hz mono 16-bit PCM WAV files. Deterministic: running it again gives the
 * same bytes.
 *
 * <pre>java tools/sounds/GenerateSounds.java src/main/resources/themes</pre>
 */
public final class GenerateSounds {

    static final int RATE = 22_050;

    public static void main(String[] args) throws IOException {
        Path themes = Path.of(args.length > 0 ? args[0] : "src/main/resources/themes");

        Path classic = Files.createDirectories(themes.resolve("classic/sounds"));
        write(classic.resolve("spin-up.wav"), sweep(1.0, 200, 600, 0.30, GenerateSounds::sine, 0));
        write(classic.resolve("burning-loop.wav"), hum(2.0, 0.08, GenerateSounds::sine));
        write(classic.resolve("success.wav"), notes(0.20, 0.30, GenerateSounds::sine, 523.25, 659.25, 783.99));
        write(classic.resolve("error.wav"), notes(0.28, 0.30, GenerateSounds::sine, 329.63, 246.94));

        Path y2k = Files.createDirectories(themes.resolve("y2k/sounds"));
        write(y2k.resolve("spin-up.wav"), sweep(1.2, 120, 900, 0.22, GenerateSounds::square, 0.10));
        write(y2k.resolve("burning-loop.wav"), hum(2.0, 0.06, GenerateSounds::square));
        write(y2k.resolve("success.wav"),
                notes(0.12, 0.22, GenerateSounds::square, 523.25, 659.25, 783.99, 1046.50));
        write(y2k.resolve("error.wav"), notes(0.22, 0.22, GenerateSounds::square, 196.00, 146.83));
    }

    static double sine(double phase) {
        return Math.sin(phase);
    }

    /** A square wave softened by keeping only its first three odd harmonics. */
    static double square(double phase) {
        return (Math.sin(phase) + Math.sin(3 * phase) / 3 + Math.sin(5 * phase) / 5) * 0.9;
    }

    /** A rising tone, with optional noise, faded in and out. */
    static double[] sweep(double seconds, double from, double to, double level, DoubleUnaryOperator wave, double noise) {
        int count = (int) (seconds * RATE);
        double[] samples = new double[count];
        Random random = new Random(20261107L);
        double phase = 0;
        for (int index = 0; index < count; index++) {
            double t = index / (double) count;
            double frequency = from + (to - from) * t * t;
            phase += 2 * Math.PI * frequency / RATE;
            double hiss = noise * (random.nextDouble() * 2 - 1) * t;
            samples[index] = (wave.applyAsDouble(phase) * level + hiss) * envelope(index, count, 0.05, 0.25);
        }
        return samples;
    }

    /**
     * A quiet hum that loops without a click: every frequency completes a whole number of cycles in
     * the clip (110 Hz and 220 Hz tones, a 1 Hz swell, over 2 seconds).
     */
    static double[] hum(double seconds, double level, DoubleUnaryOperator wave) {
        int count = (int) (seconds * RATE);
        double[] samples = new double[count];
        for (int index = 0; index < count; index++) {
            double t = index / (double) RATE;
            double swell = 0.75 + 0.25 * Math.sin(2 * Math.PI * 1 * t);
            samples[index] = level * swell
                    * (wave.applyAsDouble(2 * Math.PI * 110 * t) * 0.6 + Math.sin(2 * Math.PI * 220 * t) * 0.4);
        }
        return samples;
    }

    /** Notes one after another, each with a quick attack and a decay. */
    static double[] notes(double noteSeconds, double level, DoubleUnaryOperator wave, double... frequencies) {
        int perNote = (int) (noteSeconds * RATE);
        int tail = (int) (0.15 * RATE);
        double[] samples = new double[perNote * frequencies.length + tail];
        for (int note = 0; note < frequencies.length; note++) {
            int length = note == frequencies.length - 1 ? perNote + tail : perNote;
            for (int index = 0; index < length; index++) {
                double t = index / (double) RATE;
                double decay = Math.exp(-3.5 * index / (double) length);
                samples[note * perNote + index] += level * decay
                        * wave.applyAsDouble(2 * Math.PI * frequencies[note] * t)
                        * envelope(index, length, 0.01, 0.10);
            }
        }
        return samples;
    }

    /** Linear fade in over the first {@code in} and out over the last {@code out} of a clip. */
    static double envelope(int index, int count, double in, double out) {
        double position = index / (double) count;
        if (position < in) {
            return position / in;
        }
        if (position > 1 - out) {
            return (1 - position) / out;
        }
        return 1;
    }

    static void write(Path file, double[] samples) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (double sample : samples) {
            data.putShort((short) Math.round(Math.clamp(sample, -1, 1) * 32_767));
        }
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes()).putInt(36 + data.capacity()).put("WAVE".getBytes());
        header.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1);
        header.putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
        header.put("data".getBytes()).putInt(data.capacity());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header.array());
        out.write(data.array());
        Files.write(file, out.toByteArray());
    }
}
