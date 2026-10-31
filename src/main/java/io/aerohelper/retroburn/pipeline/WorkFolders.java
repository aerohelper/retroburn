// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Creates and removes the per-job temporary folders under the work root.
 *
 * <p>Only folders named {@code job-...} are ever deleted, so a mistaken work-root setting cannot
 * delete unrelated files.
 */
public final class WorkFolders {

    static final String PREFIX = "job-";

    private static final Logger LOG = Logger.getLogger(WorkFolders.class.getName());
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path root;

    public WorkFolders(Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    /** Creates a new, empty job folder such as {@code job-20261031-142501-1a2b3c4d}. */
    public Path createJobFolder() {
        String name = PREFIX
                + LocalDateTime.now().format(TIMESTAMP)
                + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        try {
            return Files.createDirectories(root.resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create work folder in " + root, e);
        }
    }

    /** Deletes a job folder and everything in it; failures are logged, not thrown. */
    public void delete(Path jobFolder) {
        if (!jobFolder.getFileName().toString().startsWith(PREFIX) || !Files.exists(jobFolder)) {
            return;
        }
        try {
            Files.walkFileTree(jobFolder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path folder, IOException error) throws IOException {
                    Files.delete(folder);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not delete work folder " + jobFolder, e);
        }
    }

    /**
     * Deletes job folders left by earlier runs (kept after failures or a crash). Call at start-up,
     * before any job runs.
     */
    public void deleteLeftovers() {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> children = Files.list(root)) {
            children.filter(Files::isDirectory)
                    .filter(folder -> folder.getFileName().toString().startsWith(PREFIX))
                    .forEach(this::delete);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not list work folder " + root, e);
        }
    }
}
