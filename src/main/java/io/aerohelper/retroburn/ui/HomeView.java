// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.Messages;
import java.util.List;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/**
 * The home screen: "What do you want to make?" with one card per kind of project.
 *
 * <p>No project type can be opened yet, so every card is disabled and says why. When project types
 * are added, the cards come from the project type registry instead of the fixed list below.
 */
public final class HomeView {

    /** Message keys of the cards, in display order. */
    private static final List<String> CARD_KEYS = List.of(
            "home.card.dataDisc",
            "home.card.audioCd",
            "home.card.mp3Disc",
            "home.card.videoDvd",
            "home.card.burnIso",
            "home.card.createIso",
            "home.card.coverAndLabel");

    private final Messages messages;

    public HomeView(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Builds the screen's node tree. Call on the JavaFX Application Thread. */
    public Node root() {
        Label heading = new Label(messages.get("home.heading"));
        heading.getStyleClass().add("home-heading");

        FlowPane cards = new FlowPane();
        cards.getStyleClass().add("home-cards");
        cards.setAlignment(Pos.CENTER);
        for (String key : CARD_KEYS) {
            cards.getChildren().add(createCard(messages.get(key), messages.get("home.card.notAvailableYet")));
        }

        VBox root = new VBox(heading, cards);
        root.getStyleClass().add("home-view");
        root.setAlignment(Pos.TOP_CENTER);
        return root;
    }

    private static Button createCard(String title, String unavailableReason) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("project-card-title");
        Label reasonLabel = new Label(unavailableReason);
        reasonLabel.getStyleClass().add("project-card-reason");

        Button card = new Button();
        card.setGraphic(new VBox(titleLabel, reasonLabel));
        card.getStyleClass().add("project-card");
        // Screen readers announce the reason together with the title.
        card.setAccessibleText(title + ". " + unavailableReason);
        card.setDisable(true);
        return card;
    }
}
