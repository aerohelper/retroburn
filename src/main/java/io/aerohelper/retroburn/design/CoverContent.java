// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.util.List;
import java.util.Objects;

/**
 * Text taken from a project for its cover: what track list and disc title elements show.
 *
 * @param kind the project type, such as "Audio CD", for presets that print it
 * @param title disc title
 * @param subtitle performer, date or similar; may be empty
 * @param lines one line per track, album or video, already numbered if wanted
 */
public record CoverContent(String kind, String title, String subtitle, List<String> lines) {

    public static final CoverContent EMPTY = new CoverContent("", "", "", List.of());

    public CoverContent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(subtitle, "subtitle");
        lines = List.copyOf(lines);
    }
}
