// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.simulated;

import io.aerohelper.retroburn.audio.CdAudio;
import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.engine.AudioCdEngine;
import io.aerohelper.retroburn.engine.AudioDisc;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.BurnListener;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.TrackLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Pretends to burn audio CDs: reports progress per track and remembers the layout, so the screens
 * and the check after burning can be tried without a drive.
 */
final class SimulatedAudioEngine implements AudioCdEngine {

    private volatile TrackLayout lastLayout = new TrackLayout(List.of(), 0);

    @Override
    public boolean writesCdText() {
        return true;
    }

    @Override
    public void burnAudioCd(DriveId drive, AudioDisc disc, BurnListener listener, CancelToken cancel) {
        // As on a real disc: track 1 starts at 0 (its gap lies before it), every later track after
        // the previous one and its own 2-second gap, and the lead-out right after the last track.
        List<Long> starts = new ArrayList<>();
        long position = 0;
        long total = disc.audioSectors();
        long written = 0;
        for (AudioDisc.Track track : disc.tracks()) {
            cancel.throwIfCancelled();
            if (!starts.isEmpty()) {
                position += CdAudio.GAP_SECTORS;
            }
            starts.add(position);
            position += track.sectors();
            written += track.sectors();
            listener.onEvent(new BurnEvent.LogLine("Simulated track of " + track.sectors() + " sectors"));
            listener.onEvent(BurnEvent.Progress.of(Math.min((double) written / total, 0.99)));
        }
        lastLayout = new TrackLayout(starts, position);
        listener.onEvent(BurnEvent.Progress.of(1.0));
    }

    @Override
    public TrackLayout readTrackLayout(DriveId drive) {
        return lastLayout;
    }
}
