// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.audio.CdAudio;
import io.aerohelper.retroburn.common.ByteSizes;
import io.aerohelper.retroburn.common.Messages;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * How much of the disc a compilation fills: under capacity, near full (95 % or more), over, or still
 * being calculated. The state is in the text too, never in the colour alone.
 */
final class CapacityBar {

    static final double NEAR_FULL = 0.95;

    private final Messages messages;
    private final ProgressBar bar = new ProgressBar(0);
    private final Label text = new Label();
    private final HBox root;

    CapacityBar(Messages messages) {
        this.messages = messages;
        bar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bar, Priority.ALWAYS);
        bar.setAccessibleText(messages.get("capacity.accessible"));
        root = new HBox(bar, text);
        root.getStyleClass().add("capacity-bar");
    }

    Node node() {
        return root;
    }

    void calculating() {
        setState("capacity-calculating");
        bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        text.setText(messages.get("capacity.calculating"));
    }

    /** @param capacity 0 when no disc is known; only the size is shown then */
    void show(long bytes, long capacity) {
        if (capacity <= 0) {
            setState("capacity-under");
            bar.setProgress(0);
            text.setText(ByteSizes.format(bytes));
            return;
        }
        double fraction = (double) bytes / capacity;
        bar.setProgress(Math.min(1, fraction));
        String sizes = messages.format("capacity.of", ByteSizes.format(bytes), ByteSizes.format(capacity));
        if (fraction > 1) {
            setState("capacity-over");
            text.setText(messages.format("capacity.over", sizes));
        } else if (fraction >= NEAR_FULL) {
            setState("capacity-near-full");
            text.setText(messages.format("capacity.nearFull", sizes));
        } else {
            setState("capacity-under");
            text.setText(sizes);
        }
    }

    /**
     * Length of audio against the disc, as "mm:ss of mm:ss".
     *
     * @param capacitySectors the disc's length; 0 when no disc is known, which compares with 80 minutes
     */
    void showTime(long sectors, long capacitySectors) {
        long capacity = capacitySectors > 0 ? capacitySectors : CdAudio.SECTORS_80_MINUTES;
        double fraction = (double) sectors / capacity;
        bar.setProgress(Math.min(1, fraction));
        String times =
                messages.format("capacity.of", CdAudio.minutesAndSeconds(sectors), CdAudio.minutesAndSeconds(capacity));
        if (fraction > 1) {
            setState("capacity-over");
            text.setText(messages.format("capacity.overTime", times));
        } else if (fraction >= NEAR_FULL) {
            setState("capacity-near-full");
            text.setText(messages.format("capacity.nearFull", times));
        } else {
            setState("capacity-under");
            text.setText(times);
        }
    }

    private void setState(String styleClass) {
        root.getStyleClass().removeAll("capacity-calculating", "capacity-under", "capacity-near-full", "capacity-over");
        root.getStyleClass().add(styleClass);
    }
}
