// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import java.nio.file.Path;
import java.util.Optional;

/** Finds the executable file of an external tool. Each platform decides where to look. */
@FunctionalInterface
public interface ToolLocator {

    /** The tool's executable, or empty if it is not installed. */
    Optional<Path> locate(ExternalTool tool);
}
