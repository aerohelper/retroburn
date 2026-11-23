// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.common.OperatingSystem;
import io.aerohelper.retroburn.credits.ComponentRegistry;
import io.aerohelper.retroburn.credits.ThirdPartyComponent;
import java.util.List;
import java.util.Objects;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Help → Open-source licenses: every third-party component used on this OS, with its full licence
 * text. Everything is bundled, so it works offline.
 */
public final class LicensesDialog {

    private final ComponentRegistry registry;
    private final OperatingSystem operatingSystem;
    private final Messages messages;

    private final Label name = new Label();
    private final Label role = new Label();
    private final Label authors = new Label();
    private final Label license = new Label();
    private final Label homepage = new Label();
    private final Label provision = new Label();
    private final TextArea licenseText = new TextArea();

    public LicensesDialog(ComponentRegistry registry, OperatingSystem operatingSystem, Messages messages) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.operatingSystem = Objects.requireNonNull(operatingSystem, "operatingSystem");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Shows the dialog and blocks until it is closed. Call on the JavaFX Application Thread. */
    public void showAndWait(Window owner) {
        ListView<ThirdPartyComponent> list = createComponentList();

        name.getStyleClass().add("dialog-heading");
        for (Label label : List.of(name, role, authors, license, homepage, provision)) {
            label.setWrapText(true);
        }
        licenseText.setEditable(false);
        licenseText.setWrapText(true);
        licenseText.setAccessibleText(messages.get("licenses.text"));
        VBox.setVgrow(licenseText, Priority.ALWAYS);
        VBox details = new VBox(name, role, authors, license, homepage, provision, licenseText);
        details.getStyleClass().add("dialog-content");

        SplitPane content = new SplitPane(list, details);
        content.setDividerPositions(0.3);
        content.setPrefSize(900, 600);

        list.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            if (selected != null) {
                showDetails(selected);
            }
        });
        list.getSelectionModel().selectFirst();

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Dialogs.useOwnerStylesheets(dialog, owner);
        dialog.setTitle(messages.get("licenses.title"));
        dialog.setResizable(true);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private ListView<ThirdPartyComponent> createComponentList() {
        ListView<ThirdPartyComponent> list = new ListView<>();
        list.getItems().setAll(registry.componentsFor(operatingSystem));
        list.setAccessibleText(messages.get("licenses.list"));
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(ThirdPartyComponent component, boolean empty) {
                super.updateItem(component, empty);
                setText(empty || component == null ? null : component.name());
            }
        });
        return list;
    }

    private void showDetails(ThirdPartyComponent component) {
        name.setText(component.name());
        role.setText(messages.format("licenses.role", component.role()));
        authors.setText(messages.format("licenses.authors", component.authors()));
        license.setText(messages.format("licenses.license", component.license()));
        homepage.setText(messages.format("licenses.homepage", component.homepage()));
        provision.setText(
                messages.get("licenses.provision." + component.provision().name()));

        String text = registry.licenseText(component);
        String notice = registry.noticeText(component).orElse(null);
        licenseText.setText(notice == null ? text : notice + "\n\n" + text);
        licenseText.positionCaret(0);
    }
}
