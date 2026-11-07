// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.theme;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/** The built-in themes, read from {@code themes/<id>/theme.properties}. */
public final class ThemeRegistry {

    /** Built-in theme ids; the first is the default. */
    public static final List<String> IDS = List.of("classic", "y2k");

    private final List<Theme> themes;

    private ThemeRegistry(List<Theme> themes) {
        this.themes = List.copyOf(themes);
    }

    public static ThemeRegistry builtIn() {
        return new ThemeRegistry(IDS.stream().map(ThemeRegistry::read).toList());
    }

    public List<Theme> all() {
        return themes;
    }

    /** The theme with this id, or the default theme for an unknown id. */
    public Theme get(String id) {
        return themes.stream()
                .filter(theme -> theme.id().equals(id))
                .findFirst()
                .orElse(themes.getFirst());
    }

    static Theme read(String id) {
        String folder = "/themes/" + id;
        Properties properties = new Properties();
        try (InputStream input = ThemeRegistry.class.getResourceAsStream(folder + "/theme.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing " + folder + "/theme.properties");
            }
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the theme " + id, e);
        }
        List<String> fonts = Arrays.stream(properties.getProperty("fonts", "").split(","))
                .map(String::strip)
                .filter(font -> !font.isEmpty())
                .toList();
        return new Theme(
                id,
                properties.getProperty("name", id),
                Boolean.parseBoolean(properties.getProperty("soundsDefault", "false")),
                folder + "/theme.css",
                fonts,
                folder + "/sounds");
    }
}
