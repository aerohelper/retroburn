// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.video;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Chapter points every few minutes, written as dvdauthor's {@code chapters} attribute: a comma-separated
 * list of {@code [[h:]mm:]ss} markers (dvdauthor manual, {@code --chapters}).
 */
public final class DvdChapters {

    private DvdChapters() {}

    /** Chapter starts for a title of {@code length}; one chapter at 0 when {@code minutes} is 0. */
    public static List<Duration> every(int minutes, Duration length) {
        List<Duration> points = new ArrayList<>(List.of(Duration.ZERO));
        if (minutes <= 0) {
            return points;
        }
        Duration step = Duration.ofMinutes(minutes);
        // No chapter in the last 10 seconds: it would be almost empty.
        for (Duration at = step; at.plusSeconds(10).compareTo(length) < 0; at = at.plus(step)) {
            points.add(at);
        }
        return points;
    }

    /** "0,5:00,10:00,1:05:00". */
    public static String attribute(List<Duration> points) {
        List<String> parts = new ArrayList<>();
        for (Duration point : points) {
            long seconds = point.toSeconds();
            if (seconds == 0) {
                parts.add("0");
            } else if (seconds < 3600) {
                parts.add("%d:%02d".formatted(seconds / 60, seconds % 60));
            } else {
                parts.add("%d:%02d:%02d".formatted(seconds / 3600, seconds / 60 % 60, seconds % 60));
            }
        }
        return String.join(",", parts);
    }
}
