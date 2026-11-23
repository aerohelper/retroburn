// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import io.aerohelper.retroburn.common.OperatingSystem;
import io.aerohelper.retroburn.engine.AudioCdEngine;
import io.aerohelper.retroburn.engine.BurnEngine;
import io.aerohelper.retroburn.engine.DiscImageBuilder;
import io.aerohelper.retroburn.platform.AppPaths;
import io.aerohelper.retroburn.platform.DriveWatcher;
import io.aerohelper.retroburn.platform.PlatformServices;
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
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * RetroBurn on Windows: the Image Mastering API v2 (part of Windows) for drives, burning and images,
 * reached through a PowerShell helper (ADR-0005), which also keeps the computer awake and reads the
 * power status. See {@code docs/design/LLD-windows-platform.md}.
 */
public final class WindowsPlatform implements PlatformServices {

    private final AppPaths appPaths;
    private final ToolLocator toolLocator;
    private final HelperClient helper;
    private final Imapi2Engine engine;
    private final Imapi2AudioEngine audioEngine;
    private final Imapi2ImageBuilder imageBuilder;
    private final SleepInhibitor sleepInhibitor;
    private final PowerStatus powerStatus;
    private final List<Path> fontFolders;

    /**
     * @param appPaths folders from {@link WindowsAppPaths#fromEnvironment}
     * @param runner the shared process runner
     * @param environment the process environment, for {@code PATH} and {@code SystemRoot}
     * @param toolOverrides the user's current tool path settings
     */
    public WindowsPlatform(
            AppPaths appPaths,
            ProcessRunner runner,
            Map<String, String> environment,
            Supplier<Map<ExternalTool, Path>> toolOverrides) {
        this.appPaths = appPaths;
        PathSearchToolLocator pathSearch = new PathSearchToolLocator(
                PathSearchToolLocator.foldersFromPathVariable(
                        WindowsEnvironment.get(environment, "PATH").orElse(""), File.pathSeparator),
                List.of(".exe"));
        this.toolLocator = new OverridingToolLocator(toolOverrides, pathSearch);
        HelperFiles helperFiles = new HelperFiles(appPaths.data().resolve("helper"));
        this.helper = new HelperClient(runner, HelperClient.powerShell(powerShell(environment), helperFiles::script));
        this.engine = new Imapi2Engine(helper);
        this.audioEngine = new Imapi2AudioEngine(helper);
        this.imageBuilder = new Imapi2ImageBuilder(helper);
        this.sleepInhibitor = new ExecutionStateSleepInhibitor(helper);
        this.powerStatus = new HelperPowerStatus(helper);
        this.fontFolders = fontFolders(environment);
    }

    /** The Windows fonts folder and the per-user one where fonts installed without admin rights go. */
    static List<Path> fontFolders(Map<String, String> environment) {
        List<Path> folders = new ArrayList<>();
        for (List<String> place :
                List.of(List.of("SystemRoot", "Fonts"), List.of("LOCALAPPDATA", "Microsoft", "Windows", "Fonts"))) {
            WindowsEnvironment.get(environment, place.getFirst()).ifPresent(base -> {
                try {
                    folders.add(Path.of(base, place.subList(1, place.size()).toArray(String[]::new)));
                } catch (InvalidPathException e) {
                    // Leave out a folder whose variable is not a usable path.
                }
            });
        }
        return List.copyOf(folders);
    }

    /**
     * Windows PowerShell 5.1 under the Windows folder, never from {@code PATH}, so a planted copy is
     * not run.
     */
    static Optional<Path> powerShell(Map<String, String> environment) {
        for (String variable : List.of("SystemRoot", "windir")) {
            String windows = WindowsEnvironment.get(environment, variable).orElse(null);
            if (windows == null) {
                continue;
            }
            try {
                Path candidate = Path.of(windows, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
                if (candidate.isAbsolute() && Files.isRegularFile(candidate)) {
                    return Optional.of(candidate);
                }
            } catch (InvalidPathException e) {
                // Try the next variable.
            }
        }
        return Optional.empty();
    }

    @Override
    public OperatingSystem operatingSystem() {
        return OperatingSystem.WINDOWS;
    }

    @Override
    public String displayName() {
        return "Windows";
    }

    @Override
    public AppPaths appPaths() {
        return appPaths;
    }

    @Override
    public ToolLocator toolLocator() {
        return toolLocator;
    }

    /** See {@code docs/design/LLD-windows-platform.md} section 10. */
    @Override
    public Map<Feature, List<Requirement>> featureRequirements() {
        Requirement ffmpeg = new ToolPresent(ExternalTool.FFMPEG);
        Requirement ffprobe = new ToolPresent(ExternalTool.FFPROBE);
        List<Requirement> videoDvd =
                List.of(ffmpeg, ffprobe, new ToolPresent(ExternalTool.DVDAUTHOR), new ToolPresent(ExternalTool.SPUMUX));
        return Map.of(
                // IMAPI2 is part of Windows.
                Feature.BURNING, List.of(),
                Feature.ISO_CREATION, List.of(),
                Feature.VERIFY, List.of(),
                Feature.AUDIO_CD, List.of(ffmpeg, ffprobe),
                Feature.MP3_TRANSCODING, List.of(ffmpeg, new FfmpegEncoderPresent("libmp3lame")),
                Feature.VIDEO_DVD, videoDvd,
                Feature.VIDEO_DVD_IMAGE, videoDvd,
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
        return new HelperDriveWatcher(events -> helper.start(HelperRequest.watchDrives(), events), engine::listDrives);
    }

    @Override
    public SleepInhibitor sleepInhibitor() {
        return sleepInhibitor;
    }

    @Override
    public List<Path> fontFolders() {
        return fontFolders;
    }

    @Override
    public PowerStatus powerStatus() {
        return powerStatus;
    }
}
