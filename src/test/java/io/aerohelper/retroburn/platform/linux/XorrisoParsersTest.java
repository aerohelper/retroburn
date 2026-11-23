// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.engine.MediaState;
import io.aerohelper.retroburn.platform.linux.XorrisoDeviceListParser.ListedDrive;
import io.aerohelper.retroburn.testsupport.ToolOutput;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class XorrisoParsersTest {

    @Test
    void noDrivesIsRecognisedInRealOutput() {
        List<String> output = ToolOutput.lines("xorriso/devices-none-found.txt");

        assertTrue(XorrisoDeviceListParser.reportsNoDrives(output));
        assertTrue(XorrisoDeviceListParser.parse(output).isEmpty());
    }

    /**
     * No capture with a drive exists yet, so these lines follow the format string in libisoburn's
     * {@code Xorriso_show_devices()}: {@code "%d  -dev '%s' " + padding + "%s :  '%-8.8s' '%s' \n"}.
     */
    @Test
    void readsDriveLinesInXorrisosDocumentedFormat() {
        List<String> output = List.of(
                "Beginning to scan for devices ...",
                "Full drive scan done",
                "-----------------------------------------------------------------------------",
                "0  -dev '/dev/sr0'  rwrw-- :  'HL-DT-ST' 'DVDRAM GP65NB60' ",
                "1  -dev '/dev/sr10' rw---- :  'ASUS    ' 'SDRW-08D2S-U' ",
                "-----------------------------------------------------------------------------");

        assertEquals(
                List.of(
                        new ListedDrive("/dev/sr0", "HL-DT-ST", "DVDRAM GP65NB60"),
                        new ListedDrive("/dev/sr10", "ASUS", "SDRW-08D2S-U")),
                XorrisoDeviceListParser.parse(output));
    }

    @Test
    void readsMediaFromARealTableOfContents() {
        MediaInfo media = XorrisoMediaInfoParser.parse(ToolOutput.lines("xorriso/toc-stdio-image.txt"));

        // An image file is not an optical medium, so its kind is unknown; the numbers are real.
        assertEquals(MediaKind.UNKNOWN, media.kind());
        assertEquals(MediaState.APPENDABLE, media.state());
        assertEquals(14720L * 2048, media.usedBytes());
        assertEquals(500024182L * 2048, media.freeBytes());
    }

    /** Profile names from libburn's table in {@code libburn/mmc.c}. */
    @ParameterizedTest
    @CsvSource({
        "'CD-R, sequential', CD_R",
        "'CD-RW, sequential', CD_RW",
        "'DVD-R sequential recording, sequential', DVD_R",
        "'DVD-RW restricted overwrite, sequential', DVD_RW",
        "'DVD+RW, sequential', DVD_PLUS_RW",
        "'DVD+R/DL, sequential', DVD_PLUS_R_DL",
        "'DVD-R/DL layer jump recording, sequential', DVD_R_DL",
        "'BD-R sequential recording, sequential', BD_R",
        "'BD-RE, sequential', BD_RE",
        "'DVD-ROM, sequential', DVD_ROM",
        "'is not recognizable', UNKNOWN",
        "'0042h, sequential', UNKNOWN"
    })
    void mapsLibburnProfileNamesToMediaKinds(String mediaCurrent, MediaKind expected) {
        List<String> output = List.of("Media current: " + mediaCurrent, "Media status : is blank");

        assertEquals(expected, XorrisoMediaInfoParser.parse(output).kind());
    }

    @ParameterizedTest
    @CsvSource({
        "'is blank', BLANK",
        "'is written , is appendable', APPENDABLE",
        "'is written , is closed', CLOSED",
        "'is blank , but next track is damaged', BLANK",
        "'is not recognizable', UNKNOWN"
    })
    void mapsMediaStatusLines(String status, MediaState expected) {
        List<String> output = List.of("Media current: DVD+RW, sequential", "Media status : " + status);

        assertEquals(expected, XorrisoMediaInfoParser.parse(output).state());
    }

    @Test
    void anEmptyDriveHasNoMedia() {
        List<String> output = List.of("Media current: is not recognizable", "Media status : is not present");

        assertEquals(MediaInfo.noMedia(), XorrisoMediaInfoParser.parse(output));
    }

    @Test
    void readsEveryProgressLineOfARealThreeGigabyteImage() {
        List<String> output = ToolOutput.lines("xorriso/create-iso-3gb.txt");

        List<BurnEvent.Progress> progress = output.stream()
                .map(XorrisoProgressParser::parse)
                .flatMap(Optional::stream)
                .toList();

        assertEquals(output.stream().filter(line -> line.contains("Writing:")).count(), progress.size());
        for (int i = 1; i < progress.size(); i++) {
            assertTrue(progress.get(i).fraction() >= progress.get(i - 1).fraction());
        }
    }

    @Test
    void readsPercentBufferAndSpeed() {
        Optional<BurnEvent.Progress> withSpeed = XorrisoProgressParser.parse(
                "xorriso : UPDATE : Writing:      17888s    1.2%   fifo  93%  buf  50%   65.5xD ");
        Optional<BurnEvent.Progress> withoutSpeed =
                XorrisoProgressParser.parse("xorriso : UPDATE : Writing:       4560s    0.3%   fifo  23%  buf  50%");

        assertEquals(
                Optional.of(new BurnEvent.Progress(0.012, Optional.of("65.5x DVD"), OptionalInt.of(50))), withSpeed);
        assertEquals(Optional.of(new BurnEvent.Progress(0.003, Optional.empty(), OptionalInt.of(50))), withoutSpeed);
        assertTrue(XorrisoProgressParser.parse("xorriso : UPDATE :       6 files added in 1 seconds")
                .isEmpty());
    }

    @Test
    void readsTheCdrecordStyleProgressOfARealBurn() {
        List<BurnEvent.Progress> progress = ToolOutput.lines("xorriso/burn-image-stdio.txt").stream()
                .map(XorrisoProgressParser::parse)
                .flatMap(Optional::stream)
                .toList();

        assertEquals(List.of(new BurnEvent.Progress(0.0, Optional.empty(), OptionalInt.of(50))), progress);
        // The fifo part is documented in the manual's -pacifier entry but absent with a stdio: drive.
        assertEquals(
                Optional.of(new BurnEvent.Progress(0.5, Optional.empty(), OptionalInt.of(97))),
                XorrisoProgressParser.parse("xorriso : UPDATE :   10 of   20 MB written (fifo 100%) [buf  97%]"));
        assertTrue(XorrisoProgressParser.parse("Writing to 'stdio:/tmp/drive.bin' completed successfully.")
                .isEmpty());
    }

    @Test
    void readsTheMediaStateAfterBurningAndBlankingAPseudoDrive() {
        List<String> afterBurn = ToolOutput.lines("xorriso/burn-image-stdio.txt");
        List<String> afterBlank = ToolOutput.lines("xorriso/blank-as-needed-stdio.txt");

        // Each run prints the state before and, after re-assessing, after the operation.
        assertEquals(
                MediaState.APPENDABLE,
                XorrisoMediaInfoParser.parse(tailFrom(afterBurn, "Re-assessing"))
                        .state());
        assertEquals(
                MediaState.BLANK,
                XorrisoMediaInfoParser.parse(tailFrom(afterBlank, "Re-assessing"))
                        .state());
        assertTrue(XorrisoErrorParser.mainProblem(ToolOutput.lines("xorriso/check-media-read-back-stdio.txt"))
                .isEmpty());
    }

    private static List<String> tailFrom(List<String> lines, String marker) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(marker)) {
                return lines.subList(i, lines.size());
            }
        }
        throw new AssertionError("No '" + marker + "' in " + lines);
    }

    @Test
    void explainsFailuresWithTheMostSevereMessage() {
        assertEquals(
                Optional.of("Cannot determine attributes of source file '/does/not/exist' : No such file or directory"),
                XorrisoErrorParser.mainProblem(ToolOutput.lines("xorriso/create-iso-missing-source.txt")));
        // "libburn : SORRY" comes first, but the FAILURE line is the better explanation.
        assertEquals(
                Optional.of("Cannot acquire drive 'stdio:/nonexistent-dir/out.iso'"),
                XorrisoErrorParser.mainProblem(ToolOutput.lines("xorriso/create-iso-bad-target-folder.txt")));
        assertTrue(XorrisoErrorParser.mainProblem(ToolOutput.lines("xorriso/create-iso-small.txt"))
                .isEmpty());
    }
}
