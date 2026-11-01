// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.engine.MediaState;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Imapi2MappingTest {

    @Test
    void mapsEveryPhysicalMediaType() {
        // Values printed by tools/windows/dump-imapi2-typelib.ps1 for IMAPI_MEDIA_PHYSICAL_TYPE.
        Map<Integer, MediaKind> expected = Map.ofEntries(
                Map.entry(0x0, MediaKind.UNKNOWN),
                Map.entry(0x1, MediaKind.CD_ROM),
                Map.entry(0x2, MediaKind.CD_R),
                Map.entry(0x3, MediaKind.CD_RW),
                Map.entry(0x4, MediaKind.DVD_ROM),
                Map.entry(0x5, MediaKind.DVD_RAM),
                Map.entry(0x6, MediaKind.DVD_PLUS_R),
                Map.entry(0x7, MediaKind.DVD_PLUS_RW),
                Map.entry(0x8, MediaKind.DVD_PLUS_R_DL),
                Map.entry(0x9, MediaKind.DVD_R),
                Map.entry(0xA, MediaKind.DVD_RW),
                Map.entry(0xB, MediaKind.DVD_R_DL),
                Map.entry(0xC, MediaKind.UNKNOWN),
                Map.entry(0xD, MediaKind.DVD_PLUS_RW),
                Map.entry(0xE, MediaKind.UNKNOWN),
                Map.entry(0xF, MediaKind.UNKNOWN),
                Map.entry(0x10, MediaKind.UNKNOWN),
                Map.entry(0x11, MediaKind.BD_ROM),
                Map.entry(0x12, MediaKind.BD_R),
                Map.entry(0x13, MediaKind.BD_RE),
                Map.entry(0x14, MediaKind.UNKNOWN));
        expected.forEach((type, kind) -> assertEquals(kind, Imapi2Mapping.mediaKind(type), "type " + type));
    }

    @Test
    void mapsMediaStateFlags() {
        assertEquals(MediaState.BLANK, Imapi2Mapping.mediaState(0x2)); // observed: blank DVD+RW
        assertEquals(MediaState.APPENDABLE, Imapi2Mapping.mediaState(0x1)); // observed: written DVD+RW
        assertEquals(MediaState.APPENDABLE, Imapi2Mapping.mediaState(0x4 | 0x1000));
        assertEquals(MediaState.CLOSED, Imapi2Mapping.mediaState(0x4000 | 0x1000));
        assertEquals(MediaState.BLANK, Imapi2Mapping.mediaState(0x2 | 0x1));
        assertEquals(MediaState.UNKNOWN, Imapi2Mapping.mediaState(0));
        assertEquals(MediaState.UNKNOWN, Imapi2Mapping.mediaState(0x8000));
    }

    @Test
    void convertsSectorsToBytes() {
        assertEquals(
                new MediaInfo(MediaKind.DVD_PLUS_RW, MediaState.BLANK, 2295104L * 2048, 0),
                Imapi2Mapping.mediaInfo(new HelperEvent.Media(true, 7, 2, 2295104, 2295104)));
        assertEquals(
                new MediaInfo(MediaKind.CD_R, MediaState.APPENDABLE, 1000L * 2048, 359000L * 2048),
                Imapi2Mapping.mediaInfo(new HelperEvent.Media(true, 2, 4, 1000, 360000)));
    }

    @Test
    void anEmptyDriveHasNoMedia() {
        assertEquals(MediaInfo.noMedia(), Imapi2Mapping.mediaInfo(new HelperEvent.Media(false, 0, 0, 0, 0)));
    }

    @Test
    void mapsTheBus() {
        assertEquals(ConnectionType.USB, Imapi2Mapping.connection("usb"));
        assertEquals(ConnectionType.OTHER, Imapi2Mapping.connection("other"));
        assertEquals(ConnectionType.UNKNOWN, Imapi2Mapping.connection("unknown"));
        assertEquals(ConnectionType.UNKNOWN, Imapi2Mapping.connection(""));
    }

    @Test
    void progressFractionStaysBetweenZeroAndOne() {
        assertEquals(0.5, Imapi2Mapping.fraction(new HelperEvent.Progress("writing", 5, 10)));
        assertEquals(0.0, Imapi2Mapping.fraction(new HelperEvent.Progress("erasing", 3, 0)));
        assertEquals(1.0, Imapi2Mapping.fraction(new HelperEvent.Progress("erasing", 31, 28)));
        assertEquals(0.0, Imapi2Mapping.fraction(new HelperEvent.Progress("writing", -1, 10)));
    }
}
