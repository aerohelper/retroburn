// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform;

import io.aerohelper.retroburn.common.OperatingSystem;
import io.aerohelper.retroburn.engine.BurnEngine;
import io.aerohelper.retroburn.engine.DiscImageBuilder;
import io.aerohelper.retroburn.tools.Feature;
import io.aerohelper.retroburn.tools.Requirement;
import io.aerohelper.retroburn.tools.ToolLocator;
import java.util.List;
import java.util.Map;

/**
 * Everything RetroBurn needs from the operating system, bundled behind one interface.
 *
 * <p>Each supported OS provides one implementation in its own {@code platform.<os>} package, and
 * only those packages contain OS-specific code. See {@code docs/design/LLD-engine-and-platform.md}.
 */
public interface PlatformServices {

    /** The OS family this platform runs on. */
    OperatingSystem operatingSystem();

    /** A short name for logs and the About screen, for example "Linux". */
    String displayName();

    /** Where RetroBurn keeps its files. */
    AppPaths appPaths();

    /** Where external tools are found (before user overrides from the settings). */
    ToolLocator toolLocator();

    /** What each feature needs on this OS; features missing from the map are not supported here. */
    Map<Feature, List<Requirement>> featureRequirements();

    BurnEngine burnEngine();

    DiscImageBuilder discImageBuilder();

    /** Creates a new hot-plug watcher; the caller starts and closes it. */
    DriveWatcher createDriveWatcher();

    SleepInhibitor sleepInhibitor();

    PowerStatus powerStatus();
}
