// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.simulated;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.BlankMode;
import io.aerohelper.retroburn.engine.BurnEngine;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.BurnListener;
import io.aerohelper.retroburn.engine.BurnOptions;
import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.engine.MediaState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * One pretend USB DVD writer with a DVD+RW that starts blank. Burning remembers the image's size,
 * blanking forgets it, and verifying compares the image's size with it. Thread-safe.
 */
final class SimulatedBurnEngine implements BurnEngine {

    static final DriveId DRIVE = new DriveId("simulated-0");

    /** Capacity of a single-layer DVD: 2,295,104 blocks of 2048 bytes. */
    private static final long DVD_CAPACITY_BYTES = 2_295_104L * 2048L;

    private static final int STEPS = 10;

    /** Bytes of the last image burned; 0 while the disc is blank. */
    private long writtenBytes;

    @Override
    public List<DriveInfo> listDrives() {
        return List.of(new DriveInfo(DRIVE, "RetroBurn", "Simulated DVD Writer", ConnectionType.USB, true));
    }

    @Override
    public synchronized MediaInfo readMedia(DriveId drive) {
        if (!DRIVE.equals(drive)) {
            return MediaInfo.noMedia();
        }
        // Like a real DVD+RW, the disc stays fully writable after a burn.
        return new MediaInfo(
                MediaKind.DVD_PLUS_RW,
                writtenBytes == 0 ? MediaState.BLANK : MediaState.APPENDABLE,
                DVD_CAPACITY_BYTES,
                writtenBytes);
    }

    @Override
    public void burnImage(DriveId drive, Path image, BurnOptions options, BurnListener listener, CancelToken cancel) {
        requireDrive(drive);
        long size = sizeOf(image);
        if (size > DVD_CAPACITY_BYTES) {
            throw new RetroBurnException(UserMessage.of("error.discTooSmall"), image + ": " + size + " bytes");
        }
        try {
            pretendWork(listener, cancel, 0.99);
        } catch (RuntimeException e) {
            remember(0);
            throw e;
        }
        remember(size);
        listener.onEvent(BurnEvent.Progress.of(1.0));
    }

    @Override
    public void blank(DriveId drive, BlankMode mode, BurnListener listener, CancelToken cancel) {
        requireDrive(drive);
        pretendWork(listener, cancel, 0.99);
        remember(0);
        listener.onEvent(BurnEvent.Progress.of(1.0));
    }

    @Override
    public void verifyImage(DriveId drive, Path image, BurnListener listener, CancelToken cancel) {
        requireDrive(drive);
        long size = sizeOf(image);
        pretendWork(listener, cancel, 1.0);
        long written;
        synchronized (this) {
            written = writtenBytes;
        }
        if (written != size) {
            throw new RetroBurnException(
                    UserMessage.of("error.verifyMismatch"),
                    "Simulated disc holds " + written + " bytes, image has " + size);
        }
    }

    @Override
    public void eject(DriveId drive) {
        requireDrive(drive);
    }

    private synchronized void remember(long bytes) {
        writtenBytes = bytes;
    }

    private static void pretendWork(BurnListener listener, CancelToken cancel, double last) {
        for (int step = 1; step <= STEPS; step++) {
            cancel.throwIfCancelled();
            listener.onEvent(BurnEvent.Progress.of(last * step / STEPS));
        }
    }

    private static void requireDrive(DriveId drive) {
        if (!DRIVE.equals(drive)) {
            throw new RetroBurnException(UserMessage.of("error.driveNotFound"), drive.value());
        }
    }

    private static long sizeOf(Path image) {
        try {
            return Files.size(image);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + image, e);
        }
    }
}
