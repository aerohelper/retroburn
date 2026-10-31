// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.platform.AppPaths;
import io.aerohelper.retroburn.platform.SleepInhibitionUnavailableException;
import io.aerohelper.retroburn.platform.linux.XorrisoDeviceListParser.ListedDrive;
import io.aerohelper.retroburn.tools.Feature;
import io.aerohelper.retroburn.tools.ProcessRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class LinuxPlatformTest {

    @TempDir
    Path folder;

    @Test
    void followsTheXdgDefaults() {
        Path home = folder.resolve("home");

        AppPaths paths = LinuxAppPaths.fromEnvironment(Map.of(), home);

        assertEquals(home.resolve(".config/retroburn"), paths.settings());
        assertEquals(home.resolve(".local/share/retroburn"), paths.data());
        assertEquals(home.resolve(".local/state/retroburn/logs"), paths.logs());
        assertEquals(home.resolve(".cache/retroburn/work"), paths.work());
        assertEquals(home.resolve("Videos/RetroBurn"), paths.reels());
    }

    @Test
    void honoursAbsoluteXdgVariablesAndIgnoresRelativeOnes() {
        Path home = folder.resolve("home");
        Path config = folder.resolve("custom-config").toAbsolutePath();

        AppPaths paths = LinuxAppPaths.fromEnvironment(
                Map.of("XDG_CONFIG_HOME", config.toString(), "XDG_CACHE_HOME", "relative/cache", "XDG_DATA_HOME", ""),
                home);

        assertEquals(config.resolve("retroburn"), paths.settings());
        assertEquals(home.resolve(".cache/retroburn/work"), paths.work());
        assertEquals(home.resolve(".local/share/retroburn"), paths.data());
    }

    @Test
    void marksDrivesTheUserCannotOpenAsNotAccessible() {
        XorrisoEngine engine =
                new XorrisoEngine(new ProcessRunner(), tool -> Optional.empty(), new SysfsOpticalDrives(folder));

        List<DriveInfo> drives = engine.combine(
                List.of("sr0", "sr1"), List.of(new ListedDrive("/dev/sr0", "HL-DT-ST", "DVDRAM GP65NB60")));

        assertEquals(2, drives.size());
        assertTrue(drives.get(0).accessible());
        assertEquals("HL-DT-ST DVDRAM GP65NB60", drives.get(0).displayName());
        assertFalse(drives.get(1).accessible());
        assertEquals("/dev/sr1", drives.get(1).displayName());
    }

    @Test
    void keepsDrivesXorrisoFindsUnderOtherNames() {
        XorrisoEngine engine =
                new XorrisoEngine(new ProcessRunner(), tool -> Optional.empty(), new SysfsOpticalDrives(folder));

        List<DriveInfo> drives = engine.combine(List.of(), List.of(new ListedDrive("/dev/sg3", "ASUS", "SDRW")));

        assertEquals(1, drives.size());
        assertEquals(ConnectionType.UNKNOWN, drives.getFirst().connection());
    }

    @Test
    void requiresTheRightToolsForEachFeature() {
        LinuxPlatform platform =
                new LinuxPlatform(LinuxAppPaths.fromEnvironment(Map.of(), folder), new ProcessRunner(), "", Map::of);

        assertEquals(Feature.values().length, platform.featureRequirements().size());
        assertTrue(platform.featureRequirements().get(Feature.VIDEO_DVD_IMAGE).size()
                > platform.featureRequirements().get(Feature.VIDEO_DVD).size());
    }

    @Test
    void sleepInhibitionIsUnavailableWithoutSystemdInhibit() {
        SystemdSleepInhibitor inhibitor = new SystemdSleepInhibitor(new ProcessRunner(), Optional.empty());

        assertThrows(SleepInhibitionUnavailableException.class, () -> inhibitor.acquire("Writing a disc"));
    }

    /** A stand-in for systemd-inhibit that is refused, as logind does outside a local session. */
    @Test
    @EnabledOnOs(OS.LINUX)
    void sleepInhibitionIsUnavailableWhenRefused() throws IOException {
        Path refusing = script("refusing", "echo 'Failed to inhibit: Access denied' >&2\nexit 1\n");
        SystemdSleepInhibitor inhibitor = new SystemdSleepInhibitor(new ProcessRunner(), Optional.of(refusing));

        SleepInhibitionUnavailableException error =
                assertThrows(SleepInhibitionUnavailableException.class, () -> inhibitor.acquire("Writing a disc"));
        assertTrue(error.technicalDetail().contains("Access denied"));
    }

    /** A stand-in that, like systemd-inhibit, runs its last argument ("cat") until stdin closes. */
    @Test
    @EnabledOnOs(OS.LINUX)
    void heldInhibitionEndsTheHelperWhenClosed() throws IOException {
        Path holding = script("holding", "exec \"${@: -1}\"\n");
        SystemdSleepInhibitor inhibitor = new SystemdSleepInhibitor(new ProcessRunner(), Optional.of(holding));

        try (var inhibition = inhibitor.acquire("Writing a disc")) {
            assertTrue(processExists("cat"));
        }
    }

    private Path script(String name, String body) throws IOException {
        Path script = folder.resolve(name);
        Files.writeString(script, "#!/bin/bash\n" + body);
        script.toFile().setExecutable(true);
        return script;
    }

    private static boolean processExists(String command) {
        return ProcessHandle.current()
                .descendants()
                .anyMatch(process -> process.info()
                        .command()
                        .map(c -> c.endsWith("/" + command))
                        .orElse(false));
    }
}
