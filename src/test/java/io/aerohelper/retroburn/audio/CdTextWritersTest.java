// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.engine.AudioDisc;
import io.aerohelper.retroburn.engine.CdText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The v0.7T sheet, the cdrdao TOC and the CD-Text budget. */
class CdTextWritersTest {

    private static final CdText TEXT = new CdText(
            "Joyful Nights",
            "United Cat Orchestra",
            List.of(new CdText.Track("Song of Joy", "Felix"), new CdText.Track("Café \"Mee\" \\Owww", "Mia")));

    @TempDir
    Path folder;

    @Test
    void sheetFollowsTheV07tFormat() {
        assertEquals("""
                Input Sheet Version = 0.7T
                Text Code           = 8859
                Language Code       = English
                Album Title         = Joyful Nights
                Artist Name         = United Cat Orchestra
                First Track Number  = 1
                Last Track Number   = 2
                Track 01 Title      = Song of Joy
                Track 01 Artist     = Felix
                Track 02 Title      = Café "Mee" \\Owww
                Track 02 Artist     = Mia
                """, V07tSheetWriter.render(TEXT));
    }

    @Test
    void sheetIsWrittenInLatinOne() throws IOException {
        Path sheet = folder.resolve("cdtext.v07t");
        V07tSheetWriter.write(TEXT, sheet);
        byte[] bytes = Files.readAllBytes(sheet);
        assertEquals(V07tSheetWriter.render(TEXT), new String(bytes, StandardCharsets.ISO_8859_1));
        assertTrue(new String(bytes, StandardCharsets.ISO_8859_1).contains("Café"));
        assertEquals(V07tSheetWriter.render(TEXT).length(), bytes.length);
    }

    @Test
    void sheetRefusesTextOutsideLatinOne() {
        CdText cyrillic = new CdText("Кино", "", List.of(new CdText.Track("", "")));
        assertThrows(IllegalArgumentException.class, () -> V07tSheetWriter.write(cyrillic, folder.resolve("x")));
    }

    @Test
    void tocHasCdTextForTheDiscAndEveryTrack() {
        AudioDisc disc = new AudioDisc(
                List.of(new AudioDisc.Track(Path.of("01.wav"), 400), new AudioDisc.Track(Path.of("02.wav"), 500)),
                Optional.of(TEXT));
        assertEquals("""
                CD_DA
                CD_TEXT {
                  LANGUAGE_MAP {
                    0 : EN
                  }
                  LANGUAGE 0 {
                    TITLE "Joyful Nights"
                    PERFORMER "United Cat Orchestra"
                  }
                }

                TRACK AUDIO
                CD_TEXT {
                  LANGUAGE 0 {
                    TITLE "Song of Joy"
                    PERFORMER "Felix"
                  }
                }
                PREGAP 0:2:0
                FILE "01.wav" 0

                TRACK AUDIO
                CD_TEXT {
                  LANGUAGE 0 {
                    TITLE "Café \\"Mee\\" \\\\Owww"
                    PERFORMER "Mia"
                  }
                }
                PREGAP 0:2:0
                FILE "02.wav" 0
                """, CdrdaoTocWriter.render(disc));
    }

    @Test
    void tocWithoutCdTextHasOnlyTracks() {
        AudioDisc disc = new AudioDisc(List.of(new AudioDisc.Track(Path.of("01.wav"), 400)), Optional.empty());
        assertEquals("CD_DA\n\nTRACK AUDIO\nPREGAP 0:2:0\nFILE \"01.wav\" 0\n", CdrdaoTocWriter.render(disc));
    }

    @Test
    void budgetCountsTerminatorsAndSizeInfo() {
        // Titles: "A\0" + "B\0" = 4 bytes -> 1 pack; performers: 2 x "\0" = 2 bytes -> 1 pack; + 3.
        CdText small = new CdText("A", "", List.of(new CdText.Track("B", "")));
        assertEquals(5, CdTextBudget.packs(small));
    }

    @Test
    void budgetBlocksTextsThatDoNotFit() {
        // 99 tracks with 79-character titles and performers: 100 x 80 bytes = 667 packs each.
        String text = "x".repeat(79);
        CdText full = new CdText(text, text, Collections.nCopies(99, new CdText.Track(text, text)));
        assertFalse(CdTextBudget.fits(full));
        // 20 tracks with 30-character texts: 21 x 31 = 651 bytes -> 55 packs each, 113 in all.
        String shorter = "y".repeat(30);
        CdText fits = new CdText(shorter, shorter, Collections.nCopies(20, new CdText.Track(shorter, shorter)));
        assertEquals(113, CdTextBudget.packs(fits));
        assertTrue(CdTextBudget.fits(fits));
    }

    @Test
    void budgetEdgeIsInclusive() {
        // Album and one track; titles fill exactly 125 packs, performers 125, plus 3 = 253.
        String title = "t".repeat(749);
        String performer = "p".repeat(749);
        CdText edge = new CdText(title, performer, List.of(new CdText.Track(title, performer)));
        assertEquals(253, CdTextBudget.packs(edge));
        assertTrue(CdTextBudget.fits(edge));
        CdText over = new CdText(title + "t", performer, List.of(new CdText.Track(title, performer)));
        assertFalse(CdTextBudget.fits(over));
    }

    @Test
    void wavHeaderIsCanonical() {
        // 2352 bytes of data = 0x930; RIFF size = 36 + 2352 = 0x954; 44,100 Hz = 0xAC44;
        // 176,400 bytes/s = 0x2B110. All little-endian.
        byte[] expected = {
            'R',
            'I',
            'F',
            'F',
            0x54,
            0x09,
            0,
            0,
            'W',
            'A',
            'V',
            'E',
            'f',
            'm',
            't',
            ' ',
            16,
            0,
            0,
            0,
            1,
            0,
            2,
            0,
            0x44,
            (byte) 0xAC,
            0,
            0,
            0x10,
            (byte) 0xB1,
            0x02,
            0,
            4,
            0,
            16,
            0,
            'd',
            'a',
            't',
            'a',
            0x30,
            0x09,
            0,
            0
        };
        assertArrayEquals(expected, CdWavWriter.header(2352));
    }
}
