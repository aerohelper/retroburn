// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.engine.BurnEngine;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.MediaInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drive and media information on Windows, through the Image Mastering API v2 (IMAPI2), which is part
 * of Windows, reached via the helper. See {@code docs/design/LLD-windows-platform.md} section 5.
 */
final class Imapi2Engine implements BurnEngine {

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
}
