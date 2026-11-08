// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.reel.BurnSession;
import io.aerohelper.retroburn.reel.ReelMaker;
import io.aerohelper.retroburn.reel.ReelTimeline;
import io.aerohelper.retroburn.settings.Settings;
import io.aerohelper.retroburn.tools.Feature;
import io.aerohelper.retroburn.tools.FeatureAvailability;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * "Make my burn reel" with "Hide item names in reel", a progress line, then the saved file with
 * "Open folder". See {@code docs/design/LLD-burn-reel.md} section 6.
 */
final class ReelPanel {

    private static final Logger LOG = Logger.getLogger(ReelPanel.class.getName());

    private final UiServices services;
    private final Messages messages;
    private final BurnSession session;
    private final Button make = new Button();
    private final CheckBox hideNames = new CheckBox();
    private final Label status = new Label();
    private final Button openFolder = new Button();
    private final Button cancel = new Button();
    private final VBox root;
    private CancelToken running;

    ReelPanel(UiServices services, BurnSession session) {
        this.services = services;
        this.messages = services.messages();
        this.session = session;

        make.setText(messages.get("reel.make"));
        make.getStyleClass().add("primary-button");
        make.setOnAction(event -> make());
        hideNames.setText(messages.get("reel.hideNames"));
        hideNames.setSelected(services.settings().current().reel().hideNames());
        hideNames.selectedProperty().addListener((observable, before, hide) -> {
            Settings settings = services.settings().current();
            services.settings()
                    .save(settings.withReel(
                            new Settings.Reel(hide, settings.reel().seconds())));
        });
        cancel.setText(messages.get("progress.cancel"));
        cancel.setVisible(false);
        cancel.setManaged(false);
        cancel.setOnAction(event -> {
            if (running != null) {
                running.cancel();
            }
        });
        openFolder.setText(messages.get("reel.openFolder"));
        openFolder.setVisible(false);
        openFolder.setManaged(false);
        openFolder.setOnAction(event -> openFolder());
        status.setWrapText(true);
        status.getStyleClass().add("field-hint");

        HBox actions = new HBox(make, hideNames, cancel, openFolder);
        actions.getStyleClass().add("form-row");
        root = new VBox(actions, status);
        root.setSpacing(6);

        FeatureAvailability availability = services.features().get().get(Feature.BURN_REEL);
        if (availability != null && !availability.isAvailable()) {
            make.setDisable(true);
            status.setText(messages.get("reel.unavailable"));
        }
    }

    Node node() {
        return root;
    }

    private void make() {
        CancelToken token = new CancelToken();
        running = token;
        make.setDisable(true);
        hideNames.setDisable(true);
        openFolder.setVisible(false);
        openFolder.setManaged(false);
        cancel.setVisible(true);
        cancel.setManaged(true);
        Settings.Reel options = services.settings().current().reel();
        int total = ReelTimeline.of(options.seconds()).frameCount();
        status.setText(messages.format("reel.drawing", 0, total));
        AtomicInteger shown = new AtomicInteger();
        Path work = services.workRoot().get().resolve("reel-" + session.id());
        Path folder = services.platform().appPaths().reels();
        ReelMaker maker =
                new ReelMaker(services.processes(), services.platform().toolLocator(), messages);
        CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return maker.make(
                                        session,
                                        options.seconds(),
                                        hideNames.isSelected(),
                                        work,
                                        folder,
                                        frame -> {
                                            // Update the line a few times a second, not for every frame.
                                            if (frame - shown.get() >= 10 || frame == total) {
                                                shown.set(frame);
                                                Platform.runLater(() ->
                                                        status.setText(messages.format("reel.drawing", frame, total)));
                                            }
                                        },
                                        token);
                            } catch (IOException e) {
                                throw new java.io.UncheckedIOException(e);
                            }
                        },
                        services.jobs())
                .whenComplete((result, error) -> Platform.runLater(() -> finished(result, error)));
    }

    private void finished(ReelMaker.Result result, Throwable error) {
        running = null;
        make.setDisable(false);
        hideNames.setDisable(false);
        cancel.setVisible(false);
        cancel.setManaged(false);
        if (error == null) {
            String text = messages.format("reel.saved", result.file().getFileName());
            if (!result.codec().noteKey().isEmpty()) {
                text += " " + messages.get(result.codec().noteKey());
            }
            status.setText(text);
            openFolder.setVisible(true);
            openFolder.setManaged(true);
            return;
        }
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        if (cause instanceof OperationCancelledException) {
            status.setText(messages.get("reel.cancelled"));
        } else if (cause instanceof RetroBurnException known) {
            LOG.log(Level.WARNING, "Could not make the reel: " + known.technicalDetail(), known);
            status.setText(messages.format(known.userMessage()));
        } else {
            LOG.log(Level.WARNING, "Could not make the reel", cause);
            status.setText(messages.get("reel.error.encodeFailed"));
        }
    }

    /** Opens the reels folder in the file manager, off the JavaFX thread. */
    private void openFolder() {
        Path folder = services.platform().appPaths().reels();
        services.background().execute(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(folder.toFile());
                    return;
                }
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.INFO, "Could not open " + folder, e);
            }
            Platform.runLater(() -> status.setText(messages.format("reel.folderIs", folder)));
        });
    }
}
