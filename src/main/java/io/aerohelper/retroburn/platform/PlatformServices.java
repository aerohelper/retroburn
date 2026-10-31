// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform;

import io.aerohelper.retroburn.common.OperatingSystem;

/**
 * Everything RetroBurn needs from the operating system, bundled behind one interface.
 *
 * <p>Each supported OS provides one implementation in its own {@code platform.<os>} package, and
 * only those packages contain OS-specific code. Drive access, burning engines, standard folders,
 * hot-plug detection, sleep inhibition and power status are added to this interface as those
 * features are built; see {@code docs/architecture/HLD.md} section 4.1.
 */
public interface PlatformServices {

    /** The OS family this platform runs on. */
    OperatingSystem operatingSystem();

    /** A short name for logs and the About screen, for example "Linux". */
    String displayName();
}
