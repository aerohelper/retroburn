// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.project.BurnJob;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * How a job ended: success, success without a check, check failed, failed, drive lost, or
 * cancelled. "Show details" holds the technical detail and the last tool output.
 */
final class ResultView {

    private final VBox root;

    /**
     * @param successText what the user now has, for example "The disc is ready."
     * @param back returns to the project screen
     */
    ResultView(
            Messages messages,
            BurnJob.Result result,
            String successText,
            List<String> logTail,
            Runnable back,
            Runnable home) {
        String outcome = result.outcome().name().toLowerCase(Locale.ROOT).replace('_', '-');
        Label title = new Label(messages.get("result.title." + key(result.outcome())));
        title.getStyleClass().addAll("result-title", "result-" + outcome);
        title.setWrapText(true);

        Label text = new Label(message(messages, result, successText));
        text.setWrapText(true);

        VBox content = new VBox(title, text);
        Optional<RetroBurnException> error = result.error();
        if (error.isPresent() || !logTail.isEmpty()) {
            StringBuilder details = new StringBuilder();
            error.ifPresent(found -> details.append(messages.format(found.userMessage()))
                    .append("\n\n")
                    .append(found.technicalDetail())
                    .append("\n\n"));
            result.keptWorkFolder()
                    .ifPresent(folder -> details.append(messages.format("result.keptFolder", folder))
                            .append("\n\n"));
            logTail.forEach(line -> details.append(line).append('\n'));
            TextArea area = new TextArea(details.toString());
            area.setEditable(false);
            area.setWrapText(true);
            area.setPrefRowCount(14);
            TitledPane pane = new TitledPane(messages.get("result.showDetails"), area);
            pane.setExpanded(false);
            content.getChildren().add(pane);
        }

        Button backButton = new Button(messages.get("result.back"));
        backButton.setOnAction(event -> back.run());
        Button homeButton = new Button(messages.get("result.home"));
        homeButton.setOnAction(event -> home.run());
        homeButton.setDefaultButton(true);
        HBox buttons = new HBox(backButton, homeButton);
        buttons.getStyleClass().add("result-buttons");
        content.getChildren().add(buttons);
        content.getStyleClass().add("result-view");
        root = content;
    }

    Node node() {
        return root;
    }

    private static String message(Messages messages, BurnJob.Result result, String successText) {
        return switch (result.outcome()) {
            case SUCCEEDED -> successText;
            case SUCCEEDED_WITHOUT_VERIFY -> successText + " " + messages.get("result.notVerified");
            case CANCELLED -> messages.get("result.cancelledText");
            case DRIVE_LOST -> messages.get("result.driveLostText");
            case VERIFY_FAILED, FAILED ->
                result.error()
                        .map(error -> messages.format(error.userMessage()))
                        .orElse("");
        };
    }

    private static String key(BurnJob.Outcome outcome) {
        return switch (outcome) {
            case SUCCEEDED, SUCCEEDED_WITHOUT_VERIFY -> "succeeded";
            case VERIFY_FAILED -> "verifyFailed";
            case FAILED -> "failed";
            case DRIVE_LOST -> "driveLost";
            case CANCELLED -> "cancelled";
        };
    }
}
