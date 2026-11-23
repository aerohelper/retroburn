// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.engine.DiscTree;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds up the sizes of a compilation's sources by walking them, without keeping a list of files, so
 * very large compilations cost no extra memory. Call on a background thread; it checks the cancel
 * token at every entry.
 */
public final class TreeSizeScanner {

    /** Unreadable paths kept for the message; the rest are only counted. */
    public static final int UNREADABLE_LIMIT = 20;

    static final long BLOCK_SIZE = 2048;

    /** @throws io.aerohelper.retroburn.common.OperationCancelledException when cancelled */
    public TreeSize scan(DiscTree tree, CancelToken cancel) {
        Totals totals = new Totals();
        tree.entries().forEach(entry -> walk(entry.source(), totals, cancel));
        return new TreeSize(
                totals.bytes, totals.blocks, totals.files, totals.folders, totals.unreadable, totals.unreadableCount);
    }

    private static void walk(Path source, Totals totals, CancelToken cancel) {
        try {
            // Links are not followed: they go on the disc as links, the way the image builders store them.
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path folder, BasicFileAttributes attributes) {
                    cancel.throwIfCancelled();
                    totals.folders++;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    cancel.throwIfCancelled();
                    totals.files++;
                    if (attributes.isRegularFile()) {
                        totals.bytes += attributes.size();
                        totals.blocks += Math.ceilDiv(attributes.size(), BLOCK_SIZE);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException error) {
                    cancel.throwIfCancelled();
                    totals.unreadable(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not walk " + source, e);
        }
    }

    private static final class Totals {
        long bytes;
        long blocks;
        long files;
        long folders;
        long unreadableCount;
        final List<Path> unreadable = new ArrayList<>();

        void unreadable(Path path) {
            unreadableCount++;
            if (unreadable.size() < UNREADABLE_LIMIT) {
                unreadable.add(path);
            }
        }
    }
}
