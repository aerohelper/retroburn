// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One edit of a design. Commands are kept by {@link CommandHistory} for undo and redo.
 *
 * <p>{@link #mergeKey()} lets a run of small edits (one drag, typing in one field) become a single
 * undo step: consecutive commands with the same non-empty key are merged.
 */
public interface EditorCommand {

    DesignDocument apply(DesignDocument document);

    /** Message key naming the edit, for "Undo …". */
    String nameKey();

    default String mergeKey() {
        return "";
    }

    record AddElement(DesignElement element) implements EditorCommand {

        @Override
        public DesignDocument apply(DesignDocument document) {
            List<DesignElement> elements = new ArrayList<>(document.elements());
            elements.add(element);
            return document.withElements(elements);
        }

        @Override
        public String nameKey() {
            return "design.edit.add";
        }
    }

    record RemoveElement(String id) implements EditorCommand {

        @Override
        public DesignDocument apply(DesignDocument document) {
            return document.withElements(document.elements().stream()
                    .filter(element -> !element.id().equals(id))
                    .toList());
        }

        @Override
        public String nameKey() {
            return "design.edit.remove";
        }
    }

    /** Any change to one element: its frame, text, style or picture. */
    record ReplaceElement(DesignElement element, String mergeKey) implements EditorCommand {

        public ReplaceElement {
            Objects.requireNonNull(element, "element");
            mergeKey = mergeKey == null ? "" : mergeKey;
        }

        public ReplaceElement(DesignElement element) {
            this(element, "");
        }

        @Override
        public DesignDocument apply(DesignDocument document) {
            return document.replace(element);
        }

        @Override
        public String nameKey() {
            return "design.edit.change";
        }
    }

    /** Moves an element up (positive) or down (negative) the z-order, clamped to the ends. */
    record MoveInZOrder(String id, int steps) implements EditorCommand {

        @Override
        public DesignDocument apply(DesignDocument document) {
            int from = document.indexOf(id);
            if (from < 0) {
                return document;
            }
            List<DesignElement> elements = new ArrayList<>(document.elements());
            DesignElement element = elements.remove(from);
            elements.add(Math.clamp((long) from + steps, 0, elements.size()), element);
            return document.withElements(elements);
        }

        @Override
        public String nameKey() {
            return steps > 0 ? "design.edit.forward" : "design.edit.backward";
        }
    }

    /** Aligns an element to the item's trim: an edge or the centre line. */
    record AlignToItem(String id, Edge edge, double itemWidthMm, double itemHeightMm) implements EditorCommand {

        public enum Edge {
            LEFT,
            CENTER,
            RIGHT,
            TOP,
            MIDDLE,
            BOTTOM
        }

        @Override
        public DesignDocument apply(DesignDocument document) {
            return document.element(id)
                    .map(element -> {
                        Frame frame = element.frame();
                        Frame moved = switch (edge) {
                            case LEFT -> frame.moveTo(0, frame.y());
                            case CENTER -> frame.moveTo((itemWidthMm - frame.width()) / 2, frame.y());
                            case RIGHT -> frame.moveTo(itemWidthMm - frame.width(), frame.y());
                            case TOP -> frame.moveTo(frame.x(), 0);
                            case MIDDLE -> frame.moveTo(frame.x(), (itemHeightMm - frame.height()) / 2);
                            case BOTTOM -> frame.moveTo(frame.x(), itemHeightMm - frame.height());
                        };
                        return document.replace(element.withFrame(moved));
                    })
                    .orElse(document);
        }

        @Override
        public String nameKey() {
            return "design.edit.align";
        }
    }

    /** Re-sync: new project text for track list and disc title elements. */
    record SetContent(CoverContent content) implements EditorCommand {

        @Override
        public DesignDocument apply(DesignDocument document) {
            return document.withContent(content);
        }

        @Override
        public String nameKey() {
            return "design.edit.resync";
        }
    }

    record SetSpine(double spineMm) implements EditorCommand {

        @Override
        public DesignDocument apply(DesignDocument document) {
            return document.withSpine(spineMm);
        }

        @Override
        public String nameKey() {
            return "design.edit.spine";
        }

        @Override
        public String mergeKey() {
            return "spine";
        }
    }

    record SetHole(double holeDiameterMm) implements EditorCommand {

        @Override
        public DesignDocument apply(DesignDocument document) {
            return document.withHole(holeDiameterMm);
        }

        @Override
        public String nameKey() {
            return "design.edit.hole";
        }
    }

    record SetBackground(String colour) implements EditorCommand {

        @Override
        public DesignDocument apply(DesignDocument document) {
            return document.withBackground(colour);
        }

        @Override
        public String nameKey() {
            return "design.edit.background";
        }

        @Override
        public String mergeKey() {
            return "background";
        }
    }

    /** Replaces the whole design, for example with a preset. */
    record ReplaceDocument(DesignDocument replacement) implements EditorCommand {

        @Override
        public DesignDocument apply(DesignDocument document) {
            return replacement;
        }

        @Override
        public String nameKey() {
            return "design.edit.preset";
        }
    }
}
