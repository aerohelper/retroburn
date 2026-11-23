// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.engine.BlankMode;
import io.aerohelper.retroburn.engine.BurnEngine;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.BurnListener;
import io.aerohelper.retroburn.engine.BurnOptions;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.MediaInfo;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/**
 * Drives, media, burning, blanking and verifying on Windows, through the Image Mastering API v2 (IMAPI2), which is part
 * of Windows, reached via the helper. See {@code docs/design/LLD-windows-platform.md} section 5.
 */
final class Imapi2Engine implements BurnEngine {

    /** Some drives stay silent for minutes while finalising or erasing. */
    private static final Duration WRITE_IDLE = Duration.ofMinutes(10);

    private static final Duration VERIFY_IDLE = Duration.ofMinutes(2);

    private final HelperClient helper;

    Imapi2Engine(HelperClient helper) {
        this.helper = helper;
    }

    @Override
    public List<DriveInfo> listDrives() {
        List<DriveInfo> drives = new ArrayList<>();
        helper.run(
                HelperRequest.listDrives(),
                event -> {
                    if (event instanceof HelperEvent.Drive drive) {
                        drives.add(toDriveInfo(drive));
                    }
                },
                CancelToken.none(),
                HelperClient.Limits.query());
        return List.copyOf(drives);
    }

    static DriveInfo toDriveInfo(HelperEvent.Drive drive) {
        return new DriveInfo(
                new DriveId(drive.id()),
                drive.vendor(),
                drive.product(),
                Imapi2Mapping.connection(drive.bus()),
                drive.accessible());
    }

    @Override
    public MediaInfo readMedia(DriveId drive) {
        AtomicReference<HelperEvent.Media> media = new AtomicReference<>();
        helper.run(
                HelperRequest.readMedia(drive),
                event -> {
                    if (event instanceof HelperEvent.Media found) {
                        media.set(found);
                    }
                },
                CancelToken.none(),
                HelperClient.Limits.query());
        HelperEvent.Media found = media.get();
        if (found == null) {
            throw HelperErrors.unavailable("The helper finished without describing the media");
        }
        return Imapi2Mapping.mediaInfo(found);
    }

    @Override
    public void burnImage(DriveId drive, Path image, BurnOptions options, BurnListener listener, CancelToken cancel) {
        AtomicReference<String> stage = new AtomicReference<>("");
        helper.run(
                HelperRequest.burnImage(drive, image, options.closeMedia()),
                forward(listener, Imapi2Mapping::burnFraction, progress -> {
                    // Stage changes go to "Show details", so a long calibration or finalisation is visible.
                    if (!progress.stage().equals(stage.getAndSet(progress.stage()))) {
                        listener.onEvent(new BurnEvent.LogLine("Stage: " + progress.stage()));
                    }
                }),
                cancel,
                HelperClient.Limits.silentFor(WRITE_IDLE));
        listener.onEvent(BurnEvent.Progress.of(1.0));
    }

    @Override
    public void blank(DriveId drive, BlankMode mode, BurnListener listener, CancelToken cancel) {
        // IMAPI2 cannot stop a started erase; stopping the helper would only leave it running unseen.
        cancel.throwIfCancelled();
        // IMAPI2's estimate is no use (verified: the estimated total always equals the time elapsed),
        // so only log lines are passed on and the UI shows an erase without a percentage.
        helper.run(
                HelperRequest.blank(drive, mode == BlankMode.FULL),
                event -> {
                    if (event instanceof HelperEvent.Log log) {
                        listener.onEvent(new BurnEvent.LogLine(log.text()));
                    }
                },
                CancelToken.none(),
                HelperClient.Limits.silentFor(WRITE_IDLE));
        listener.onEvent(BurnEvent.Progress.of(1.0));
    }

    @Override
    public void verifyImage(DriveId drive, Path image, BurnListener listener, CancelToken cancel) {
        helper.run(
                HelperRequest.verifyImage(drive, image),
                forward(listener, Imapi2Mapping::fraction, progress -> {}),
                cancel,
                HelperClient.Limits.silentFor(VERIFY_IDLE));
        listener.onEvent(BurnEvent.Progress.of(1.0));
    }

    @Override
    public void eject(DriveId drive) {
        helper.run(HelperRequest.eject(drive), event -> {}, CancelToken.none(), HelperClient.Limits.query());
    }

    /** Passes progress (as {@code fraction} computes it) and log lines on to the listener. */
    private static Consumer<HelperEvent> forward(
            BurnListener listener,
            ToDoubleFunction<HelperEvent.Progress> fraction,
            Consumer<HelperEvent.Progress> alsoOnProgress) {
        return event -> {
            switch (event) {
                case HelperEvent.Progress progress -> {
                    alsoOnProgress.accept(progress);
                    listener.onEvent(BurnEvent.Progress.of(fraction.applyAsDouble(progress)));
                }
                case HelperEvent.Log log -> listener.onEvent(new BurnEvent.LogLine(log.text()));
                default -> {}
            }
        };
    }
}
