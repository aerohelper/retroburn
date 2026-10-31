// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Where RetroBurn keeps its files on this computer. Each platform creates one following its OS
 * conventions; no other code hard-codes these locations.
 *
 * @param settings settings file folder
 * @param data burn sessions and other app data
 * @param logs rotating log files
 * @param work per-job temporary folders
 * @param reels where burn reel videos are saved
 */
public record AppPaths(Path settings, Path data, Path logs, Path work, Path reels) {

    public AppPaths {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(logs, "logs");
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(reels, "reels");
    }

    /**
     * Creates the folders RetroBurn writes to on its own. The reels folder is created only when a
     * reel is saved, so the user's Videos folder is not touched otherwise.
     *
     * @throws UncheckedIOException if a folder cannot be created
     */
    public void ensureExists() {
        for (Path folder : List.of(settings, data, logs, work)) {
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not create " + folder, e);
            }
        }
    }
}
