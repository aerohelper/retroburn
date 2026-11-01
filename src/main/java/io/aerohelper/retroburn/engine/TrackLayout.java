// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * A disc's table of contents: where each track starts and where the lead-out starts, as logical
 * block addresses (a track's 2-second gap belongs to the track before it in these numbers).
 *
 * @param trackStarts each track's first sector, in order
 * @param leadOut the lead-out's first sector
 */
public record TrackLayout(List<Long> trackStarts, long leadOut) {

    public TrackLayout {
        trackStarts = List.copyOf(trackStarts);
    }

    /** Sectors from each track's start to the next track's start (or the lead-out). */
    public List<Long> spans() {
        List<Long> spans = new ArrayList<>();
        for (int index = 0; index < trackStarts.size(); index++) {
            long end = index + 1 < trackStarts.size() ? trackStarts.get(index + 1) : leadOut;
            spans.add(end - trackStarts.get(index));
        }
        return List.copyOf(spans);
    }
}
