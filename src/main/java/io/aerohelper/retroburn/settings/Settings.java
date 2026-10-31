// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.settings;

import io.aerohelper.retroburn.tools.ExternalTool;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The user's settings. Immutable: changing a setting creates a new instance.
 *
 * <p>More settings (theme, sounds, verify, TV standard, ...) are added with the features that use
 * them. The file format is described in {@code docs/DATA_MODEL.md}.
 *
 * @param preventSleepWhileBurning keep the computer awake while writing a disc
 * @param keepTemporaryFiles keep job work folders after success or cancel
 * @param workDirectory folder for job work folders instead of the default
 * @param toolPaths user-chosen executables for external tools
 */
public record Settings(
        boolean preventSleepWhileBurning,
        boolean keepTemporaryFiles,
        Optional<Path> workDirectory,
        Map<ExternalTool, Path> toolPaths) {

    public Settings {
        Objects.requireNonNull(workDirectory, "workDirectory");
        toolPaths = Map.copyOf(toolPaths);
    }

    /** Settings for a first start. */
    public static Settings defaults() {
        return new Settings(true, false, Optional.empty(), Map.of());
    }
}
