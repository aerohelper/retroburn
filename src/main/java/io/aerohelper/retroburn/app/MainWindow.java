// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.app;

import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.ui.AboutDialog;
import io.aerohelper.retroburn.ui.HomeView;
import io.aerohelper.retroburn.ui.LicensesDialog;
import java.util.Objects;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/** The main window: menu bar on top, the current screen in the centre. */
public final class MainWindow {

    private static final String THEME_STYLESHEET = "/themes/classic/theme.css";
    private static final double INITIAL_WIDTH = 960;
    private static final double INITIAL_HEIGHT = 640;

    private final AppContext context;

    public MainWindow(AppContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    /** Fills the stage with the main window content and shows it. */
    public void show(Stage stage) {
        BorderPane root = new BorderPane();
        root.setTop(createMenuBar(stage));
        root.setCenter(new HomeView(context.messages()).root());

        Scene scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
        scene.getStylesheets().add(stylesheetUrl());

        stage.setTitle(context.appInfo().name());
        stage.setScene(scene);
        stage.show();
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
