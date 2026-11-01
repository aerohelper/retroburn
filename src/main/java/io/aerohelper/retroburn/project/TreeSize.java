// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import java.nio.file.Path;
import java.util.List;

/**
 * What a scan of a compilation's sources found.
 *
 * @param bytes total size of all files
 * @param blocks total 2048-byte blocks the files' contents take on a disc
 * @param files files, including symbolic links (stored as links, not followed)
 * @param folders folders, including the ones the user added
 * @param unreadable paths that could not be read, at most {@link TreeSizeScanner#UNREADABLE_LIMIT}
 * @param unreadableCount how many paths could not be read in all
 */
public record TreeSize(long bytes, long blocks, long files, long folders, List<Path> unreadable, long unreadableCount) {

    public TreeSize {
        unreadable = List.copyOf(unreadable);
    }

    public static TreeSize empty() {
        return new TreeSize(0, 0, 0, 0, List.of(), 0);
    }

    /** Entries the disc's directories must describe. */
    public long entries() {
        return files + folders;
    }
}
