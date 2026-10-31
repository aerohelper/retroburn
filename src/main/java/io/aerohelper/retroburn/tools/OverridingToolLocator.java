// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Uses tool paths the user set in the settings, and the platform's normal search otherwise. */
public final class OverridingToolLocator implements ToolLocator {

    private final Supplier<Map<ExternalTool, Path>> overrides;
    private final ToolLocator fallback;

    /**
     * @param overrides the user's current tool paths; asked on every lookup so changed settings
     *     apply immediately
     * @param fallback the platform's normal search
     */
    public OverridingToolLocator(Supplier<Map<ExternalTool, Path>> overrides, ToolLocator fallback) {
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    /**
     * Returns the user's path when it points at an executable file; otherwise falls back, so a stale
     * setting does not disable a tool that is installed normally.
     */
    @Override
    public Optional<Path> locate(ExternalTool tool) {
        Path override = overrides.get().get(tool);
        if (override != null && Files.isRegularFile(override) && Files.isExecutable(override)) {
            return Optional.of(override.toAbsolutePath());
        }
        return fallback.locate(tool);
    }
}
