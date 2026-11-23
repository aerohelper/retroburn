// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.testsupport.ToolOutput;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExternalToolTest {

    @ParameterizedTest
    @CsvSource({
        "XORRISO, xorriso/version.txt, 1.5.6",
        "CDRSKIN, cdrskin/version.txt, 1.5.6",
        "GENISOIMAGE, genisoimage/version.txt, 1.1.11",
        "FFMPEG, ffmpeg/ffmpeg-version.txt, 6.1.1-3ubuntu5",
        "FFPROBE, ffmpeg/ffprobe-version.txt, 6.1.1-3ubuntu5",
        "DVDAUTHOR, dvdauthor/dvdauthor-help.txt, 0.7.2",
        "SPUMUX, dvdauthor/spumux-help.txt, 0.7.2"
    })
    void readsTheVersionFromRealOutput(ExternalTool tool, String fixture, String expectedVersion) {
        assertEquals(Optional.of(expectedVersion), tool.parseVersion(ToolOutput.lines(fixture)));
    }

    @Test
    void doesNotMistakeAnotherProgramForTheTool() {
        List<String> ffprobeOutput = ToolOutput.lines("ffmpeg/ffprobe-version.txt");

        assertTrue(ExternalTool.FFMPEG.parseVersion(ffprobeOutput).isEmpty());
        assertTrue(ExternalTool.SPUMUX
                .parseVersion(ToolOutput.lines("dvdauthor/dvdauthor-help.txt"))
                .isEmpty());
    }

    @Test
    void everyToolHasAVersionCommand() {
        for (ExternalTool tool : ExternalTool.values()) {
            assertTrue(!tool.versionArguments().isEmpty(), tool.name());
            assertTrue(!tool.displayName().isBlank(), tool.name());
        }
    }
}
