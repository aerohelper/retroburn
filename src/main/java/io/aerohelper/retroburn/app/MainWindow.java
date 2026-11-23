// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.app;

import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.ui.AboutDialog;
import io.aerohelper.retroburn.ui.HomeView;
import io.aerohelper.retroburn.ui.LicensesDialog;
import io.aerohelper.retroburn.ui.Navigation;
import io.aerohelper.retroburn.ui.UiServices;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The main window: menu bar on top, the current screen in the centre. It is also the {@link
 * Navigation} between screens.
 */
public final class MainWindow implements Navigation {

    private static final String THEME_STYLESHEET = "/themes/classic/theme.css";
    private static final double INITIAL_WIDTH = 960;
    private static final double INITIAL_HEIGHT = 640;

    private final AppContext context;
    private final UiServices services;
    private final BorderPane root = new BorderPane();
    private Stage stage;
    private Screen project;

    public MainWindow(AppContext context) {
        this.context = Objects.requireNonNull(context, "context");
        this.services = context.uiServices();
    }

    /** Fills the stage with the main window content and shows it. */
    public void show(Stage stage) {
        this.stage = stage;
        root.setTop(createMenuBar(stage));
        showHome();

        Scene scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
        scene.getStylesheets().add(stylesheetUrl());

        stage.setTitle(context.appInfo().name());
        stage.setScene(scene);
        stage.setOnHidden(event -> closeProject());
        stage.show();
    }

    @Override
    public void showHome() {
        closeProject();
        root.setCenter(new HomeView(services, this).root());
    }

    @Override
    public void openProject(Screen screen) {
        closeProject();
        project = screen;
        root.setCenter(screen.node());
    }

    @Override
    public void showOnTop(Node node) {
        root.setCenter(node);
    }

    @Override
    public void backToProject() {
        if (project == null) {
            showHome();
        } else {
            root.setCenter(project.node());
        }
    }

    @Override
    public Window window() {
        return stage;
    }

    private void closeProject() {
        if (project != null) {
            project.close();
            project = null;
        }
    }

    private MenuBar createMenuBar(Stage owner) {
        Messages messages = context.messages();

        MenuItem about = new MenuItem(messages.get("menu.help.about"));
        about.setOnAction(event -> new AboutDialog(context.appInfo(), context.platform(), messages).showAndWait(owner));

        MenuItem licenses = new MenuItem(messages.get("menu.help.licenses"));
        licenses.setOnAction(event -> new LicensesDialog(
                        context.componentRegistry(), context.platform().operatingSystem(), messages)
                .showAndWait(owner));

        Menu help = new Menu(messages.get("menu.help"));
        help.getItems().addAll(about, licenses);
        return new MenuBar(help);
    }

    private static String stylesheetUrl() {
        var url = MainWindow.class.getResource(THEME_STYLESHEET);
        if (url == null) {
            throw new IllegalStateException("Missing bundled resource " + THEME_STYLESHEET);
        }
        return url.toExternalForm();
    }
}
