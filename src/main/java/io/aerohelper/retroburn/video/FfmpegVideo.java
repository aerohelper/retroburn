// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.video;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * FFmpeg command lines for video DVDs. {@code -target} is documented in
 * https://ffmpeg.org/ffmpeg.html ("vcd, svcd, dvd, dv, dv50 … prefixed with pal-, ntsc- or film-");
 * {@code anullsrc} in https://ffmpeg.org/ffmpeg-filters.html. See {@code
 * docs/design/LLD-video-dvd.md} section 5.
 */
public final class FfmpegVideo {

    static final List<String> QUIET = List.of("-nostdin", "-hide_banner", "-loglevel", "error", "-y");

    private FfmpegVideo() {}

    /** Converts {@code source} to a DVD MPEG-2 program stream with AC-3 stereo audio. */
    public static List<String> title(Path source, Path mpeg, TvStandard standard, String aspect, int videoKbps) {
        return concat(
                QUIET,
                List.of(
                        "-i",
                        source.toString(),
                        "-map",
                        "0:v:0",
                        "-map",
                        "0:a:0?",
                        "-target",
                        standard.ffmpegTarget(),
                        "-aspect",
                        aspect,
                        "-vf",
                        "scale=" + TvStandard.WIDTH + ":" + standard.height(),
                        "-b:v",
                        videoKbps + "k",
                        "-maxrate",
                        DvdBitrate.MAX_VIDEO_KBPS + "k",
                        "-c:a",
                        "ac3",
                        "-b:a",
                        DvdBitrate.AUDIO_KBPS + "k",
                        "-ar",
                        "48000",
                        "-ac",
                        "2",
                        "-progress",
                        "pipe:1",
                        "-nostats",
                        mpeg.toString()));
    }

    /** One frame at 10 % of the title, 320 pixels wide. */
    public static List<String> thumbnail(Path source, Duration length, Path png) {
        double at = length.toMillis() / 10000.0;
        return concat(
                QUIET,
                List.of(
                        "-ss",
                        "%.3f".formatted(at).replace(',', '.'),
                        "-i",
                        source.toString(),
                        "-frames:v",
                        "1",
                        "-vf",
                        "scale=320:-2",
                        png.toString()));
    }

    /** A one-second menu video of {@code background} with silent AC-3 audio. */
    public static List<String> menu(Path background, Path mpeg, TvStandard standard) {
        return concat(
                QUIET,
                List.of(
                        "-loop",
                        "1",
                        "-framerate",
                        standard.frameRate(),
                        "-i",
                        background.toString(),
                        "-f",
                        "lavfi",
                        "-i",
                        "anullsrc=r=48000:cl=stereo",
                        "-t",
                        "1",
                        "-target",
                        standard.ffmpegTarget(),
                        "-aspect",
                        "4:3",
                        "-c:a",
                        "ac3",
                        "-b:a",
                        DvdBitrate.AUDIO_KBPS + "k",
                        "-shortest",
                        mpeg.toString()));
    }

    private static List<String> concat(List<String> first, List<String> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }
}
