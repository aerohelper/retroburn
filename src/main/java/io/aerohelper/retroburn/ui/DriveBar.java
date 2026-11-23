// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.common.OperatingSystem;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaState;
import io.aerohelper.retroburn.platform.DriveWatcher;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * The strip at the top of every burning screen: drive picker, USB badge, what disc is inserted,
 * refresh, and "Erase disc…" for rewritable discs with data. Follows hot-plug through the
 * platform's {@link DriveWatcher}; reads the disc on selection, on refresh and when the window gets
 * the focus. All updates happen on the JavaFX Application Thread.
 */
public final class DriveBar {

    private static final Logger LOG = Logger.getLogger(DriveBar.class.getName());

    private final UiServices services;
    private final Messages messages;
    private final DriveWatcher watcher;
    private final ComboBox<DriveInfo> drives = new ComboBox<>();
    private final Label usbBadge = new Label();
    private final Label mediaLabel = new Label();
    private final Label emptyLabel = new Label();
    private final Button refresh = new Button();
    private final Button erase = new Button();
    private final HBox root;
    private final ObjectProperty<MediaInfo> media = new SimpleObjectProperty<>();
    /** Ignores media answers for an earlier selection that arrive late. */
    private final AtomicLong readGeneration = new AtomicLong();

    public DriveBar(UiServices services, Runnable onErase) {
        this.services = Objects.requireNonNull(services, "services");
        this.messages = services.messages();
        this.watcher = services.platform().createDriveWatcher();

        drives.setAccessibleText(messages.get("drive.picker"));
        drives.setCellFactory(list -> new DriveCell());
        drives.setButtonCell(new DriveCell());
        drives.getSelectionModel().selectedItemProperty().addListener((observable, before, after) -> readMedia());

        usbBadge.setText(messages.get("drive.usb"));
        usbBadge.getStyleClass().add("usb-badge");
        usbBadge.setAccessibleText(messages.get("drive.usbAccessible"));
        mediaLabel.getStyleClass().add("drive-media");
        emptyLabel.setText(messages.format(UserMessage.of("burn.blocker.noDrive")));
        emptyLabel.getStyleClass().add("drive-empty");
        emptyLabel.setWrapText(true);

        refresh.setText(messages.get("drive.refresh"));
        refresh.setOnAction(event -> readMedia());
        erase.setText(messages.get("drive.erase"));
        erase.setOnAction(event -> onErase.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        root = new HBox(drives, usbBadge, mediaLabel, emptyLabel, spacer, erase, refresh);
        root.getStyleClass().add("drive-bar");
        update(List.of());

        watcher.start(found -> Platform.runLater(() -> update(found)));
        root.sceneProperty().addListener((observable, before, scene) -> {
            if (scene != null && scene.getWindow() != null) {
                scene.getWindow().focusedProperty().addListener((o, wasFocused, focused) -> {
                    if (focused) {
                        readMedia();
                    }
                });
            }
        });
    }

    public Node node() {
        return root;
    }

    public Optional<DriveInfo> selectedDrive() {
        return Optional.ofNullable(drives.getValue());
    }

    /** The disc in the selected drive; null while it is being read or when no drive is selected. */
    public ReadOnlyObjectProperty<MediaInfo> media() {
        return media;
    }

    public ReadOnlyObjectProperty<DriveInfo> drive() {
        return drives.valueProperty();
    }

    /** The watcher, so a job can pause hot-plug polling while it writes. */
    public DriveWatcher watcher() {
        return watcher;
    }

    /** Reads the disc again, for example after a burn or an erase. */
    public void readMedia() {
        long generation = readGeneration.incrementAndGet();
        DriveInfo drive = drives.getValue();
        media.set(null);
        showMedia(null, drive);
        if (drive == null || !drive.accessible()) {
            return;
        }
        DriveId id = drive.id();
        CompletableFuture.supplyAsync(() -> services.platform().burnEngine().readMedia(id), services.background())
                .whenComplete((found, error) -> Platform.runLater(() -> {
                    if (generation != readGeneration.get()) {
                        return;
                    }
                    if (error != null) {
                        LOG.log(Level.WARNING, "Could not read the disc in " + id.value(), error);
                        mediaLabel.setText(errorText(error));
                        return;
                    }
                    media.set(found);
                    showMedia(found, drive);
                }));
    }

    public void close() {
        watcher.close();
    }

    private void update(List<DriveInfo> found) {
        DriveInfo previous = drives.getValue();
        drives.setItems(FXCollections.observableArrayList(found));
        // Keep the choice if that drive is still there; otherwise fall back to the first one.
        Optional<DriveInfo> keep = previous == null
                ? Optional.empty()
                : found.stream()
                        .filter(drive -> drive.id().equals(previous.id()))
                        .findFirst();
        drives.setValue(keep.orElse(found.isEmpty() ? null : found.getFirst()));
        boolean none = found.isEmpty();
        drives.setVisible(!none);
        drives.setManaged(!none);
        emptyLabel.setVisible(none);
        emptyLabel.setManaged(none);
        refresh.setDisable(none);
        if (none) {
            // No selection change fires when there was none before, so clear the badge and Erase here.
            media.set(null);
            showMedia(null, null);
        }
        if (keep.isPresent()) {
            // Same drive as before: the selection listener did not fire, so read the disc anyway.
            readMedia();
        }
    }

    private void showMedia(MediaInfo found, DriveInfo drive) {
        boolean usb = drive != null && drive.connection() == ConnectionType.USB;
        usbBadge.setVisible(usb);
        usbBadge.setManaged(usb);
        if (drive == null) {
            mediaLabel.setText("");
        } else if (!drive.accessible()) {
            mediaLabel.setText(messages.format(UserMessage.of(
                    services.platform().operatingSystem() == OperatingSystem.LINUX
                            ? "burn.blocker.noPermissionLinux"
                            : "burn.blocker.noPermission")));
        } else if (found == null) {
            mediaLabel.setText(messages.get("drive.reading"));
        } else {
            mediaLabel.setText(MediaText.describe(found, messages));
        }
        boolean erasable = found != null && found.kind().isRewritable() && found.state() != MediaState.BLANK;
        erase.setVisible(erasable);
        erase.setManaged(erasable);
    }

    private String errorText(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        return cause instanceof RetroBurnException known
                ? messages.format(known.userMessage())
                : messages.get("drive.readFailed");
    }

    private static final class DriveCell extends ListCell<DriveInfo> {
        @Override
        protected void updateItem(DriveInfo drive, boolean empty) {
            super.updateItem(drive, empty);
            setText(empty || drive == null ? null : drive.displayNameWithLocation());
        }
    }
}
