// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import io.aerohelper.retroburn.platform.AppPaths;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;

/**
 * RetroBurn's folders on Windows: settings in the roaming profile ({@code %APPDATA%}), everything
 * else in the local one ({@code %LOCALAPPDATA%}). A variable that is unset, empty or not an absolute
 * path is ignored in favour of its default under the user's home. See {@code
 * docs/design/LLD-windows-platform.md} section 9.
 */
public final class WindowsAppPaths {

    private static final String APP_FOLDER = "RetroBurn";

    private WindowsAppPaths() {}

    /**
     * @param environment the process environment, normally {@link System#getenv()}
     * @param home the user's home folder
     */
    public static AppPaths fromEnvironment(Map<String, String> environment, Path home) {
        Path roaming = folder(environment, "APPDATA", home.resolve("AppData").resolve("Roaming"));
        Path local = folder(environment, "LOCALAPPDATA", home.resolve("AppData").resolve("Local"));
        Path profile = folder(environment, "USERPROFILE", home);
        Path app = local.resolve(APP_FOLDER);
        return new AppPaths(
                roaming.resolve(APP_FOLDER),
                app.resolve("data"),
                app.resolve("logs"),
                app.resolve("work"),
                profile.resolve("Videos").resolve(APP_FOLDER));
    }

    private static Path folder(Map<String, String> environment, String variable, Path fallback) {
        String value = WindowsEnvironment.get(environment, variable).orElse(null);
        if (value == null) {
            return fallback;
        }
        try {
            Path path = Path.of(value);
            return path.isAbsolute() ? path : fallback;
        } catch (InvalidPathException e) {
            return fallback;
        }
    }
}
