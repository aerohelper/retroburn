// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.engine.TrackLayout;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrackListCheckTest {

    @Test
    void aMatchingDiscPasses() {
        // Two tracks of 338 sectors: the second starts after the first and its own 2-second gap.
        TrackLayout layout = new TrackLayout(List.of(0L, 488L), 826);
        assertEquals(Optional.empty(), TrackListCheck.difference(List.of(338L, 338L), layout));
    }

    @Test
    void smallRunOutDifferencesAreTolerated() {
        TrackLayout layout = new TrackLayout(List.of(0L, 490L), 830);
        assertEquals(Optional.empty(), TrackListCheck.difference(List.of(338L, 338L), layout));
    }

    @Test
    void missingOrTruncatedTracksAreReported() {
        TrackLayout oneTrack = new TrackLayout(List.of(0L), 338);
        assertTrue(TrackListCheck.difference(List.of(338L, 338L), oneTrack)
                .orElseThrow()
                .contains("1 tracks"));

        TrackLayout truncated = new TrackLayout(List.of(0L, 488L), 600);
        assertEquals(
                Optional.of("Track 2 spans 112 sectors; expected 338"),
                TrackListCheck.difference(List.of(338L, 338L), truncated));
    }
}
