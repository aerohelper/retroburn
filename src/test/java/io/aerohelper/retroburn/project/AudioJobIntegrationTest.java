// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.aerohelper.retroburn.audio.AudioTools;
import io.aerohelper.retroburn.audio.CdAudio;
import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperatingSystem;
import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.engine.MediaState;
import io.aerohelper.retroburn.pipeline.Pipeline;
import io.aerohelper.retroburn.pipeline.PipelineEvent;
import io.aerohelper.retroburn.pipeline.PipelineResult;
import io.aerohelper.retroburn.pipeline.PipelineRunner;
import io.aerohelper.retroburn.pipeline.WorkFolders;
import io.aerohelper.retroburn.platform.AppPaths;
import io.aerohelper.retroburn.platform.PowerStatus.PowerSource;
import io.aerohelper.retroburn.platform.simulated.SimulatedPlatform;
import io.aerohelper.retroburn.tools.ExternalTool;
import io.aerohelper.retroburn.tools.PathSearchToolLocator;
import io.aerohelper.retroburn.tools.ProcessRequest;
import io.aerohelper.retroburn.tools.ProcessRunner;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole audio CD pipeline with the real FFmpeg and the simulated audio engine: probe two tones,
 * convert them in parallel, "burn", and check the track list. Runs where FFmpeg is installed.
 */
class AudioJobIntegrationTest {

    @TempDir
    Path folder;

    @Test
    void tonesAreConvertedBurnedAndChecked() {
        PathSearchToolLocator locator = new PathSearchToolLocator(
                PathSearchToolLocator.foldersFromPathVariable(System.getenv("PATH"), File.pathSeparator),
                List.of("", ".exe"));
        assumeTrue(locator.locate(ExternalTool.FFMPEG).isPresent(), "FFmpeg is not installed");
        assumeTrue(locator.locate(ExternalTool.FFPROBE).isPresent(), "ffprobe is not installed");
        ProcessRunner processes = new ProcessRunner();
        Path one = tone(processes, locator, "one.flac", 5.2);
        Path two = tone(processes, locator, "two.mp3", 6.0);
        AudioTools tools = new AudioTools(processes, locator);
        AudioCompilation compilation = AudioCompilation.empty()
                .add(List.of(
                        AudioCompilation.Track.of(one, tools.probe(one, new CancelToken())),
                        AudioCompilation.Track.of(two, tools.probe(two, new CancelToken()))));

        SimulatedPlatform platform = new SimulatedPlatform(
                OperatingSystem.LINUX,
                new AppPaths(
                        folder.resolve("settings"),
                        folder.resolve("data"),
                        folder.resolve("logs"),
                        folder.resolve("work"),
                        folder.resolve("reels")));
        DriveInfo drive = new DriveInfo(new DriveId("SIM:"), "Sim", "Writer", ConnectionType.OTHER, true);
        MediaInfo cd = new MediaInfo(MediaKind.CD_R, MediaState.BLANK, CdAudio.SECTORS_80_MINUTES * 2048, 0);
        BurnPlan plan = AudioPreCheck.check(
                new BurnPreCheck.Request(
                        JobKind.AUDIO_CD,
                        Optional.of(drive),
                        Optional.of(cd),
                        AudioPreCheck.blocksToWrite(compilation),
                        Optional.empty(),
                        true,
                        false,
                        PowerSource.MAINS,
                        Map.of(),
                        OperatingSystem.LINUX),
                compilation,
                true,
                true);
        assertTrue(plan.canStart(), plan.blockers().toString());

        Pipeline pipeline = new AudioJobFactory(platform.burnEngine(), platform.audioCdEngine(), tools, 2)
                .audioCd(plan, compilation, true, false);
        List<Double> progress = new ArrayList<>();
        PipelineResult result = new PipelineRunner(new WorkFolders(folder.resolve("work")), platform.sleepInhibitor())
                .run(
                        pipeline,
                        new PipelineRunner.Options(false, false),
                        event -> {
                            if (event instanceof PipelineEvent.OverallProgress p) {
                                progress.add(p.fraction());
                            }
                        },
                        new CancelToken());

        assertInstanceOf(PipelineResult.Succeeded.class, result, result.toString());
        assertEquals(1.0, progress.getLast(), 1e-9);
        assertEquals(
                List.of(0L, CdAudio.sectorsFor(Duration.ofMillis(5200)) + CdAudio.GAP_SECTORS),
                platform.audioCdEngine().readTrackLayout(drive.id()).trackStarts());
    }

    private Path tone(ProcessRunner processes, PathSearchToolLocator locator, String name, double seconds) {
        Path file = folder.resolve(name);
        assertTrue(processes
                .run(
                        ProcessRequest.builder(
                                        locator.locate(ExternalTool.FFMPEG).orElseThrow(),
                                        List.of(
                                                "-nostdin",
                                                "-hide_banner",
                                                "-loglevel",
                                                "error",
                                                "-y",
                                                "-f",
                                                "lavfi",
                                                "-i",
                                                "sine=frequency=330:duration=" + seconds,
                                                "-metadata",
                                                "title=" + name,
                                                file.toString()))
                                .totalTimeout(Duration.ofMinutes(1))
                                .build(),
                        new CancelToken())
                .succeeded());
        return file;
    }
}
