// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.pipeline.Pipeline;
import io.aerohelper.retroburn.pipeline.PipelineEvent;
import io.aerohelper.retroburn.pipeline.PipelineListener;
import io.aerohelper.retroburn.pipeline.PipelineStep;
import io.aerohelper.retroburn.theme.DiscAnimation;
import io.aerohelper.retroburn.theme.DiscMotion;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Shows a running job: the steps with ticks, overall percentage, speed and drive buffer when the
 * engine reports them, elapsed time, and Cancel. Events arrive on background threads; they are
 * folded into a snapshot and drawn at most about ten times a second.
 */
final class ProgressView implements PipelineListener {

    /** Lines kept for "Show details" on the result screen. */
    static final int LOG_LINES = 500;

    private final Messages messages;
    private final CancelToken cancel;
    private final Window owner;
    private final List<Label> stepLabels = new ArrayList<>();
    private final ProgressBar bar = new ProgressBar(0);
    private final Label percent = new Label("0 %");
    private final Label details = new Label();
    private final Label warning = new Label();
    private final Label elapsed = new Label();
    private final Button cancelButton = new Button();
    private final VBox root;
    private final Instant started = Instant.now();
    private final Timeline clock;
    private final DiscAnimation disc;

    // Written by background threads, read on the JavaFX thread.
    private final Object lock = new Object();
    private final Deque<String> log = new ArrayDeque<>();
    private int currentStep = -1;
    private int finishedSteps;
    private double overall;
    private String speed = "";
    private int buffer = -1;
    private boolean sleepWarning;
    private final AtomicBoolean redrawPending = new AtomicBoolean();

    ProgressView(
            Messages messages, Pipeline pipeline, CancelToken cancel, Window owner, String title, DiscAnimation disc) {
        this.messages = messages;
        this.disc = disc;
        disc.setState(DiscMotion.State.SPIN_UP);
        this.cancel = cancel;
        this.owner = owner;

        Label heading = new Label(title);
        heading.getStyleClass().add("progress-heading");
        VBox steps = new VBox();
        steps.getStyleClass().add("progress-steps");
        for (PipelineStep step : pipeline.steps()) {
            Label label = new Label(messages.format(step.name()));
            label.getStyleClass().add("progress-step");
            stepLabels.add(label);
            steps.getChildren().add(label);
        }
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setAccessibleText(messages.get("progress.accessible"));
        HBox.setHgrow(bar, Priority.ALWAYS);
        percent.getStyleClass().add("progress-percent");
        details.getStyleClass().add("progress-details");
        warning.getStyleClass().add("confirm-warning");
        warning.setWrapText(true);
        warning.setVisible(false);
        warning.setManaged(false);

        cancelButton.setText(messages.get("progress.cancel"));
        cancelButton.setOnAction(event -> askToCancel());
        Label keepAwake = new Label(messages.get("progress.keepOpen"));
        keepAwake.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(keepAwake, spacer, cancelButton);
        bottom.getStyleClass().add("progress-bottom");

        HBox top = new HBox(disc.node(), heading);
        top.getStyleClass().add("progress-top");
        root = new VBox(top, new HBox(bar, percent), details, elapsed, steps, warning, bottom);
        root.getStyleClass().add("progress-view");

        clock = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> redraw()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        redraw();
    }

    Node node() {
        return root;
    }

    /** The spinning disc, handed on to the result screen so it slows down from its current speed. */
    DiscAnimation disc() {
        return disc;
    }

    void close() {
        clock.stop();
    }

    @Override
    public void onEvent(PipelineEvent event) {
        synchronized (lock) {
            switch (event) {
                case PipelineEvent.StepStarted started -> {
                    currentStep = started.index();
                    speed = "";
                    buffer = -1;
                }
                case PipelineEvent.StepFinished finished -> finishedSteps = finished.index() + 1;
                case PipelineEvent.OverallProgress progress -> overall = progress.fraction();
                case PipelineEvent.StepOutput output -> record(output.event());
                case PipelineEvent.SleepInhibitionUnavailable unavailable -> {
                    sleepWarning = true;
                    addLog("Sleep inhibition unavailable: " + unavailable.technicalDetail());
                }
            }
        }
        if (redrawPending.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                redrawPending.set(false);
                redraw();
            });
        }
    }

    /** The most recent tool output, for "Show details". */
    List<String> logTail() {
        synchronized (lock) {
            return List.copyOf(log);
        }
    }

    private void record(BurnEvent event) {
        switch (event) {
            case BurnEvent.Progress progress -> {
                speed = progress.speed().orElse(speed);
                buffer = progress.bufferFillPercent().orElse(buffer);
            }
            case BurnEvent.LogLine line -> addLog(line.text());
        }
    }

    private void addLog(String line) {
        log.addLast(line);
        if (log.size() > LOG_LINES) {
            log.removeFirst();
        }
    }

    private void redraw() {
        int step;
        int finished;
        double fraction;
        String speedText;
        int bufferPercent;
        boolean sleep;
        synchronized (lock) {
            step = currentStep;
            finished = finishedSteps;
            fraction = overall;
            speedText = speed;
            bufferPercent = buffer;
            sleep = sleepWarning;
        }
        bar.setProgress(fraction);
        percent.setText(String.format(Locale.ROOT, "%d %%", (int) Math.floor(fraction * 100)));
        for (int i = 0; i < stepLabels.size(); i++) {
            Label label = stepLabels.get(i);
            label.getStyleClass().removeAll("step-done", "step-running", "step-waiting");
            String marker;
            if (i < finished) {
                label.getStyleClass().add("step-done");
                marker = "✓";
            } else if (i == step) {
                label.getStyleClass().add("step-running");
                marker = "▶";
            } else {
                label.getStyleClass().add("step-waiting");
                marker = " ";
            }
            label.setGraphic(new Label(marker));
        }
        List<String> parts = new ArrayList<>();
        if (!speedText.isEmpty()) {
            parts.add(speedText);
        }
        if (bufferPercent >= 0) {
            parts.add(messages.format("progress.buffer", bufferPercent));
        }
        details.setText(String.join(" · ", parts));
        Duration time = Duration.between(started, Instant.now());
        elapsed.setText(messages.format(
                "progress.elapsed", String.format(Locale.ROOT, "%d:%02d", time.toMinutes(), time.toSecondsPart())));
        if (sleep) {
            warning.setText(messages.get("progress.sleepUnavailable"));
            warning.setVisible(true);
            warning.setManaged(true);
        }
    }

    private void askToCancel() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(owner);
        alert.setTitle(messages.get("progress.cancel"));
        alert.setHeaderText(null);
        alert.setContentText(messages.get("progress.cancelWarning"));
        ButtonType stop = new ButtonType(messages.get("progress.stop"), ButtonBar.ButtonData.OTHER);
        ButtonType keep = new ButtonType(messages.get("progress.keepGoing"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(stop, keep);
        ((Button) alert.getDialogPane().lookupButton(keep)).setDefaultButton(true);
        ((Button) alert.getDialogPane().lookupButton(stop)).setDefaultButton(false);
        if (alert.showAndWait().filter(button -> button == stop).isPresent()) {
            cancel.cancel();
            cancelButton.setDisable(true);
            cancelButton.setText(messages.get("progress.stopping"));
        }
    }
}
