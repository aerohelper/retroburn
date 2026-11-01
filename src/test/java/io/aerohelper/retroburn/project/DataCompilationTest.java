// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.UnsupportedInputException;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.engine.MediaState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DataCompilationTest {

    @TempDir
    Path folder;

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Holiday 2026|Holiday 2026",
                "  padded  |padded",
                "café|caf_",
                "a/b:c*d|a_b_c_d",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ|ABCDEFGHIJKLMNOP",
                "fifteen chars x 2|fifteen chars x",
                "''|RETROBURN",
                "हिंदी|_____",
            })
    void labelsAreCutToWhatReadsTheSameEverywhere(String typed, String expected) {
        assertEquals(expected, VolumeLabel.sanitize(typed).text());
        assertEquals(typed.equals(expected), VolumeLabel.isValid(typed));
    }

    @Test
    void anInvalidLabelCannotBeMadeDirectly() {
        assertThrows(IllegalArgumentException.class, () -> new VolumeLabel("bad/label"));
        assertEquals("Ok_label-1", new VolumeLabel("Ok_label-1").text());
    }

    @Test
    void itemsGoToTheRootAndClashingNamesAreNumbered() {
        Path a = folder.resolve("one/Photos");
        Path b = folder.resolve("two/photos");
        Path c = folder.resolve("one/report.pdf");
        Path d = folder.resolve("two/report.pdf");
        Path e = folder.resolve("three/report.pdf");
        Path hidden = folder.resolve(".config");

        DataCompilation compilation =
                DataCompilation.empty().add(List.of(a, b, c, d)).add(List.of(e, hidden));

        assertEquals(
                List.of(
                        new DiscEntry("/Photos", a),
                        new DiscEntry("/photos (2)", b),
                        new DiscEntry("/report.pdf", c),
                        new DiscEntry("/report (2).pdf", d),
                        new DiscEntry("/report (3).pdf", e),
                        new DiscEntry("/.config", hidden)),
                compilation.list());
        assertEquals(6, compilation.entries().count());
    }

    @Test
    void removingFreesTheName() {
        Path a = folder.resolve("one/a.txt");
        Path b = folder.resolve("two/a.txt");
        DataCompilation compilation = DataCompilation.empty().add(List.of(a));

        DataCompilation removed = compilation.remove(List.of("/a.txt"));

        assertTrue(removed.isEmpty());
        assertEquals(
                List.of(new DiscEntry("/a.txt", b)), removed.add(List.of(b)).list());
        assertFalse(compilation.isEmpty(), "the original is unchanged");
    }

    @Test
    void aDriveRootHasNoNameToUse() {
        Path root = folder.toAbsolutePath().getRoot();

        assertThrows(
                UnsupportedInputException.class, () -> DataCompilation.empty().add(List.of(root)));
    }

    @Test
    void scanningAddsUpFilesFoldersAndBlocks() throws IOException {
        Path tree = Files.createDirectories(folder.resolve("tree/sub"));
        Files.write(tree.resolve("one-block.bin"), new byte[2048]);
        Files.write(folder.resolve("tree/two-blocks.bin"), new byte[2049]);
        Files.write(folder.resolve("tree/empty.txt"), new byte[0]);
        Path single = Files.write(folder.resolve("single.bin"), new byte[10]);

        TreeSize size = new TreeSizeScanner()
                .scan(DataCompilation.empty().add(List.of(folder.resolve("tree"), single)), new CancelToken());

        assertEquals(2048 + 2049 + 10, size.bytes());
        assertEquals(1 + 2 + 0 + 1, size.blocks());
        assertEquals(4, size.files());
        assertEquals(2, size.folders());
        assertEquals(0, size.unreadableCount());
        assertEquals(
                4L * 2048 + 6 * ImageSizeEstimate.BYTES_PER_ENTRY + ImageSizeEstimate.FIXED_BYTES,
                ImageSizeEstimate.bytes(size));
    }

    @Test
    void aMissingSourceIsReportedAsUnreadable() {
        Path missing = folder.resolve("gone.txt");

        TreeSize size = new TreeSizeScanner().scan(DataCompilation.empty().add(List.of(missing)), new CancelToken());

        assertEquals(1, size.unreadableCount());
        assertEquals(List.of(missing.toAbsolutePath()), size.unreadable());
    }

    @Test
    void scanningStopsWhenCancelled() throws IOException {
        Files.write(folder.resolve("a.txt"), new byte[1]);
        CancelToken cancel = new CancelToken();
        cancel.cancel();

        assertThrows(
                OperationCancelledException.class,
                () -> new TreeSizeScanner().scan(DataCompilation.empty().add(List.of(folder)), cancel));
    }

    @Test
    void rewritableDiscsWithDataOfferTheirWholeCapacity() {
        assertEquals(100, DiscCapacity.usableBytes(new MediaInfo(MediaKind.DVD_R, MediaState.BLANK, 100, 0)));
        assertEquals(
                100, DiscCapacity.usableBytes(new MediaInfo(MediaKind.DVD_PLUS_RW, MediaState.APPENDABLE, 60, 40)));
        assertEquals(60, DiscCapacity.usableBytes(new MediaInfo(MediaKind.DVD_PLUS_R, MediaState.APPENDABLE, 60, 40)));
    }
}
