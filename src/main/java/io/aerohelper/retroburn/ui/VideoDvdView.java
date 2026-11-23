// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.project.BurnJobFactory;
import io.aerohelper.retroburn.project.DiscCapacity;
import io.aerohelper.retroburn.project.DvdJobFactory;
import io.aerohelper.retroburn.project.DvdProject;
import io.aerohelper.retroburn.project.JobKind;
import io.aerohelper.retroburn.project.VolumeLabel;
import io.aerohelper.retroburn.settings.Settings;
import io.aerohelper.retroburn.video.DvdBitrate;
import io.aerohelper.retroburn.video.DvdTools;
import io.aerohelper.retroburn.video.MenuLayout;
import io.aerohelper.retroburn.video.MenuRenderer;
import io.aerohelper.retroburn.video.MenuTemplate;
import io.aerohelper.retroburn.video.TvStandard;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
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
 * Video DVD: add videos, name the titles, choose the TV standard, aspect, chapters and menu template,
 * see the first menu page and the bitrate the disc allows, then burn, save an ISO or save the
 * VIDEO_TS folder. See {@code docs/design/LLD-video-dvd.md}.
 */
final class VideoDvdView implements Navigation.Screen {

    private static final Logger LOG = Logger.getLogger(VideoDvdView.class.getName());
    private static final List<Integer> CHAPTER_CHOICES = List.of(0, 5, 10, 15, 30);

    private final UiServices services;
    private final Navigation navigation;
    private final Messages messages;
    private final BurnFlow flow;
    private final BurnJobFactory dataFactory;
    private final DvdJobFactory dvdFactory;
    private final DvdTools tools;
    private final DvdJobFactory.MenuTexts texts;
    private final DriveBar driveBar;
    private final CapacityBar capacity;
    private final Label summary = new Label();
    private final Label notes = new Label();
    private final TextField discTitle = new TextField();
    private final ComboBox<TvStandard> standard = new ComboBox<>();
    private final ComboBox<String> aspect = new ComboBox<>();
    private final ComboBox<Integer> chapters = new ComboBox<>();
    private final ComboBox<MenuTemplate> template = new ComboBox<>();
    private final CheckBox playNext = new CheckBox();
    private final TableView<DvdProject.Title> table = new TableView<>();
    private final ImageView preview = new ImageView();
    private final Button burn = new Button();
    private final Button createIso = new Button();
    private final Button saveFolder = new Button();
    private final BorderPane root = new BorderPane();
    private final CancelToken probing = new CancelToken();
    private final List<String> refused = new ArrayList<>();

    private DvdProject project;
    private int pendingProbes;

    VideoDvdView(UiServices services, Navigation navigation) {
        this.services = services;
        this.navigation = navigation;
        this.messages = services.messages();
        this.flow = new BurnFlow(services, navigation);
        this.tools = new DvdTools(services.processes(), services.platform().toolLocator());
        this.texts = new DvdJobFactory.MenuTexts(
                messages.get("video.menu.playAll"),
                messages.get("video.menu.previous"),
                messages.get("video.menu.next"));
        this.dataFactory = new BurnJobFactory(
                services.platform().burnEngine(), services.platform().discImageBuilder());
        this.dvdFactory = new DvdJobFactory(dataFactory, services.platform().discImageBuilder(), tools, texts);
        Settings.Video saved = services.settings().current().video();
        TvStandard tv =
                saved.tvStandard().isEmpty() ? TvStandard.forLocale(Locale.getDefault()) : parse(saved.tvStandard());
        project = new DvdProject(
                messages.get("video.defaultTitle"),
                List.of(),
                tv,
                "16:9",
                saved.chapterMinutes(),
                MenuTemplate.named(saved.template()),
                "NEXT".equals(saved.afterTitle()));
        this.capacity = new CapacityBar(messages);
        this.driveBar = new DriveBar(services, this::erase);
        driveBar.media().addListener((observable, before, after) -> refresh());

        Label heading = new Label(messages.get("home.card.videoDvd"));
        heading.getStyleClass().add("view-heading");
        root.setTop(new VBox(driveBar.node(), heading, capacity.node()));

        discTitle.setText(project.discTitle());
        discTitle.setAccessibleText(messages.get("video.discTitle"));
        discTitle.textProperty().addListener((observable, before, after) -> changed());
        HBox titleRow = new HBox(new Label(messages.get("video.discTitle")), discTitle);
        titleRow.getStyleClass().add("form-row");

        standard.getItems().setAll(TvStandard.values());
        standard.setValue(project.standard());
        aspect.getItems().setAll("16:9", "4:3");
        aspect.setValue(project.aspect());
        chapters.getItems().setAll(CHAPTER_CHOICES);
        chapters.setValue(CHAPTER_CHOICES.contains(saved.chapterMinutes()) ? saved.chapterMinutes() : 5);
        chapters.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer minutes) {
                return minutes == null
                        ? ""
                        : minutes == 0
                                ? messages.get("video.chapters.none")
                                : messages.format("video.chapters.every", minutes);
            }

            @Override
            public Integer fromString(String text) {
                throw new UnsupportedOperationException();
            }
        });
        template.getItems().setAll(MenuTemplate.CLASSIC, MenuTemplate.Y2K, MenuTemplate.MINIMAL);
        template.setValue(project.template());
        template.setConverter(new StringConverter<>() {
            @Override
            public String toString(MenuTemplate value) {
                return value == null ? "" : messages.get("video.template." + value.id());
            }

            @Override
            public MenuTemplate fromString(String text) {
                throw new UnsupportedOperationException();
            }
        });
        playNext.setText(messages.get("video.playNext"));
        playNext.setSelected(project.playNext());
        standard.valueProperty().addListener((observable, before, after) -> changed());
        aspect.valueProperty().addListener((observable, before, after) -> changed());
        chapters.valueProperty().addListener((observable, before, after) -> changed());
        template.valueProperty().addListener((observable, before, after) -> changed());
        playNext.selectedProperty().addListener((observable, before, after) -> changed());
        HBox options = new HBox(
                new Label(messages.get("video.standard")),
                standard,
                new Label(messages.get("video.aspect")),
                aspect,
                new Label(messages.get("video.chapters")),
                chapters,
                new Label(messages.get("video.template")),
                template,
                playNext);
        options.getStyleClass().add("form-row");

        table.setEditable(true);
        TableColumn<DvdProject.Title, String> name = new TableColumn<>(messages.get("video.column.name"));
        name.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        name.setCellFactory(TextFieldTableCell.forTableColumn());
        name.setOnEditCommit(event -> rename(event.getTablePosition().getRow(), event.getNewValue()));
        name.setPrefWidth(220);
        TableColumn<DvdProject.Title, String> length = new TableColumn<>(messages.get("audio.column.length"));
        length.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(clock(cell.getValue().video().duration())));
        length.setPrefWidth(80);
        TableColumn<DvdProject.Title, String> source = new TableColumn<>(messages.get("data.column.source"));
        source.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().source().toString()));
        List.of(name, length, source).forEach(table.getColumns()::add);
        table.getColumns().forEach(column -> column.setSortable(false));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(messages.get("video.empty")));
        table.setAccessibleText(messages.get("video.titles"));
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
        HBox.setHgrow(table, Priority.ALWAYS);
        preview.setFitWidth(288);
        preview.setPreserveRatio(false);
        preview.setFitHeight(216);
        preview.setAccessibleText(messages.get("video.preview"));
        VBox previewBox = new VBox(new Label(messages.get("video.preview")), preview);
        HBox middle = new HBox(table, previewBox);
        middle.getStyleClass().add("form-row");
        VBox.setVgrow(middle, Priority.ALWAYS);
        summary.getStyleClass().add("field-hint");
        notes.getStyleClass().add("confirm-blocker");
        notes.setWrapText(true);

        Button add = new Button(messages.get("video.add"));
        add.setOnAction(event -> chooseFiles());
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
        createIso.setText(messages.get("data.createIso"));
        createIso.setOnAction(event -> createIso());
        saveFolder.setText(messages.get("video.saveFolder"));
        saveFolder.setOnAction(event -> saveFolder());
        Button home = new Button(messages.get("view.home"));
        home.setOnAction(event -> navigation.showHome());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(add, remove, up, down, spacer, home, saveFolder, createIso, burn);
        actions.getStyleClass().add("view-actions");

        VBox centre = new VBox(summary, titleRow, options, middle, notes, actions);
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
        chooser.setTitle(messages.get("video.add"));
        chooser.getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter(
                                messages.get("video.files"),
                                "*.mp4",
                                "*.mkv",
                                "*.avi",
                                "*.mov",
                                "*.m4v",
                                "*.mpg",
                                "*.mpeg",
                                "*.wmv",
                                "*.webm",
                                "*.ts",
                                "*.mts",
                                "*.m2ts",
                                "*.3gp",
                                "*.flv"),
                        new FileChooser.ExtensionFilter(messages.get("audio.allFiles"), "*.*"));
        List<File> files = chooser.showOpenMultipleDialog(navigation.window());
        if (files != null) {
            add(files.stream().map(File::toPath).toList());
        }
    }

    private void add(List<Path> files) {
        pendingProbes += files.size();
        refresh();
        CompletableFuture.supplyAsync(
                        () -> {
                            List<DvdProject.Title> titles = new ArrayList<>();
                            List<String> failures = new ArrayList<>();
                            for (Path file : files) {
                                try {
                                    titles.add(DvdProject.Title.of(file, tools.probe(file, probing)));
                                } catch (RetroBurnException e) {
                                    failures.add(messages.format(e.userMessage()));
                                }
                            }
                            return Map.entry(titles, failures);
                        },
                        services.background())
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    pendingProbes -= files.size();
                    if (error != null) {
                        LOG.log(Level.WARNING, "Could not read the videos", error);
                        refused.add(messages.get("audio.readFailed"));
                    } else {
                        boolean first = project.titles().isEmpty();
                        project = project.add(result.getKey());
                        refused.addAll(result.getValue());
                        if (first) {
                            aspect.setValue(project.aspect());
                        }
                    }
                    refresh();
                }));
    }

    private void removeSelected() {
        int index = table.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            List<DvdProject.Title> titles = new ArrayList<>(project.titles());
            titles.remove(index);
            project = project.withTitles(titles);
            refresh();
        }
    }

    private void move(int by) {
        int index = table.getSelectionModel().getSelectedIndex();
        int target = index + by;
        if (index >= 0 && target >= 0 && target < project.titles().size()) {
            List<DvdProject.Title> titles = new ArrayList<>(project.titles());
            titles.add(target, titles.remove(index));
            project = project.withTitles(titles);
            refresh();
            table.getSelectionModel().select(target);
        }
    }

    private void rename(int index, String name) {
        List<DvdProject.Title> titles = new ArrayList<>(project.titles());
        DvdProject.Title title = titles.get(index);
        titles.set(index, new DvdProject.Title(title.source(), title.video(), name));
        project = project.withTitles(titles);
        refresh();
    }

    /** Takes the option controls into the project and saves them as settings. */
    private void changed() {
        project = new DvdProject(
                discTitle.getText(),
                project.titles(),
                standard.getValue(),
                aspect.getValue(),
                chapters.getValue(),
                template.getValue(),
                playNext.isSelected());
        Settings settings = services.settings()
                .current()
                .withVideo(new Settings.Video(
                        standard.getValue().name(),
                        chapters.getValue(),
                        template.getValue().id(),
                        playNext.isSelected() ? "NEXT" : "MENU"));
        services.background().execute(() -> services.settings().save(settings));
        refresh();
    }

    private long capacityBytes() {
        MediaInfo media = driveBar.media().get();
        long usable = media == null ? 0 : DiscCapacity.usableBytes(media);
        return usable > 0 ? usable : DvdBitrate.SINGLE_LAYER_BYTES;
    }

    private void refresh() {
        table.setItems(FXCollections.observableArrayList(project.titles()));
        CoverContents.publish(services, CoverContents.video(messages.get("home.card.videoDvd"), project));
        DvdBitrate bitrate = project.bitrate(capacityBytes());
        long estimate = project.titles().isEmpty() ? 0 : project.estimatedBytes(bitrate);
        if (pendingProbes > 0) {
            capacity.calculating();
        } else {
            MediaInfo media = driveBar.media().get();
            capacity.show(estimate, media == null ? DvdBitrate.SINGLE_LAYER_BYTES : DiscCapacity.usableBytes(media));
        }
        summary.setText(messages.format(
                "video.summary", project.titles().size(), clock(project.totalDuration()), bitrate.videoKbps()));
        List<String> lines = new ArrayList<>(refused);
        if (!project.titles().isEmpty() && bitrate.lowQuality()) {
            lines.add(messages.get("video.lowQuality"));
        }
        notes.setText(String.join("\n", lines));
        notes.setVisible(!lines.isEmpty());
        notes.setManaged(!lines.isEmpty());
        showPreview();
        boolean ready = !project.titles().isEmpty() && pendingProbes == 0;
        burn.setDisable(!ready);
        createIso.setDisable(!ready);
        saveFolder.setDisable(!ready);
    }

    /** The first menu page as it will look, without thumbnails (they come from the videos while making the DVD). */
    private void showPreview() {
        List<MenuLayout.Page> pages = project.titles().isEmpty()
                ? MenuLayout.pages(
                        List.of(messages.get("video.previewTitle")),
                        project.standard(),
                        texts.playAll(),
                        texts.previous(),
                        texts.next())
                : project.pages(texts.playAll(), texts.previous(), texts.next());
        BufferedImage image = MenuRenderer.render(
                        pages.getFirst(), project.discTitle(), project.template(), project.standard(), Map.of())
                .background();
        WritableImage fx = new WritableImage(image.getWidth(), image.getHeight());
        PixelWriter writer = fx.getPixelWriter();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                writer.setArgb(x, y, image.getRGB(x, y));
            }
        }
        preview.setImage(fx);
    }

    private VolumeLabel label() {
        return VolumeLabel.sanitize(project.discTitle());
    }

    private void burn() {
        DvdProject burned = project;
        DvdBitrate bitrate = burned.bitrate(capacityBytes());
        VolumeLabel label = label();
        flow.start(
                driveBar,
                new BurnFlow.Job(
                        JobKind.VIDEO_DVD,
                        burned.estimatedBytes(bitrate),
                        true,
                        (plan, blankMode) -> dvdFactory.burn(plan, burned, bitrate, label),
                        messages.get("progress.title.videoDvd"),
                        messages.get("result.text.videoDvd"),
                        request -> DvdJobFactory.check(request, burned)));
    }

    private void createIso() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get("data.createIso"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.get("data.isoFiles"), "*.iso"));
        chooser.setInitialFileName(label().text().replace(' ', '_') + ".iso");
        File file = chooser.showSaveDialog(navigation.window());
        if (file != null) {
            Path target = file.toPath();
            flow.startWithoutDrive(
                    dvdFactory.createIso(project, project.bitrate(DvdBitrate.SINGLE_LAYER_BYTES), label(), target),
                    messages.get("progress.title.videoDvd"),
                    messages.format("result.text.createIso", target));
        }
    }

    private void saveFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(messages.get("video.saveFolder"));
        File folder = chooser.showDialog(navigation.window());
        if (folder != null) {
            Path target = folder.toPath();
            flow.startWithoutDrive(
                    dvdFactory.saveFolder(project, project.bitrate(DvdBitrate.SINGLE_LAYER_BYTES), target),
                    messages.get("progress.title.videoDvd"),
                    messages.format("result.text.videoFolder", target));
        }
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

    private static String clock(Duration duration) {
        return "%d:%02d:%02d".formatted(duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }

    private static TvStandard parse(String name) {
        try {
            return TvStandard.valueOf(name);
        } catch (IllegalArgumentException e) {
            return TvStandard.forLocale(Locale.getDefault());
        }
    }
}
