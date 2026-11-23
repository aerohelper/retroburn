// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import javafx.scene.control.Dialog;
import javafx.stage.Window;

/** Small helpers shared by RetroBurn's dialogs. */
final class Dialogs {

    private Dialogs() {}

    /**
     * Applies the owner window's stylesheets to a dialog. Dialogs get their own scene, so without
     * this they would ignore the active theme.
     */
    static void useOwnerStylesheets(Dialog<?> dialog, Window owner) {
        if (owner != null && owner.getScene() != null) {
            dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
    }
}
