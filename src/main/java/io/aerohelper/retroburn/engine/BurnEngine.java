// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import io.aerohelper.retroburn.common.CancelToken;
import java.nio.file.Path;
import java.util.List;

/**
 * Talks to optical drives. One implementation per operating system.
 *
 * <p>Calls block and may take seconds to an hour; never call them on the JavaFX Application
 * Thread. Errors are reported as {@link io.aerohelper.retroburn.common.RetroBurnException}
 * subclasses; cancelling throws {@link io.aerohelper.retroburn.common.OperationCancelledException}.
 * See {@code docs/design/LLD-engine-and-platform.md} section 1.1.
 */
public interface BurnEngine {

    /** All optical drives, including ones the user lacks permission for (marked not accessible). */
    List<DriveInfo> listDrives();

    /** The medium in a drive; {@link MediaInfo#noMedia()} when the drive is empty. */
    MediaInfo readMedia(DriveId drive);

    /**
     * Writes an image file to the disc as one data track, without blanking it first.
     *
     * @param listener receives progress (held below 1.0 until the track is closed) and log lines
     * @param cancel stops the burn; the disc is then unusable until blanked
     */
    void burnImage(DriveId drive, Path image, BurnOptions options, BurnListener listener, CancelToken cancel);

    /**
     * Makes a rewritable disc ready for writing from scratch; a blank disc is left alone.
     *
     * @param cancel honoured before the erase starts; on Windows a started erase cannot be stopped
     */
    void blank(DriveId drive, BlankMode mode, BurnListener listener, CancelToken cancel);

    /**
     * Reads back as many bytes as the image has, from the start of the disc, and compares them with
     * the image.
     *
     * @throws io.aerohelper.retroburn.common.RetroBurnException with {@code error.verifyMismatch} if
     *     they differ or the disc is shorter
     */
    void verifyImage(DriveId drive, Path image, BurnListener listener, CancelToken cancel);

    /** Opens the tray, where possible. */
    void eject(DriveId drive);
}
