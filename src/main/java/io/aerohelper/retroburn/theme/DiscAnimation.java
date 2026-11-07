// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.theme;

import java.util.function.BooleanSupplier;
import javafx.animation.AnimationTimer;
import javafx.scene.AccessibleRole;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.transform.Rotate;

/**
 * The spinning disc: an original drawing of a disc with a hub and a coloured sheen, styled by the
 * theme ({@code .disc-surface}, {@code .disc-sheen}, {@code .disc-sheen-2}, {@code .disc-hub},
 * {@code .disc-hole}) and turned by {@link DiscMotion}. Decorative: screen readers skip it. It stops
 * its timer when it leaves the scene.
 */
public final class DiscAnimation {

    private final DiscMotion motion = new DiscMotion();
    private final Rotate rotate = new Rotate(0, 0, 0);
    private final Group root;
    private final BooleanSupplier animate;
    private final AnimationTimer timer;
    private long lastFrame;

    /** @param animate whether to move; checked every frame, so turning it off stops the disc at once */
    public DiscAnimation(double radius, BooleanSupplier animate) {
        this.animate = animate;
        Circle surface = styled(new Circle(radius), "disc-surface");
        Arc sheen = styled(new Arc(0, 0, radius * 0.95, radius * 0.95, 20, 50), "disc-sheen");
        Arc opposite = styled(new Arc(0, 0, radius * 0.95, radius * 0.95, 200, 50), "disc-sheen", "disc-sheen-2");
        sheen.setType(ArcType.ROUND);
        opposite.setType(ArcType.ROUND);
        Circle hub = styled(new Circle(radius * 0.32), "disc-hub");
        Circle hole = styled(new Circle(radius * 0.11), "disc-hole");
        Group spinning = new Group(surface, sheen, opposite, hub, hole);
        spinning.getTransforms().add(rotate);
        root = new Group(spinning);
        root.getStyleClass().add("disc-animation");
        root.setAccessibleRole(AccessibleRole.NODE);
        root.setFocusTraversable(false);
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double seconds = lastFrame == 0 ? 0 : (now - lastFrame) / 1e9;
                lastFrame = now;
                if (DiscAnimation.this.animate.getAsBoolean()) {
                    motion.advance(Math.min(seconds, 0.1));
                    rotate.setAngle(motion.angle());
                }
            }
        };
        root.sceneProperty().addListener((observable, before, scene) -> {
            if (scene == null) {
                timer.stop();
            } else {
                lastFrame = 0;
                timer.start();
            }
        });
    }

    public Node node() {
        return root;
    }

    public DiscMotion.State state() {
        return motion.state();
    }

    public void setState(DiscMotion.State state) {
        motion.setState(state);
    }

    private static <T extends Node> T styled(T node, String... classes) {
        node.getStyleClass().addAll(classes);
        return node;
    }
}
