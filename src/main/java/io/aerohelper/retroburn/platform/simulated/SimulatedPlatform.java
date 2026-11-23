// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.simulated;

import io.aerohelper.retroburn.common.OperatingSystem;
import io.aerohelper.retroburn.engine.AudioCdEngine;
import io.aerohelper.retroburn.engine.BurnEngine;
import io.aerohelper.retroburn.engine.DiscImageBuilder;
import io.aerohelper.retroburn.platform.AppPaths;
import io.aerohelper.retroburn.platform.DriveWatcher;
import io.aerohelper.retroburn.platform.PlatformServices;
import io.aerohelper.retroburn.platform.PollingDriveWatcher;
import io.aerohelper.retroburn.platform.PowerStatus;
import io.aerohelper.retroburn.platform.SleepInhibitor;
import io.aerohelper.retroburn.tools.Feature;
import io.aerohelper.retroburn.tools.Requirement;
import io.aerohelper.retroburn.tools.ToolLocator;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A platform with a fake drive, for UI development, demos and tests.
 *
 * <p>It never touches real hardware and needs no external tools: every feature is reported as
 * available. It reports the host OS so that OS-dependent information that is not about drives, such
 * as the credits list, stays correct.
 */
public final class SimulatedPlatform implements PlatformServices {

    private final OperatingSystem hostOperatingSystem;
    private final AppPaths appPaths;
    private final SimulatedBurnEngine engine = new SimulatedBurnEngine();
    private final SimulatedImageBuilder imageBuilder = new SimulatedImageBuilder();
    private final SimulatedAudioEngine audioEngine = new SimulatedAudioEngine();

    /**
     * @param hostOperatingSystem the OS the app is actually running on
     * @param appPaths where to keep settings, logs and work folders
     */
    public SimulatedPlatform(OperatingSystem hostOperatingSystem, AppPaths appPaths) {
        this.hostOperatingSystem = Objects.requireNonNull(hostOperatingSystem, "hostOperatingSystem");
        this.appPaths = Objects.requireNonNull(appPaths, "appPaths");
    }

    @Override
    public OperatingSystem operatingSystem() {
        return hostOperatingSystem;
    }

    @Override
    public String displayName() {
        return "Simulated drive";
    }

    @Override
    public AppPaths appPaths() {
        return appPaths;
    }

    @Override
    public ToolLocator toolLocator() {
        return tool -> Optional.empty();
    }

    @Override
    public Map<Feature, List<Requirement>> featureRequirements() {
        Map<Feature, List<Requirement>> requirements = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            requirements.put(feature, List.of());
        }
        return Map.copyOf(requirements);
    }

    @Override
    public BurnEngine burnEngine() {
        return engine;
    }

    @Override
    public AudioCdEngine audioCdEngine() {
        return audioEngine;
    }

    @Override
    public DiscImageBuilder discImageBuilder() {
        return imageBuilder;
    }

    @Override
    public DriveWatcher createDriveWatcher() {
        // The simulated drive never changes, so the signature is constant.
        return new PollingDriveWatcher(() -> "simulated", engine::listDrives, Duration.ofSeconds(2));
    }

    @Override
    public SleepInhibitor sleepInhibitor() {
        return SleepInhibitor.none();
    }

    @Override
    public PowerStatus powerStatus() {
        return () -> PowerStatus.PowerSource.UNKNOWN;
    }
}
