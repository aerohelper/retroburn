// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.AudioCdEngine;
import io.aerohelper.retroburn.engine.AudioDisc;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.BurnListener;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.TrackLayout;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Audio CDs on Windows through IMAPI2 track-at-once ({@code MsftDiscFormat2TrackAtOnce}), via the
 * helper's {@code burn-audio} and {@code read-toc} operations. Writes no CD-Text: IMAPI2 has no way
 * to (ADR-0014). See {@code docs/design/LLD-audio-cd.md} section 6.2.
 */
final class Imapi2AudioEngine implements AudioCdEngine {

    private static final Duration WRITE_IDLE = Duration.ofMinutes(10);

    private final HelperClient helper;

    Imapi2AudioEngine(HelperClient helper) {
        this.helper = helper;
    }

    @Override
    public boolean writesCdText() {
        return false;
    }

    @Override
    public void burnAudioCd(DriveId drive, AudioDisc disc, BurnListener listener, CancelToken cancel) {
        AtomicReference<String> stage = new AtomicReference<>("");
        helper.run(
                HelperRequest.burnAudio(
                        drive, disc.tracks().stream().map(AudioDisc.Track::wav).toList()),
                event -> {
                    switch (event) {
                        case HelperEvent.Progress progress -> {
                            if (!progress.stage().equals(stage.getAndSet(progress.stage()))) {
                                listener.onEvent(new BurnEvent.LogLine("Stage: " + progress.stage()));
                            }
                            // "done" counts every track so far, whatever the stage; 1.0 only once the disc is closed.
                            listener.onEvent(BurnEvent.Progress.of(Math.min(Imapi2Mapping.fraction(progress), 0.99)));
                        }
                        case HelperEvent.Log log -> listener.onEvent(new BurnEvent.LogLine(log.text()));
                        default -> {}
                    }
                },
                cancel,
                HelperClient.Limits.silentFor(WRITE_IDLE));
        listener.onEvent(BurnEvent.Progress.of(1.0));
    }

    @Override
    public TrackLayout readTrackLayout(DriveId drive) {
        AtomicReference<HelperEvent.Toc> toc = new AtomicReference<>();
        helper.run(
                HelperRequest.readToc(drive),
                event -> {
                    if (event instanceof HelperEvent.Toc found) {
                        toc.set(found);
                    }
                },
                CancelToken.none(),
                HelperClient.Limits.query());
        HelperEvent.Toc found = toc.get();
        if (found == null) {
            throw new RetroBurnException(
                    UserMessage.of("error.mediaReadFailed", "no table of contents"), "read-toc sent no toc event");
        }
        return new TrackLayout(found.starts(), found.leadOut());
    }
}
