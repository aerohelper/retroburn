// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.engine.MediaState;

/**
 * IMAPI2 values in terms of RetroBurn's engine types. The numbers were read from the type library in
 * {@code imapi2.dll} with {@code tools/windows/dump-imapi2-typelib.ps1}; see {@code
 * docs/design/LLD-windows-platform.md} section 5.2.
 */
final class Imapi2Mapping {

    static final long SECTOR_SIZE = 2048;

    // IMAPI_FORMAT2_DATA_MEDIA_STATE flags.
    static final int STATE_OVERWRITE_ONLY = 0x1;
    static final int STATE_BLANK = 0x2;
    static final int STATE_APPENDABLE = 0x4;
    static final int STATE_FINALIZED = 0x4000;

    private Imapi2Mapping() {}

    /** The kind of medium for an {@code IMAPI_MEDIA_PHYSICAL_TYPE} value. */
    static MediaKind mediaKind(int physicalType) {
        return switch (physicalType) {
            case 0x1 -> MediaKind.CD_ROM; // IMAPI_MEDIA_TYPE_CDROM
            case 0x2 -> MediaKind.CD_R; // IMAPI_MEDIA_TYPE_CDR
            case 0x3 -> MediaKind.CD_RW; // IMAPI_MEDIA_TYPE_CDRW
            case 0x4 -> MediaKind.DVD_ROM; // IMAPI_MEDIA_TYPE_DVDROM
            case 0x5 -> MediaKind.DVD_RAM; // IMAPI_MEDIA_TYPE_DVDRAM
            case 0x6 -> MediaKind.DVD_PLUS_R; // IMAPI_MEDIA_TYPE_DVDPLUSR
            case 0x7 -> MediaKind.DVD_PLUS_RW; // IMAPI_MEDIA_TYPE_DVDPLUSRW
            case 0x8 -> MediaKind.DVD_PLUS_R_DL; // IMAPI_MEDIA_TYPE_DVDPLUSR_DUALLAYER
            case 0x9 -> MediaKind.DVD_R; // IMAPI_MEDIA_TYPE_DVDDASHR
            case 0xA -> MediaKind.DVD_RW; // IMAPI_MEDIA_TYPE_DVDDASHRW
            case 0xB -> MediaKind.DVD_R_DL; // IMAPI_MEDIA_TYPE_DVDDASHR_DUALLAYER
            case 0xD -> MediaKind.DVD_PLUS_RW; // IMAPI_MEDIA_TYPE_DVDPLUSRW_DUALLAYER
            case 0x11 -> MediaKind.BD_ROM; // IMAPI_MEDIA_TYPE_BDROM
            case 0x12 -> MediaKind.BD_R; // IMAPI_MEDIA_TYPE_BDR
            case 0x13 -> MediaKind.BD_RE; // IMAPI_MEDIA_TYPE_BDRE
            // UNKNOWN (0x0), DISK (0xC) and the HD DVD types (0xE to 0x10).
            default -> MediaKind.UNKNOWN;
        };
    }

    /**
     * What is on the medium, from {@code IMAPI_FORMAT2_DATA_MEDIA_STATE} flags; the first match wins.
     * A written DVD+RW reports only "overwrite only" and can be written again, so it counts as
     * appendable, as xorriso reports it on Linux.
     */
    static MediaState mediaState(int status) {
        if ((status & STATE_BLANK) != 0) {
            return MediaState.BLANK;
        }
        if ((status & (STATE_APPENDABLE | STATE_OVERWRITE_ONLY)) != 0) {
            return MediaState.APPENDABLE;
        }
        if ((status & STATE_FINALIZED) != 0) {
            return MediaState.CLOSED;
        }
        return MediaState.UNKNOWN;
    }

    static MediaInfo mediaInfo(HelperEvent.Media media) {
        if (!media.present()) {
            return MediaInfo.noMedia();
        }
        long free = Math.max(0, media.freeSectors());
        long used = Math.max(0, media.totalSectors() - free);
        return new MediaInfo(
                mediaKind(media.physicalType()), mediaState(media.status()), free * SECTOR_SIZE, used * SECTOR_SIZE);
    }

    /** The connection from the helper's {@code bus} field. */
    static ConnectionType connection(String bus) {
        return switch (bus) {
            case "usb" -> ConnectionType.USB;
            case "other" -> ConnectionType.OTHER;
            default -> ConnectionType.UNKNOWN;
        };
    }

    /** The highest fraction shown before the helper reports success. */
    static final double BEFORE_DONE = 0.99;

    /**
     * Progress of a burn as a whole: the written share while writing, nothing before, and {@link
     * #BEFORE_DONE} while the drive finalises or checks its work. See the LLD section 5.7.
     */
    static double burnFraction(HelperEvent.Progress progress) {
        return switch (progress.stage()) {
            case "writing" -> Math.min(fraction(progress), BEFORE_DONE);
            case "finalising", "verifying", "completed" -> BEFORE_DONE;
            default -> 0.0;
        };
    }

    /** Progress of a stage as a fraction from 0 to 1; 0 when the total is unknown. */
    static double fraction(HelperEvent.Progress progress) {
        if (progress.total() <= 0) {
            return 0.0;
        }
        return Math.clamp((double) progress.done() / progress.total(), 0.0, 1.0);
    }
}
