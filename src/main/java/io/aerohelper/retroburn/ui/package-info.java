// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later

/**
 * JavaFX screens and dialogs.
 *
 * <p>Currently the home screen ({@link io.aerohelper.retroburn.ui.HomeView}) and the Help dialogs
 * ({@link io.aerohelper.retroburn.ui.AboutDialog}, {@link io.aerohelper.retroburn.ui.LicensesDialog}).
 * Later: one compilation view per project type and the shared drive bar, confirmation, progress and
 * result views. Views only display state and forward user actions; work happens in lower layers,
 * off the JavaFX Application Thread. Only {@code app} may depend on this package.
 *
 * <p>See {@code docs/design/LLD-app.md}, {@code docs/design/LLD-credits.md} and {@code
 * docs/ux/UX_SPEC.md}.
 */
package io.aerohelper.retroburn.ui;
