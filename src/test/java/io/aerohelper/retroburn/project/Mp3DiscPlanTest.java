// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.audio.Mp3Encoding;
import io.aerohelper.retroburn.audio.ProbedAudio;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class Mp3DiscPlanTest {

    private static final Mp3DiscPlan.Options CAR =
            new Mp3DiscPlan.Options(Mp3Layout.KEEP_FOLDERS, true, true, Mp3Encoding.Bitrate.CBR_192, false);

    @Test
    void carModeNumbersTracksByTagAndCleansNames() {
        Mp3Compilation compilation = Mp3Compilation.empty()
                .add(List.of(
                        mp3("b.mp3", "Zweite: Straße", "Mia", "Live", "2/10", List.of("Mia — Live")),
                        mp3("a.mp3", "Intro", "Mia", "Live", "1/10", List.of("Mia — Live"))));
        assertEquals(
                List.of("/Mia Live/01 - Intro.mp3", "/Mia Live/02 - Zweite Strasse.mp3"),
                paths(Mp3DiscPlan.of(compilation, CAR)));
    }

    @Test
    void layoutsPlaceFilesByFoldersTagsOrFlat() {
        Mp3Compilation compilation = Mp3Compilation.empty()
                .add(List.of(
                        mp3("x.mp3", "One", "Felix", "Nights", "1", List.of("Music", "Felix")),
                        mp3("y.mp3", "Two", null, null, "", List.of())));
        assertEquals(List.of("/Music/Felix/01 - One.mp3", "/01 - Two.mp3"), paths(Mp3DiscPlan.of(compilation, CAR)));
        assertEquals(
                List.of("/Felix/Nights/01 - One.mp3", "/Unknown artist/Unknown album/01 - Two.mp3"),
                paths(Mp3DiscPlan.of(compilation, options(Mp3Layout.ARTIST_ALBUM, true))));
        assertEquals(
                List.of("/01 - Two.mp3", "/02 - One.mp3"),
                paths(Mp3DiscPlan.of(compilation, options(Mp3Layout.FLAT, true))));
    }

    @Test
    void carModeJoinsFoldersBelowTheSecondLevel() {
        Mp3Compilation compilation =
                Mp3Compilation.empty().add(List.of(mp3("x.mp3", "One", null, null, "", List.of("A", "B", "C"))));
        assertEquals(List.of("/A/B - C/01 - One.mp3"), paths(Mp3DiscPlan.of(compilation, CAR)));
        assertEquals(
                List.of("/A/B/C/x.mp3"), paths(Mp3DiscPlan.of(compilation, options(Mp3Layout.KEEP_FOLDERS, false))));
    }

    @Test
    void otherFormatsAreConvertedOrLeftOut() {
        Mp3Compilation compilation = Mp3Compilation.empty()
                .add(List.of(
                        mp3("a.mp3", "A", null, null, "1", List.of()), item("b.flac", "flac", "B", "2", List.of())));
        Mp3DiscPlan converted = Mp3DiscPlan.of(compilation, CAR);
        assertEquals(List.of("/01 - A.mp3", "/02 - B.mp3"), paths(converted));
        assertTrue(converted.needsConversion());
        Mp3DiscPlan copiedOnly = Mp3DiscPlan.of(
                compilation,
                new Mp3DiscPlan.Options(Mp3Layout.KEEP_FOLDERS, true, false, Mp3Encoding.Bitrate.CBR_192, false));
        assertEquals(List.of("/01 - A.mp3"), paths(copiedOnly));
        assertEquals(List.of(Path.of("b.flac")), copiedOnly.skipped());
    }

    @Test
    void clashingNamesAreNumbered() {
        Mp3Compilation compilation = Mp3Compilation.empty()
                .add(List.of(
                        mp3("1/a.mp3", "Song", null, null, "", List.of()),
                        mp3("2/a.mp3", "Song", null, null, "", List.of())));
        assertEquals(
                List.of("/a.mp3", "/a (2).mp3"),
                paths(Mp3DiscPlan.of(compilation, options(Mp3Layout.KEEP_FOLDERS, false))));
    }

    @Test
    void playlistsListEachFoldersTracks() {
        Mp3Compilation compilation = Mp3Compilation.empty()
                .add(List.of(
                        mp3("a.mp3", "Intro", "Mia", null, "1", List.of("Live")),
                        mp3("b.mp3", "Outro", "Mia", null, "2", List.of("Live"))));
        Mp3DiscPlan plan = Mp3DiscPlan.of(
                compilation,
                new Mp3DiscPlan.Options(Mp3Layout.KEEP_FOLDERS, true, true, Mp3Encoding.Bitrate.CBR_192, true));
        assertEquals(1, plan.playlists().size());
        Mp3DiscPlan.Playlist playlist = plan.playlists().getFirst();
        assertEquals("/Live/Live.m3u", playlist.discPath());
        assertEquals(StandardCharsets.US_ASCII, playlist.charset());
        assertEquals(
                "#EXTM3U\r\n#EXTINF:180,Mia - Intro\r\n01 - Intro.mp3\r\n#EXTINF:180,Mia - Outro\r\n02 - Outro.mp3\r\n",
                playlist.text());
    }

    @Test
    void crowdedFoldersAndTooManyFoldersAreWarnedAbout() {
        List<Mp3Compilation.Item> items = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            items.add(mp3("crowded/" + i + ".mp3", "T" + i, null, null, "", List.of("Crowded")));
        }
        for (int i = 0; i < 100; i++) {
            items.add(mp3("f" + i + "/a.mp3", "A", null, null, "", List.of("F" + i)));
        }
        Mp3DiscPlan plan = Mp3DiscPlan.of(Mp3Compilation.empty().add(items), CAR);
        assertEquals(
                List.of(
                        new Mp3DiscPlan.Warning(Mp3DiscPlan.Warning.Kind.CROWDED_FOLDER, "/Crowded/", 256),
                        new Mp3DiscPlan.Warning(Mp3DiscPlan.Warning.Kind.TOO_MANY_FOLDERS, "", 101)),
                plan.warnings());
    }

    @Test
    void estimateCountsConvertedFilesByBitrate() {
        Mp3Compilation flac = Mp3Compilation.empty().add(List.of(item("b.flac", "flac", "B", "", List.of())));
        long estimate = Mp3DiscPlan.of(flac, CAR).estimatedBytes();
        long converted = Mp3Encoding.estimatedBytes(Duration.ofMinutes(3), Mp3Encoding.Bitrate.CBR_192);
        assertTrue(estimate > converted, estimate + " <= " + converted);
        assertEquals(Duration.ofMinutes(3), Mp3DiscPlan.of(flac, CAR).totalDuration());
    }

    private static Mp3DiscPlan.Options options(Mp3Layout layout, boolean car) {
        return new Mp3DiscPlan.Options(layout, car, true, Mp3Encoding.Bitrate.CBR_192, false);
    }

    private static List<String> paths(Mp3DiscPlan plan) {
        return plan.entries().stream().map(Mp3DiscPlan.Entry::discPath).toList();
    }

    private static Mp3Compilation.Item mp3(
            String file, String title, String artist, String album, String track, List<String> folders) {
        return new Mp3Compilation.Item(
                Path.of(file),
                new ProbedAudio(
                        Duration.ofMinutes(3),
                        "mp3",
                        "[0][0][0][0]",
                        44_100,
                        2,
                        new ProbedAudio.AudioTags(
                                Optional.ofNullable(title),
                                Optional.ofNullable(artist),
                                Optional.ofNullable(album),
                                Optional.empty(),
                                Optional.of(track),
                                Optional.empty())),
                folders);
    }

    private static Mp3Compilation.Item item(
            String file, String codec, String title, String track, List<String> folders) {
        return new Mp3Compilation.Item(
                Path.of(file),
                new ProbedAudio(
                        Duration.ofMinutes(3),
                        codec,
                        "[0][0][0][0]",
                        44_100,
                        2,
                        new ProbedAudio.AudioTags(
                                Optional.of(title),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(track),
                                Optional.empty())),
                folders);
    }
}
