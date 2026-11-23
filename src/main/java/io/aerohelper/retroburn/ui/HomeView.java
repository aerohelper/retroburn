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
 * The home screen: "What do you want to make?" with one card per kind of project. Cards for project
 * types that are not built yet are disabled and say so.
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

    private final UiServices services;
    private final Navigation navigation;
    private final Messages messages;

    public HomeView(UiServices services, Navigation navigation) {
        this.services = Objects.requireNonNull(services, "services");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.messages = services.messages();
    }

    /** Builds the screen's node tree. Call on the JavaFX Application Thread. */
    public Node root() {
        Label heading = new Label(messages.get("home.heading"));
        heading.getStyleClass().add("home-heading");

        FlowPane cards = new FlowPane();
        cards.getStyleClass().add("home-cards");
        cards.setAlignment(Pos.CENTER);
        for (String key : CARD_KEYS) {
            cards.getChildren().add(createCard(key));
        }

        VBox root = new VBox(heading, cards);
        root.getStyleClass().add("home-view");
        root.setAlignment(Pos.TOP_CENTER);
        return root;
    }

    private Button createCard(String key) {
        Runnable open = switch (key) {
            case "home.card.dataDisc" ->
                () -> navigation.openProject(new DataDiscView(services, navigation, DataDiscView.Mode.BURN));
            case "home.card.burnIso" -> () -> navigation.openProject(new BurnIsoView(services, navigation));
            case "home.card.createIso" ->
                () -> navigation.openProject(new DataDiscView(services, navigation, DataDiscView.Mode.CREATE_ISO));
            default -> null;
        };
        String title = messages.get(key);
        String description = messages.get(open == null ? "home.card.notAvailableYet" : key + ".description");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("project-card-title");
        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("project-card-reason");
        descriptionLabel.setWrapText(true);

        Button card = new Button();
        card.setGraphic(new VBox(titleLabel, descriptionLabel));
        card.getStyleClass().add("project-card");
        // Screen readers announce the description or the reason together with the title.
        card.setAccessibleText(title + ". " + description);
        if (open == null) {
            card.setDisable(true);
        } else {
            card.setOnAction(event -> open.run());
        }
        return card;
    }
}
