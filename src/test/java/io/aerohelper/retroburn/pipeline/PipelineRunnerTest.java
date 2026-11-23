// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.ToolFailedException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.platform.SleepInhibitionUnavailableException;
import io.aerohelper.retroburn.platform.SleepInhibitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipelineRunnerTest {

    private static final PipelineRunner.Options DEFAULTS = new PipelineRunner.Options(true, false);

    @TempDir
    Path workRoot;

    private final List<PipelineEvent> events = new ArrayList<>();
    private final CountingInhibitor inhibitor = new CountingInhibitor();

    @Test
    void runsStepsInOrderAndReportsWeightedProgress() {
        List<String> order = new ArrayList<>();
        Pipeline pipeline = Pipeline.of(
                step("prepare", 1, false, context -> {
                    order.add("prepare");
                    context.reportProgress(0.5);
                }),
                step("write", 3, false, context -> order.add("write")));

        PipelineResult result = runner().run(pipeline, DEFAULTS, events::add, new CancelToken());

        assertInstanceOf(PipelineResult.Succeeded.class, result);
        assertEquals(List.of("prepare", "write"), order);
        assertEquals(List.of(0.0, 0.125, 0.25, 1.0), overallProgress());
    }

    @Test
    void progressNeverGoesBackwardsAndIsClamped() {
        Pipeline pipeline = Pipeline.of(step("jumpy", 1, false, context -> {
            context.reportProgress(0.6);
            context.reportProgress(0.4);
            context.reportProgress(7.0);
        }));

        runner().run(pipeline, DEFAULTS, events::add, new CancelToken());

        assertEquals(List.of(0.0, 0.6, 1.0), overallProgress());
    }

    @Test
    void engineProgressBecomesStepProgressAndIsPassedOn() {
        Pipeline pipeline = Pipeline.of(
                step("engine", 1, false, context -> context.burnListener().onEvent(BurnEvent.Progress.of(0.5))));

        runner().run(pipeline, DEFAULTS, events::add, new CancelToken());

        assertTrue(events.stream()
                .anyMatch(event -> event instanceof PipelineEvent.StepOutput output
                        && output.event().equals(BurnEvent.Progress.of(0.5))));
        assertTrue(overallProgress().contains(0.5));
    }

    @Test
    void keepsTheComputerAwakeFromTheFirstWritingStepUntilTheEnd() {
        List<Boolean> awakeDuringStep = new ArrayList<>();
        Consumer<StepContext> record = context -> awakeDuringStep.add(inhibitor.held > 0);
        Pipeline pipeline = Pipeline.of(
                step("convert", 1, false, record), step("burn", 1, true, record), step("verify", 1, false, record));

        runner().run(pipeline, DEFAULTS, events::add, new CancelToken());

        assertEquals(List.of(false, true, true), awakeDuringStep);
        assertEquals(1, inhibitor.acquired);
        assertEquals(0, inhibitor.held);
    }

    @Test
    void releasesTheSleepLockWhenAStepFails() {
        Pipeline pipeline = Pipeline.of(step("burn", 1, true, context -> {
            throw new ToolFailedException(UserMessage.of("error.isoCreationFailed", "disc full"), "detail");
        }));

        PipelineResult result = runner().run(pipeline, DEFAULTS, events::add, new CancelToken());

        assertInstanceOf(PipelineResult.Failed.class, result);
        assertEquals(1, inhibitor.acquired);
        assertEquals(0, inhibitor.held);
    }

    @Test
    void releasesTheSleepLockWhenCancelled() {
        CancelToken cancel = new CancelToken();
        Pipeline pipeline =
                Pipeline.of(step("burn", 1, true, context -> cancel.cancel()), step("verify", 1, false, context -> {}));

        PipelineResult result = runner().run(pipeline, DEFAULTS, events::add, cancel);

        assertInstanceOf(PipelineResult.Cancelled.class, result);
        assertEquals(0, inhibitor.held);
    }

    @Test
    void doesNotKeepTheComputerAwakeWhenTheUserTurnedThatOff() {
        Pipeline pipeline = Pipeline.of(step("burn", 1, true, context -> {}));

        runner().run(pipeline, new PipelineRunner.Options(false, false), events::add, new CancelToken());

        assertEquals(0, inhibitor.acquired);
    }

    @Test
    void carriesOnAndWarnsWhenTheComputerCannotBeKeptAwake() {
        SleepInhibitor refusing = reason -> {
            throw new SleepInhibitionUnavailableException("Failed to inhibit: Access denied");
        };
        Pipeline pipeline = Pipeline.of(step("burn", 1, true, context -> {}));

        PipelineResult result = new PipelineRunner(new WorkFolders(workRoot), refusing)
                .run(pipeline, DEFAULTS, events::add, new CancelToken());

        assertInstanceOf(PipelineResult.Succeeded.class, result);
        assertTrue(events.stream().anyMatch(PipelineEvent.SleepInhibitionUnavailable.class::isInstance));
    }

    @Test
    void cancellingBeforeAStepSkipsItAndDeletesTheWorkFolder() {
        CancelToken cancel = new CancelToken();
        List<String> ran = new ArrayList<>();
        Pipeline pipeline =
                Pipeline.of(step("first", 1, false, context -> cancel.cancel()), step("second", 1, false, context -> {
                    ran.add("second");
                }));

        PipelineResult result = runner().run(pipeline, DEFAULTS, events::add, cancel);

        assertInstanceOf(PipelineResult.Cancelled.class, result);
        assertTrue(ran.isEmpty());
        assertEquals(0, jobFolderCount());
    }

    @Test
    void keepsTheWorkFolderAfterAFailureAndDeletesItAfterSuccess() {
        Pipeline failing = Pipeline.of(step("fail", 1, false, context -> {
            throw new ToolFailedException(UserMessage.of("error.unexpected"), "detail");
        }));
        Pipeline succeeding = Pipeline.of(step("ok", 1, false, context -> {}));

        PipelineResult failed = runner().run(failing, DEFAULTS, events::add, new CancelToken());
        runner().run(succeeding, DEFAULTS, events::add, new CancelToken());

        Path kept = ((PipelineResult.Failed) failed).keptWorkFolder();
        assertTrue(Files.isDirectory(kept));
        assertEquals(1, jobFolderCount());
    }

    @Test
    void keepsTheWorkFolderAfterSuccessWhenAskedTo() {
        Pipeline pipeline = Pipeline.of(step("ok", 1, false, context -> {}));

        runner().run(pipeline, new PipelineRunner.Options(true, true), events::add, new CancelToken());

        assertEquals(1, jobFolderCount());
    }

    @Test
    void wrapsUnexpectedErrorsSoTheUserAlwaysGetsAMessage() {
        Pipeline pipeline = Pipeline.of(step("bug", 1, false, context -> {
            throw new IllegalStateException("bug");
        }));

        PipelineResult result = runner().run(pipeline, DEFAULTS, events::add, new CancelToken());

        PipelineResult.Failed failed = assertInstanceOf(PipelineResult.Failed.class, result);
        assertEquals("error.unexpected", failed.error().userMessage().key());
    }

    @Test
    void reportsStepStartAndFinish() {
        Pipeline pipeline = Pipeline.of(step("a", 1, false, context -> {}), step("b", 1, false, context -> {}));

        runner().run(pipeline, DEFAULTS, events::add, new CancelToken());

        List<PipelineEvent> lifecycle = events.stream()
                .filter(event -> !(event instanceof PipelineEvent.OverallProgress))
                .toList();
        assertEquals(
                List.of(
                        new PipelineEvent.StepStarted(0, 2, UserMessage.of("a")),
                        new PipelineEvent.StepFinished(0, UserMessage.of("a")),
                        new PipelineEvent.StepStarted(1, 2, UserMessage.of("b")),
                        new PipelineEvent.StepFinished(1, UserMessage.of("b"))),
                lifecycle);
    }

    @Test
    void rejectsEmptyPipelinesAndNonPositiveWeights() {
        assertThrows(IllegalArgumentException.class, () -> new Pipeline(List.of()));
        assertThrows(IllegalArgumentException.class, () -> Pipeline.of(step("zero", 0, false, context -> {})));
    }

    @Test
    void stepsGetAnEmptyWorkFolder() {
        Pipeline pipeline = Pipeline.of(step("look", 1, false, context -> {
            assertTrue(Files.isDirectory(context.workFolder()));
            try (Stream<Path> files = Files.list(context.workFolder())) {
                assertFalse(files.findAny().isPresent());
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }));

        assertInstanceOf(
                PipelineResult.Succeeded.class, runner().run(pipeline, DEFAULTS, events::add, new CancelToken()));
    }

    private PipelineRunner runner() {
        return new PipelineRunner(new WorkFolders(workRoot), inhibitor);
    }

    private List<Double> overallProgress() {
        return events.stream()
                .filter(PipelineEvent.OverallProgress.class::isInstance)
                .map(event -> ((PipelineEvent.OverallProgress) event).fraction())
                .toList();
    }

    private long jobFolderCount() {
        try (Stream<Path> children = Files.list(workRoot)) {
            return children.count();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static PipelineStep step(String name, double weight, boolean awake, Consumer<StepContext> body) {
        return new PipelineStep() {
            @Override
            public UserMessage name() {
                return UserMessage.of(name);
            }

            @Override
            public double weight() {
                return weight;
            }

            @Override
            public boolean needsSystemAwake() {
                return awake;
            }

            @Override
            public void run(StepContext context) {
                body.accept(context);
            }
        };
    }

    /** Counts acquisitions and how many locks are currently held. */
    private static final class CountingInhibitor implements SleepInhibitor {

        int acquired;
        int held;

        @Override
        public SleepInhibition acquire(String reason) {
            acquired++;
            held++;
            boolean[] closed = {false};
            return () -> {
                if (!closed[0]) {
                    closed[0] = true;
                    held--;
                }
            };
        }
    }
}
