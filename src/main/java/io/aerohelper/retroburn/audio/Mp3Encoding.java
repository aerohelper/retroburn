// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Converting music to MP3 with FFmpeg's {@code libmp3lame} encoder, with ID3v2.3 and ID3v1 tags for
 * car stereos. Options from https://ffmpeg.org/ffmpeg-codecs.html#libmp3lame-1 and
 * https://ffmpeg.org/ffmpeg-formats.html#mp3. See {@code docs/design/LLD-mp3-disc.md} section 4.
 */
public final class Mp3Encoding {

    private Mp3Encoding() {}

    /** The bitrate choices; VBR quality 2 is LAME's {@code -V 2}. */
    public enum Bitrate {
        CBR_128(128, false),
        CBR_192(192, false),
        CBR_256(256, false),
        CBR_320(320, false),
        VBR_2(256, true);

        private final int kbps;
        private final boolean variable;

        Bitrate(int kbps, boolean variable) {
            this.kbps = kbps;
            this.variable = variable;
        }

        /** Kilobits per second; for VBR the size estimate's assumption. */
        public int kbps() {
            return kbps;
        }

        public boolean variable() {
            return variable;
        }
    }

    /** ffmpeg arguments that convert the first audio stream of {@code source} into {@code mp3}. */
    public static List<String> arguments(Path source, Path mp3, Bitrate bitrate) {
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
                "-c:a",
                "libmp3lame"));
        arguments.addAll(bitrate.variable() ? List.of("-q:a", "2") : List.of("-b:a", bitrate.kbps() + "k"));
        arguments.addAll(List.of(
                "-map_metadata",
                "0",
                "-id3v2_version",
                "3",
                "-write_id3v1",
                "1",
                "-progress",
                "pipe:1",
                "-nostats",
                mp3.toString()));
        return List.copyOf(arguments);
    }

    /** Bytes a converted file will take: bitrate × duration plus 10 %. */
    public static long estimatedBytes(Duration duration, Bitrate bitrate) {
        // milliseconds × kbps / 8 = bytes; × 11 / 10 for the margin.
        return Math.ceilDiv(duration.toMillis() * bitrate.kbps() * 11, 80);
    }
}
