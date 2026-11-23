// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Car stereo names, MP3 encoding arguments and playlists. */
class CarNamesTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Café del Mar|Cafe del Mar",
                "AC/DC: Live?|AC_DC Live",
                "Straße Ærø|Strasse AEro",
                "  ..Hidden..  |Hidden",
                "Rock & Roll (Remix)|Rock & Roll (Remix)",
                "राग Yaman|Yaman",
                "राग|Track",
            })
    void carTextKeepsSafeCharacters(String input, String expected) {
        assertEquals(expected, CarNames.carText(input, "Track"));
    }

    @Test
    void carFileNamesKeepTheirExtensionWithinTheLimit() {
        String name = CarNames.carFile("x".repeat(100), "mp3");
        assertEquals(CarNames.FILE_LIMIT, name.length());
        assertTrue(name.endsWith("x.mp3"));
        assertEquals("Track.mp3", CarNames.carFile("???", "mp3"));
        assertEquals(CarNames.FOLDER_LIMIT, CarNames.carFolder("y".repeat(50)).length());
    }

    @Test
    void prefixesSortByPosition() {
        assertEquals("07 - Intro", CarNames.prefixed(7, "Intro"));
    }

    @Test
    void plainNamesOnlyLoseForbiddenCharacters() {
        assertEquals("Café_ «Live».mp3", CarNames.plain("Café: «Live»", "mp3", "Track"));
        assertEquals("Folder", CarNames.plain("..", "", "Folder"));
    }

    @Test
    void cbrAndVbrArgumentsWriteId3v23AndId3v1() {
        List<String> cbr = Mp3Encoding.arguments(Path.of("in.flac"), Path.of("out.mp3"), Mp3Encoding.Bitrate.CBR_192);
        assertEquals(
                List.of(
                        "-nostdin",
                        "-hide_banner",
                        "-loglevel",
                        "error",
                        "-y",
                        "-i",
                        "in.flac",
                        "-map",
                        "0:a:0",
                        "-vn",
                        "-c:a",
                        "libmp3lame",
                        "-b:a",
                        "192k",
                        "-map_metadata",
                        "0",
                        "-id3v2_version",
                        "3",
                        "-write_id3v1",
                        "1",
                        "-progress",
                        "pipe:1",
                        "-nostats",
                        "out.mp3"),
                cbr);
        List<String> vbr = Mp3Encoding.arguments(Path.of("in.flac"), Path.of("out.mp3"), Mp3Encoding.Bitrate.VBR_2);
        assertTrue(vbr.containsAll(List.of("-q:a", "2")));
        assertTrue(!vbr.contains("-b:a"));
    }

    @Test
    void sizeEstimateIsBitrateTimesDurationPlusTenPercent() {
        // 60 s at 192 kbps = 1,440,000 bytes; plus 10 %.
        assertEquals(1_584_000, Mp3Encoding.estimatedBytes(Duration.ofSeconds(60), Mp3Encoding.Bitrate.CBR_192));
    }

    @Test
    void playlistsAreExtendedM3uWithCrlf() {
        List<M3uWriter.Entry> entries = List.of(
                new M3uWriter.Entry("01 - Intro.mp3", Duration.ofMillis(61_400), "Mia - Intro"),
                new M3uWriter.Entry("02 - Café.mp3", Duration.ofSeconds(200), "Mia - Café"));
        assertEquals(
                "#EXTM3U\r\n#EXTINF:61,Mia - Intro\r\n01 - Intro.mp3\r\n#EXTINF:200,Mia - Cafe\r\n02 - Café.mp3\r\n",
                M3uWriter.render(entries, true));
        assertTrue(M3uWriter.render(entries, false).contains("#EXTINF:200,Mia - Café\r\n"));
        assertEquals("m3u", M3uWriter.extension(true));
        assertEquals("m3u8", M3uWriter.extension(false));
    }
}
