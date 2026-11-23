// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.CancelToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The scan of a 100,000-file compilation stays quick and keeps no file list: a coarse performance
 * budget for large compilations, not a precise benchmark. Linux only: creating the files takes
 * seconds there, but about two minutes on Windows, where each new file is scanned for malware (the
 * scan itself took 0.4 s there).
 */
@EnabledOnOs(OS.LINUX)
class TreeSizeScannerBudgetTest {

    private static final int FOLDERS = 100;
    private static final int FILES_PER_FOLDER = 1000;

    /** Generous, so slow CI disks pass; a scan that holds lists or rereads files is far slower. */
    private static final Duration BUDGET = Duration.ofSeconds(10);

    @TempDir
    Path folder;

    @Test
    void scansAHundredThousandFilesWithinTheBudget() throws IOException {
        Path root = Files.createDirectories(folder.resolve("many"));
        for (int f = 0; f < FOLDERS; f++) {
            Path sub = Files.createDirectory(root.resolve("folder " + f));
            for (int i = 0; i < FILES_PER_FOLDER; i++) {
                Files.createFile(sub.resolve("file " + i + ".txt"));
            }
        }
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long usedBefore = runtime.totalMemory() - runtime.freeMemory();
        Instant start = Instant.now();

        TreeSize size = new TreeSizeScanner().scan(DataCompilation.empty().add(List.of(root)), new CancelToken());

        Duration took = Duration.between(start, Instant.now());
        long usedAfter = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("[budget] scanned " + size.files() + " files in " + took.toMillis() + " ms");
        assertEquals((long) FOLDERS * FILES_PER_FOLDER, size.files());
        assertEquals(FOLDERS + 1, size.folders());
        assertTrue(took.compareTo(BUDGET) < 0, "Scan took " + took);
        // Garbage may or may not have been collected, so this only catches a scan that keeps every path.
        assertTrue(usedAfter - usedBefore < 200L * 1024 * 1024, "Memory grew by " + (usedAfter - usedBefore));
    }
}
