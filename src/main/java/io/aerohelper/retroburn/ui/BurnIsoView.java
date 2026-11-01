// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.project.BurnJobFactory;
import io.aerohelper.retroburn.project.DiscCapacity;
import io.aerohelper.retroburn.project.JobKind;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Burn ISO: pick an image file, see whether it fits, burn it as it is. */
final class BurnIsoView implements Navigation.Screen {

    private final Messages messages;
    private final Navigation navigation;
    private final BurnFlow flow;
    private final BurnJobFactory factory;
    private final DriveBar driveBar;
    private final CapacityBar capacity;
    private final Label file = new Label();
    private final Button burn = new Button();
    private final BorderPane root = new BorderPane();
    private Path image;
    private long size;

    BurnIsoView(UiServices services, Navigation navigation) {
        this.messages = services.messages();
        this.navigation = navigation;
        this.flow = new BurnFlow(services, navigation);
        this.factory = new BurnJobFactory(
                services.platform().burnEngine(), services.platform().discImageBuilder());
        this.capacity = new CapacityBar(messages);
        this.driveBar = new DriveBar(services, this::erase);
        driveBar.media().addListener((observable, before, after) -> showCapacity());

        Label heading = new Label(messages.get("home.card.burnIso"));
        heading.getStyleClass().add("view-heading");
        root.setTop(new VBox(driveBar.node(), heading, capacity.node()));

        Button choose = new Button(messages.get("iso.choose"));
        choose.setOnAction(event -> choose());
        file.setText(messages.get("iso.none"));
        file.setWrapText(true);
        burn.setText(messages.get("iso.burn"));
        burn.getStyleClass().add("primary-button");
        burn.setOnAction(event -> burn());
        Button home = new Button(messages.get("view.home"));
        home.setOnAction(event -> navigation.showHome());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(choose, spacer, home, burn);
        actions.getStyleClass().add("view-actions");
        VBox centre = new VBox(file, actions);
        centre.getStyleClass().add("view-content");
        root.setCenter(centre);
        root.getStyleClass().add("project-view");
        showCapacity();
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void close() {
        driveBar.close();
    }

    private void choose() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get("iso.choose"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.get("data.isoFiles"), "*.iso"));
        File chosen = chooser.showOpenDialog(navigation.window());
        if (chosen == null) {
            return;
        }
        try {
            size = Files.size(chosen.toPath());
            image = chosen.toPath();
            file.setText(image.toString());
        } catch (IOException e) {
            image = null;
            size = 0;
            file.setText(messages.format("iso.unreadable", chosen));
        }
        showCapacity();
    }

    private void showCapacity() {
        MediaInfo media = driveBar.media().get();
        capacity.show(size, media == null ? 0 : DiscCapacity.usableBytes(media));
        burn.setDisable(image == null);
    }

    private void burn() {
        Path chosen = image;
        flow.start(
                driveBar,
                new BurnFlow.Job(
                        JobKind.BURN_ISO,
                        size,
                        false,
                        (plan, blankMode) -> factory.burnIso(plan, chosen),
                        messages.get("progress.title.burnIso"),
                        messages.get("result.text.burnIso")));
    }

    private void erase() {
        flow.start(
                driveBar,
                new BurnFlow.Job(
                        JobKind.ERASE,
                        0,
                        false,
                        factory::erase,
                        messages.get("progress.title.erase"),
                        messages.get("result.text.erase")));
    }
}
