// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.engine.BurnEngine;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.pipeline.Pipeline;
import io.aerohelper.retroburn.pipeline.PipelineListener;
import io.aerohelper.retroburn.pipeline.PipelineResult;
import io.aerohelper.retroburn.pipeline.PipelineRunner;
import io.aerohelper.retroburn.platform.DriveWatcher;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs one job that uses a drive: hot-plug polling is paused while it runs, and a failure is
 * checked for a vanished drive. Blocks; call on the pipeline thread. See {@code
 * docs/design/LLD-data-burning.md} section 4.1.
 */
public final class BurnJob {

    private static final Logger LOG = Logger.getLogger(BurnJob.class.getName());

    /** How a job ended, as the result view shows it. */
    public enum Outcome {
        SUCCEEDED,
        SUCCEEDED_WITHOUT_VERIFY,
        VERIFY_FAILED,
        FAILED,
        DRIVE_LOST,
        CANCELLED
    }

    /**
     * @param error what went wrong, for every outcome but success and cancel
     * @param keptWorkFolder the failed job's work folder, kept for inspection
     */
    public record Result(Outcome outcome, Optional<RetroBurnException> error, Optional<Path> keptWorkFolder) {

        public Result {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(error, "error");
            Objects.requireNonNull(keptWorkFolder, "keptWorkFolder");
        }
    }

    private final PipelineRunner runner;
    private final BurnEngine engine;
    private final DriveWatcher driveWatcher;

    public BurnJob(PipelineRunner runner, BurnEngine engine, DriveWatcher driveWatcher) {
        this.runner = runner;
        this.engine = engine;
        this.driveWatcher = driveWatcher;
    }

    public Result run(
            BurnPlan plan,
            Pipeline pipeline,
            PipelineRunner.Options options,
            PipelineListener listener,
            CancelToken cancel) {
        DriveId drive = plan.drive().orElseThrow().id();
        PipelineResult result;
        // Nothing may poll the drive while it writes.
        driveWatcher.pause();
        try {
            result = runner.run(pipeline, options, listener, cancel);
        } finally {
            driveWatcher.resume();
        }
        return switch (result) {
            case PipelineResult.Succeeded _ ->
                new Result(
                        plan.verify() || plan.kind() == JobKind.ERASE
                                ? Outcome.SUCCEEDED
                                : Outcome.SUCCEEDED_WITHOUT_VERIFY,
                        Optional.empty(),
                        Optional.empty());
            case PipelineResult.Cancelled _ -> new Result(Outcome.CANCELLED, Optional.empty(), Optional.empty());
            case PipelineResult.Failed failed ->
                new Result(
                        failureOutcome(drive, failed.error()),
                        Optional.of(failed.error()),
                        Optional.of(failed.keptWorkFolder()));
        };
    }

    private Outcome failureOutcome(DriveId drive, RetroBurnException error) {
        if ("error.verifyMismatch".equals(error.userMessage().key())) {
            return Outcome.VERIFY_FAILED;
        }
        return driveIsGone(drive) ? Outcome.DRIVE_LOST : Outcome.FAILED;
    }

    private boolean driveIsGone(DriveId drive) {
        try {
            return engine.listDrives().stream().noneMatch(found -> found.id().equals(drive));
        } catch (RuntimeException e) {
            // Cannot tell; report the failure as it was.
            LOG.log(Level.INFO, "Could not list drives after a failed job", e);
            return false;
        }
    }
}
