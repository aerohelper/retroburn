// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.aerohelper.retroburn.platform.AppPaths;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowsAppPathsTest {

    @TempDir
    Path home;

    @Test
    void usesTheProfileFoldersFromTheEnvironment() {
        Path roaming = home.resolve("Roaming");
        Path local = home.resolve("Local");
        Path profile = home.resolve("Profile");

        AppPaths paths = WindowsAppPaths.fromEnvironment(
                Map.of(
                        "APPDATA",
                        roaming.toString(),
                        "LOCALAPPDATA",
                        local.toString(),
                        "USERPROFILE",
                        profile.toString()),
                home);

        assertEquals(
                new AppPaths(
                        roaming.resolve("RetroBurn"),
                        local.resolve("RetroBurn").resolve("data"),
                        local.resolve("RetroBurn").resolve("logs"),
                        local.resolve("RetroBurn").resolve("work"),
                        profile.resolve("Videos").resolve("RetroBurn")),
                paths);
    }

    @Test
    void fallsBackToTheHomeFolderWhenVariablesAreMissingEmptyOrRelative() {
        AppPaths paths = WindowsAppPaths.fromEnvironment(Map.of("APPDATA", "", "LOCALAPPDATA", "relative"), home);

        assertEquals(home.resolve("AppData").resolve("Roaming").resolve("RetroBurn"), paths.settings());
        assertEquals(
                home.resolve("AppData").resolve("Local").resolve("RetroBurn").resolve("work"), paths.work());
        assertEquals(home.resolve("Videos").resolve("RetroBurn"), paths.reels());
    }
}
