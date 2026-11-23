// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import io.aerohelper.retroburn.platform.AppPaths;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;

/**
 * RetroBurn's folders on Linux, following the XDG Base Directory Specification
 * (https://specifications.freedesktop.org/basedir-spec/latest/): a variable that is unset, empty or
 * not an absolute path is ignored in favour of its default.
 */
public final class LinuxAppPaths {

    private static final String APP_FOLDER = "retroburn";

    private LinuxAppPaths() {}

    /**
     * @param environment the process environment, normally {@link System#getenv()}
     * @param home the user's home folder
     */
    public static AppPaths fromEnvironment(Map<String, String> environment, Path home) {
        Path config = xdg(environment, "XDG_CONFIG_HOME", home.resolve(".config"));
        Path data = xdg(environment, "XDG_DATA_HOME", home.resolve(".local/share"));
        Path state = xdg(environment, "XDG_STATE_HOME", home.resolve(".local/state"));
        Path cache = xdg(environment, "XDG_CACHE_HOME", home.resolve(".cache"));
        return new AppPaths(
                config.resolve(APP_FOLDER),
                data.resolve(APP_FOLDER),
                state.resolve(APP_FOLDER).resolve("logs"),
                cache.resolve(APP_FOLDER).resolve("work"),
                home.resolve("Videos").resolve("RetroBurn"));
    }

    private static Path xdg(Map<String, String> environment, String variable, Path fallback) {
        String value = environment.get(variable);
        if (value == null || value.isBlank()) {
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
