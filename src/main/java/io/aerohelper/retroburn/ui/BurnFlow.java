// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.BlankMode;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.pipeline.Pipeline;
import io.aerohelper.retroburn.pipeline.PipelineResult;
import io.aerohelper.retroburn.pipeline.PipelineRunner;
import io.aerohelper.retroburn.platform.PowerStatus.PowerSource;
import io.aerohelper.retroburn.project.BurnJob;
import io.aerohelper.retroburn.project.BurnPlan;
import io.aerohelper.retroburn.project.BurnPreCheck;
import io.aerohelper.retroburn.project.JobKind;
import io.aerohelper.retroburn.settings.Settings;
import io.aerohelper.retroburn.theme.Appearance;
import io.aerohelper.retroburn.theme.DiscMotion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.control.Alert;

/**
 * The shared path of every job: pre-check in the background, confirmation, progress, result. See
 * {@code docs/design/LLD-data-burning.md} sections 3 to 6.
 */
final class BurnFlow {

    /** Size of the spinning disc on the progress and result screens. */
    private static final double DISC_RADIUS = 30;

    private static final Logger LOG = Logger.getLogger(BurnFlow.class.getName());

    /** Builds a job's pipeline once the user has confirmed its plan. */
    @FunctionalInterface
    interface PipelineBuilder {
        Pipeline build(BurnPlan plan, BlankMode mode);
    }

    /**
     * What one job is about.
     *
     * @param bytesToWrite image size, estimated for a data disc
     * @param needsWorkSpace the image is built in the work folder first
     * @param title heading of the progress view
     * @param successText the result text on success
     * @param planner turns the checks' inputs into a plan; {@link BurnPreCheck#check} unless the
     *     project adds rules of its own
     */
    record Job(
            JobKind kind,
            long bytesToWrite,
            boolean needsWorkSpace,
            PipelineBuilder pipeline,
            String title,
            String successText,
            Function<BurnPreCheck.Request, BurnPlan> planner) {

        Job(
                JobKind kind,
                long bytesToWrite,
                boolean needsWorkSpace,
                PipelineBuilder pipeline,
                String title,
                String successText) {
            this(kind, bytesToWrite, needsWorkSpace, pipeline, title, successText, BurnPreCheck::check);
        }
    }

    private final UiServices services;
    private final Navigation navigation;
    private final Messages messages;

    BurnFlow(UiServices services, Navigation navigation) {
        this.services = services;
        this.navigation = navigation;
        this.messages = services.messages();
    }

    /** Checks, asks, runs and reports a job that uses the drive bar's drive. Call on the FX thread. */
    void start(DriveBar driveBar, Job job) {
        Optional<DriveInfo> drive = driveBar.selectedDrive();
        Settings settings = services.settings().current();
        CompletableFuture.supplyAsync(() -> request(job, drive, settings.verifyAfterBurn()), services.background())
                .whenComplete((request, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        LOG.log(Level.WARNING, "Could not prepare the job", error);
                        showError(error);
                        return;
                    }
                    confirmAndRun(driveBar, job, request);
                }));
    }

    /** Runs a job that needs no drive, such as creating an ISO file. Call on the FX thread. */
    void startWithoutDrive(Pipeline pipeline, String title, String successText) {
        CancelToken cancel = new CancelToken();
        ProgressView progress = new ProgressView(
                messages,
                pipeline,
                cancel,
                navigation.window(),
                title,
                services.appearance().newDisc(DISC_RADIUS));
        navigation.showOnTop(progress.node());
        Settings settings = services.settings().current();
        CompletableFuture.supplyAsync(
                        () -> services.pipelineRunner().run(pipeline, options(settings), progress, cancel),
                        services.jobs())
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    progress.close();
                    showResult(error == null ? toJobResult(result) : unexpected(error), successText, progress);
                }));
    }

    private BurnPreCheck.Request request(Job job, Optional<DriveInfo> drive, boolean verify) {
        Optional<MediaInfo> media = drive.filter(DriveInfo::accessible)
                .map(found -> services.platform().burnEngine().readMedia(found.id()));
        PowerSource power = services.platform().powerStatus().current();
        Optional<BurnPreCheck.WorkSpace> workSpace = job.needsWorkSpace() ? workSpace() : Optional.empty();
        return new BurnPreCheck.Request(
                job.kind(),
                drive,
                media,
                job.bytesToWrite(),
                workSpace,
                verify,
                services.settings().current().showUsbTip(),
                power,
                services.features().get(),
                services.platform().operatingSystem());
    }

    private Optional<BurnPreCheck.WorkSpace> workSpace() {
        Path root = services.workRoot().get();
        try {
            Files.createDirectories(root);
            return Optional.of(
                    new BurnPreCheck.WorkSpace(root, Files.getFileStore(root).getUsableSpace()));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not read free space of " + root, e);
            return Optional.of(new BurnPreCheck.WorkSpace(root, 0));
        }
    }

    private void confirmAndRun(DriveBar driveBar, Job job, BurnPreCheck.Request request) {
        BurnPlan plan = job.planner().apply(request);
        Optional<ConfirmBurnDialog.Choice> choice = new ConfirmBurnDialog(messages)
                .show(navigation.window(), plan, verify -> job.planner().apply(withVerify(request, verify)));
        if (choice.isEmpty()) {
            return;
        }
        if (choice.get().dismissUsbTip()) {
            Settings settings = services.settings().current().withShowUsbTip(false);
            services.background().execute(() -> services.settings().save(settings));
        }
        run(driveBar, job, choice.get());
    }

    private void run(DriveBar driveBar, Job job, ConfirmBurnDialog.Choice choice) {
        BurnPlan plan = choice.plan();
        Pipeline pipeline = job.pipeline().build(plan, choice.blankMode());
        CancelToken cancel = new CancelToken();
        ProgressView progress = new ProgressView(
                messages,
                pipeline,
                cancel,
                navigation.window(),
                job.title(),
                services.appearance().newDisc(DISC_RADIUS));
        navigation.showOnTop(progress.node());
        BurnJob burnJob =
                new BurnJob(services.pipelineRunner(), services.platform().burnEngine(), driveBar.watcher());
        Settings settings = services.settings().current();
        services.appearance().burnStarted();
        CompletableFuture.supplyAsync(
                        () -> burnJob.run(plan, pipeline, options(settings), progress, cancel), services.jobs())
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    progress.close();
                    driveBar.readMedia();
                    BurnJob.Result finished = error == null ? result : unexpected(error);
                    services.appearance().burnFinished(ending(finished.outcome()));
                    showResult(finished, job.successText(), progress);
                }));
    }

    private void showResult(BurnJob.Result result, String successText, ProgressView progress) {
        progress.disc()
                .setState(
                        ending(result.outcome()) == Appearance.Ending.SUCCESS
                                ? DiscMotion.State.DONE
                                : DiscMotion.State.STOPPED);
        ResultView view = new ResultView(
                messages,
                result,
                successText,
                progress.logTail(),
                progress.disc().node(),
                navigation::backToProject,
                navigation::showHome);
        navigation.showOnTop(view.node());
    }

    static Appearance.Ending ending(BurnJob.Outcome outcome) {
        return switch (outcome) {
            case SUCCEEDED, SUCCEEDED_WITHOUT_VERIFY -> Appearance.Ending.SUCCESS;
            case CANCELLED -> Appearance.Ending.CANCELLED;
            case VERIFY_FAILED, FAILED, DRIVE_LOST -> Appearance.Ending.ERROR;
        };
    }

    private static PipelineRunner.Options options(Settings settings) {
        return new PipelineRunner.Options(settings.preventSleepWhileBurning(), settings.keepTemporaryFiles());
    }

    private static BurnPreCheck.Request withVerify(BurnPreCheck.Request request, boolean verify) {
        return new BurnPreCheck.Request(
                request.kind(),
                request.drive(),
                request.media(),
                request.bytesToWrite(),
                request.workFolder(),
                verify,
                request.usbTipWanted(),
                request.power(),
                request.features(),
                request.operatingSystem());
    }

    private static BurnJob.Result toJobResult(PipelineResult result) {
        return switch (result) {
            case PipelineResult.Succeeded _ ->
                new BurnJob.Result(BurnJob.Outcome.SUCCEEDED, Optional.empty(), Optional.empty());
            case PipelineResult.Cancelled _ ->
                new BurnJob.Result(BurnJob.Outcome.CANCELLED, Optional.empty(), Optional.empty());
            case PipelineResult.Failed failed ->
                new BurnJob.Result(
                        BurnJob.Outcome.FAILED, Optional.of(failed.error()), Optional.of(failed.keptWorkFolder()));
        };
    }

    /** The pipeline runner wraps step errors itself; this only catches a bug around it. */
    private static BurnJob.Result unexpected(Throwable error) {
        LOG.log(Level.SEVERE, "Job ended with an unexpected error", error);
        return new BurnJob.Result(
                BurnJob.Outcome.FAILED,
                Optional.of(new RetroBurnException(UserMessage.of("error.unexpected"), error.toString(), error)),
                Optional.empty());
    }

    private void showError(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(navigation.window());
        alert.setHeaderText(null);
        alert.setContentText(
                cause instanceof RetroBurnException known
                        ? messages.format(known.userMessage())
                        : messages.get("error.unexpected"));
        alert.showAndWait();
    }
}
