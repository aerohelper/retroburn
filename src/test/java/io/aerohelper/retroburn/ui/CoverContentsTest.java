// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.aerohelper.retroburn.audio.ProbedAudio;
import io.aerohelper.retroburn.design.CoverContent;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.project.AudioCompilation;
import io.aerohelper.retroburn.project.DvdProject;
import io.aerohelper.retroburn.video.MenuTemplate;
import io.aerohelper.retroburn.video.ProbedVideo;
import io.aerohelper.retroburn.video.TvStandard;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoverContentsTest {

    private static ProbedAudio audio(int seconds) {
        return new ProbedAudio(
                Duration.ofSeconds(seconds), "flac", "[0][0][0][0]", 44_100, 2, ProbedAudio.AudioTags.none());
    }

    @Test
    void audioTracksAreNumberedWithTheirLength() {
        AudioCompilation compilation = new AudioCompilation(
                "Road Trip",
                "Various",
                List.of(
                        new AudioCompilation.Track(Path.of("a.flac"), audio(192), "Opening", "Band"),
                        new AudioCompilation.Track(Path.of("b.flac"), audio(61), "Grüße", "Various")));
        CoverContent content = CoverContents.audio("Audio CD", compilation);
        assertEquals("Road Trip", content.title());
        assertEquals("Various", content.subtitle());
        assertEquals(List.of("1. Opening — Band (3:12)", "2. Grüße (1:01)"), content.lines());
    }

    @Test
    void videoTitlesShowHoursMinutesAndSeconds() {
        ProbedVideo video = new ProbedVideo(Duration.ofSeconds(3725), 720, 576, 16 / 9.0, true);
        DvdProject project = new DvdProject(
                        "Holiday", List.of(), TvStandard.PAL, "16:9", 5, MenuTemplate.CLASSIC, false)
                .add(List.of(new DvdProject.Title(Path.of("beach.mp4"), video, "Beach")));
        CoverContent content = CoverContents.video("Video DVD", project);
        assertEquals("Holiday", content.title());
        assertEquals(List.of("1. Beach (1:02:05)"), content.lines());
    }

    @Test
    void dataDiscsListTheRootNames() {
        CoverContent content = CoverContents.data(
                "Data disc",
                "BACKUP",
                List.of(new DiscEntry("/Photos", Path.of("p")), new DiscEntry("/notes.txt", Path.of("n"))),
                LocalDate.of(2026, 11, 7));
        assertEquals("BACKUP", content.title());
        assertEquals(List.of("Photos", "notes.txt"), content.lines());
    }
}
