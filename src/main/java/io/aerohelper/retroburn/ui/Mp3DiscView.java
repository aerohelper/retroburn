// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.audio.AudioTools;
import io.aerohelper.retroburn.audio.Mp3Encoding;
import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.project.AudioJobFactory;
import io.aerohelper.retroburn.project.BurnJobFactory;
import io.aerohelper.retroburn.project.BurnPlan;
import io.aerohelper.retroburn.project.BurnPreCheck;
import io.aerohelper.retroburn.project.DiscCapacity;
import io.aerohelper.retroburn.project.JobKind;
import io.aerohelper.retroburn.project.Mp3Compilation;
import io.aerohelper.retroburn.project.Mp3DiscPlan;
import io.aerohelper.retroburn.project.Mp3JobFactory;
import io.aerohelper.retroburn.project.Mp3Layout;
import io.aerohelper.retroburn.project.VolumeLabel;
import io.aerohelper.retroburn.settings.Settings;
import io.aerohelper.retroburn.tools.Feature;
import io.aerohelper.retroburn.tools.FeatureAvailability;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/**
 * MP3 disc: add music files and folders, choose the layout and car stereo rules, see exactly what
 * the disc will hold, burn or save as ISO. See {@code docs/design/LLD-mp3-disc.md}.
 */
final class Mp3DiscView implements Navigation.Screen {

    private static final Logger LOG = Logger.getLogger(Mp3DiscView.class.getName());
    static final Set<String> MUSIC_EXTENSIONS =
            Set.of("mp3", "flac", "wav", "ogg", "oga", "opus", "m4a", "aac", "wma", "aif", "aiff", "ape", "mpc", "wv");

    private final UiServices services;
    private final Navigation navigation;
    private final Messages messages;
    private final BurnFlow flow;
    private final BurnJobFactory dataFactory;
    private final Mp3JobFactory mp3Factory;
    private final AudioTools tools;
    private final DriveBar driveBar;
    private final CapacityBar capacity;
    private final TextField labelField = new TextField(VolumeLabel.FALLBACK);
    private final ComboBox<Mp3Layout> layout = new ComboBox<>();
    private final CheckBox car = new CheckBox();
    private final CheckBox convert = new CheckBox();
    private final ComboBox<Mp3Encoding.Bitrate> bitrate = new ComboBox<>();
    private final CheckBox playlists = new CheckBox();
    private final TableView<Mp3DiscPlan.Entry> table = new TableView<>();
    private final Label summary = new Label();
    private final Label notes = new Label();
    private final Button burn = new Button();
    private final Button createIso = new Button();
    private final BorderPane root = new BorderPane();
    private final CancelToken probing = new CancelToken();
    private final List<String> refused = new ArrayList<>();

    private Mp3Compilation compilation = Mp3Compilation.empty();
    private Mp3DiscPlan plan = Mp3DiscPlan.of(Mp3Compilation.empty(), defaultOptions());
    private int pendingProbes;

    Mp3DiscView(UiServices services, Navigation navigation) {
        this.services = services;
        this.navigation = navigation;
        this.messages = services.messages();
        this.flow = new BurnFlow(services, navigation);
        this.tools = new AudioTools(services.processes(), services.platform().toolLocator());
        this.dataFactory = new BurnJobFactory(
                services.platform().burnEngine(), services.platform().discImageBuilder());
        this.mp3Factory = new Mp3JobFactory(
                dataFactory,
                services.platform().discImageBuilder(),
                tools,
                AudioJobFactory.defaultWorkers(Runtime.getRuntime().availableProcessors()));
        this.capacity = new CapacityBar(messages);
        this.driveBar = new DriveBar(services, this::erase);
        driveBar.media().addListener((observable, before, after) -> showCapacity());

        Label heading = new Label(messages.get("home.card.mp3Disc"));
        heading.getStyleClass().add("view-heading");
        summary.getStyleClass().add("field-hint");
        root.setTop(new VBox(driveBar.node(), heading, capacity.node()));

        labelField.setAccessibleText(messages.get("data.label"));
        HBox labelRow = new HBox(new Label(messages.get("data.label")), labelField);
        labelRow.getStyleClass().add("form-row");

        Settings.Mp3 saved = services.settings().current().mp3();
        layout.getItems().setAll(Mp3Layout.values());
        layout.setConverter(
                converter(value -> messages.get("mp3.layout." + value.name().toLowerCase(Locale.ROOT))));
        layout.setValue(parse(Mp3Layout.class, saved.layout(), Mp3Layout.KEEP_FOLDERS));
        layout.setAccessibleText(messages.get("mp3.layout"));
        car.setText(messages.get("mp3.car"));
        car.setSelected(saved.carCompat());
        convert.setText(messages.get("mp3.convert"));
        convert.setSelected(saved.convert());
        bitrate.getItems().setAll(Mp3Encoding.Bitrate.values());
        bitrate.setConverter(
                converter(value -> messages.get("mp3.bitrate." + value.name().toLowerCase(Locale.ROOT))));
        bitrate.setValue(parse(Mp3Encoding.Bitrate.class, saved.bitrate(), Mp3Encoding.Bitrate.CBR_192));
        bitrate.setAccessibleText(messages.get("mp3.bitrate"));
        bitrate.disableProperty().bind(convert.selectedProperty().not());
        playlists.setText(messages.get("mp3.playlists"));
        playlists.setSelected(saved.playlists());
        layout.valueProperty().addListener((observable, before, after) -> optionsChanged());
        bitrate.valueProperty().addListener((observable, before, after) -> optionsChanged());
        List.of(car, convert, playlists)
                .forEach(box -> box.selectedProperty().addListener((observable, before, after) -> optionsChanged()));
        HBox options = new HBox(new Label(messages.get("mp3.layout")), layout, car, convert, bitrate, playlists);
        options.getStyleClass().add("form-row");

        TableColumn<Mp3DiscPlan.Entry, String> name = new TableColumn<>(messages.get("data.column.name"));
        name.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().discPath().substring(1)));
        name.setPrefWidth(320);
        TableColumn<Mp3DiscPlan.Entry, String> how = new TableColumn<>(messages.get("mp3.column.how"));
        how.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(messages.get(cell.getValue().convert() ? "mp3.converted" : "mp3.copied")));
        how.setPrefWidth(100);
        TableColumn<Mp3DiscPlan.Entry, String> source = new TableColumn<>(messages.get("data.column.source"));
        source.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().item().source().toString()));
        List.of(name, how, source).forEach(table.getColumns()::add);
        table.getColumns().forEach(column -> column.setSortable(false));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(messages.get("mp3.empty")));
        table.setAccessibleText(messages.get("data.items"));
        table.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        table.setOnDragDropped(event -> {
            boolean dropped = event.getDragboard().hasFiles();
            if (dropped) {
                event.getDragboard().getFiles().forEach(file -> add(file.toPath()));
            }
            event.setDropCompleted(dropped);
            event.consume();
        });
        VBox.setVgrow(table, Priority.ALWAYS);
        notes.getStyleClass().add("confirm-blocker");
        notes.setWrapText(true);

        Button addFiles = new Button(messages.get("data.addFiles"));
        addFiles.setOnAction(event -> chooseFiles());
        Button addFolder = new Button(messages.get("data.addFolder"));
        addFolder.setOnAction(event -> chooseFolder());
        Button clear = new Button(messages.get("mp3.clear"));
        clear.setOnAction(event -> {
            compilation = Mp3Compilation.empty();
            refused.clear();
            replan();
        });
        burn.setText(messages.get("data.burn"));
        burn.getStyleClass().add("primary-button");
        burn.setOnAction(event -> burn());
        createIso.setText(messages.get("data.createIso"));
        createIso.setOnAction(event -> createIso());
        Button home = new Button(messages.get("view.home"));
        home.setOnAction(event -> navigation.showHome());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(addFiles, addFolder, clear, spacer, home, createIso, burn);
        actions.getStyleClass().add("view-actions");

        VBox centre = new VBox(summary, labelRow, options, table, notes, actions);
        centre.getStyleClass().add("view-content");
        root.setCenter(centre);
        root.getStyleClass().add("project-view");
        replan();
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
        List<String> patterns = MUSIC_EXTENSIONS.stream()
                .sorted()
                .map(extension -> "*." + extension)
                .toList();
        chooser.getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter(messages.get("audio.musicFiles"), patterns),
                        new FileChooser.ExtensionFilter(messages.get("audio.allFiles"), "*.*"));
        List<File> files = chooser.showOpenMultipleDialog(navigation.window());
        if (files != null) {
            files.forEach(file -> add(file.toPath()));
        }
    }

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(messages.get("data.addFolder"));
        File folder = chooser.showDialog(navigation.window());
        if (folder != null) {
            add(folder.toPath());
        }
    }

    /** Adds a file, or every music file in a folder, probing them in the background. */
    private void add(Path chosen) {
        pendingProbes++;
        showCapacity();
        CompletableFuture.supplyAsync(() -> probeAll(chosen), services.background())
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    pendingProbes--;
                    if (error != null) {
                        LOG.log(Level.WARNING, "Could not read " + chosen, error);
                        refused.add(messages.format("mp3.readFailed", chosen));
                    } else {
                        compilation = compilation.add(result.items());
                        refused.addAll(result.failures());
                    }
                    replan();
                }));
    }

    private record Probed(List<Mp3Compilation.Item> items, List<String> failures) {}

    private Probed probeAll(Path chosen) {
        List<Mp3Compilation.Item> items = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        if (Files.isDirectory(chosen)) {
            Path base = chosen.getParent() == null ? chosen : chosen.getParent();
            try (Stream<Path> walk = Files.walk(chosen)) {
                List<Path> files = walk.filter(Files::isRegularFile)
                        .filter(Mp3DiscView::isMusic)
                        .sorted()
                        .toList();
                for (Path file : files) {
                    List<String> folders = new ArrayList<>();
                    base.relativize(file.getParent()).forEach(part -> folders.add(part.toString()));
                    probe(file, folders, items, failures);
                }
            } catch (IOException e) {
                failures.add(messages.format("mp3.readFailed", chosen));
            }
        } else {
            probe(chosen, List.of(), items, failures);
        }
        return new Probed(items, failures);
    }

    private void probe(Path file, List<String> folders, List<Mp3Compilation.Item> items, List<String> failures) {
        try {
            items.add(new Mp3Compilation.Item(file, tools.probe(file, probing), folders));
        } catch (RetroBurnException e) {
            failures.add(messages.format(e.userMessage()));
        }
    }

    static boolean isMusic(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && MUSIC_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private void optionsChanged() {
        Settings settings = services.settings()
                .current()
                .withMp3(new Settings.Mp3(
                        layout.getValue().name(),
                        car.isSelected(),
                        convert.isSelected(),
                        bitrate.getValue().name(),
                        playlists.isSelected()));
        services.background().execute(() -> services.settings().save(settings));
        replan();
    }

    private Mp3DiscPlan.Options options() {
        return new Mp3DiscPlan.Options(
                layout.getValue(), car.isSelected(), convert.isSelected(), bitrate.getValue(), playlists.isSelected());
    }

    private static Mp3DiscPlan.Options defaultOptions() {
        return new Mp3DiscPlan.Options(Mp3Layout.KEEP_FOLDERS, true, true, Mp3Encoding.Bitrate.CBR_192, false);
    }

    private void replan() {
        plan = Mp3DiscPlan.of(compilation, options());
        table.setItems(FXCollections.observableArrayList(plan.entries()));
        Duration total = plan.totalDuration();
        summary.setText(messages.format(
                "mp3.summary",
                plan.entries().size(),
                "%d:%02d:%02d".formatted(total.toHours(), total.toMinutesPart(), total.toSecondsPart())));
        List<String> lines = new ArrayList<>(refused);
        if (!plan.skipped().isEmpty()) {
            lines.add(messages.format("mp3.skipped", plan.skipped().size()));
        }
        for (Mp3DiscPlan.Warning warning : plan.warnings()) {
            lines.add(
                    warning.kind() == Mp3DiscPlan.Warning.Kind.CROWDED_FOLDER
                            ? messages.format("mp3.warning.crowded", warning.folder(), warning.count())
                            : messages.format("mp3.warning.folders", warning.count()));
        }
        if (plan.needsConversion()) {
            transcodingUnavailable().ifPresent(reason -> lines.add(messages.format(reason)));
        }
        notes.setText(String.join("\n", lines));
        notes.setVisible(!lines.isEmpty());
        notes.setManaged(!lines.isEmpty());
        showCapacity();
    }

    private Optional<UserMessage> transcodingUnavailable() {
        FeatureAvailability availability = services.features().get().get(Feature.MP3_TRANSCODING);
        return availability == null || availability.isAvailable()
                ? Optional.empty()
                : availability.reasons().stream().findFirst();
    }

    private void showCapacity() {
        if (pendingProbes > 0) {
            capacity.calculating();
        } else {
            MediaInfo media = driveBar.media().get();
            capacity.show(
                    plan.entries().isEmpty() ? 0 : plan.estimatedBytes(),
                    media == null ? 0 : DiscCapacity.usableBytes(media));
        }
        CoverContents.publish(
                services, CoverContents.mp3(messages.get("home.card.mp3Disc"), labelField.getText(), plan));
        boolean ready = !plan.entries().isEmpty() && pendingProbes == 0;
        burn.setDisable(!ready);
        createIso.setDisable(!ready);
    }

    private BurnPlan check(BurnPreCheck.Request request, Mp3DiscPlan checked) {
        BurnPlan base = BurnPreCheck.check(request);
        if (!checked.needsConversion() || transcodingUnavailable().isEmpty()) {
            return base;
        }
        List<UserMessage> blockers = new ArrayList<>(base.blockers());
        blockers.add(transcodingUnavailable().get());
        return new BurnPlan(
                base.kind(),
                base.drive(),
                base.media(),
                base.bytesToWrite(),
                base.eraseFirst(),
                base.verify(),
                blockers,
                base.warnings(),
                base.showUsbTip());
    }

    private void burn() {
        Mp3DiscPlan burned = plan;
        VolumeLabel label = VolumeLabel.sanitize(labelField.getText());
        Mp3Encoding.Bitrate chosen = bitrate.getValue();
        flow.start(
                driveBar,
                new BurnFlow.Job(
                        JobKind.MP3_DISC,
                        burned.estimatedBytes(),
                        true,
                        (burnPlan, blankMode) -> mp3Factory.burn(burnPlan, burned, label, chosen),
                        messages.get("progress.title.mp3Disc"),
                        messages.get("result.text.mp3Disc"),
                        request -> check(request, burned)));
    }

    private void createIso() {
        if (plan.needsConversion() && transcodingUnavailable().isPresent()) {
            return;
        }
        VolumeLabel label = VolumeLabel.sanitize(labelField.getText());
        FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get("data.createIso"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.get("data.isoFiles"), "*.iso"));
        chooser.setInitialFileName(label.text().replace(' ', '_') + ".iso");
        File file = chooser.showSaveDialog(navigation.window());
        if (file == null) {
            return;
        }
        Path target = file.toPath();
        flow.startWithoutDrive(
                mp3Factory.createIso(plan, label, bitrate.getValue(), target),
                messages.get("progress.title.createIso"),
                messages.format("result.text.createIso", target));
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

    private static <T> StringConverter<T> converter(Function<T, String> text) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : text.apply(value);
            }

            @Override
            public T fromString(String string) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
