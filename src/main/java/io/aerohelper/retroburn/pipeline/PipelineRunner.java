// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.pipeline;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.platform.SleepInhibitionUnavailableException;
import io.aerohelper.retroburn.platform.SleepInhibitor;
import io.aerohelper.retroburn.platform.SleepInhibitor.SleepInhibition;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs a {@link Pipeline}: creates the job's work folder, runs the steps in order with weighted
 * progress, keeps the computer awake while writing, and cleans up. See {@code
 * docs/design/LLD-pipeline.md}.
 *
 * <p>{@link #run} blocks; call it on the pipeline thread, never on the JavaFX Application Thread.
 */
public final class PipelineRunner {

    private static final Logger LOG = Logger.getLogger(PipelineRunner.class.getName());
    private static final String AWAKE_REASON = "Writing a disc";

    private final WorkFolders workFolders;
    private final SleepInhibitor sleepInhibitor;

    public PipelineRunner(WorkFolders workFolders, SleepInhibitor sleepInhibitor) {
        this.workFolders = Objects.requireNonNull(workFolders, "workFolders");
        this.sleepInhibitor = Objects.requireNonNull(sleepInhibitor, "sleepInhibitor");
    }

    /**
     * Per-run choices taken from the user's settings.
     *
     * @param preventSleep keep the computer awake during steps that need it
     * @param keepTemporaryFiles keep the work folder after success or cancel
     */
    public record Options(boolean preventSleep, boolean keepTemporaryFiles) {}

    /** Runs every step and reports how the job ended; never throws for step failures. */
    public PipelineResult run(Pipeline pipeline, Options options, PipelineListener unsafeListener, CancelToken cancel) {
        // Engines report from tool-output threads as well as the pipeline thread; deliver one at a time.
        Object deliveryLock = new Object();
        PipelineListener listener = event -> {
            synchronized (deliveryLock) {
                unsafeListener.onEvent(event);
            }
        };
        Path workFolder = workFolders.createJobFolder();
        ProgressTracker progress = new ProgressTracker(pipeline, listener);
        SleepInhibition inhibition = null;
        PipelineResult result;
        try {
            List<PipelineStep> steps = pipeline.steps();
            for (int index = 0; index < steps.size(); index++) {
                cancel.throwIfCancelled();
                PipelineStep step = steps.get(index);
                if (inhibition == null && step.needsSystemAwake() && options.preventSleep()) {
                    inhibition = keepAwake(listener);
                }
                runStep(index, steps.size(), step, workFolder, cancel, progress, listener);
            }
            result = new PipelineResult.Succeeded();
        } catch (OperationCancelledException e) {
            result = new PipelineResult.Cancelled();
        } catch (RetroBurnException e) {
            result = new PipelineResult.Failed(e, workFolder);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Unexpected error in pipeline", e);
            result = new PipelineResult.Failed(
                    new RetroBurnException(UserMessage.of("error.unexpected"), e.toString(), e), workFolder);
        } finally {
            if (inhibition != null) {
                inhibition.close();
            }
        }
        if (!(result instanceof PipelineResult.Failed) && !options.keepTemporaryFiles()) {
            workFolders.delete(workFolder);
        }
        return result;
    }

    private static void runStep(
            int index,
            int stepCount,
            PipelineStep step,
            Path workFolder,
            CancelToken cancel,
            ProgressTracker progress,
            PipelineListener listener) {
        listener.onEvent(new PipelineEvent.StepStarted(index, stepCount, step.name()));
        progress.startStep(index);
        StepContext context = new StepContext(
                workFolder,
                cancel,
                progress::reportStepFraction,
                event -> listener.onEvent(new PipelineEvent.StepOutput(index, event)));
        step.run(context);
        progress.finishStep();
        listener.onEvent(new PipelineEvent.StepFinished(index, step.name()));
    }

    /** Returns the inhibition, or null after telling the listener it is unavailable. */
    private SleepInhibition keepAwake(PipelineListener listener) {
        try {
            return sleepInhibitor.acquire(AWAKE_REASON);
        } catch (SleepInhibitionUnavailableException e) {
            LOG.warning(() -> "Cannot keep the computer awake: " + e.technicalDetail());
            listener.onEvent(new PipelineEvent.SleepInhibitionUnavailable(e.technicalDetail()));
            return null;
        }
    }

    /**
     * Turns per-step fractions into overall progress that never goes backwards. Synchronized because
     * engines may report progress from their output threads.
     */
    private static final class ProgressTracker {

        private final List<PipelineStep> steps;
        private final double totalWeight;
        private final PipelineListener listener;
        private double finishedWeight;
        private int currentStep;
        private double lastReported = -1;

        ProgressTracker(Pipeline pipeline, PipelineListener listener) {
            this.steps = pipeline.steps();
            this.totalWeight = pipeline.totalWeight();
            this.listener = listener;
        }

        synchronized void startStep(int index) {
            currentStep = index;
            report(0);
        }

        synchronized void reportStepFraction(double fraction) {
            report(Math.clamp(fraction, 0.0, 1.0));
        }

        synchronized void finishStep() {
            report(1);
            finishedWeight += steps.get(currentStep).weight();
        }

        private void report(double stepFraction) {
            double overall = (finishedWeight + steps.get(currentStep).weight() * stepFraction) / totalWeight;
            overall = Math.min(1.0, overall);
            if (overall > lastReported) {
                lastReported = overall;
                listener.onEvent(new PipelineEvent.OverallProgress(overall));
            }
        }
    }
}
