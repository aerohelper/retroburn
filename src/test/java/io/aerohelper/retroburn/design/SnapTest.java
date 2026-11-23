// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SnapTest {

    private final PrintTemplate front = PrintTemplates.builtIn().get("cd-front");

    @Test
    void anEdgeNearTheSafeZoneSnapsToIt() {
        Snap.Guides guides = Snap.guides(front, CommandHistoryTest.empty(), "x");
        Snap.Result result = Snap.move(Frame.of(4, 40.7, 20, 10), guides, Snap.TOLERANCE_MM);
        assertEquals(3, result.frame().x(), 1e-9);
        assertEquals(List.of(3.0), result.vertical());
        // Top 40.7, centre 45.7 and bottom 50.7 are all more than 1.5 mm from a line.
        assertEquals(40.7, result.frame().y(), 1e-9);
        assertTrue(result.horizontal().isEmpty());
    }

    @Test
    void theCentreSnapsToTheCentreLine() {
        Snap.Guides guides = Snap.guides(front, CommandHistoryTest.empty(), "x");
        Snap.Result result = Snap.move(Frame.of(39.2, 20, 40, 10), guides, Snap.TOLERANCE_MM);
        assertEquals(40, result.frame().x(), 1e-9);
        assertEquals(List.of(60.0), result.vertical());
    }

    @Test
    void otherElementsAreGuidesButNotTheMovingOne() {
        DesignDocument document = CommandHistoryTest.empty()
                .withElements(List.of(CommandHistoryTest.text("a", 30), CommandHistoryTest.text("b", 70)));
        Snap.Guides guides = Snap.guides(front, document, "b");
        assertTrue(guides.vertical().contains(80.0), "right edge of a");
        assertTrue(!guides.vertical().contains(95.0), "centre of b itself");
    }

    @Test
    void resizingSnapsTheBottomRightCorner() {
        Snap.Guides guides = Snap.guides(front, CommandHistoryTest.empty(), "x");
        Snap.Result result = Snap.resize(Frame.of(10, 10, 106.4, 50), guides, Snap.TOLERANCE_MM);
        assertEquals(107, result.frame().width(), 1e-9);
        assertEquals(50, result.frame().height(), 1e-9);
    }

    @Test
    void foldsAreGuides() {
        PrintTemplate wrap = PrintTemplates.builtIn().get("dvd-wrap");
        DesignDocument document =
                Presets.create("minimal", wrap, CoverContent.EMPTY).withElements(List.of());
        Snap.Guides guides = Snap.guides(wrap, document, "x");
        assertTrue(guides.vertical().containsAll(List.of(129.5, 143.5, 132.5, 140.5)));
    }
}
