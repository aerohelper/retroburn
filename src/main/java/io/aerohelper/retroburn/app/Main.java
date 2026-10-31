// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.app;

import javafx.application.Application;

/**
 * Program entry point.
 *
 * <p>Deliberately not the {@link Application} subclass: when the main class extends {@code
 * Application}, the Java launcher refuses to start unless JavaFX is on the module path. Launching
 * through this class works in both set-ups (see {@code docs/adr/0010}).
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        Application.launch(RetroBurnApplication.class, args);
    }
}
