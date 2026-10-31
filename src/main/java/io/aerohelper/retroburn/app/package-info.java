// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later

/**
 * Application entry point, composition root and main window.
 *
 * <p>{@link io.aerohelper.retroburn.app.Main} starts JavaFX, {@link
 * io.aerohelper.retroburn.app.AppContext} wires every long-lived object by hand, and {@link
 * io.aerohelper.retroburn.app.MainWindow} hosts the screens from {@code ui}. This is the top layer:
 * nothing else depends on it. See {@code docs/design/LLD-app.md}.
 */
package io.aerohelper.retroburn.app;
