// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the encoder names from {@code ffmpeg -hide_banner -encoders}.
 *
 * <p>The output is a legend, a line of dashes, then one line per encoder such as {@code " A....D
 * libmp3lame  libmp3lame MP3 (MPEG audio layer 3) (codec mp3)"}: six capability flags, the encoder
 * name, a description. See https://ffmpeg.org/ffmpeg.html#Generic-options ({@code -encoders}).
 */
public final class FfmpegEncoderListParser {

    private static final Pattern ENCODER_LINE = Pattern.compile("^\\s*[VAS][.A-Z]{5}\\s+(\\S+)\\s");

    private FfmpegEncoderListParser() {}

    public static Set<String> parse(List<String> outputLines) {
        Set<String> encoders = new HashSet<>();
        boolean pastSeparator = false;
        for (String line : outputLines) {
            if (!pastSeparator) {
                pastSeparator = line.strip().startsWith("------");
                continue;
            }
            Matcher matcher = ENCODER_LINE.matcher(line);
            if (matcher.find()) {
                encoders.add(matcher.group(1));
            }
        }
        return Set.copyOf(encoders);
    }
}
