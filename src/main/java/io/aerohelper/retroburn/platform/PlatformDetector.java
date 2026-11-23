// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform;

import io.aerohelper.retroburn.common.OperatingSystem;
import java.util.Locale;

/**
 * Works out which operating system the app is running on.
 *
 * <p>This is the only place outside the {@code platform.<os>} packages that looks at the OS, so the
 * rest of the code never needs {@code if (windows)} checks.
 */
public final class PlatformDetector {

    private PlatformDetector() {}

    /** The OS family of the running JVM. */
    public static OperatingSystem currentOperatingSystem() {
        return fromOsName(System.getProperty("os.name", ""));
    }

    /**
     * Maps a value of the {@code os.name} system property to an OS family.
     *
     * <p>The JDK reports names such as "Linux" and "Windows 11"; see the {@code os.name} entry in
     * {@link System#getProperties()}.
     */
    static OperatingSystem fromOsName(String osName) {
        String name = osName.toLowerCase(Locale.ROOT);
        if (name.startsWith("windows")) {
            return OperatingSystem.WINDOWS;
        }
        if (name.startsWith("linux")) {
            return OperatingSystem.LINUX;
        }
        return OperatingSystem.OTHER;
    }
}
