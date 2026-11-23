// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.AppInfo;
import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.platform.PlatformServices;
import java.util.Objects;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Help → About: name, version, copyright, licence, where to get the source, and the no-warranty
 * statement the GPL asks interactive programs to show.
 */
public final class AboutDialog {

    private final AppInfo appInfo;
    private final PlatformServices platform;
    private final Messages messages;

    public AboutDialog(AppInfo appInfo, PlatformServices platform, Messages messages) {
        this.appInfo = Objects.requireNonNull(appInfo, "appInfo");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Shows the dialog and blocks until it is closed. Call on the JavaFX Application Thread. */
    public void showAndWait(Window owner) {
        Label title = new Label(appInfo.name() + " " + appInfo.version());
        title.getStyleClass().add("dialog-heading");

        // A read-only text field rather than a link: the app makes no network calls, but users can
        // still select and copy the address.
        TextField sourceUrl = new TextField(appInfo.sourceUrl());
        sourceUrl.setEditable(false);
        sourceUrl.setAccessibleText(messages.get("about.sourceCode"));

        Label warranty = new Label(messages.get("about.noWarranty"));
        warranty.setWrapText(true);

        VBox content = new VBox(
                title,
                new Label(appInfo.copyright()),
                new Label(messages.get("about.license")),
                new Label(messages.get("about.sourceCode")),
                sourceUrl,
                warranty,
                new Label(messages.format("about.platform", platform.displayName())));
        content.getStyleClass().add("dialog-content");
        content.setPrefWidth(460);

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Dialogs.useOwnerStylesheets(dialog, owner);
        dialog.setTitle(messages.format("about.title", appInfo.name()));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }
}
