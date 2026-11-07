// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * The current design with undo and redo. Keeps the document before and after each command, at most
 * {@link #LIMIT} steps; documents are immutable, so a step costs only the changed records. Not
 * thread-safe: used from the JavaFX thread.
 */
public final class CommandHistory {

    public static final int LIMIT = 100;

    private record Step(EditorCommand command, DesignDocument before, DesignDocument after) {}

    private final Deque<Step> undo = new ArrayDeque<>();
    private final Deque<Step> redo = new ArrayDeque<>();
    private DesignDocument current;

    public CommandHistory(DesignDocument start) {
        this.current = Objects.requireNonNull(start, "start");
    }

    public DesignDocument current() {
        return current;
    }

    /** Applies a command; merges it with the previous one when both have the same merge key. */
    public DesignDocument execute(EditorCommand command) {
        DesignDocument after = command.apply(current);
        if (after.equals(current)) {
            return current;
        }
        Step last = undo.peekLast();
        if (last != null
                && !command.mergeKey().isEmpty()
                && command.mergeKey().equals(last.command().mergeKey())) {
            undo.removeLast();
            undo.addLast(new Step(command, last.before(), after));
        } else {
            undo.addLast(new Step(command, current, after));
            if (undo.size() > LIMIT) {
                undo.removeFirst();
            }
        }
        redo.clear();
        current = after;
        return current;
    }

    /** Starts over with a document, such as one just opened; clears undo and redo. */
    public void reset(DesignDocument document) {
        undo.clear();
        redo.clear();
        current = Objects.requireNonNull(document, "document");
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** Message key of the edit Undo would revert. */
    public Optional<String> undoName() {
        return Optional.ofNullable(undo.peekLast()).map(step -> step.command().nameKey());
    }

    public Optional<String> redoName() {
        return Optional.ofNullable(redo.peekLast()).map(step -> step.command().nameKey());
    }

    public DesignDocument undo() {
        Step step = undo.pollLast();
        if (step != null) {
            redo.addLast(step);
            current = step.before();
        }
        return current;
    }

    public DesignDocument redo() {
        Step step = redo.pollLast();
        if (step != null) {
            undo.addLast(step);
            current = step.after();
        }
        return current;
    }

    /** Ends merging, so the next command starts a new undo step (for example when a drag ends). */
    public void seal() {
        Step last = undo.pollLast();
        if (last != null) {
            undo.addLast(new Step(new Sealed(last.command()), last.before(), last.after()));
        }
    }

    /** A command that never merges with the next one. */
    private record Sealed(EditorCommand inner) implements EditorCommand {

        @Override
        public DesignDocument apply(DesignDocument document) {
            return inner.apply(document);
        }

        @Override
        public String nameKey() {
            return inner.nameKey();
        }
    }
}
