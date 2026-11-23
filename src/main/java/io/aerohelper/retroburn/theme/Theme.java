// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.theme;

import java.util.List;
import java.util.Objects;

/**
 * One theme: a folder {@code themes/<id>/} with a stylesheet, its fonts and its sounds. See {@code
 * docs/design/LLD-themes.md} section 2.
 *
 * @param id folder name, stored in the settings
 * @param name name shown in the View menu
 * @param soundsDefault whether sounds are on when the user has not chosen
 * @param stylesheet resource path of {@code theme.css}
 * @param fonts resource paths of fonts the stylesheet uses
 * @param soundFolder resource folder with the theme's sound files
 */
public record Theme(
        String id, String name, boolean soundsDefault, String stylesheet, List<String> fonts, String soundFolder) {

    public Theme {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(stylesheet, "stylesheet");
        fonts = List.copyOf(fonts);
        Objects.requireNonNull(soundFolder, "soundFolder");
    }

    public String sound(Sound sound) {
        return soundFolder + "/" + sound.fileName();
    }
}
