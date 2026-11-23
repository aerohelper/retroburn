// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.common.UnsupportedInputException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.engine.DiscTree;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The items of a data disc: one entry at the disc's root per file or folder the user added; a folder
 * brings its whole subtree. Immutable: every change returns a new compilation.
 *
 * <p>Names are compared without regard to case, because Windows treats {@code Photos} and {@code
 * photos} as the same name; a clash gets " (2)", " (3)", ... before the extension.
 */
public final class DataCompilation implements DiscTree {

    private static final DataCompilation EMPTY = new DataCompilation(List.of());

    private final List<DiscEntry> entries;

    private DataCompilation(List<DiscEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static DataCompilation empty() {
        return EMPTY;
    }

    /**
     * Adds files or folders at the root of the disc, in the given order.
     *
     * @throws UnsupportedInputException if a source has no usable name (a drive root, or a name with a
     *     line break); nothing is added then
     */
    public DataCompilation add(Collection<Path> sources) {
        List<DiscEntry> added = new ArrayList<>(entries);
        Set<String> taken = new HashSet<>();
        entries.forEach(entry -> taken.add(key(entry.discPath())));
        for (Path source : sources) {
            String name = nameOf(source);
            String unique = unique(name, taken);
            taken.add(key("/" + unique));
            added.add(new DiscEntry("/" + unique, source.toAbsolutePath()));
        }
        return new DataCompilation(added);
    }

    /** Removes the entries with these disc paths. */
    public DataCompilation remove(Collection<String> discPaths) {
        Set<String> doomed = Set.copyOf(discPaths);
        return new DataCompilation(entries.stream()
                .filter(entry -> !doomed.contains(entry.discPath()))
                .toList());
    }

    public List<DiscEntry> list() {
        return entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public Stream<DiscEntry> entries() {
        return entries.stream();
    }

    private static String nameOf(Path source) {
        Path fileName = source.toAbsolutePath().normalize().getFileName();
        String name = fileName == null ? "" : fileName.toString();
        if (name.isBlank() || name.contains("\n") || name.contains("\r") || name.contains("/")) {
            throw new UnsupportedInputException(UserMessage.of("error.unsupportedName", source), source.toString());
        }
        return name;
    }

    private static String unique(String name, Set<String> taken) {
        if (!taken.contains(key("/" + name))) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        // A leading dot is part of the name (".config"), not an extension.
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int number = 2; ; number++) {
            String candidate = base + " (" + number + ")" + extension;
            if (!taken.contains(key("/" + candidate))) {
                return candidate;
            }
        }
    }

    private static String key(String discPath) {
        return discPath.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DataCompilation compilation && compilation.entries.equals(entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "DataCompilation" + entries;
    }
}
