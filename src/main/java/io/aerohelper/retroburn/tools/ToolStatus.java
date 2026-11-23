// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Whether an external tool is usable, for the dependency check and feature gating.
 *
 * @param tool the tool
 * @param state found and working, missing, or found but not working
 * @param path where it was found, if anywhere
 * @param version its version, when it is working
 * @param detail technical detail when it is not working, otherwise empty
 */
public record ToolStatus(ExternalTool tool, State state, Optional<Path> path, Optional<String> version, String detail) {

    /** The possible outcomes of checking a tool. */
    public enum State {
        FOUND,
        MISSING,
        NOT_WORKING
    }

    public ToolStatus {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(version, "version");
        detail = Objects.requireNonNullElse(detail, "");
    }

    public static ToolStatus found(ExternalTool tool, Path path, String version) {
        return new ToolStatus(tool, State.FOUND, Optional.of(path), Optional.of(version), "");
    }

    public static ToolStatus missing(ExternalTool tool) {
        return new ToolStatus(tool, State.MISSING, Optional.empty(), Optional.empty(), "");
    }

    public static ToolStatus notWorking(ExternalTool tool, Path path, String detail) {
        return new ToolStatus(tool, State.NOT_WORKING, Optional.of(path), Optional.empty(), detail);
    }

    public boolean isUsable() {
        return state == State.FOUND;
    }
}
