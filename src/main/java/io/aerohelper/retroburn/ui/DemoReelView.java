// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.reel.DemoSession;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Help → Make a demo burn reel: a reel of a made-up burn, without a drive. */
public final class DemoReelView {

    private final VBox root;

    public DemoReelView(UiServices services, Navigation navigation) {
        Messages messages = services.messages();
        Label heading = new Label(messages.get("reel.demo.title"));
        heading.getStyleClass().add("result-title");
        Label text = new Label(messages.get("reel.demo.text"));
        text.setWrapText(true);
        ReelPanel panel = new ReelPanel(
                services, DemoSession.create(services.appearance().theme().id()));
        Button home = new Button(messages.get("result.home"));
        home.setOnAction(event -> navigation.showHome());
        root = new VBox(heading, text, panel.node(), home);
        root.getStyleClass().add("result-view");
    }

    public Node node() {
        return root;
    }
}
