// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A cover or label design. Immutable: every edit returns a new document. Elements are in z-order,
 * the first at the bottom. Images are kept apart, in an {@link ImageStore}. See {@code
 * docs/design/LLD-cover-designer.md} section 3.
 *
 * @param schemaVersion file format version, {@value #SCHEMA_VERSION}
 * @param templateId the {@link PrintTemplate} this design is for
 * @param spineMm spine width, used when the template's spine is adjustable
 * @param holeDiameterMm hole of a disc label
 * @param preset the preset the design started from
 * @param background colour behind everything, {@code #RRGGBB}
 * @param content the project text shown by track list and disc title elements
 */
public record DesignDocument(
        int schemaVersion,
        String templateId,
        double spineMm,
        double holeDiameterMm,
        String preset,
        String background,
        CoverContent content,
        List<DesignElement> elements) {

    public static final int SCHEMA_VERSION = 1;

    public DesignDocument {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(background, "background");
        content = content == null ? CoverContent.EMPTY : content;
        elements = List.copyOf(elements);
    }

    public Optional<DesignElement> element(String id) {
        return elements.stream().filter(element -> element.id().equals(id)).findFirst();
    }

    public int indexOf(String id) {
        for (int index = 0; index < elements.size(); index++) {
            if (elements.get(index).id().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    public DesignDocument withElements(List<DesignElement> changed) {
        return new DesignDocument(
                schemaVersion, templateId, spineMm, holeDiameterMm, preset, background, content, changed);
    }

    public DesignDocument withContent(CoverContent changed) {
        return new DesignDocument(
                schemaVersion, templateId, spineMm, holeDiameterMm, preset, background, changed, elements);
    }

    public DesignDocument withSpine(double changed) {
        return new DesignDocument(
                schemaVersion, templateId, changed, holeDiameterMm, preset, background, content, elements);
    }

    public DesignDocument withHole(double changed) {
        return new DesignDocument(schemaVersion, templateId, spineMm, changed, preset, background, content, elements);
    }

    public DesignDocument withBackground(String changed) {
        return new DesignDocument(
                schemaVersion, templateId, spineMm, holeDiameterMm, preset, changed, content, elements);
    }

    /** Replaces the element with the same id. */
    public DesignDocument replace(DesignElement changed) {
        int index = indexOf(changed.id());
        if (index < 0) {
            throw new IllegalArgumentException("No element " + changed.id());
        }
        List<DesignElement> copy = new ArrayList<>(elements);
        copy.set(index, changed);
        return withElements(copy);
    }

    /** An id not used by any element yet, such as {@code text-3}. */
    public String newId(String prefix) {
        for (int number = 1; ; number++) {
            String id = prefix + "-" + number;
            if (indexOf(id) < 0) {
                return id;
            }
        }
    }

    /** Ids of the images the elements use. */
    public List<String> imageIds() {
        return elements.stream()
                .filter(DesignElement.Image.class::isInstance)
                .map(element -> ((DesignElement.Image) element).imageId())
                .distinct()
                .toList();
    }
}
