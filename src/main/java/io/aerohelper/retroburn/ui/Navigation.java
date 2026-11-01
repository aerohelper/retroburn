// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import javafx.scene.Node;
import javafx.stage.Window;

/**
 * Moves between screens in the main window. Implemented by the application shell.
 *
 * <p>A project screen (data disc, Burn ISO) stays open while its job's progress and result are
 * shown, so its drive bar keeps watching drives and the user can go back to the same compilation.
 * Going home closes it.
 */
public interface Navigation {

    /** Closes the open project screen, if any, and shows the home screen. */
    void showHome();

    /** Closes the open project screen, if any, and shows this one. */
    void openProject(Screen project);

    /** Shows a node, such as progress or a result, without closing the project screen. */
    void showOnTop(Node node);

    /** Shows the open project screen again, or home if there is none. */
    void backToProject();

    /** The main window, as owner for dialogs and file choosers. */
    Window window();

    /** One project screen of the main window. */
    interface Screen {

        Node node();

        /** Stops whatever the screen started, such as hot-plug watching. */
        default void close() {}
    }
}
