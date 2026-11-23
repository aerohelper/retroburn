// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One item to put on a disc: where it appears on the disc and which file or folder supplies it. A
 * folder brings its whole subtree.
 *
 * @param discPath absolute path on the disc, for example {@code /Photos/2026}; uses {@code /} as
 *     separator on every OS
 * @param source the file or folder on the computer
 */
public record DiscEntry(String discPath, Path source) {

    public DiscEntry {
        Objects.requireNonNull(source, "source");
        validateDiscPath(discPath);
    }

    private static void validateDiscPath(String discPath) {
        Objects.requireNonNull(discPath, "discPath");
        if (!discPath.startsWith("/") || discPath.length() < 2) {
            throw new IllegalArgumentException("Disc path must start with / and name an item: " + discPath);
        }
        // Burning tools receive the tree as line-based lists, so a line break would split an entry.
        if (discPath.indexOf('\n') >= 0 || discPath.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Disc path must not contain line breaks: " + discPath);
        }
        for (String segment : discPath.substring(1).split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Disc path has an empty, '.' or '..' segment: " + discPath);
            }
        }
    }
}
