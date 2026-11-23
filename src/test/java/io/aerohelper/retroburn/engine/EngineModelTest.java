// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EngineModelTest {

    private static final Path SOURCE = Path.of("source");

    @ParameterizedTest
    @ValueSource(strings = {"/a", "/Photos/2026", "/name with spaces/and=equals.txt", "/café/हिंदी"})
    void acceptsWellFormedDiscPaths(String discPath) {
        assertDoesNotThrow(() -> new DiscEntry(discPath, SOURCE));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"", "/", "relative", "/a//b", "/a/", "/./a", "/a/../b", "/line\nbreak", "/carriage\rreturn"})
    void rejectsMalformedDiscPaths(String discPath) {
        assertThrows(IllegalArgumentException.class, () -> new DiscEntry(discPath, SOURCE));
    }

    @Test
    void discTreeStreamsItsEntriesEachTime() {
        DiscTree tree = DiscTree.of(List.of(new DiscEntry("/a", SOURCE), new DiscEntry("/b", SOURCE)));

        assertEquals(2, tree.entries().count());
        assertEquals(2, tree.entries().count());
    }

    @Test
    void mediaKindsKnowTheirFamilyAndWhetherTheyCanBeWritten() {
        assertEquals(Optional.of(MediaFamily.CD), MediaKind.CD_R.family());
        assertTrue(MediaKind.DVD_PLUS_RW.isRewritable());
        assertFalse(MediaKind.DVD_PLUS_R.isRewritable());
        assertTrue(MediaKind.BD_R.isWritable());
        assertFalse(MediaKind.DVD_ROM.isWritable());
        assertTrue(MediaKind.NONE.family().isEmpty());
    }

    @Test
    void driveDisplayNameFallsBackToTheAddress() {
        DriveId id = new DriveId("/dev/sr0");

        assertEquals("ASUS SDRW", new DriveInfo(id, " ASUS ", "SDRW", ConnectionType.USB, true).displayName());
        assertEquals("/dev/sr0", new DriveInfo(id, null, "", ConnectionType.UNKNOWN, false).displayName());
        assertThrows(IllegalArgumentException.class, () -> new DriveId(" "));
    }

    @Test
    void progressMustBeAFraction() {
        assertThrows(IllegalArgumentException.class, () -> BurnEvent.Progress.of(1.5));
        assertThrows(IllegalArgumentException.class, () -> BurnEvent.Progress.of(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new MediaInfo(MediaKind.CD_R, MediaState.BLANK, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ImageOptions(" "));
    }
}
