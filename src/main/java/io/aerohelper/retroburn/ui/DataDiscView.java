// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.ByteSizes;
import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.project.BurnJobFactory;
import io.aerohelper.retroburn.project.DataCompilation;
import io.aerohelper.retroburn.project.DiscCapacity;
import io.aerohelper.retroburn.project.ImageSizeEstimate;
import io.aerohelper.retroburn.project.JobKind;
import io.aerohelper.retroburn.project.TreeSize;
import io.aerohelper.retroburn.project.TreeSizeScanner;
import io.aerohelper.retroburn.project.VolumeLabel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/**
 * A data disc: files and folders at the disc's root, a volume label, the total against the disc's
 * capacity, and Burn. The same screen, without the drive bar, serves "Create ISO".
 */
final class DataDiscView implements Navigation.Screen {

    private static final Logger LOG = Logger.getLogger(DataDiscView.class.getName());
    private static final KeyCombination ADD_FILES = new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN);

    enum Mode {
        BURN,
        CREATE_ISO
    }

    private final UiServices services;
    private final Navigation navigation;
    private final Messages messages;
    private final Mode mode;
    private final BurnFlow flow;
    private final BurnJobFactory factory;
    private final DriveBar driveBar;
    private final CapacityBar capacity;
    private final TextField labelField = new TextField();
    private final Label labelHint = new Label();
    private final TableView<DiscEntry> table = new TableView<>();
    private final Label scanProblem = new Label();
    private final Button burn = new Button();
    private final Button createIso = new Button();
    private final BorderPane root = new BorderPane();

    private DataCompilation compilation = DataCompilation.empty();
    private CancelToken scanCancel = new CancelToken();
    /** Estimated image size; -1 while the sources are being scanned. */
    private long estimate = 0;

    private boolean unreadable;

    DataDiscView(UiServices services, Navigation navigation, Mode mode) {
        this.services = services;
        this.navigation = navigation;
        this.messages = services.messages();
        this.mode = mode;
        this.flow = new BurnFlow(services, navigation);
        this.factory = new BurnJobFactory(
                services.platform().burnEngine(), services.platform().discImageBuilder());
        this.capacity = new CapacityBar(messages);

        VBox top = new VBox();
        if (mode == Mode.BURN) {
            driveBar = new DriveBar(services, this::erase);
            driveBar.media().addListener((observable, before, after) -> showCapacity());
            top.getChildren().add(driveBar.node());
        } else {
            driveBar = null;
        }
        Label heading = new Label(messages.get(mode == Mode.BURN ? "home.card.dataDisc" : "home.card.createIso"));
        heading.getStyleClass().add("view-heading");
        top.getChildren().addAll(heading, capacity.node());
        root.setTop(top);

        labelField.setText(VolumeLabel.FALLBACK);
        labelField.setAccessibleText(messages.get("data.label"));
        labelField.textProperty().addListener((observable, before, after) -> showLabelHint());
        labelHint.getStyleClass().add("field-hint");
        HBox labelRow = new HBox(new Label(messages.get("data.label")), labelField, labelHint);
        labelRow.getStyleClass().add("form-row");

        TableColumn<DiscEntry, String> name = new TableColumn<>(messages.get("data.column.name"));
        name.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().discPath().substring(1)));
        name.setPrefWidth(260);
        TableColumn<DiscEntry, String> source = new TableColumn<>(messages.get("data.column.source"));
        source.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().source().toString()));
        source.setPrefWidth(480);
        table.getColumns().add(name);
        table.getColumns().add(source);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPlaceholder(new Label(messages.get("data.empty")));
        table.setAccessibleText(messages.get("data.items"));
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                removeSelected();
            }
        });
        table.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        table.setOnDragDropped(event -> {
            boolean dropped = event.getDragboard().hasFiles();
            if (dropped) {
                add(event.getDragboard().getFiles().stream().map(File::toPath).toList());
            }
            event.setDropCompleted(dropped);
            event.consume();
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        Button addFiles = new Button(messages.get("data.addFiles"));
        addFiles.setOnAction(event -> chooseFiles());
        Button addFolder = new Button(messages.get("data.addFolder"));
        addFolder.setOnAction(event -> chooseFolder());
        Button remove = new Button(messages.get("data.remove"));
        remove.setOnAction(event -> removeSelected());
        remove.disableProperty()
                .bind(table.getSelectionModel().selectedItemProperty().isNull());
        scanProblem.getStyleClass().add("confirm-blocker");
        scanProblem.setWrapText(true);
        scanProblem.setVisible(false);
        scanProblem.setManaged(false);

        burn.setText(messages.get("data.burn"));
        burn.getStyleClass().add("primary-button");
        burn.setOnAction(event -> burn());
        createIso.setText(messages.get("data.createIso"));
        createIso.setOnAction(event -> createIso());
        if (mode == Mode.CREATE_ISO) {
            createIso.getStyleClass().add("primary-button");
            burn.setVisible(false);
            burn.setManaged(false);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button home = new Button(messages.get("view.home"));
        home.setOnAction(event -> navigation.showHome());
        HBox actions = new HBox(addFiles, addFolder, remove, spacer, home, createIso, burn);
        actions.getStyleClass().add("view-actions");

        VBox centre = new VBox(labelRow, table, scanProblem, actions);
        centre.getStyleClass().add("view-content");
        root.setCenter(centre);
        root.getStyleClass().add("project-view");
        root.sceneProperty().addListener((observable, before, scene) -> {
            if (scene != null) {
                scene.getAccelerators().put(ADD_FILES, this::chooseFiles);
            }
        });
        refresh();
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void close() {
        scanCancel.cancel();
        if (driveBar != null) {
            driveBar.close();
        }
        if (root.getScene() != null) {
            root.getScene().getAccelerators().remove(ADD_FILES);
        }
    }

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get("data.addFiles"));
        List<File> files = chooser.showOpenMultipleDialog(navigation.window());
        if (files != null) {
            add(files.stream().map(File::toPath).toList());
        }
    }

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(messages.get("data.addFolder"));
        File folder = chooser.showDialog(navigation.window());
        if (folder != null) {
            add(List.of(folder.toPath()));
        }
    }

    private void add(Collection<Path> sources) {
        try {
            compilation = compilation.add(sources);
        } catch (RetroBurnException e) {
            alert(messages.format(e.userMessage()));
            return;
        }
        refresh();
    }

    private void removeSelected() {
        List<String> paths = table.getSelectionModel().getSelectedItems().stream()
                .map(DiscEntry::discPath)
                .toList();
        compilation = compilation.remove(paths);
        refresh();
    }

    /** Shows the items and recounts their size in the background. */
    private void refresh() {
        table.setItems(FXCollections.observableArrayList(compilation.list()));
        CoverContents.publish(
                services,
                CoverContents.data(
                        messages.get("home.card.dataDisc"), labelField.getText(), compilation.list(), LocalDate.now()));
        scanCancel.cancel();
        if (compilation.isEmpty()) {
            estimate = 0;
            unreadable = false;
            showCapacity();
            return;
        }
        CancelToken cancel = new CancelToken();
        scanCancel = cancel;
        estimate = -1;
        showCapacity();
        DataCompilation scanned = compilation;
        CompletableFuture.supplyAsync(() -> new TreeSizeScanner().scan(scanned, cancel), services.background())
                .whenComplete((size, error) -> Platform.runLater(() -> {
                    if (cancel.isCancelled() || error instanceof OperationCancelledException) {
                        return;
                    }
                    if (error != null) {
                        LOG.log(Level.WARNING, "Could not scan the compilation", error);
                        estimate = 0;
                        unreadable = true;
                        showScanProblem(messages.get("data.scanFailed"));
                    } else {
                        showScanResult(size);
                    }
                    showCapacity();
                }));
    }

    private void showScanResult(TreeSize size) {
        estimate = ImageSizeEstimate.bytes(size);
        unreadable = size.unreadableCount() > 0;
        if (unreadable) {
            showScanProblem(messages.format(
                    "data.unreadable", size.unreadableCount(), size.unreadable().getFirst()));
        } else {
            scanProblem.setVisible(false);
            scanProblem.setManaged(false);
        }
    }

    private void showScanProblem(String text) {
        scanProblem.setText(text);
        scanProblem.setVisible(true);
        scanProblem.setManaged(true);
    }

    private void showCapacity() {
        if (estimate < 0) {
            capacity.calculating();
        } else {
            MediaInfo media = driveBar == null ? null : driveBar.media().get();
            capacity.show(estimate, media == null ? 0 : DiscCapacity.usableBytes(media));
        }
        boolean ready = !compilation.isEmpty() && estimate > 0 && !unreadable;
        burn.setDisable(!ready);
        createIso.setDisable(!ready);
    }

    private void showLabelHint() {
        String typed = labelField.getText();
        labelHint.setText(
                VolumeLabel.isValid(typed)
                        ? ""
                        : messages.format(
                                "data.labelHint", VolumeLabel.sanitize(typed).text(), VolumeLabel.MAX_LENGTH));
    }

    private VolumeLabel label() {
        return VolumeLabel.sanitize(labelField.getText());
    }

    private void burn() {
        DataCompilation burned = compilation;
        VolumeLabel label = label();
        flow.start(
                driveBar,
                new BurnFlow.Job(
                        JobKind.DATA_DISC,
                        estimate,
                        true,
                        (plan, blankMode) -> factory.dataDisc(plan, burned, label),
                        messages.get("progress.title.dataDisc"),
                        messages.get("result.text.dataDisc")));
    }

    private void erase() {
        flow.start(
                driveBar,
                new BurnFlow.Job(
                        JobKind.ERASE,
                        0,
                        false,
                        factory::erase,
                        messages.get("progress.title.erase"),
                        messages.get("result.text.erase")));
    }

    private void createIso() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get("data.createIso"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.get("data.isoFiles"), "*.iso"));
        chooser.setInitialFileName(label().text().replace(' ', '_') + ".iso");
        File file = chooser.showSaveDialog(navigation.window());
        if (file == null) {
            return;
        }
        Path target = file.toPath();
        long free = freeSpace(target.toAbsolutePath().getParent());
        if (free < estimate) {
            alert(messages.format(
                    "data.targetFull", target.getParent(), ByteSizes.format(estimate), ByteSizes.format(free)));
            return;
        }
        flow.startWithoutDrive(
                factory.createIso(compilation, label(), target, estimate),
                messages.get("progress.title.createIso"),
                messages.format("result.text.createIso", target));
    }

    private static long freeSpace(Path folder) {
        try {
            return Files.getFileStore(folder).getUsableSpace();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not read free space of " + folder, e);
            return Long.MAX_VALUE;
        }
    }

    private void alert(String text) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(navigation.window());
        alert.setHeaderText(null);
        alert.setContentText(text);
        alert.showAndWait();
    }
}
