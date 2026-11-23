// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import io.aerohelper.retroburn.engine.TrackLayout;
import java.util.List;
import java.util.Optional;

/**
 * Compares the table of contents of a burned audio CD with the tracks that were written. Every
 * track but the last spans its own sectors plus the next track's 2-second gap; the last one ends at
 * the lead-out. Drives may add a few run-out sectors, so lengths may be up to one second off; a
 * missing, extra or truncated track is still caught. See {@code docs/design/LLD-audio-cd.md}
 * section 6.3.
 */
public final class TrackListCheck {

    static final long TOLERANCE_SECTORS = CdAudio.SECTORS_PER_SECOND;

    private TrackListCheck() {}

    /** Empty if the disc matches; otherwise what differs, for "Show details". */
    public static Optional<String> difference(List<Long> writtenSectors, TrackLayout layout) {
        List<Long> spans = layout.spans();
        if (spans.size() != writtenSectors.size()) {
            return Optional.of("The disc has " + spans.size() + " tracks; " + writtenSectors.size() + " were written");
        }
        for (int index = 0; index < spans.size(); index++) {
            boolean last = index == spans.size() - 1;
            long expected = writtenSectors.get(index) + (last ? 0 : CdAudio.GAP_SECTORS);
            if (Math.abs(spans.get(index) - expected) > TOLERANCE_SECTORS) {
                return Optional.of(
                        "Track " + (index + 1) + " spans " + spans.get(index) + " sectors; expected " + expected);
            }
        }
        return Optional.empty();
    }
}
