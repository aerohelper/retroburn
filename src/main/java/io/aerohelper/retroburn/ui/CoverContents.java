// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.design.CoverContent;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.project.AudioCompilation;
import io.aerohelper.retroburn.project.DvdProject;
import io.aerohelper.retroburn.project.Mp3DiscPlan;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** What each kind of project puts on its cover: the text the designer's auto-fill uses. */
final class CoverContents {

    private CoverContents() {}

    /** Makes a project's text the one auto-fill uses, unless the project is still empty. */
    static void publish(UiServices services, CoverContent content) {
        if (!content.lines().isEmpty()) {
            services.coverContent().set(content);
        }
    }

    /** "1. Title — Performer (3:12)" per track; the album and its performer as title and subtitle. */
    static CoverContent audio(String kind, AudioCompilation compilation) {
        List<String> lines = new ArrayList<>();
        int number = 1;
        for (AudioCompilation.Track track : compilation.tracks()) {
            String performer = track.performer().isBlank() || track.performer().equals(compilation.albumPerformer())
                    ? ""
                    : " — " + track.performer();
            lines.add(number++ + ". " + track.title() + performer + " ("
                    + minutes(track.audio().duration()) + ")");
        }
        return new CoverContent(kind, compilation.albumTitle(), compilation.albumPerformer(), lines);
    }

    /** One line per folder on the disc (the albums), in disc order. */
    static CoverContent mp3(String kind, String label, Mp3DiscPlan plan) {
        Set<String> folders = new LinkedHashSet<>();
        for (Mp3DiscPlan.Entry entry : plan.entries()) {
            int slash = entry.discPath().lastIndexOf('/');
            folders.add(slash > 0 ? entry.discPath().substring(0, slash).replace("/", " — ") : label);
        }
        return new CoverContent(kind, label, "", List.copyOf(folders));
    }

    /** "1. Name (0:42:10)" per title. */
    static CoverContent video(String kind, DvdProject project) {
        List<String> lines = new ArrayList<>();
        int number = 1;
        for (DvdProject.Title title : project.titles()) {
            lines.add(
                    number++ + ". " + title.name() + " (" + hours(title.video().duration()) + ")");
        }
        return new CoverContent(kind, project.discTitle(), "", lines);
    }

    /** The disc name, today's date, and the names at the disc's root. */
    static CoverContent data(String kind, String label, List<DiscEntry> entries, LocalDate today) {
        // Disc paths always start with a slash.
        List<String> lines =
                entries.stream().map(entry -> entry.discPath().substring(1)).toList();
        String date = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
        return new CoverContent(kind, label, date, lines);
    }

    static String minutes(Duration duration) {
        return "%d:%02d".formatted(duration.toMinutes(), duration.toSecondsPart());
    }

    static String hours(Duration duration) {
        return "%d:%02d:%02d".formatted(duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }
}
