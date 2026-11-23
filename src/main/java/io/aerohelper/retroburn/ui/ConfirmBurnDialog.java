// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.ByteSizes;
import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.BlankMode;
import io.aerohelper.retroburn.project.BurnPlan;
import io.aerohelper.retroburn.project.JobKind;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Asks before anything is written or erased: what will happen, to which drive and disc, what stops
 * it and what to watch out for. The confirming button is never the default, so Enter or Esc never
 * starts a burn.
 */
final class ConfirmBurnDialog {

    /** What the user chose. */
    record Choice(BurnPlan plan, boolean dismissUsbTip, BlankMode blankMode) {}

    private final Messages messages;

    ConfirmBurnDialog(Messages messages) {
        this.messages = messages;
    }

    /**
     * @param replan the plan again with verify on or off, when the user flips the checkbox
     * @return the confirmed plan, or empty if the user went back
     */
    Optional<Choice> show(Window owner, BurnPlan initial, Replanner replan) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Dialogs.useOwnerStylesheets(dialog, owner);
        boolean erase = initial.kind() == JobKind.ERASE;
        dialog.setTitle(messages.get(erase ? "confirm.erase.title" : "confirm.burn.title"));

        ButtonType go = new ButtonType(
                messages.get(erase ? "confirm.erase.button" : "confirm.burn.button"), ButtonBar.ButtonData.OTHER);
        ButtonType back = new ButtonType(messages.get("confirm.back"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(go, back);
        Button goButton = (Button) dialog.getDialogPane().lookupButton(go);
        goButton.setDefaultButton(false);
        goButton.getStyleClass().add("danger-button");
        ((Button) dialog.getDialogPane().lookupButton(back)).setDefaultButton(true);

        VBox content = new VBox();
        content.getStyleClass().add("dialog-content");
        content.setPrefWidth(520);

        CheckBox verify = new CheckBox(messages.get("confirm.verify"));
        verify.setSelected(initial.verify());
        CheckBox dismissTip = new CheckBox(messages.get("confirm.dontShowUsbTip"));
        ToggleGroup modes = new ToggleGroup();
        RadioButton quick = new RadioButton(messages.get("confirm.erase.quick"));
        RadioButton full = new RadioButton(messages.get("confirm.erase.full"));
        quick.setToggleGroup(modes);
        full.setToggleGroup(modes);
        quick.setSelected(true);

        BurnPlan[] plan = {initial};
        Runnable fill = () -> {
            content.getChildren().setAll(describe(plan[0]));
            if (erase) {
                content.getChildren().addAll(quick, full);
            } else {
                content.getChildren().add(verify);
            }
            if (plan[0].showUsbTip()) {
                content.getChildren().add(dismissTip);
            }
            goButton.setDisable(!plan[0].canStart());
        };
        verify.selectedProperty().addListener((observable, before, after) -> {
            plan[0] = replan.withVerify(after);
            fill.run();
        });
        fill.run();
        dialog.getDialogPane().setContent(content);

        return dialog.showAndWait()
                .filter(button -> button == go)
                .map(button -> new Choice(
                        plan[0], dismissTip.isSelected(), full.isSelected() ? BlankMode.FULL : BlankMode.QUICK));
    }

    /** Re-runs the pre-check with verify switched on or off. */
    @FunctionalInterface
    interface Replanner {
        BurnPlan withVerify(boolean verify);
    }

    private Node[] describe(BurnPlan plan) {
        VBox summary = new VBox();
        summary.getStyleClass().add("confirm-summary");
        Label heading = new Label(messages.get(heading(plan.kind())));
        heading.getStyleClass().add("dialog-heading");
        heading.setWrapText(true);
        summary.getChildren().add(heading);
        plan.drive()
                .ifPresent(drive -> summary.getChildren()
                        .add(new Label(messages.format("confirm.drive", drive.displayNameWithLocation()))));
        plan.media().ifPresent(media -> summary.getChildren().add(new Label(MediaText.describe(media, messages))));
        if (plan.bytesToWrite() > 0) {
            summary.getChildren()
                    .add(new Label(messages.format("confirm.size", ByteSizes.format(plan.bytesToWrite()))));
        }
        if (plan.eraseFirst()) {
            summary.getChildren().add(new Label(messages.get("confirm.eraseFirst")));
        }
        VBox notes = new VBox();
        notes.getStyleClass().add("confirm-notes");
        for (UserMessage blocker : plan.blockers()) {
            notes.getChildren().add(note(blocker, "blocker"));
        }
        for (UserMessage warning : plan.warnings()) {
            notes.getChildren().add(note(warning, warning.key().startsWith("burn.note") ? "note" : "warning"));
        }
        return new Node[] {summary, notes};
    }

    private Label note(UserMessage message, String kind) {
        String prefix = messages.get("confirm.prefix." + kind);
        String text = messages.format(message);
        Label label = new Label(prefix.isEmpty() ? text : prefix + " " + text);
        label.setWrapText(true);
        label.getStyleClass().add("confirm-" + kind);
        return label;
    }

    private static String heading(JobKind kind) {
        return switch (kind) {
            case DATA_DISC -> "confirm.heading.dataDisc";
            case BURN_ISO -> "confirm.heading.burnIso";
            case ERASE -> "confirm.heading.erase";
        };
    }
}
