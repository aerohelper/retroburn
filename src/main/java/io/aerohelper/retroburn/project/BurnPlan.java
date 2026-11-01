// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.MediaInfo;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a burn or erase will do, as the confirmation dialog shows it and the job runs it. Made by
 * {@link BurnPreCheck}.
 *
 * @param kind what kind of job
 * @param drive the drive, if one is chosen
 * @param media the disc in it, if known
 * @param bytesToWrite image size (estimated for a data disc); 0 for an erase
 * @param eraseFirst a quick erase runs before writing (a used CD-RW or DVD-RW)
 * @param verify the disc is compared with the image afterwards
 * @param blockers reasons the job cannot start; empty when it can
 * @param warnings things the user should know; they do not stop the job
 * @param showUsbTip the USB advice applies and has not been dismissed
 */
public record BurnPlan(
        JobKind kind,
        Optional<DriveInfo> drive,
        Optional<MediaInfo> media,
        long bytesToWrite,
        boolean eraseFirst,
        boolean verify,
        List<UserMessage> blockers,
        List<UserMessage> warnings,
        boolean showUsbTip) {

    public BurnPlan {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(drive, "drive");
        Objects.requireNonNull(media, "media");
        blockers = List.copyOf(blockers);
        warnings = List.copyOf(warnings);
    }

    public boolean canStart() {
        return blockers.isEmpty();
    }
}
