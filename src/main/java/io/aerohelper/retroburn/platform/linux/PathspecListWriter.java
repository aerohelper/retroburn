// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import io.aerohelper.retroburn.common.UnsupportedInputException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.engine.DiscTree;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Writes a {@link DiscTree} as an xorriso {@code -path_list} file for {@code -pathspecs on}.
 *
 * <p>Each line is {@code disc_path=source_path}. In "on" mode the xorriso manual says {@code '='}
 * must be escaped as {@code \=} in the disc path only and {@code '\'} must not be escaped at all,
 * which means a disc path cannot end with {@code '\'} and {@code "\="} in a name would be read as an
 * escaped {@code '='}; such names are rejected. Entries are streamed, never collected.
 */
final class PathspecListWriter {

    private PathspecListWriter() {}

    /**
     * @return the number of entries written
     * @throws UnsupportedInputException if a name cannot be expressed in the list format
     */
    static long write(DiscTree tree, Path listFile) {
        long count = 0;
        try (Stream<DiscEntry> entries = tree.entries();
                BufferedWriter writer = Files.newBufferedWriter(listFile, StandardCharsets.UTF_8)) {
            Iterator<DiscEntry> iterator = entries.iterator();
            while (iterator.hasNext()) {
                writer.write(line(iterator.next()));
                writer.newLine();
                count++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + listFile, e);
        }
        return count;
    }

    static String line(DiscEntry entry) {
        String discPath = entry.discPath();
        if (discPath.endsWith("\\") || discPath.contains("\\=")) {
            throw unsupported(discPath);
        }
        String source = entry.source().toAbsolutePath().toString();
        if (source.indexOf('\n') >= 0 || source.indexOf('\r') >= 0) {
            throw unsupported(source);
        }
        return discPath.replace("=", "\\=") + "=" + source;
    }

    private static UnsupportedInputException unsupported(String name) {
        return new UnsupportedInputException(
                UserMessage.of("error.unsupportedName", name), "Cannot express in an xorriso pathspec: " + name);
    }
}
