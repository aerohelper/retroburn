// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.simulated;

import io.aerohelper.retroburn.engine.BurnEngine;
import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.engine.MediaState;
import java.util.List;

/** One pretend USB DVD writer with a blank DVD+RW inserted. */
final class SimulatedBurnEngine implements BurnEngine {

    static final DriveId DRIVE = new DriveId("simulated-0");

    /** Capacity of a single-layer DVD: 2,295,104 blocks of 2048 bytes. */
    private static final long DVD_CAPACITY_BYTES = 2_295_104L * 2048L;

    @Override
    public List<DriveInfo> listDrives() {
        return List.of(new DriveInfo(DRIVE, "RetroBurn", "Simulated DVD Writer", ConnectionType.USB, true));
    }

    @Override
    public MediaInfo readMedia(DriveId drive) {
        if (!DRIVE.equals(drive)) {
            return MediaInfo.noMedia();
        }
        return new MediaInfo(MediaKind.DVD_PLUS_RW, MediaState.BLANK, DVD_CAPACITY_BYTES, 0);
    }
}
