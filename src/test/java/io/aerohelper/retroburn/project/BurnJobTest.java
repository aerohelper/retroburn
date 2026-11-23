// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperatingSystem;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.ToolFailedException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.BlankMode;
import io.aerohelper.retroburn.engine.BurnEngine;
import io.aerohelper.retroburn.engine.BurnListener;
import io.aerohelper.retroburn.engine.BurnOptions;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.engine.MediaState;
import io.aerohelper.retroburn.pipeline.Pipeline;
import io.aerohelper.retroburn.pipeline.PipelineEvent;
import io.aerohelper.retroburn.pipeline.PipelineRunner;
import io.aerohelper.retroburn.pipeline.PipelineStep;
import io.aerohelper.retroburn.pipeline.WorkFolders;
import io.aerohelper.retroburn.platform.AppPaths;
import io.aerohelper.retroburn.platform.DriveWatcher;
import io.aerohelper.retroburn.platform.PlatformServices;
import io.aerohelper.retroburn.platform.PowerStatus.PowerSource;
import io.aerohelper.retroburn.platform.simulated.SimulatedPlatform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Data-burning jobs end to end on the simulated platform, plus failures from a scripted engine. */
class BurnJobTest {

    private static final PipelineRunner.Options OPTIONS = new PipelineRunner.Options(true, false);

    @TempDir
    Path folder;

    private PlatformServices platform;
    private RecordingWatcher watcher;
    private PipelineRunner runner;
    private final List<PipelineEvent> events = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void createPlatform() {
        platform = new SimulatedPlatform(
                OperatingSystem.LINUX,
                new AppPaths(
                        folder.resolve("settings"),
                        folder.resolve("data"),
                        folder.resolve("logs"),
                        folder.resolve("work"),
                        folder.resolve("reels")));
        watcher = new RecordingWatcher();
        runner = new PipelineRunner(new WorkFolders(folder.resolve("work")), platform.sleepInhibitor());
    }

    @Test
    void aDataDiscIsBuiltBurnedAndVerifiedWithHotPlugPaused() throws IOException {
        BurnEngine engine = platform.burnEngine();
        DataCompilation compilation =
                DataCompilation.empty().add(List.of(Files.writeString(folder.resolve("a.txt"), "hello")));
        BurnPlan plan = plan(JobKind.DATA_DISC, engine, 10_000, true);
        Pipeline pipeline = new BurnJobFactory(engine, platform.discImageBuilder())
                .dataDisc(plan, compilation, VolumeLabel.sanitize("Test"));

        BurnJob.Result result =
                new BurnJob(runner, engine, watcher).run(plan, pipeline, OPTIONS, events::add, new CancelToken());

        assertEquals(BurnJob.Outcome.SUCCEEDED, result.outcome(), result.toString());
        assertEquals(List.of("step.buildImage", "step.burnImage", "step.verify"), stepKeys(pipeline));
        assertEquals(List.of("pause", "resume"), watcher.calls);
        assertTrue(events.contains(new PipelineEvent.OverallProgress(1.0)));
        // The image lived in the work folder, which is gone after success.
        try (var left = Files.list(folder.resolve("work"))) {
            assertEquals(0, left.count());
        }
    }

    @Test
    void withoutVerifyTheResultSaysSo() throws IOException {
        BurnEngine engine = platform.burnEngine();
        Path iso = Files.write(folder.resolve("disc.iso"), new byte[4096]);
        BurnPlan plan = plan(JobKind.BURN_ISO, engine, 4096, false);
        Pipeline pipeline = new BurnJobFactory(engine, platform.discImageBuilder()).burnIso(plan, iso);

        BurnJob.Result result =
                new BurnJob(runner, engine, watcher).run(plan, pipeline, OPTIONS, events::add, new CancelToken());

        assertEquals(BurnJob.Outcome.SUCCEEDED_WITHOUT_VERIFY, result.outcome());
        assertEquals(List.of("step.burnImage"), stepKeys(pipeline));
    }

    @Test
    void aUsedCdRwIsErasedFirstAndAnEraseJobOnlyErases() {
        BurnEngine engine = platform.burnEngine();
        BurnPlan burn = new BurnPlan(
                JobKind.BURN_ISO, drive(engine), Optional.empty(), 1, true, true, List.of(), List.of(), false);
        BurnPlan erase = new BurnPlan(
                JobKind.ERASE, drive(engine), Optional.empty(), 0, false, false, List.of(), List.of(), false);
        BurnJobFactory factory = new BurnJobFactory(engine, platform.discImageBuilder());

        assertEquals(
                List.of("step.blank", "step.burnImage", "step.verify"),
                stepKeys(factory.burnIso(burn, folder.resolve("x.iso"))));
        assertEquals(List.of("step.blank"), stepKeys(factory.erase(erase, BlankMode.FULL)));
        assertEquals(
                List.of("step.buildImage"),
                stepKeys(factory.createIso(
                        DataCompilation.empty(), VolumeLabel.sanitize("x"), folder.resolve("o.iso"), 0)));
    }

    @Test
    void aMismatchIsAVerifyFailure() throws IOException {
        ScriptedEngine engine = new ScriptedEngine(platform.burnEngine());
        engine.verifyFailure = new RetroBurnException(UserMessage.of("error.verifyMismatch"), "byte 7");

        assertEquals(BurnJob.Outcome.VERIFY_FAILED, burnIso(engine).outcome());
    }

    @Test
    void aFailureWithTheDriveGoneIsADriveLoss() throws IOException {
        ScriptedEngine engine = new ScriptedEngine(platform.burnEngine());
        engine.burnFailure = new ToolFailedException(UserMessage.of("error.burnFailed", "I/O error"), "detail");
        engine.drivesAfterFailure = List.of();

        BurnJob.Result result = burnIso(engine);

        assertEquals(BurnJob.Outcome.DRIVE_LOST, result.outcome());
        assertEquals(
                "error.burnFailed", result.error().orElseThrow().userMessage().key());
        assertTrue(result.keptWorkFolder().isPresent());
    }

    @Test
    void aFailureWithTheDriveStillThereIsAFailure() throws IOException {
        ScriptedEngine engine = new ScriptedEngine(platform.burnEngine());
        engine.burnFailure = new ToolFailedException(UserMessage.of("error.burnFailed", "bad disc"), "detail");

        assertEquals(BurnJob.Outcome.FAILED, burnIso(engine).outcome());
    }

    @Test
    void anImageThatTurnsOutTooBigIsStoppedBeforeWriting() throws IOException {
        ScriptedEngine engine = new ScriptedEngine(platform.burnEngine());
        Path iso = Files.write(folder.resolve("big.iso"), new byte[4096]);
        BurnPlan plan = plan(JobKind.BURN_ISO, engine, 1, false);
        // The disc changed after the pre-check: now it holds less than the image.
        engine.media = new MediaInfo(MediaKind.CD_R, MediaState.BLANK, 2048, 0);

        BurnJob.Result result = new BurnJob(runner, engine, watcher)
                .run(
                        plan,
                        new BurnJobFactory(engine, platform.discImageBuilder()).burnIso(plan, iso),
                        OPTIONS,
                        events::add,
                        new CancelToken());

        assertEquals(BurnJob.Outcome.FAILED, result.outcome());
        assertEquals(
                "error.discTooSmall", result.error().orElseThrow().userMessage().key());
    }

    @Test
    void cancellingEndsAsCancelled() throws IOException {
        BurnEngine engine = platform.burnEngine();
        Path iso = Files.write(folder.resolve("disc.iso"), new byte[4096]);
        BurnPlan plan = plan(JobKind.BURN_ISO, engine, 4096, true);
        CancelToken cancel = new CancelToken();
        cancel.cancel();

        BurnJob.Result result = new BurnJob(runner, engine, watcher)
                .run(
                        plan,
                        new BurnJobFactory(engine, platform.discImageBuilder()).burnIso(plan, iso),
                        OPTIONS,
                        events::add,
                        cancel);

        assertEquals(BurnJob.Outcome.CANCELLED, result.outcome());
        assertEquals(List.of("pause", "resume"), watcher.calls);
    }

    private BurnJob.Result burnIso(BurnEngine engine) throws IOException {
        Path iso = Files.write(folder.resolve("disc.iso"), new byte[4096]);
        BurnPlan plan = plan(JobKind.BURN_ISO, engine, 4096, true);
        return new BurnJob(runner, engine, watcher)
                .run(
                        plan,
                        new BurnJobFactory(engine, platform.discImageBuilder()).burnIso(plan, iso),
                        OPTIONS,
                        events::add,
                        new CancelToken());
    }

    private static BurnPlan plan(JobKind kind, BurnEngine engine, long bytes, boolean verify) {
        Optional<DriveInfo> drive = drive(engine);
        BurnPlan plan = BurnPreCheck.check(new BurnPreCheck.Request(
                kind,
                drive,
                drive.map(found -> engine.readMedia(found.id())),
                bytes,
                Optional.empty(),
                verify,
                false,
                PowerSource.MAINS,
                Map.of(),
                OperatingSystem.LINUX));
        assertTrue(plan.canStart(), plan.blockers().toString());
        return plan;
    }

    private static Optional<DriveInfo> drive(BurnEngine engine) {
        return engine.listDrives().stream().findFirst();
    }

    private static List<String> stepKeys(Pipeline pipeline) {
        return pipeline.steps().stream()
                .map(PipelineStep::name)
                .map(UserMessage::key)
                .toList();
    }

    private static final class RecordingWatcher implements DriveWatcher {
        final List<String> calls = new ArrayList<>();

        @Override
        public void start(Consumer<List<DriveInfo>> listener) {}

        @Override
        public void pause() {
            calls.add("pause");
        }

        @Override
        public void resume() {
            calls.add("resume");
        }

        @Override
        public void close() {}
    }

    /** The simulated engine, with failures switched in. */
    private static final class ScriptedEngine implements BurnEngine {
        private final BurnEngine delegate;
        RuntimeException burnFailure;
        RuntimeException verifyFailure;
        List<DriveInfo> drivesAfterFailure;
        MediaInfo media;
        private boolean failed;

        ScriptedEngine(BurnEngine delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<DriveInfo> listDrives() {
            return failed && drivesAfterFailure != null ? drivesAfterFailure : delegate.listDrives();
        }

        @Override
        public MediaInfo readMedia(DriveId drive) {
            return media != null ? media : delegate.readMedia(drive);
        }

        @Override
        public void burnImage(
                DriveId drive, Path image, BurnOptions options, BurnListener listener, CancelToken cancel) {
            if (burnFailure != null) {
                failed = true;
                throw burnFailure;
            }
            delegate.burnImage(drive, image, options, listener, cancel);
        }

        @Override
        public void blank(DriveId drive, BlankMode mode, BurnListener listener, CancelToken cancel) {
            delegate.blank(drive, mode, listener, cancel);
        }

        @Override
        public void verifyImage(DriveId drive, Path image, BurnListener listener, CancelToken cancel) {
            if (verifyFailure != null) {
                failed = true;
                throw verifyFailure;
            }
            delegate.verifyImage(drive, image, listener, cancel);
        }

        @Override
        public void eject(DriveId drive) {
            delegate.eject(drive);
        }
    }
}
