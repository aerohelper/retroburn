// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.app;

import javafx.application.Application;
import javafx.stage.Stage;

/** JavaFX lifecycle: builds the application context and shows the main window. */
public final class RetroBurnApplication extends Application {

    @Override
    public void start(Stage stage) {
        AppContext context = AppContext.createDefault();
        new MainWindow(context).show(stage);
    }
}
