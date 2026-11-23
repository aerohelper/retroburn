// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.design.AlignmentPage;
import io.aerohelper.retroburn.design.CommandHistory;
import io.aerohelper.retroburn.design.CoverContent;
import io.aerohelper.retroburn.design.DesignDocument;
import io.aerohelper.retroburn.design.DesignElement;
import io.aerohelper.retroburn.design.DesignElement.Align;
import io.aerohelper.retroburn.design.DesignElement.TextStyle;
import io.aerohelper.retroburn.design.DesignFile;
import io.aerohelper.retroburn.design.DesignRenderer;
import io.aerohelper.retroburn.design.EditorCommand;
import io.aerohelper.retroburn.design.FontCatalog;
import io.aerohelper.retroburn.design.Frame;
import io.aerohelper.retroburn.design.ImageStore;
import io.aerohelper.retroburn.design.Paper;
import io.aerohelper.retroburn.design.PdfExporter;
import io.aerohelper.retroburn.design.PdfPainter;
import io.aerohelper.retroburn.design.PngExporter;
import io.aerohelper.retroburn.design.Presets;
import io.aerohelper.retroburn.design.PrintTemplate;
import io.aerohelper.retroburn.design.PrintTemplates;
import io.aerohelper.retroburn.design.SheetLayout;
import io.aerohelper.retroburn.design.SheetRenderer;
import io.aerohelper.retroburn.design.Snap;
import io.aerohelper.retroburn.settings.Settings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/**
 * The cover and label designer: template and paper, a canvas with the item at a zoom, element
 * buttons on the left and the selected element's properties on the right. Every edit goes through a
 * {@link CommandHistory}. See {@code docs/design/LLD-cover-designer.md} section 10.
 */
public final class CoverDesignerView implements Navigation.Screen {

    private static final Logger LOG = Logger.getLogger(CoverDesignerView.class.getName());

    /** Screen pixels per millimetre at zoom 1. */
    private static final double BASE_SCALE = 3.0;

    private static final double HANDLE = 9;

    private final UiServices services;
    private final Navigation navigation;
    private final Messages messages;
    private final PrintTemplates templates = PrintTemplates.builtIn();
    private final CommandHistory history;
    private final BorderPane root = new BorderPane();

    private FontCatalog fonts = FontCatalog.bundled();
    private ImageStore images = new ImageStore();
    private DesignRenderer renderer = new DesignRenderer(fonts, images);
    private Paper paper;
    private double zoom = 1.5;
    private String selectedId;
    private Path currentFile;
    private boolean updatingControls;

    // Drag state.
    private Point2D dragStart;
    private Frame dragFrame;
    private boolean resizing;
    private List<Double> guideX = List.of();
    private List<Double> guideY = List.of();

    private final ImageView canvas = new ImageView();
    private final Pane overlay = new Pane();
    private final ComboBox<PrintTemplate> template = new ComboBox<>();
    private final ComboBox<Paper> paperChoice = new ComboBox<>();
    private final Spinner<Double> spine = new Spinner<>(5.0, 30.0, 14.0, 0.5);
    private final ComboBox<Double> hole = new ComboBox<>();
    private final ComboBox<String> preset = new ComboBox<>();
    private final ColorPicker background = new ColorPicker();
    private final CheckBox snap = new CheckBox();
    private final Button undo = new Button();
    private final Button redo = new Button();
    private final Label status = new Label();

    private final Spinner<Double> x = mmSpinner(-50, 400);
    private final Spinner<Double> y = mmSpinner(-50, 300);
    private final Spinner<Double> width = mmSpinner(Frame.MIN_SIZE_MM, 400);
    private final Spinner<Double> height = mmSpinner(Frame.MIN_SIZE_MM, 300);
    private final Spinner<Double> rotation = new Spinner<>(-180.0, 180.0, 0.0, 1.0);
    private final TextArea text = new TextArea();
    private final ComboBox<String> font = new ComboBox<>();
    private final Spinner<Double> size = new Spinner<>(4.0, 200.0, 12.0, 0.5);
    private final CheckBox bold = new CheckBox();
    private final ColorPicker colour = new ColorPicker();
    private final ComboBox<Align> align = new ComboBox<>();
    private final ColorPicker fill = new ColorPicker();
    private final CheckBox noFill = new CheckBox();
    private final ColorPicker stroke = new ColorPicker();
    private final Spinner<Double> strokeWidth = new Spinner<>(0.0, 10.0, 0.5, 0.1);
    private final Label fontWarning = new Label();
    private final VBox properties = new VBox();
    private Node frameGrid;
    private Node alignRow;
    private Node orderRow;

    public CoverDesignerView(UiServices services, Navigation navigation) {
        this.services = services;
        this.navigation = navigation;
        this.messages = services.messages();
        this.paper = Paper.fromSetting(services.settings().current().design().paper(), Locale.getDefault());
        CoverContent content = services.coverContent().get();
        this.history = new CommandHistory(Presets.create("classic", templates.get("cd-front"), content));

        Label heading = new Label(messages.get("home.card.coverAndLabel"));
        heading.getStyleClass().add("view-heading");
        root.setTop(new VBox(heading, documentBar(), editBar()));
        root.setLeft(palette());
        root.setRight(propertiesPanel());

        StackPane stage = new StackPane(canvas, overlay);
        stage.getStyleClass().add("design-canvas");
        overlay.setFocusTraversable(true);
        overlay.setPickOnBounds(true);
        overlay.addEventHandler(MouseEvent.MOUSE_PRESSED, this::pressed);
        overlay.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::dragged);
        overlay.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> released());
        overlay.addEventHandler(KeyEvent.KEY_PRESSED, this::key);
        ScrollPane scroller = new ScrollPane(new StackPane(stage));
        scroller.setFitToWidth(true);
        scroller.setFitToHeight(true);
        status.setWrapText(true);
        status.getStyleClass().add("field-hint");
        root.setCenter(new VBox(scroller, status));
        VBox.setVgrow(scroller, javafx.scene.layout.Priority.ALWAYS);
        root.getStyleClass().add("view-content");

        loadSystemFonts();
        redraw();
        // Fit the item to the space once the screen has its size.
        scroller.viewportBoundsProperty().addListener(new javafx.beans.value.ChangeListener<>() {
            @Override
            public void changed(
                    javafx.beans.value.ObservableValue<? extends javafx.geometry.Bounds> observable,
                    javafx.geometry.Bounds before,
                    javafx.geometry.Bounds after) {
                if (after.getWidth() > 0 && after.getHeight() > 0) {
                    observable.removeListener(this);
                    fitTo(after.getWidth(), after.getHeight());
                }
            }
        });
    }

    @Override
    public Node node() {
        return root;
    }

    // ---------------------------------------------------------------------------------------- bars

    private Node documentBar() {
        template.getItems().setAll(templates.all());
        template.setConverter(converter(item -> messages.get("design.template." + item.id())));
        template.setValue(currentTemplate());
        template.valueProperty().addListener((o, before, after) -> {
            if (!updatingControls && after != null) {
                execute(new EditorCommand.ReplaceDocument(
                        Presets.create(document().preset(), after, document().content())));
                selectedId = null;
            }
        });
        paperChoice.getItems().setAll(Paper.values());
        paperChoice.setConverter(converter(item -> messages.get("design.paper." + item.name())));
        paperChoice.setValue(paper);
        paperChoice.valueProperty().addListener((o, before, after) -> {
            paper = after;
            Settings settings = services.settings().current();
            services.settings()
                    .save(settings.withDesign(
                            new Settings.Design(after.name(), settings.design().imageNoticeShown())));
            redraw();
        });
        spine.setEditable(true);
        spine.setPrefWidth(80);
        spine.valueProperty().addListener((o, before, after) -> {
            if (!updatingControls && after != null) {
                execute(new EditorCommand.SetSpine(after));
            }
        });
        hole.setConverter(converter(value -> messages.format("design.hole.value", value.intValue())));
        hole.valueProperty().addListener((o, before, after) -> {
            if (!updatingControls && after != null) {
                execute(new EditorCommand.SetHole(after));
            }
        });
        preset.getItems().setAll(Presets.NAMES);
        preset.setConverter(converter(name -> messages.get("video.template." + name)));
        preset.setValue(document().preset());
        Button applyPreset = new Button(messages.get("design.preset.apply"));
        applyPreset.setOnAction(event -> {
            execute(new EditorCommand.ReplaceDocument(Presets.create(
                    preset.getValue(), currentTemplate(), document().content())));
            selectedId = null;
        });
        background.valueProperty().addListener((o, before, after) -> {
            if (!updatingControls) {
                execute(new EditorCommand.SetBackground(hex(after)));
            }
        });
        FlowPane bar = new FlowPane(
                6,
                6,
                new Label(messages.get("design.template")),
                template,
                new Label(messages.get("design.paper")),
                paperChoice,
                new Label(messages.get("design.spine")),
                spine,
                new Label(messages.get("design.hole")),
                hole,
                new Label(messages.get("design.preset")),
                preset,
                applyPreset,
                new Label(messages.get("design.background")),
                background);
        bar.getStyleClass().add("form-row");
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return bar;
    }

    private Node editBar() {
        undo.setOnAction(event -> {
            history.undo();
            afterChange();
        });
        redo.setOnAction(event -> {
            history.redo();
            afterChange();
        });
        Button zoomOut = button("design.zoomOut", () -> setZoom(zoom / 1.25));
        Button zoomIn = button("design.zoomIn", () -> setZoom(zoom * 1.25));
        Button fit = button("design.zoomFit", this::fitZoom);
        snap.setText(messages.get("design.snap"));
        snap.setSelected(true);
        Button resync = button("design.resync", this::resync);
        Button open = button("design.open", this::open);
        Button save = button("design.save", this::save);
        Button pdf = button("design.exportPdf", this::exportPdf);
        Button png = button("design.exportPng", this::exportPng);
        Button print = button("design.print", this::print);
        Button testPage = button("design.testPage", this::testPage);
        FlowPane bar = new FlowPane(
                undo,
                redo,
                new Separator(Orientation.VERTICAL),
                zoomOut,
                zoomIn,
                fit,
                snap,
                new Separator(Orientation.VERTICAL),
                resync,
                open,
                save,
                new Separator(Orientation.VERTICAL),
                pdf,
                png,
                print,
                testPage);
        bar.getStyleClass().add("form-row");
        bar.setHgap(6);
        bar.setVgap(6);
        return bar;
    }

    private Node palette() {
        VBox palette = new VBox(
                new Label(messages.get("design.add")),
                button(
                        "design.add.text",
                        () -> add(new DesignElement.Text(
                                document().newId("text"),
                                centred(60, 12),
                                messages.get("design.add.textSample"),
                                new TextStyle(FontCatalog.DEFAULT_FAMILY, 14, false, "#000000", Align.CENTER)))),
                button("design.add.image", this::addImage),
                button(
                        "design.add.rectangle",
                        () -> add(new DesignElement.ShapeElement(
                                document().newId("shape"),
                                centred(40, 25),
                                DesignElement.ShapeElement.Kind.RECTANGLE,
                                "#F2B33D",
                                "",
                                0))),
                button(
                        "design.add.ellipse",
                        () -> add(new DesignElement.ShapeElement(
                                document().newId("shape"),
                                centred(30, 30),
                                DesignElement.ShapeElement.Kind.ELLIPSE,
                                "",
                                "#1F3B66",
                                0.8))),
                button(
                        "design.add.trackList",
                        () -> add(new DesignElement.TrackList(
                                document().newId("tracks"),
                                centred(80, 60),
                                new TextStyle(FontCatalog.DEFAULT_FAMILY, 9, false, "#000000", Align.LEFT)))),
                button(
                        "design.add.title",
                        () -> add(new DesignElement.DiscTitle(
                                document().newId("title"),
                                centred(90, 16),
                                new TextStyle(FontCatalog.DEFAULT_FAMILY, 20, true, "#000000", Align.CENTER)))));
        palette.getStyleClass().add("design-palette");
        palette.setSpacing(6);
        palette.getChildren().stream()
                .filter(Button.class::isInstance)
                .forEach(node -> ((Button) node).setMaxWidth(Double.MAX_VALUE));
        return palette;
    }

    private Node propertiesPanel() {
        GridPane frame = new GridPane();
        frame.setHgap(6);
        frame.setVgap(4);
        frame.addRow(0, new Label(messages.get("design.x")), x, new Label(messages.get("design.y")), y);
        frame.addRow(
                1, new Label(messages.get("design.width")), width, new Label(messages.get("design.height")), height);
        frame.addRow(2, new Label(messages.get("design.rotation")), rotation);
        rotation.setEditable(true);
        rotation.setPrefWidth(80);
        for (Spinner<Double> spinner : List.of(x, y, width, height, rotation)) {
            spinner.valueProperty().addListener((o, before, after) -> frameEdited());
        }

        text.setPrefRowCount(3);
        text.setWrapText(true);
        text.textProperty().addListener((o, before, after) -> styleEdited("text"));
        font.valueProperty().addListener((o, before, after) -> styleEdited("font"));
        size.setEditable(true);
        size.setPrefWidth(80);
        size.valueProperty().addListener((o, before, after) -> styleEdited("size"));
        bold.setText(messages.get("design.bold"));
        bold.selectedProperty().addListener((o, before, after) -> styleEdited("bold"));
        colour.valueProperty().addListener((o, before, after) -> styleEdited("colour"));
        align.getItems().setAll(Align.values());
        align.setConverter(
                converter(value -> messages.get("design.align." + value.name().toLowerCase(Locale.ROOT))));
        align.valueProperty().addListener((o, before, after) -> styleEdited("align"));
        fill.valueProperty().addListener((o, before, after) -> styleEdited("fill"));
        noFill.setText(messages.get("design.noFill"));
        noFill.selectedProperty().addListener((o, before, after) -> styleEdited("noFill"));
        stroke.valueProperty().addListener((o, before, after) -> styleEdited("stroke"));
        strokeWidth.setEditable(true);
        strokeWidth.setPrefWidth(80);
        strokeWidth.valueProperty().addListener((o, before, after) -> styleEdited("strokeWidth"));
        fontWarning.setWrapText(true);
        fontWarning.getStyleClass().add("confirm-warning");

        FlowPane alignButtons = new FlowPane(6, 6);
        for (EditorCommand.AlignToItem.Edge edge : EditorCommand.AlignToItem.Edge.values()) {
            alignButtons
                    .getChildren()
                    .add(button("design.alignTo." + edge.name().toLowerCase(Locale.ROOT), () -> {
                        if (selectedId != null) {
                            PrintTemplate item = currentTemplate();
                            execute(new EditorCommand.AlignToItem(
                                    selectedId, edge, item.width(document().spineMm()), item.heightMm()));
                        }
                    }));
        }
        FlowPane order = new FlowPane(
                6,
                6,
                button("design.forward", () -> zOrder(1)),
                button("design.backward", () -> zOrder(-1)),
                button("design.delete", this::deleteSelected));

        properties.setSpacing(6);
        properties.setPrefWidth(280);
        properties.getStyleClass().add("design-properties");
        frameGrid = frame;
        alignRow = alignButtons;
        orderRow = order;
        ScrollPane scroller = new ScrollPane(properties);
        scroller.setFitToWidth(true);
        scroller.setPrefWidth(300);
        return scroller;
    }

    // ------------------------------------------------------------------------------------ editing

    private DesignDocument document() {
        return history.current();
    }

    private PrintTemplate currentTemplate() {
        return templates.get(document().templateId());
    }

    private Optional<DesignElement> selected() {
        return selectedId == null ? Optional.empty() : document().element(selectedId);
    }

    private void execute(EditorCommand command) {
        history.execute(command);
        afterChange();
    }

    private void afterChange() {
        if (selectedId != null && document().element(selectedId).isEmpty()) {
            selectedId = null;
        }
        redraw();
    }

    private void add(DesignElement element) {
        execute(new EditorCommand.AddElement(element));
        selectedId = element.id();
        redraw();
        overlay.requestFocus();
    }

    /** A frame of this size in the middle of the item (of its front panel for wraps). */
    private Frame centred(double w, double h) {
        PrintTemplate item = currentTemplate();
        double itemWidth = item.width(document().spineMm());
        double centreX =
                item.adjustableSpine() ? (item.folds(document().spineMm()).getLast() + itemWidth) / 2 : itemWidth / 2;
        double boxWidth = Math.min(w, itemWidth - 2 * item.safeMm());
        return Frame.of(centreX - boxWidth / 2, (item.heightMm() - h) / 2, boxWidth, h);
    }

    private void zOrder(int steps) {
        if (selectedId != null) {
            execute(new EditorCommand.MoveInZOrder(selectedId, steps));
        }
    }

    private void deleteSelected() {
        if (selectedId != null) {
            execute(new EditorCommand.RemoveElement(selectedId));
        }
    }

    private void resync() {
        execute(new EditorCommand.SetContent(services.coverContent().get()));
        status.setText(
                services.coverContent().get().lines().isEmpty()
                        ? messages.get("design.resync.nothing")
                        : messages.format(
                                "design.resync.done",
                                services.coverContent().get().kind()));
    }

    private void frameEdited() {
        if (updatingControls) {
            return;
        }
        selected().ifPresent(element -> {
            Frame frame =
                    new Frame(x.getValue(), y.getValue(), width.getValue(), height.getValue(), rotation.getValue());
            execute(new EditorCommand.ReplaceElement(element.withFrame(frame), "frame-" + element.id()));
        });
    }

    private void styleEdited(String field) {
        if (updatingControls) {
            return;
        }
        selected().ifPresent(element -> {
            DesignElement changed = switch (element) {
                case DesignElement.Text t -> new DesignElement.Text(t.id(), t.frame(), text.getText(), style());
                case DesignElement.TrackList t -> new DesignElement.TrackList(t.id(), t.frame(), style());
                case DesignElement.DiscTitle t -> new DesignElement.DiscTitle(t.id(), t.frame(), style());
                case DesignElement.ShapeElement s ->
                    new DesignElement.ShapeElement(
                            s.id(),
                            s.frame(),
                            s.kind(),
                            noFill.isSelected() ? "" : hex(fill.getValue()),
                            strokeWidth.getValue() > 0 ? hex(stroke.getValue()) : "",
                            strokeWidth.getValue());
                case DesignElement.Image image -> image;
            };
            execute(new EditorCommand.ReplaceElement(changed, field + "-" + element.id()));
        });
    }

    private TextStyle style() {
        return new TextStyle(
                font.getValue() == null ? FontCatalog.DEFAULT_FAMILY : font.getValue(),
                size.getValue(),
                bold.isSelected(),
                hex(colour.getValue()),
                align.getValue() == null ? Align.LEFT : align.getValue());
    }

    // ------------------------------------------------------------------------------------ canvas

    private double scale() {
        return BASE_SCALE * zoom;
    }

    private void setZoom(double value) {
        zoom = Math.clamp(value, 0.3, 6);
        redraw();
    }

    private void fitZoom() {
        Node centre = root.getCenter();
        fitTo(centre.getLayoutBounds().getWidth() - 20, centre.getLayoutBounds().getHeight() - 60);
    }

    private void fitTo(double availableWidth, double availableHeight) {
        PrintTemplate item = currentTemplate();
        double neededWidth = (item.width(document().spineMm()) + 2 * item.bleedMm()) * BASE_SCALE;
        double neededHeight = (item.heightMm() + 2 * item.bleedMm()) * BASE_SCALE;
        setZoom(Math.min(Math.max(200, availableWidth) / neededWidth, Math.max(200, availableHeight) / neededHeight));
    }

    /** Millimetres in the item from a point on the overlay. */
    private Point2D toMm(double px, double py) {
        double bleed = currentTemplate().bleedMm();
        return new Point2D(px / scale() - bleed, py / scale() - bleed);
    }

    private double toPx(double mm) {
        return (mm + currentTemplate().bleedMm()) * scale();
    }

    private void pressed(MouseEvent event) {
        overlay.requestFocus();
        Point2D point = toMm(event.getX(), event.getY());
        Optional<DesignElement> current = selected();
        if (current.isPresent() && nearHandle(current.get().frame(), event.getX(), event.getY())) {
            resizing = true;
            dragStart = point;
            dragFrame = current.get().frame();
            return;
        }
        resizing = false;
        List<DesignElement> elements = new ArrayList<>(document().elements());
        selectedId = null;
        for (int index = elements.size() - 1; index >= 0; index--) {
            Frame frame = elements.get(index).frame();
            if (point.getX() >= frame.x()
                    && point.getX() <= frame.right()
                    && point.getY() >= frame.y()
                    && point.getY() <= frame.bottom()) {
                selectedId = elements.get(index).id();
                dragStart = point;
                dragFrame = frame;
                break;
            }
        }
        redraw();
    }

    private boolean nearHandle(Frame frame, double px, double py) {
        return frame.rotationDegrees() == 0
                && Math.abs(px - toPx(frame.right())) <= HANDLE
                && Math.abs(py - toPx(frame.bottom())) <= HANDLE;
    }

    private void dragged(MouseEvent event) {
        Optional<DesignElement> current = selected();
        if (current.isEmpty() || dragStart == null) {
            return;
        }
        Point2D point = toMm(event.getX(), event.getY());
        double dx = point.getX() - dragStart.getX();
        double dy = point.getY() - dragStart.getY();
        Frame frame = resizing
                ? dragFrame.resize(dragFrame.width() + dx, dragFrame.height() + dy)
                : dragFrame.moveTo(dragFrame.x() + dx, dragFrame.y() + dy);
        guideX = List.of();
        guideY = List.of();
        if (snap.isSelected() && !event.isAltDown() && frame.rotationDegrees() == 0) {
            Snap.Guides guides =
                    Snap.guides(currentTemplate(), document(), current.get().id());
            double tolerance = Snap.TOLERANCE_MM / zoom;
            Snap.Result result = resizing ? Snap.resize(frame, guides, tolerance) : Snap.move(frame, guides, tolerance);
            frame = result.frame();
            guideX = result.vertical();
            guideY = result.horizontal();
        }
        history.execute(new EditorCommand.ReplaceElement(
                current.get().withFrame(frame), "drag-" + current.get().id()));
        redraw();
    }

    private void released() {
        history.seal();
        dragStart = null;
        guideX = List.of();
        guideY = List.of();
        redraw();
    }

    private void key(KeyEvent event) {
        if (event.isShortcutDown() && event.getCode() == KeyCode.Z) {
            history.undo();
            afterChange();
            event.consume();
            return;
        }
        if (event.isShortcutDown() && event.getCode() == KeyCode.Y) {
            history.redo();
            afterChange();
            event.consume();
            return;
        }
        Optional<DesignElement> current = selected();
        if (current.isEmpty()) {
            return;
        }
        double step = event.isShiftDown() ? 5 : 0.5;
        Frame frame = current.get().frame();
        Frame moved = switch (event.getCode()) {
            case LEFT -> frame.moveTo(frame.x() - step, frame.y());
            case RIGHT -> frame.moveTo(frame.x() + step, frame.y());
            case UP -> frame.moveTo(frame.x(), frame.y() - step);
            case DOWN -> frame.moveTo(frame.x(), frame.y() + step);
            default -> null;
        };
        if (moved != null) {
            execute(new EditorCommand.ReplaceElement(
                    current.get().withFrame(moved), "nudge-" + current.get().id()));
            event.consume();
        } else if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
            deleteSelected();
            event.consume();
        }
    }

    private void redraw() {
        PrintTemplate item = currentTemplate();
        try {
            canvas.setImage(FxImages.of(new PngExporter(renderer).draw(document(), item, scale(), true)));
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not draw the design", e);
        }
        overlay.setPrefSize(canvas.getImage().getWidth(), canvas.getImage().getHeight());
        overlay.setMaxSize(canvas.getImage().getWidth(), canvas.getImage().getHeight());
        overlay.getChildren().clear();
        selected().ifPresent(element -> {
            Frame frame = element.frame();
            Rectangle outline =
                    new Rectangle(toPx(frame.x()), toPx(frame.y()), frame.width() * scale(), frame.height() * scale());
            outline.setFill(Color.TRANSPARENT);
            outline.setStroke(Color.web("#1F5FBF"));
            outline.getStrokeDashArray().setAll(4.0, 3.0);
            outline.getTransforms()
                    .add(new Rotate(frame.rotationDegrees(), toPx(frame.centerX()), toPx(frame.centerY())));
            overlay.getChildren().add(outline);
            if (frame.rotationDegrees() == 0) {
                Rectangle handle = new Rectangle(
                        toPx(frame.right()) - HANDLE / 2, toPx(frame.bottom()) - HANDLE / 2, HANDLE, HANDLE);
                handle.setFill(Color.web("#1F5FBF"));
                overlay.getChildren().add(handle);
            }
        });
        double imageWidth = canvas.getImage().getWidth();
        double imageHeight = canvas.getImage().getHeight();
        for (double guide : guideX) {
            overlay.getChildren().add(guideLine(toPx(guide), 0, toPx(guide), imageHeight));
        }
        for (double guide : guideY) {
            overlay.getChildren().add(guideLine(0, toPx(guide), imageWidth, toPx(guide)));
        }
        updateControls();
    }

    private static Line guideLine(double x1, double y1, double x2, double y2) {
        Line line = new Line(x1, y1, x2, y2);
        line.setStroke(Color.web("#E5397F"));
        return line;
    }

    /** Puts the document and the selected element into the controls without recording edits. */
    private void updateControls() {
        updatingControls = true;
        try {
            PrintTemplate item = currentTemplate();
            template.setValue(item);
            spine.setDisable(!item.adjustableSpine());
            spine.getValueFactory()
                    .setValue(
                            item.adjustableSpine() ? item.clampSpine(document().spineMm()) : 14.0);
            hole.getItems().setAll(item.holeDiametersMm());
            hole.setDisable(!item.isDisc());
            hole.setValue(item.isDisc() ? document().holeDiameterMm() : null);
            background.setValue(Color.web(document().background()));
            undo.setText(history.undoName()
                    .map(key -> messages.format("design.undo", messages.get(key)))
                    .orElse(messages.get("design.undo.none")));
            undo.setDisable(!history.canUndo());
            redo.setText(history.redoName()
                    .map(key -> messages.format("design.redo", messages.get(key)))
                    .orElse(messages.get("design.redo.none")));
            redo.setDisable(!history.canRedo());
            font.getItems().setAll(fonts.families());

            properties.getChildren().clear();
            Optional<DesignElement> current = selected();
            if (current.isEmpty()) {
                Label hint = new Label(messages.get("design.selectHint"));
                hint.setWrapText(true);
                properties.getChildren().add(hint);
                showSheetNotes();
                return;
            }
            DesignElement element = current.get();
            Frame frame = element.frame();
            x.getValueFactory().setValue(round(frame.x()));
            y.getValueFactory().setValue(round(frame.y()));
            width.getValueFactory().setValue(round(frame.width()));
            height.getValueFactory().setValue(round(frame.height()));
            rotation.getValueFactory().setValue(frame.rotationDegrees());
            properties.getChildren().addAll(new Label(messages.get("design.element." + kind(element))), frameGrid);
            switch (element) {
                case DesignElement.Text t -> {
                    text.setText(t.text());
                    properties.getChildren().addAll(new Label(messages.get("design.text")), text);
                    textControls(t.style());
                }
                case DesignElement.TrackList t -> textControls(t.style());
                case DesignElement.DiscTitle t -> textControls(t.style());
                case DesignElement.ShapeElement s -> {
                    fill.setValue(s.fill().isEmpty() ? Color.WHITE : Color.web(s.fill()));
                    noFill.setSelected(s.fill().isEmpty());
                    stroke.setValue(s.stroke().isEmpty() ? Color.BLACK : Color.web(s.stroke()));
                    strokeWidth.getValueFactory().setValue(s.stroke().isEmpty() ? 0.0 : s.strokeMm());
                    properties
                            .getChildren()
                            .addAll(
                                    new HBox(6, new Label(messages.get("design.fill")), fill, noFill),
                                    new HBox(6, new Label(messages.get("design.stroke")), stroke, strokeWidth));
                }
                case DesignElement.Image image -> imageNote(image);
            }
            properties.getChildren().addAll(new Label(messages.get("design.alignTo")), alignRow, orderRow);
            showSheetNotes();
        } finally {
            updatingControls = false;
        }
    }

    private void textControls(TextStyle style) {
        font.setValue(style.fontFamily());
        size.getValueFactory().setValue(style.sizePt());
        bold.setSelected(style.bold());
        colour.setValue(Color.web(style.colour()));
        align.setValue(style.align());
        properties
                .getChildren()
                .addAll(
                        new HBox(6, new Label(messages.get("design.font")), font),
                        new HBox(6, new Label(messages.get("design.size")), size, bold),
                        new HBox(6, new Label(messages.get("design.colour")), colour, align));
        if (!fonts.embeddable(style.fontFamily())) {
            fontWarning.setText(messages.format("design.font.notEmbeddable", style.fontFamily()));
            properties.getChildren().add(fontWarning);
        }
    }

    /** Warns when the picture has fewer than 300 pixels per inch at its printed size. */
    private void imageNote(DesignElement.Image image) {
        if (!images.contains(image.imageId())) {
            return;
        }
        int pixels = images.image(image.imageId()).getWidth();
        long dpi = Math.round(pixels / (image.frame().width() / 25.4));
        Label note = new Label(
                dpi < PngExporter.DPI
                        ? messages.format("design.image.lowDpi", dpi)
                        : messages.format("design.image.dpi", dpi));
        note.setWrapText(true);
        properties.getChildren().add(note);
    }

    private void showSheetNotes() {
        Optional<SheetLayout.Sheet> sheet =
                SheetLayout.plan(currentTemplate(), document().spineMm(), paper);
        if (sheet.isEmpty()) {
            status.setText(messages.get("design.sheet.tooLarge"));
            return;
        }
        List<String> notes = new ArrayList<>();
        notes.add(messages.format(
                "design.sheet.copies", sheet.get().copies(), messages.get("design.paper." + paper.name())));
        sheet.get().warnings().forEach(key -> notes.add(messages.get(key)));
        status.setText(String.join(" ", notes));
    }

    private static String kind(DesignElement element) {
        return switch (element) {
            case DesignElement.Text t -> "text";
            case DesignElement.Image i -> "image";
            case DesignElement.ShapeElement s -> "shape";
            case DesignElement.TrackList t -> "trackList";
            case DesignElement.DiscTitle t -> "title";
        };
    }

    // ------------------------------------------------------------------------------------- files

    private void addImage() {
        Settings settings = services.settings().current();
        if (!settings.design().imageNoticeShown()) {
            Alert notice = new Alert(Alert.AlertType.INFORMATION, messages.get("design.image.rights"));
            notice.initOwner(navigation.window());
            notice.setHeaderText(messages.get("design.image.rightsTitle"));
            notice.showAndWait();
            services.settings()
                    .save(settings.withDesign(
                            new Settings.Design(settings.design().paper(), true)));
        }
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter(
                        messages.get("design.image.files"), "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File file = chooser.showOpenDialog(navigation.window());
        if (file == null) {
            return;
        }
        try {
            if (Files.size(file.toPath()) > 50L * 1024 * 1024) {
                alert(messages.get("design.image.tooLarge"));
                return;
            }
            String id = images.add(Files.readAllBytes(file.toPath()));
            var picture = images.image(id);
            Frame frame = centred(60, 60.0 * picture.getHeight() / picture.getWidth());
            add(new DesignElement.Image(document().newId("image"), frame, id));
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.INFO, "Could not add " + file, e);
            alert(messages.format("design.image.unreadable", file.getName()));
        }
    }

    private void open() {
        FileChooser chooser = designChooser();
        File file = chooser.showOpenDialog(navigation.window());
        if (file == null) {
            return;
        }
        try {
            DesignFile.Opened opened = DesignFile.open(file.toPath());
            images = opened.images();
            renderer = new DesignRenderer(fonts, images);
            history.reset(opened.document());
            selectedId = null;
            currentFile = file.toPath();
            preset.setValue(opened.document().preset());
            redraw();
        } catch (DesignFile.DesignFileException e) {
            LOG.log(Level.INFO, "Could not open " + file + ": " + e.getMessage(), e);
            alert(messages.get(e.reasonKey()));
        } catch (IOException e) {
            LOG.log(Level.INFO, "Could not open " + file, e);
            alert(messages.get("design.open.damaged"));
        }
    }

    private void save() {
        FileChooser chooser = designChooser();
        if (currentFile != null) {
            chooser.setInitialDirectory(currentFile.getParent().toFile());
            chooser.setInitialFileName(currentFile.getFileName().toString());
        } else {
            chooser.setInitialFileName(fileName(DesignFile.EXTENSION));
        }
        File file = chooser.showSaveDialog(navigation.window());
        if (file == null) {
            return;
        }
        Path target = withExtension(file.toPath(), DesignFile.EXTENSION);
        try {
            DesignFile.save(document(), images, target);
            currentFile = target;
            status.setText(messages.format("design.saved", target.getFileName()));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not save " + target, e);
            alert(messages.format("design.saveFailed", target.getFileName()));
        }
    }

    private void exportPdf() {
        Optional<SheetLayout.Sheet> sheet =
                SheetLayout.plan(currentTemplate(), document().spineMm(), paper);
        if (sheet.isEmpty()) {
            alert(messages.get("design.sheet.tooLarge"));
            return;
        }
        Path target = chooseTarget("*.pdf", ".pdf", "design.pdfFiles");
        if (target == null) {
            return;
        }
        DesignDocument snapshot = document();
        PrintTemplate item = currentTemplate();
        DesignRenderer drawing = renderer;
        exportInBackground(target, () -> new PdfExporter(drawing).export(snapshot, item, sheet.get(), target));
    }

    private void exportPng() {
        Path target = chooseTarget("*.png", ".png", "design.pngFiles");
        if (target == null) {
            return;
        }
        DesignDocument snapshot = document();
        PrintTemplate item = currentTemplate();
        DesignRenderer drawing = renderer;
        exportInBackground(target, () -> new PngExporter(drawing).export(snapshot, item, target));
    }

    private interface Export {
        void run() throws IOException;
    }

    private void exportInBackground(Path target, Export export) {
        status.setText(messages.get("design.exporting"));
        CompletableFuture.runAsync(
                        () -> {
                            try {
                                export.run();
                            } catch (IOException e) {
                                throw new java.io.UncheckedIOException(e);
                            }
                        },
                        services.background())
                .whenComplete((ignored, error) -> Platform.runLater(() -> {
                    if (error == null) {
                        status.setText(messages.format("design.exported", target.getFileName()));
                        return;
                    }
                    Throwable cause = error.getCause() != null ? error.getCause() : error;
                    if (cause.getCause() instanceof PdfPainter.FontNotEmbeddableException locked) {
                        alert(messages.format(
                                "design.font.blocksPdf", locked.face().family()));
                    } else {
                        LOG.log(Level.WARNING, "Could not export " + target, error);
                        alert(messages.format("design.exportFailed", target.getFileName()));
                    }
                    status.setText("");
                }));
    }

    private void print() {
        Optional<SheetLayout.Sheet> sheet =
                SheetLayout.plan(currentTemplate(), document().spineMm(), paper);
        if (sheet.isEmpty()) {
            alert(messages.get("design.sheet.tooLarge"));
            return;
        }
        SheetLayout.Sheet planned = sheet.get();
        try {
            new CoverPrinting(messages, navigation.window())
                    .print(
                            paper,
                            planned.landscape(),
                            planned.widthMm(),
                            planned.heightMm(),
                            images,
                            painter -> new SheetRenderer(renderer)
                                    .render(document(), currentTemplate(), planned, painter));
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not print", e);
            alert(messages.get("design.printFailed"));
        }
    }

    private void testPage() {
        try {
            new CoverPrinting(messages, navigation.window())
                    .print(
                            paper,
                            false,
                            paper.widthMm(),
                            paper.heightMm(),
                            images,
                            painter -> AlignmentPage.render(
                                    paper, fonts, messages.get("design.testPage.headline"), painter));
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not print the test page", e);
            alert(messages.get("design.printFailed"));
        }
    }

    private FileChooser designChooser() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter(messages.get("design.designFiles"), "*" + DesignFile.EXTENSION));
        return chooser;
    }

    private Path chooseTarget(String pattern, String extension, String filterKey) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.get(filterKey), pattern));
        chooser.setInitialFileName(fileName(extension));
        File file = chooser.showSaveDialog(navigation.window());
        return file == null ? null : withExtension(file.toPath(), extension);
    }

    private String fileName(String extension) {
        String title = document().content().title().isBlank()
                ? "cover"
                : document().content().title();
        return title.replaceAll("[\\\\/:*?\"<>|]", "_") + "-" + document().templateId() + extension;
    }

    private static Path withExtension(Path file, String extension) {
        String name = file.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(extension) ? file : file.resolveSibling(name + extension);
    }

    /** Reads the computer's fonts in the background; the bundled ones work meanwhile. */
    private void loadSystemFonts() {
        List<Path> folders = services.platform().fontFolders();
        CompletableFuture.supplyAsync(() -> FontCatalog.withSystemFonts(folders), services.background())
                .whenComplete((catalog, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        LOG.log(Level.INFO, "Could not read the computer's fonts", error);
                        return;
                    }
                    fonts = catalog;
                    renderer = new DesignRenderer(fonts, images);
                    redraw();
                }));
    }

    private void alert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.initOwner(navigation.window());
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    // ------------------------------------------------------------------------------------- small

    private Button button(String key, Runnable action) {
        Button button = new Button(messages.get(key));
        button.setOnAction(event -> action.run());
        return button;
    }

    private static Spinner<Double> mmSpinner(double min, double max) {
        Spinner<Double> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, 0, 0.5));
        spinner.setEditable(true);
        spinner.setPrefWidth(80);
        return spinner;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    static String hex(Color colour) {
        return String.format(
                "#%02X%02X%02X",
                (int) Math.round(colour.getRed() * 255), (int) Math.round(colour.getGreen() * 255), (int)
                        Math.round(colour.getBlue() * 255));
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> text) {
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
}
