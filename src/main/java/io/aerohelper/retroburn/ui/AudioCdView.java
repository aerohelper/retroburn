// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.audio.AudioTools;
import io.aerohelper.retroburn.audio.CdAudio;
import io.aerohelper.retroburn.audio.CdTextRules;
import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.engine.AudioCdEngine;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.project.AudioCompilation;
import io.aerohelper.retroburn.project.AudioJobFactory;
import io.aerohelper.retroburn.project.AudioPreCheck;
import io.aerohelper.retroburn.project.BurnJobFactory;
import io.aerohelper.retroburn.project.DiscCapacity;
import io.aerohelper.retroburn.project.JobKind;
import io.aerohelper.retroburn.settings.Settings;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Audio CD: add music files, edit the album and track texts, reorder, see the length against the
 * disc, burn. See {@code docs/design/LLD-audio-cd.md} section 1.
 */
final class AudioCdView implements Navigation.Screen {

    private static final Logger LOG = Logger.getLogger(AudioCdView.class.getName());

    private final UiServices services;
    private final Navigation navigation;
    private final Messages messages;
    private final BurnFlow flow;
    private final BurnJobFactory dataFactory;
    private final AudioJobFactory audioFactory;
    private final AudioCdEngine engine;
    private final AudioTools tools;
    private final DriveBar driveBar;
    private final CapacityBar capacity;
    private final TextField albumTitle = new TextField();
    private final TextField albumPerformer = new TextField();
    private final CheckBox cdText = new CheckBox();
    private final CheckBox loudness = new CheckBox();
    private final TableView<AudioCompilation.Track> table = new TableView<>();
    private final Label problems = new Label();
    private final Button burn = new Button();
    private final BorderPane root = new BorderPane();
    private final CancelToken probing = new CancelToken();

    private AudioCompilation compilation = AudioCompilation.empty();
    private int pendingProbes;
    private final List<String> refused = new ArrayList<>();

    AudioCdView(UiServices services, Navigation navigation) {
        this.services = services;
        this.navigation = navigation;
        this.messages = services.messages();
        this.flow = new BurnFlow(services, navigation);
        this.engine = services.platform().audioCdEngine();
        this.tools = new AudioTools(services.processes(), services.platform().toolLocator());
        this.dataFactory = new BurnJobFactory(
                services.platform().burnEngine(), services.platform().discImageBuilder());
        this.audioFactory = new AudioJobFactory(
                services.platform().burnEngine(),
                engine,
                tools,
                AudioJobFactory.defaultWorkers(Runtime.getRuntime().availableProcessors()));
        this.capacity = new CapacityBar(messages);
        this.driveBar = new DriveBar(services, this::erase);
        driveBar.media().addListener((observable, before, after) -> refresh());

        Label heading = new Label(messages.get("home.card.audioCd"));
        heading.getStyleClass().add("view-heading");
        root.setTop(new VBox(driveBar.node(), heading, capacity.node()));

        albumTitle.setPromptText(messages.get("audio.albumTitle"));
        albumTitle.setAccessibleText(messages.get("audio.albumTitle"));
        albumTitle.textProperty().addListener((observable, before, after) -> albumChanged());
        albumPerformer.setPromptText(messages.get("audio.albumPerformer"));
        albumPerformer.setAccessibleText(messages.get("audio.albumPerformer"));
        albumPerformer.textProperty().addListener((observable, before, after) -> albumChanged());
        HBox albumRow = new HBox(
                new Label(messages.get("audio.albumTitle")),
                albumTitle,
                new Label(messages.get("audio.albumPerformer")),
                albumPerformer);
        albumRow.getStyleClass().add("form-row");

        Settings.Audio audio = services.settings().current().audio();
        cdText.setText(messages.get("audio.cdText"));
        cdText.setSelected(audio.cdText() && engine.writesCdText());
        cdText.selectedProperty().addListener((observable, before, after) -> {
            saveAudioSettings();
            refresh();
        });
        // Where CD-Text cannot be written, say so in readable text rather than a faded checkbox.
        Label noCdText = new Label(messages.get("audio.cdTextUnavailable"));
        noCdText.getStyleClass().add("field-hint");
        cdText.setVisible(engine.writesCdText());
        cdText.setManaged(engine.writesCdText());
        noCdText.setVisible(!engine.writesCdText());
        noCdText.setManaged(!engine.writesCdText());
        loudness.setText(messages.get("audio.normalize"));
        loudness.setSelected(audio.normalizeLoudness());
        loudness.selectedProperty().addListener((observable, before, after) -> saveAudioSettings());
        HBox options = new HBox(cdText, noCdText, loudness);
        options.getStyleClass().add("form-row");

        table.setEditable(true);
        TableColumn<AudioCompilation.Track, String> number = new TableColumn<>("#");
        number.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(Integer.toString(table.getItems().indexOf(cell.getValue()) + 1)));
        number.setSortable(false);
        number.setPrefWidth(40);
        number.setMaxWidth(50);
        TableColumn<AudioCompilation.Track, String> title = new TableColumn<>(messages.get("audio.column.title"));
        title.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().title()));
        title.setCellFactory(TextFieldTableCell.forTableColumn());
        title.setOnEditCommit(event -> editTrack(event.getTablePosition().getRow(), event.getNewValue(), null));
        title.setSortable(false);
        title.setPrefWidth(240);
        TableColumn<AudioCompilation.Track, String> performer =
                new TableColumn<>(messages.get("audio.column.performer"));
        performer.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().performer()));
        performer.setCellFactory(TextFieldTableCell.forTableColumn());
        performer.setOnEditCommit(event -> editTrack(event.getTablePosition().getRow(), null, event.getNewValue()));
        performer.setSortable(false);
        performer.setPrefWidth(180);
        TableColumn<AudioCompilation.Track, String> length = new TableColumn<>(messages.get("audio.column.length"));
        length.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                CdAudio.minutesAndSeconds(cell.getValue().sectors())));
        length.setSortable(false);
        length.setPrefWidth(70);
        TableColumn<AudioCompilation.Track, String> file = new TableColumn<>(messages.get("data.column.source"));
        file.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().source().toString()));
        file.setSortable(false);
        List.of(number, title, performer, length, file).forEach(table.getColumns()::add);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(messages.get("audio.empty")));
        table.setAccessibleText(messages.get("audio.tracks"));
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE && table.getEditingCell() == null) {
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

        problems.getStyleClass().add("confirm-blocker");
        problems.setWrapText(true);

        Button addFiles = new Button(messages.get("data.addFiles"));
        addFiles.setOnAction(event -> chooseFiles());
        Button remove = new Button(messages.get("data.remove"));
        remove.setOnAction(event -> removeSelected());
        remove.disableProperty()
                .bind(table.getSelectionModel().selectedItemProperty().isNull());
        Button up = new Button(messages.get("audio.up"));
        up.setOnAction(event -> move(-1));
        up.disableProperty()
                .bind(table.getSelectionModel().selectedItemProperty().isNull());
        Button down = new Button(messages.get("audio.down"));
        down.setOnAction(event -> move(1));
        down.disableProperty()
                .bind(table.getSelectionModel().selectedItemProperty().isNull());
        burn.setText(messages.get("data.burn"));
        burn.getStyleClass().add("primary-button");
        burn.setOnAction(event -> burn());
        Button home = new Button(messages.get("view.home"));
        home.setOnAction(event -> navigation.showHome());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(addFiles, remove, up, down, spacer, home, burn);
        actions.getStyleClass().add("view-actions");

        VBox centre = new VBox(albumRow, options, table, problems, actions);
        centre.getStyleClass().add("view-content");
        root.setCenter(centre);
        root.getStyleClass().add("project-view");
        refresh();
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void close() {
        probing.cancel();
        driveBar.close();
    }

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get("data.addFiles"));
        chooser.getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter(
                                messages.get("audio.musicFiles"),
                                "*.mp3",
                                "*.flac",
                                "*.wav",
                                "*.ogg",
                                "*.opus",
                                "*.m4a",
                                "*.aac",
                                "*.wma",
                                "*.aiff"),
                        new FileChooser.ExtensionFilter(messages.get("audio.allFiles"), "*.*"));
        List<File> files = chooser.showOpenMultipleDialog(navigation.window());
        if (files != null) {
            add(files.stream().map(File::toPath).toList());
        }
    }

    /** Probes the files in the background and adds them in the order given. */
    private void add(List<Path> files) {
        pendingProbes += files.size();
        refresh();
        CompletableFuture.supplyAsync(
                        () -> {
                            List<AudioCompilation.Track> tracks = new ArrayList<>();
                            List<String> failures = new ArrayList<>();
                            for (Path file : files) {
                                try {
                                    tracks.add(AudioCompilation.Track.of(file, tools.probe(file, probing)));
                                } catch (RetroBurnException e) {
                                    failures.add(messages.format(e.userMessage()));
                                }
                            }
                            return new Probed(tracks, failures);
                        },
                        services.background())
                .whenComplete((probed, error) -> Platform.runLater(() -> {
                    pendingProbes -= files.size();
                    if (error != null) {
                        LOG.log(Level.WARNING, "Could not read the music files", error);
                        refused.add(messages.get("audio.readFailed"));
                    } else {
                        compilation = compilation.add(probed.tracks());
                        refused.addAll(probed.failures());
                        showAlbum();
                    }
                    refresh();
                }));
    }

    private record Probed(List<AudioCompilation.Track> tracks, List<String> failures) {}

    private void removeSelected() {
        int index = table.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            compilation = compilation.remove(index);
            refresh();
        }
    }

    private void move(int by) {
        int index = table.getSelectionModel().getSelectedIndex();
        int target = index + by;
        if (index >= 0 && target >= 0 && target < compilation.tracks().size()) {
            compilation = compilation.move(index, target);
            refresh();
            table.getSelectionModel().select(target);
        }
    }

    private void editTrack(int index, String title, String performer) {
        AudioCompilation.Track track = compilation.tracks().get(index);
        compilation = compilation.withTrack(
                index,
                new AudioCompilation.Track(
                        track.source(),
                        track.audio(),
                        title == null ? track.title() : title,
                        performer == null ? track.performer() : performer));
        refresh();
    }

    private void albumChanged() {
        if (!albumTitle.getText().equals(compilation.albumTitle())
                || !albumPerformer.getText().equals(compilation.albumPerformer())) {
            compilation = compilation.withAlbum(albumTitle.getText(), albumPerformer.getText());
            showProblems();
        }
    }

    /** Puts album texts filled in from tags into the fields. */
    private void showAlbum() {
        albumTitle.setText(compilation.albumTitle());
        albumPerformer.setText(compilation.albumPerformer());
    }

    private void refresh() {
        table.setItems(FXCollections.observableArrayList(compilation.tracks()));
        if (pendingProbes > 0) {
            capacity.calculating();
        } else {
            MediaInfo media = driveBar.media().get();
            capacity.showTime(compilation.discSectors(), media == null ? 0 : DiscCapacity.usableBytes(media) / 2048);
        }
        showProblems();
        burn.setDisable(compilation.tracks().isEmpty() || pendingProbes > 0);
    }

    /** Files that could not be added, then CD-Text fields that cannot be written. */
    private void showProblems() {
        List<String> lines = new ArrayList<>(refused);
        if (cdText.isSelected()) {
            for (AudioCompilation.CdTextProblem problem : compilation.cdTextProblems()) {
                lines.add(messages.format(
                        "burn.blocker.cdText."
                                + (problem.status() == CdTextRules.Status.NEEDS_LATIN ? "needs_latin" : "too_long"),
                        problem.track(),
                        problem.field().name().toLowerCase(Locale.ROOT)));
            }
        }
        problems.setText(String.join("\n", lines));
        problems.setVisible(!lines.isEmpty());
        problems.setManaged(!lines.isEmpty());
    }

    private void saveAudioSettings() {
        Settings settings = services.settings()
                .current()
                .withAudio(new Settings.Audio(
                        engine.writesCdText()
                                ? cdText.isSelected()
                                : services.settings().current().audio().cdText(),
                        loudness.isSelected()));
        services.background().execute(() -> services.settings().save(settings));
    }

    private void burn() {
        AudioCompilation burned = compilation;
        boolean withCdText = cdText.isSelected();
        boolean normalize = loudness.isSelected();
        refused.clear();
        flow.start(
                driveBar,
                new BurnFlow.Job(
                        JobKind.AUDIO_CD,
                        AudioPreCheck.blocksToWrite(burned),
                        true,
                        (plan, blankMode) -> audioFactory.audioCd(plan, burned, withCdText, normalize),
                        messages.get("progress.title.audioCd"),
                        messages.get("result.text.audioCd"),
                        request -> AudioPreCheck.check(request, burned, withCdText, engine.writesCdText())));
    }

    private void erase() {
        flow.start(
                driveBar,
                new BurnFlow.Job(
                        JobKind.ERASE,
                        0,
                        false,
                        dataFactory::erase,
                        messages.get("progress.title.erase"),
                        messages.get("result.text.erase")));
    }
}
