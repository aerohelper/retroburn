// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.simulated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperatingSystem;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.ImageOptions;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.platform.AppPaths;
import io.aerohelper.retroburn.platform.PowerStatus.PowerSource;
import io.aerohelper.retroburn.tools.ExternalTool;
import io.aerohelper.retroburn.tools.Feature;
import io.aerohelper.retroburn.tools.FeatureGate;
import io.aerohelper.retroburn.tools.ToolReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimulatedPlatformTest {

    @TempDir
    Path folder;

    @Test
    void reportsTheHostOperatingSystemSoCreditsStayCorrect() {
        assertEquals(OperatingSystem.WINDOWS, platform(OperatingSystem.WINDOWS).operatingSystem());
        assertFalse(platform(OperatingSystem.LINUX).displayName().isBlank());
    }

    @Test
    void offersOneUsbDriveWithABlankDvd() {
        SimulatedPlatform platform = platform(OperatingSystem.LINUX);

        List<DriveInfo> drives = platform.burnEngine().listDrives();

        assertEquals(1, drives.size());
        assertEquals(ConnectionType.USB, drives.getFirst().connection());
        assertEquals(
                MediaKind.DVD_PLUS_RW,
                platform.burnEngine().readMedia(drives.getFirst().id()).kind());
        assertEquals(MediaInfo.noMedia(), platform.burnEngine().readMedia(new DriveId("other")));
    }

    @Test
    void needsNoToolsSoEveryFeatureIsAvailable() {
        SimulatedPlatform platform = platform(OperatingSystem.LINUX);
        ToolReport nothingInstalled = new ToolReport(Map.of(), Set.of());

        FeatureGate.evaluate(nothingInstalled, platform.featureRequirements())
                .values()
                .forEach(availability -> assertTrue(availability.isAvailable(), availability.toString()));
        assertTrue(platform.toolLocator().locate(ExternalTool.XORRISO).isEmpty());
        assertTrue(platform.featureRequirements().containsKey(Feature.BURNING));
    }

    @Test
    void pretendsToBuildAnImageWithProgress() throws IOException {
        Path target = folder.resolve("fake.iso");
        List<BurnEvent> events = new ArrayList<>();

        platform(OperatingSystem.LINUX)
                .discImageBuilder()
                .buildDataImage(
                        DiscTree.of(List.of(new DiscEntry("/a.txt", folder.resolve("a.txt")))),
                        new ImageOptions("SIMULATED"),
                        target,
                        events::add,
                        new CancelToken());

        assertEquals(BurnEvent.Progress.of(1.0), events.getLast());
        assertTrue(Files.readString(target).contains("/a.txt"));
    }

    @Test
    void neverBlocksSleepOrReportsBattery() {
        SimulatedPlatform platform = platform(OperatingSystem.LINUX);

        platform.sleepInhibitor().acquire("test").close();

        assertEquals(PowerSource.UNKNOWN, platform.powerStatus().current());
    }

    private SimulatedPlatform platform(OperatingSystem host) {
        return new SimulatedPlatform(
                host,
                new AppPaths(
                        folder.resolve("settings"),
                        folder.resolve("data"),
                        folder.resolve("logs"),
                        folder.resolve("work"),
                        folder.resolve("reels")));
    }
}
