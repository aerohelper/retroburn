// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.pipeline;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.WorkFolderFullException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkFoldersAndDiskSpaceTest {

    @TempDir
    Path root;

    @Test
    void createsDistinctJobFolders() {
        WorkFolders folders = new WorkFolders(root);

        Path first = folders.createJobFolder();
        Path second = folders.createJobFolder();

        assertNotEquals(first, second);
        assertTrue(first.getFileName().toString().startsWith("job-"));
        assertTrue(Files.isDirectory(second));
    }

    @Test
    void deletesAJobFolderWithNestedContent() throws IOException {
        WorkFolders folders = new WorkFolders(root);
        Path job = folders.createJobFolder();
        Files.createDirectories(job.resolve("a/b"));
        Files.writeString(job.resolve("a/b/file.wav"), "data");

        folders.delete(job);

        assertFalse(Files.exists(job));
    }

    @Test
    void neverDeletesFoldersItDidNotCreate() throws IOException {
        Path foreign = Files.createDirectory(root.resolve("my-photos"));
        WorkFolders folders = new WorkFolders(root);

        folders.delete(foreign);
        folders.deleteLeftovers();

        assertTrue(Files.isDirectory(foreign));
    }

    @Test
    void removesLeftoverJobFoldersAtStartUp() throws IOException {
        WorkFolders folders = new WorkFolders(root);
        Path leftover = folders.createJobFolder();
        Files.writeString(leftover.resolve("partial.iso"), "x");

        folders.deleteLeftovers();

        assertEquals(List.of(), Files.list(root).toList());
    }

    @Test
    void toleratesAMissingWorkRoot() {
        assertDoesNotThrow(() -> new WorkFolders(root.resolve("not-there")).deleteLeftovers());
    }

    @Test
    void acceptsAJobThatFitsAndRejectsOneThatDoesNot() {
        DiskSpaceCheck check = new DiskSpaceCheck(folder -> 10L * 1024 * 1024);

        assertDoesNotThrow(() -> check.requireFreeSpace(root, 10L * 1024 * 1024));
        WorkFolderFullException error =
                assertThrows(WorkFolderFullException.class, () -> check.requireFreeSpace(root, 30L * 1024 * 1024));
        assertEquals("error.notEnoughSpace", error.userMessage().key());
        assertEquals(List.of(root.toString(), 30L, 10L), error.userMessage().arguments());
    }

    @Test
    void readsTheRealFreeSpace() {
        assertDoesNotThrow(() -> new DiskSpaceCheck().requireFreeSpace(root, 1));
    }
}
