// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.TrackLayout;
import io.aerohelper.retroburn.testsupport.ToolOutput;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class CdrskinAudioTest {

    private static final DriveId DRIVE = new DriveId("/dev/sr0");
    private static final long MIB = 1024 * 1024;

    @Test
    void burnWritesSaoWithTheSheetAndAGapBeforeEveryLaterTrack() {
        Path sheet = Path.of("/w/cdtext.v07t");
        Path first = Path.of("/w/01.wav");
        Path second = Path.of("/w/02.wav");
        Path third = Path.of("/w/03.wav");
        assertEquals(
                List.of(
                        "-v",
                        "dev=/dev/sr0",
                        "-sao",
                        "input_sheet_v07t=" + sheet,
                        "-audio",
                        first.toString(),
                        "sao_pregap=150",
                        second.toString(),
                        "sao_pregap=150",
                        third.toString()),
                CdrskinAudio.burn(DRIVE, List.of(first, second, third), Optional.of(sheet)));
        assertEquals(
                List.of("-v", "dev=/dev/sr0", "-sao", "-audio", first.toString()),
                CdrskinAudio.burn(DRIVE, List.of(first), Optional.empty()));
    }

    @Test
    void capturedPacifierLinesGiveBytesWritten() {
        List<Long> oneTrack = List.of(20 * MIB);
        List<Long> written = ToolOutput.lines("cdrskin/burn-data-track-stdio.txt").stream()
                .map(line -> CdrskinAudio.bytesWritten(line, oneTrack))
                .filter(OptionalLong::isPresent)
                .map(OptionalLong::getAsLong)
                .toList();
        assertEquals(List.of(10 * MIB, 16 * MIB, 20 * MIB), written);
    }

    @Test
    void laterTracksCountTheTracksBefore() {
        List<Long> tracks = List.of(30 * MIB, 40 * MIB);
        String line = "Track 02:   12 of   40 MB written (fifo 100%) [buf  97%]  16.0x.";
        assertEquals(OptionalLong.of(42 * MIB), CdrskinAudio.bytesWritten(line, tracks));
        assertEquals(OptionalLong.empty(), CdrskinAudio.bytesWritten("Track 03:  1 of 2 MB written", tracks));
        assertEquals(OptionalLong.empty(), CdrskinAudio.bytesWritten("Total size: 20 MB", tracks));
    }

    @Test
    void tableOfContentsFollowsCdrskinsPrintfFormat() {
        // Written to match cdrskin.c's printf("track:  %2d lba: %9d (%9d) %2.2d:%2.2d:%2.2d", ...)
        // plus " adr: %d control: %d mode: %d", and "track:lout lba: %9d ..." for the lead-out. No CD
        // was at hand to capture a real audio TOC.
        List<String> lines = List.of(
                "first: 1 last: 2",
                "track:   1 lba:         0 (        0) 00:02:00 adr: 1 control: 0 mode: 0",
                "track:   2 lba:       488 (     1952) 00:08:38 adr: 1 control: 0 mode: 0",
                "track:lout lba:       826 (     3304) 00:13:01 adr: 1 control: 0 mode: -1");
        assertEquals(Optional.of(new TrackLayout(List.of(0L, 488L), 826)), CdrskinAudio.layout(lines));
        assertEquals(Optional.empty(), CdrskinAudio.layout(List.of("first: 1 last: 1")));
    }
}
