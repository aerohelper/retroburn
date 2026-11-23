// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.design.DesignElement.Align;
import io.aerohelper.retroburn.design.DesignElement.TextStyle;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CommandHistoryTest {

    static DesignDocument empty() {
        return new DesignDocument(
                DesignDocument.SCHEMA_VERSION, "cd-front", 0, 0, "classic", "#FFFFFF", CoverContent.EMPTY, List.of());
    }

    static DesignElement.Text text(String id, double x) {
        return new DesignElement.Text(
                id, Frame.of(x, 10, 50, 10), "Hello", new TextStyle("Lato", 12, false, "#000000", Align.LEFT));
    }

    @Test
    void undoAndRedoWalkThroughTheEdits() {
        CommandHistory history = new CommandHistory(empty());
        assertFalse(history.canUndo());
        history.execute(new EditorCommand.AddElement(text("text-1", 10)));
        history.execute(new EditorCommand.ReplaceElement(text("text-1", 20)));
        assertEquals(20, history.current().elements().getFirst().frame().x());
        assertEquals(Optional.of("design.edit.change"), history.undoName());

        history.undo();
        assertEquals(10, history.current().elements().getFirst().frame().x());
        assertTrue(history.canRedo());
        assertEquals(Optional.of("design.edit.change"), history.redoName());
        history.undo();
        assertTrue(history.current().elements().isEmpty());
        history.undo();
        assertTrue(history.current().elements().isEmpty(), "nothing more to undo");

        history.redo();
        history.redo();
        assertEquals(20, history.current().elements().getFirst().frame().x());
    }

    @Test
    void aNewEditClearsRedo() {
        CommandHistory history = new CommandHistory(empty());
        history.execute(new EditorCommand.AddElement(text("text-1", 10)));
        history.undo();
        history.execute(new EditorCommand.AddElement(text("text-2", 10)));
        assertFalse(history.canRedo());
    }

    @Test
    void keepsTheLastHundredSteps() {
        CommandHistory history = new CommandHistory(empty());
        history.execute(new EditorCommand.AddElement(text("text-1", 0)));
        for (int step = 1; step <= 150; step++) {
            history.execute(new EditorCommand.ReplaceElement(text("text-1", step)));
        }
        int undone = 0;
        while (history.canUndo()) {
            history.undo();
            undone++;
        }
        assertEquals(CommandHistory.LIMIT, undone);
        assertEquals(50, history.current().elements().getFirst().frame().x());
    }

    @Test
    void oneDragIsOneUndoStepUntilSealed() {
        CommandHistory history = new CommandHistory(empty());
        history.execute(new EditorCommand.AddElement(text("text-1", 0)));
        for (int x = 1; x <= 5; x++) {
            history.execute(new EditorCommand.ReplaceElement(text("text-1", x), "drag-text-1"));
        }
        history.seal();
        history.execute(new EditorCommand.ReplaceElement(text("text-1", 9), "drag-text-1"));

        history.undo();
        assertEquals(5, history.current().elements().getFirst().frame().x());
        history.undo();
        assertEquals(0, history.current().elements().getFirst().frame().x());
    }

    @Test
    void anEditThatChangesNothingIsNotRecorded() {
        CommandHistory history = new CommandHistory(empty());
        history.execute(new EditorCommand.RemoveElement("nothing"));
        assertFalse(history.canUndo());
    }

    @Test
    void zOrderAlignAndContentCommands() {
        DesignDocument document = empty().withElements(List.of(text("a", 0), text("b", 0), text("c", 0)));
        DesignDocument raised = new EditorCommand.MoveInZOrder("a", 5).apply(document);
        assertEquals(
                List.of("b", "c", "a"),
                raised.elements().stream().map(DesignElement::id).toList());
        DesignDocument lowered = new EditorCommand.MoveInZOrder("c", -1).apply(document);
        assertEquals(
                List.of("a", "c", "b"),
                lowered.elements().stream().map(DesignElement::id).toList());

        DesignDocument right =
                new EditorCommand.AlignToItem("b", EditorCommand.AlignToItem.Edge.RIGHT, 120, 120).apply(document);
        assertEquals(70, right.element("b").orElseThrow().frame().x());
        DesignDocument middle =
                new EditorCommand.AlignToItem("b", EditorCommand.AlignToItem.Edge.MIDDLE, 120, 120).apply(document);
        assertEquals(55, middle.element("b").orElseThrow().frame().y());

        CoverContent content = new CoverContent("Audio CD", "Mix", "", List.of("1. One"));
        assertEquals(
                content, new EditorCommand.SetContent(content).apply(document).content());
        assertEquals("text-1", empty().newId("text"));
        assertEquals("a-1", document.newId("a"));
    }
}
