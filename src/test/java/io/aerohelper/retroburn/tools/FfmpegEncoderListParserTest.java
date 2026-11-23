// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.testsupport.ToolOutput;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FfmpegEncoderListParserTest {

    private final Set<String> encoders = FfmpegEncoderListParser.parse(ToolOutput.lines("ffmpeg/encoders.txt"));

    @Test
    void findsTheEncodersRetroBurnUses() {
        assertTrue(encoders.contains("libmp3lame"));
        assertTrue(encoders.contains("libx264"));
        assertTrue(encoders.contains("mpeg4"));
        assertTrue(encoders.contains("ac3"));
        assertTrue(encoders.contains("mpeg2video"));
    }

    @Test
    void ignoresTheLegendAboveTheSeparator() {
        // The legend has lines like " V..... = Video"; "=" must not become an encoder name.
        assertFalse(encoders.contains("="));
        assertFalse(encoders.contains("Encoders:"));
    }

    @Test
    void returnsNothingForUnrelatedOutput() {
        assertTrue(FfmpegEncoderListParser.parse(ToolOutput.lines("xorriso/version.txt"))
                .isEmpty());
    }
}
