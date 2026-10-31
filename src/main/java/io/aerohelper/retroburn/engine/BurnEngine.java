// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import java.util.List;

/**
 * Talks to optical drives. One implementation per operating system.
 *
 * <p>Calls block and may take seconds; never call them on the JavaFX Application Thread. Errors are
 * reported as {@link io.aerohelper.retroburn.common.RetroBurnException} subclasses. Burning,
 * blanking and verifying are added with the data-burning features.
 */
public interface BurnEngine {

    /** All optical drives, including ones the user lacks permission for (marked not accessible). */
    List<DriveInfo> listDrives();

    /** The medium in a drive; {@link MediaInfo#noMedia()} when the drive is empty. */
    MediaInfo readMedia(DriveId drive);
}
