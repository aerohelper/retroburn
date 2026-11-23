// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Objects;

/**
 * One thing on a cover or label. Immutable; edits replace the element (see {@link EditorCommand}).
 * Colours are {@code #RRGGBB}; an empty colour means none.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DesignElement.Text.class, name = "text"),
    @JsonSubTypes.Type(value = DesignElement.Image.class, name = "image"),
    @JsonSubTypes.Type(value = DesignElement.ShapeElement.class, name = "shape"),
    @JsonSubTypes.Type(value = DesignElement.TrackList.class, name = "trackList"),
    @JsonSubTypes.Type(value = DesignElement.DiscTitle.class, name = "discTitle")
})
public sealed interface DesignElement {

    String id();

    Frame frame();

    DesignElement withFrame(Frame frame);

    enum Align {
        LEFT,
        CENTER,
        RIGHT
    }

    /** How text looks; shared by text, track list and disc title elements. */
    record TextStyle(String fontFamily, double sizePt, boolean bold, String colour, Align align) {

        public TextStyle {
            Objects.requireNonNull(fontFamily, "fontFamily");
            Objects.requireNonNull(colour, "colour");
            Objects.requireNonNull(align, "align");
            if (sizePt <= 0) {
                throw new IllegalArgumentException("Text size must be positive");
            }
        }
    }

    /** Free text; line breaks in {@code text} are kept and long lines wrap. */
    record Text(String id, Frame frame, String text, TextStyle style) implements DesignElement {

        public Text {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(style, "style");
        }

        @Override
        public Text withFrame(Frame changed) {
            return new Text(id, changed, text, style);
        }
    }

    /**
     * A picture, stretched to the frame.
     *
     * @param imageId SHA-256 of the image file, the key in the document's {@link ImageStore}
     */
    record Image(String id, Frame frame, String imageId) implements DesignElement {

        public Image {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(imageId, "imageId");
        }

        @Override
        public Image withFrame(Frame changed) {
            return new Image(id, changed, imageId);
        }
    }

    /** A filled and/or outlined rectangle or ellipse. */
    record ShapeElement(String id, Frame frame, Kind kind, String fill, String stroke, double strokeMm)
            implements DesignElement {

        public enum Kind {
            RECTANGLE,
            ELLIPSE
        }

        public ShapeElement {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(kind, "kind");
            fill = fill == null ? "" : fill;
            stroke = stroke == null ? "" : stroke;
        }

        @Override
        public ShapeElement withFrame(Frame changed) {
            return new ShapeElement(id, changed, kind, fill, stroke, strokeMm);
        }
    }

    /** The project's lines (tracks, albums, titles); shrinks its text to fit the frame. */
    record TrackList(String id, Frame frame, TextStyle style) implements DesignElement {

        public TrackList {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(style, "style");
        }

        @Override
        public TrackList withFrame(Frame changed) {
            return new TrackList(id, changed, style);
        }
    }

    /** The project's title. */
    record DiscTitle(String id, Frame frame, TextStyle style) implements DesignElement {

        public DiscTitle {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(style, "style");
        }

        @Override
        public DiscTitle withFrame(Frame changed) {
            return new DiscTitle(id, changed, style);
        }
    }
}
