// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

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
import io.aerohelper.retroburn.tools.ExternalTool;
import io.aerohelper.retroburn.tools.Feature;
import io.aerohelper.retroburn.tools.OverridingToolLocator;
import io.aerohelper.retroburn.tools.PathSearchToolLocator;
import io.aerohelper.retroburn.tools.ProcessRunner;
import io.aerohelper.retroburn.tools.Requirement;
import io.aerohelper.retroburn.tools.Requirement.FfmpegEncoderPresent;
import io.aerohelper.retroburn.tools.Requirement.ToolPresent;
import io.aerohelper.retroburn.tools.ToolLocator;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * RetroBurn on Linux: GNU xorriso for drives and images, sysfs for hot-plug, USB and power, and
 * systemd-inhibit for keeping the computer awake. Tools come from the distribution's packages.
 */
public final class LinuxPlatform implements PlatformServices {

    private static final Duration DRIVE_POLL_INTERVAL = Duration.ofSeconds(2);

    private final AppPaths appPaths;
    private final ToolLocator toolLocator;
    private final SysfsOpticalDrives sysfs;
    private final XorrisoEngine engine;
    private final CdrskinAudioEngine audioEngine;
    private final XorrisoImageBuilder imageBuilder;
    private final SleepInhibitor sleepInhibitor;
    private final PowerStatus powerStatus;

    /**
     * @param appPaths folders from {@link LinuxAppPaths#fromEnvironment}
     * @param runner the shared process runner
     * @param pathVariable value of the {@code PATH} environment variable
     * @param toolOverrides the user's current tool path settings
     */
    public LinuxPlatform(
            AppPaths appPaths,
            ProcessRunner runner,
            String pathVariable,
            Supplier<Map<ExternalTool, Path>> toolOverrides) {
        this.appPaths = appPaths;
        PathSearchToolLocator pathSearch = new PathSearchToolLocator(
                PathSearchToolLocator.foldersFromPathVariable(pathVariable, File.pathSeparator), List.of(""));
        this.toolLocator = new OverridingToolLocator(toolOverrides, pathSearch);
        this.sysfs = new SysfsOpticalDrives(Path.of("/sys/block"));
        this.engine = new XorrisoEngine(runner, toolLocator, sysfs);
        this.audioEngine = new CdrskinAudioEngine(runner, toolLocator);
        this.imageBuilder = new XorrisoImageBuilder(runner, toolLocator, appPaths.work());
        this.sleepInhibitor = new SystemdSleepInhibitor(runner, pathSearch.locateExecutable("systemd-inhibit"));
        this.powerStatus = new SysfsPowerStatus(Path.of("/sys/class/power_supply"));
    }

    @Override
    public OperatingSystem operatingSystem() {
        return OperatingSystem.LINUX;
    }

    @Override
    public String displayName() {
        return "Linux";
    }

    @Override
    public AppPaths appPaths() {
        return appPaths;
    }

    @Override
    public ToolLocator toolLocator() {
        return toolLocator;
    }

    /** See {@code docs/design/LLD-tools.md} section 5. */
    @Override
    public Map<Feature, List<Requirement>> featureRequirements() {
        Requirement xorriso = new ToolPresent(ExternalTool.XORRISO);
        Requirement ffmpeg = new ToolPresent(ExternalTool.FFMPEG);
        Requirement ffprobe = new ToolPresent(ExternalTool.FFPROBE);
        List<Requirement> videoDvd =
                List.of(ffmpeg, ffprobe, new ToolPresent(ExternalTool.DVDAUTHOR), new ToolPresent(ExternalTool.SPUMUX));
        return Map.of(
                Feature.BURNING, List.of(xorriso),
                Feature.ISO_CREATION, List.of(xorriso),
                Feature.VERIFY, List.of(xorriso),
                Feature.AUDIO_CD, List.of(new ToolPresent(ExternalTool.CDRSKIN), ffmpeg, ffprobe),
                Feature.MP3_TRANSCODING, List.of(ffmpeg, new FfmpegEncoderPresent("libmp3lame")),
                Feature.VIDEO_DVD, videoDvd,
                Feature.VIDEO_DVD_IMAGE, concat(videoDvd, new ToolPresent(ExternalTool.GENISOIMAGE)),
                Feature.BURN_REEL, List.of(ffmpeg));
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
        return new PollingDriveWatcher(sysfs::deviceNames, engine::listDrives, DRIVE_POLL_INTERVAL);
    }

    @Override
    public SleepInhibitor sleepInhibitor() {
        return sleepInhibitor;
    }

    @Override
    public List<Path> fontFolders() {
        Path home = Path.of(System.getProperty("user.home"));
        return List.of(
                Path.of("/usr/share/fonts"),
                Path.of("/usr/local/share/fonts"),
                home.resolve(".local/share/fonts"),
                home.resolve(".fonts"));
    }

    @Override
    public PowerStatus powerStatus() {
        return powerStatus;
    }

    private static List<Requirement> concat(List<Requirement> first, Requirement extra) {
        return Stream.concat(first.stream(), Stream.of(extra)).toList();
    }
}
