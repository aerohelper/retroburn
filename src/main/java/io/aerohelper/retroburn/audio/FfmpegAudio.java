// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * FFmpeg and ffprobe command lines for audio CDs, and the progress they report. Options from
 * https://ffmpeg.org/ffmpeg.html and https://ffmpeg.org/ffprobe.html; the {@code loudnorm} filter
 * from https://ffmpeg.org/ffmpeg-filters.html#loudnorm. See {@code docs/design/LLD-audio-cd.md}
 * section 5.
 */
public final class FfmpegAudio {

    /** Integrated loudness, true peak and loudness range used when normalising. */
    static final String LOUDNORM = "loudnorm=I=-16:TP=-1.5:LRA=11";

    private static final String OUT_TIME = "out_time_us=";

    private FfmpegAudio() {}

    /** ffprobe arguments that print a file's format and streams as JSON. */
    public static List<String> probeArguments(Path source) {
        return List.of("-v", "error", "-print_format", "json", "-show_format", "-show_streams", source.toString());
    }

    /**
     * ffmpeg arguments that decode the first audio stream of {@code source} into raw CD audio PCM
     * (signed 16-bit little-endian, stereo, 44.1 kHz) at {@code pcm}, printing progress on stdout.
     */
    public static List<String> toCdPcmArguments(Path source, Path pcm, boolean normalizeLoudness) {
        List<String> arguments = new ArrayList<>(List.of(
                "-nostdin",
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-i",
                source.toString(),
                "-map",
                "0:a:0",
                "-vn",
                "-sn",
                "-dn",
                "-ac",
                Integer.toString(CdAudio.CHANNELS),
                "-ar",
                Integer.toString(CdAudio.SAMPLE_RATE)));
        if (normalizeLoudness) {
            arguments.addAll(List.of("-af", LOUDNORM));
        }
        arguments.addAll(
                List.of("-c:a", "pcm_s16le", "-f", "s16le", "-progress", "pipe:1", "-nostats", pcm.toString()));
        return List.copyOf(arguments);
    }

    /**
     * The fraction done from one line of {@code -progress} output ({@code key=value} lines; {@code
     * out_time_us} is the position written so far), or empty for other lines.
     */
    public static OptionalDouble progress(String line, Duration total) {
        if (line.equals("progress=end")) {
            return OptionalDouble.of(1.0);
        }
        if (!line.startsWith(OUT_TIME) || total.isZero()) {
            return OptionalDouble.empty();
        }
        try {
            long micros = Long.parseLong(line.substring(OUT_TIME.length()).strip());
            return OptionalDouble.of(Math.clamp(micros / (total.toNanos() / 1000.0), 0.0, 1.0));
        } catch (NumberFormatException e) {
            // "N/A" before the first packet is written.
            return OptionalDouble.empty();
        }
    }
}
