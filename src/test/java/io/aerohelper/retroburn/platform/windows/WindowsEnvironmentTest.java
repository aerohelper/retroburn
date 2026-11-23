// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WindowsEnvironmentTest {

    @Test
    void namesIgnoreCase() {
        Map<String, String> environment = Map.of("Path", "C:\\tools", "SYSTEMROOT", "C:\\Windows");

        assertEquals(Optional.of("C:\\tools"), WindowsEnvironment.get(environment, "PATH"));
        assertEquals(Optional.of("C:\\Windows"), WindowsEnvironment.get(environment, "SystemRoot"));
    }

    @Test
    void blankValuesCountAsUnset() {
        assertEquals(Optional.empty(), WindowsEnvironment.get(Map.of("APPDATA", " "), "APPDATA"));
        assertEquals(Optional.empty(), WindowsEnvironment.get(Map.of("appdata", ""), "APPDATA"));
        assertEquals(Optional.empty(), WindowsEnvironment.get(Map.of(), "APPDATA"));
    }
}
