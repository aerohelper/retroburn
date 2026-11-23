// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.app;

import javafx.application.Application;
import javafx.stage.Stage;

/** JavaFX lifecycle: builds the application context, shows the main window, closes the context. */
public final class RetroBurnApplication extends Application {

    private AppContext context;

    @Override
    public void start(Stage stage) {
        context = AppContext.createDefault();
        new MainWindow(context).show(stage);
        context.checkToolsInBackground();
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
    }
}
