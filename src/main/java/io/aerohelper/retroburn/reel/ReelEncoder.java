// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.reel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * FFmpeg arguments for the reel: raw RGB frames on standard input, the soundtrack from a file, H.264
 * (or the fallback) and AAC into an MP4. See {@code docs/design/LLD-burn-reel.md} section 5.
 */
public final class ReelEncoder {

    /**
     * A video encoder and its arguments.
     *
     * @param name the FFmpeg encoder name
     * @param noteKey message key telling the user about a fallback; empty for libx264
     */
    public record Codec(String name, List<String> arguments, String noteKey) {

        public Codec {
            arguments = List.copyOf(arguments);
        }

        public boolean h264() {
            return !name.equals("mpeg4");
        }
    }

    static final List<Codec> PREFERENCE = List.of(
            new Codec("libx264", List.of("-c:v", "libx264", "-preset", "medium", "-crf", "20"), ""),
            new Codec("libopenh264", List.of("-c:v", "libopenh264", "-b:v", "8M"), "reel.encoder.openh264"),
            new Codec("mpeg4", List.of("-c:v", "mpeg4", "-q:v", "3"), "reel.encoder.mpeg4"));

    private ReelEncoder() {}

    /** The first preferred encoder in FFmpeg's list ({@code ffmpeg -encoders}), if any. */
    public static Optional<Codec> choose(Set<String> encoders) {
        return PREFERENCE.stream()
                .filter(codec -> encoders.contains(codec.name()))
                .findFirst();
    }

    public static List<String> arguments(Codec codec, Path soundtrack, Path output) {
        List<String> arguments = new ArrayList<>(List.of(
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-f",
                "rawvideo",
                "-pixel_format",
                "rgb24",
                "-video_size",
                ReelStyle.WIDTH + "x" + ReelStyle.HEIGHT,
                "-framerate",
                Integer.toString(ReelTimeline.FPS),
                "-i",
                "pipe:0",
                "-i",
                soundtrack.toString(),
                "-map",
                "0:v:0",
                "-map",
                "1:a:0"));
        arguments.addAll(codec.arguments());
        arguments.addAll(List.of(
                "-pix_fmt",
                "yuv420p",
                "-c:a",
                "aac",
                "-b:a",
                "160k",
                "-shortest",
                "-movflags",
                "+faststart",
                output.toString()));
        return arguments;
    }
}
