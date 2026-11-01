// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.ByteSizes;
import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.project.DiscCapacity;
import java.util.Locale;

/** Short descriptions of discs for the drive bar. */
final class MediaText {

    private MediaText() {}

    /** The kind as printed on discs, for example "DVD+RW". */
    static String kind(MediaKind kind) {
        return switch (kind) {
            case CD_ROM -> "CD-ROM";
            case CD_R -> "CD-R";
            case CD_RW -> "CD-RW";
            case DVD_ROM -> "DVD-ROM";
            case DVD_R -> "DVD-R";
            case DVD_RW -> "DVD-RW";
            case DVD_RAM -> "DVD-RAM";
            case DVD_R_DL -> "DVD-R DL";
            case DVD_PLUS_R -> "DVD+R";
            case DVD_PLUS_RW -> "DVD+RW";
            case DVD_PLUS_R_DL -> "DVD+R DL";
            case BD_ROM -> "BD-ROM";
            case BD_R -> "BD-R";
            case BD_RE -> "BD-RE";
            case NONE, UNKNOWN -> "";
        };
    }

    /** For example "DVD+RW · blank · 4.7 GB free", or "No disc". */
    static String describe(MediaInfo media, Messages messages) {
        return switch (media.state()) {
            case NO_MEDIA -> messages.get("drive.noDisc");
            case UNKNOWN -> messages.get("drive.unknownDisc");
            default ->
                messages.format(
                        "drive.media",
                        media.kind() == MediaKind.UNKNOWN ? messages.get("drive.unknownDisc") : kind(media.kind()),
                        messages.get("media.state." + media.state().name().toLowerCase(Locale.ROOT)),
                        ByteSizes.format(DiscCapacity.usableBytes(media)));
        };
    }
}
