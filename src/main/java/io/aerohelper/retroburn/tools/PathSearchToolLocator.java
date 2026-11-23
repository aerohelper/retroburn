// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Looks for a tool's executable in a list of folders, trying each file extension in turn.
 *
 * <p>The folders and extensions come from the platform: the {@code PATH} folders and no extension on
 * Linux, the bundled tools folder and {@code .exe} on Windows. That keeps this class free of OS
 * checks.
 */
public final class PathSearchToolLocator implements ToolLocator {

    private final List<Path> folders;
    private final List<String> extensions;

    /**
     * @param folders folders to search, in order
     * @param extensions file extensions to try, in order; use {@code ""} for none
     */
    public PathSearchToolLocator(List<Path> folders, List<String> extensions) {
        this.folders = List.copyOf(folders);
        this.extensions = List.copyOf(extensions);
    }

    /**
     * Splits the value of a {@code PATH}-style variable into folders, skipping empty and malformed
     * entries.
     *
     * @param value the variable's value, possibly null
     * @param separator the platform's list separator, {@link java.io.File#pathSeparator}
     */
    public static List<Path> foldersFromPathVariable(String value, String separator) {
        List<Path> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (String part : value.split(Pattern.quote(separator))) {
            if (part.isBlank()) {
                continue;
            }
            try {
                result.add(Path.of(part));
            } catch (InvalidPathException e) {
                // A broken PATH entry should not hide tools in the other folders.
            }
        }
        return result;
    }

    @Override
    public Optional<Path> locate(ExternalTool tool) {
        return locateExecutable(tool.executableName());
    }

    /**
     * Finds any executable by base name, for OS helpers that are not user-facing tools (such as
     * {@code systemd-inhibit}).
     */
    public Optional<Path> locateExecutable(String baseName) {
        for (Path folder : folders) {
            for (String extension : extensions) {
                Path candidate = folder.resolve(baseName + extension);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return Optional.of(candidate.toAbsolutePath());
                }
            }
        }
        return Optional.empty();
    }
}
