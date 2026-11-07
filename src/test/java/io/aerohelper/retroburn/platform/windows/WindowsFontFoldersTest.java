// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WindowsFontFoldersTest {

    @Test
    void systemAndPerUserFontFolders() {
        List<Path> folders = WindowsPlatform.fontFolders(Map.of("SYSTEMROOT", "C:/Windows", "LocalAppData", "C:/u"));
        assertEquals(
                List.of(Path.of("C:/Windows", "Fonts"), Path.of("C:/u", "Microsoft", "Windows", "Fonts")), folders);
    }

    @Test
    void missingVariablesAreLeftOut() {
        assertEquals(List.of(), WindowsPlatform.fontFolders(Map.of()));
    }
}
