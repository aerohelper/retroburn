// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.video.DvdBitrate;
import io.aerohelper.retroburn.video.MenuLayout;
import io.aerohelper.retroburn.video.MenuTemplate;
import io.aerohelper.retroburn.video.ProbedVideo;
import io.aerohelper.retroburn.video.TvStandard;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A video DVD: its titles and how it is authored. Immutable. See {@code docs/design/LLD-video-dvd.md}.
 *
 * @param discTitle shown at the top of the menu and used for the volume label
 * @param titles the titles, in disc order
 * @param standard PAL or NTSC
 * @param aspect "16:9" or "4:3" for every title
 * @param chapterMinutes a chapter every so many minutes; 0 for none
 * @param template the menu's look
 * @param playNext after a title, play the next one instead of returning to the menu
 */
public record DvdProject(
        String discTitle,
        List<Title> titles,
        TvStandard standard,
        String aspect,
        int chapterMinutes,
        MenuTemplate template,
        boolean playNext) {

    public static final int MAX_TITLES = 99;

    public DvdProject {
        Objects.requireNonNull(discTitle, "discTitle");
        titles = List.copyOf(titles);
        Objects.requireNonNull(standard, "standard");
        Objects.requireNonNull(aspect, "aspect");
        Objects.requireNonNull(template, "template");
    }

    /**
     * One title.
     *
     * @param source the video file, never changed
     * @param video what ffprobe found
     * @param name the text under its menu button
     */
    public record Title(Path source, ProbedVideo video, String name) {

        public static Title of(Path source, ProbedVideo video) {
            String file = source.getFileName().toString();
            int dot = file.lastIndexOf('.');
            return new Title(source, video, dot > 0 ? file.substring(0, dot) : file);
        }
    }

    public DvdProject withTitles(List<Title> changed) {
        return new DvdProject(discTitle, changed, standard, aspect, chapterMinutes, template, playNext);
    }

    public DvdProject add(List<Title> added) {
        List<Title> all = new ArrayList<>(titles);
        all.addAll(added);
        // The first title decides the aspect ratio until the user changes it.
        String detected =
                titles.isEmpty() && !added.isEmpty() ? added.getFirst().video().dvdAspect() : aspect;
        return new DvdProject(discTitle, all, standard, detected, chapterMinutes, template, playNext);
    }

    public Duration totalDuration() {
        return titles.stream().map(title -> title.video().duration()).reduce(Duration.ZERO, Duration::plus);
    }

    public List<MenuLayout.Page> pages(String playAll, String previous, String next) {
        return MenuLayout.pages(titles.stream().map(Title::name).toList(), standard, playAll, previous, next);
    }

    public int pageCount() {
        return Math.max(1, Math.ceilDiv(titles.size(), MenuLayout.TITLES_PER_PAGE));
    }

    /** The bitrate that fills a disc of {@code capacityBytes}. */
    public DvdBitrate bitrate(long capacityBytes) {
        return DvdBitrate.fit(capacityBytes, totalDuration(), pageCount());
    }

    /** Bytes of the finished image at {@code bitrate}: titles plus menus. */
    public long estimatedBytes(DvdBitrate bitrate) {
        return bitrate.titleBytes(totalDuration()) + pageCount() * DvdBitrate.MENU_PAGE_BYTES;
    }
}
