// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.tools.ExternalTool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsStoreTest {

    @TempDir
    Path folder;

    @Test
    void startsWithDefaultsWhenThereIsNoFile() {
        assertEquals(Settings.defaults(), new SettingsStore(folder).current());
        assertTrue(Settings.defaults().preventSleepWhileBurning());
    }

    @Test
    void savedSettingsSurviveARestart() {
        Settings changed = new Settings(
                false,
                true,
                Optional.of(folder.resolve("work")),
                Map.of(ExternalTool.XORRISO, folder.resolve("bin/xorriso")),
                false,
                false,
                new Settings.Audio(false, true),
                new Settings.Mp3("FLAT", false, false, "VBR_2", true),
                new Settings.Video("NTSC", 10, "y2k", "NEXT"),
                new Settings.Design("LETTER", true));
        SettingsStore store = new SettingsStore(folder);

        store.save(changed);

        assertEquals(changed, store.current());
        assertEquals(changed, new SettingsStore(folder).current());
        assertTrue(Files.isRegularFile(folder.resolve(SettingsStore.FILE_NAME)));
    }

    @Test
    void invalidValuesFallBackToDefaultsAndUnknownKeysAreIgnored() throws IOException {
        Files.writeString(folder.resolve(SettingsStore.FILE_NAME), """
                schemaVersion=99
                burn.preventSleep=maybe
                jobs.keepTemporaryFiles=TRUE
                jobs.workDirectory=
                tools.path.ffmpeg=/opt/ffmpeg/bin/ffmpeg
                some.future.setting=on
                """);

        Settings settings = new SettingsStore(folder).current();

        assertTrue(settings.preventSleepWhileBurning());
        assertTrue(settings.keepTemporaryFiles());
        assertEquals(Optional.empty(), settings.workDirectory());
        assertEquals(Map.of(ExternalTool.FFMPEG, Path.of("/opt/ffmpeg/bin/ffmpeg")), settings.toolPaths());
    }
}
