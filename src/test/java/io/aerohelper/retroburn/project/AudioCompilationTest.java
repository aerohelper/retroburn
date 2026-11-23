// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.audio.CdTextRules;
import io.aerohelper.retroburn.audio.ProbedAudio;
import io.aerohelper.retroburn.engine.CdText;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AudioCompilationTest {

    @Test
    void trackTextsComeFromTagsOrTheFileName() {
        AudioCompilation.Track tagged =
                AudioCompilation.Track.of(Path.of("a.flac"), audio("Intro", "Mia", "Live", null));
        AudioCompilation.Track untagged =
                AudioCompilation.Track.of(Path.of("02 Outro.v2.mp3"), audio(null, null, null, null));

        assertEquals("Intro", tagged.title());
        assertEquals("Mia", tagged.performer());
        assertEquals("02 Outro.v2", untagged.title());
        assertEquals("", untagged.performer());
    }

    @Test
    void albumTextsComeFromTagsAllTracksShare() {
        AudioCompilation compilation = AudioCompilation.empty()
                .add(List.of(
                        AudioCompilation.Track.of(Path.of("1.mp3"), audio("A", "Mia", "Live", null)),
                        AudioCompilation.Track.of(Path.of("2.mp3"), audio("B", "Mia", "Live", null))));
        assertEquals("Live", compilation.albumTitle());
        assertEquals("Mia", compilation.albumPerformer());

        AudioCompilation mixed = compilation.add(
                List.of(AudioCompilation.Track.of(Path.of("3.mp3"), audio("C", "Felix", "Other", null))));
        assertEquals("Live", mixed.albumTitle(), "texts already set are kept");

        AudioCompilation various = AudioCompilation.empty()
                .add(List.of(
                        AudioCompilation.Track.of(Path.of("1.mp3"), audio("A", "Mia", "Hits", "Various")),
                        AudioCompilation.Track.of(Path.of("2.mp3"), audio("B", "Felix", "Hits", "Various"))));
        assertEquals("Various", various.albumPerformer());

        AudioCompilation unshared = AudioCompilation.empty()
                .add(List.of(
                        AudioCompilation.Track.of(Path.of("1.mp3"), audio("A", "Mia", "One", null)),
                        AudioCompilation.Track.of(Path.of("2.mp3"), audio("B", "Felix", "Two", null))));
        assertEquals("", unshared.albumTitle());
        assertEquals("", unshared.albumPerformer());
    }

    @Test
    void tracksMoveAndAreRemoved() {
        AudioCompilation compilation = AudioCompilation.empty()
                .add(List.of(track("A"), track("B"), track("C")))
                .move(2, 0)
                .remove(1);
        assertEquals(
                List.of("C", "B"),
                compilation.tracks().stream().map(AudioCompilation.Track::title).toList());
    }

    @Test
    void lengthCountsGaps() {
        AudioCompilation compilation = AudioCompilation.empty().add(List.of(track("A"), track("B")));
        assertEquals(2 * 10 * 75, compilation.audioSectors());
        assertEquals(2 * 10 * 75 + 2 * 150, compilation.discSectors());
    }

    @Test
    void cdTextIsCleanedAndProblemsAreListed() {
        AudioCompilation compilation = AudioCompilation.empty()
                .add(List.of(track("Dvořák"), track("राग")))
                .withAlbum("Best of", "x".repeat(81));

        CdText text = compilation.cdText();
        assertEquals("Dvorák", text.tracks().getFirst().title());
        assertEquals(
                List.of(
                        new AudioCompilation.CdTextProblem(
                                0, AudioCompilation.CdTextProblem.Field.PERFORMER, CdTextRules.Status.TOO_LONG),
                        new AudioCompilation.CdTextProblem(
                                2, AudioCompilation.CdTextProblem.Field.TITLE, CdTextRules.Status.NEEDS_LATIN)),
                compilation.cdTextProblems());
        assertTrue(AudioCompilation.empty()
                .add(List.of(track("A")))
                .cdTextProblems()
                .isEmpty());
    }

    private static AudioCompilation.Track track(String title) {
        return new AudioCompilation.Track(Path.of(title + ".flac"), audio(title, "", "", null), title, "");
    }

    private static ProbedAudio audio(String title, String artist, String album, String albumArtist) {
        return new ProbedAudio(
                Duration.ofSeconds(10),
                "flac",
                "[0][0][0][0]",
                44_100,
                2,
                new ProbedAudio.AudioTags(
                        Optional.ofNullable(title),
                        Optional.ofNullable(artist),
                        Optional.ofNullable(album).filter(a -> !a.isEmpty()),
                        Optional.ofNullable(albumArtist)));
    }
}
