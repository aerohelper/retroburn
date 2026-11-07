// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.theme;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.text.Font;

/**
 * The look and sound of the app right now: the theme, whether sounds play, whether the disc moves.
 * Used on the JavaFX thread. See {@code docs/design/LLD-themes.md}.
 */
public final class Appearance implements AutoCloseable {

    /** How a burn ended, for its sound and the disc. */
    public enum Ending {
        SUCCESS,
        ERROR,
        CANCELLED
    }

    private static final Logger LOG = Logger.getLogger(Appearance.class.getName());

    private final ThemeRegistry registry;
    private final SoundPlayer sounds;
    private final Set<String> loadedFonts = new HashSet<>();
    private Theme theme;
    private Boolean soundChoice;
    private boolean animateDisc;

    /** @param soundChoice the user's choice, or null to follow the theme's default */
    public Appearance(ThemeRegistry registry, String themeId, Boolean soundChoice, boolean animateDisc) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.theme = registry.get(themeId);
        this.soundChoice = soundChoice;
        this.animateDisc = animateDisc;
        this.sounds = new SoundPlayer(theme, soundsOn());
    }

    public ThemeRegistry registry() {
        return registry;
    }

    public Theme theme() {
        return theme;
    }

    public boolean soundsOn() {
        return soundChoice != null ? soundChoice : theme.soundsDefault();
    }

    /** The user's sound choice, or null when it follows the theme. */
    public Boolean soundChoice() {
        return soundChoice;
    }

    public boolean animateDiscSetting() {
        return animateDisc;
    }

    /** Whether the disc moves: the setting, unless the system asks for reduced motion. */
    public boolean animateDisc() {
        return animateDisc && !Platform.getPreferences().isReducedMotion();
    }

    public void setAnimateDisc(boolean on) {
        animateDisc = on;
    }

    public void setSounds(boolean on) {
        soundChoice = on;
        sounds.setEnabled(on);
    }

    /** Switches the theme and restyles the scene at once. */
    public void setTheme(String id, Scene scene) {
        theme = registry.get(id);
        sounds.setTheme(theme);
        sounds.setEnabled(soundsOn());
        apply(scene);
    }

    /** Puts the current theme's stylesheet on a scene, replacing any other theme's. */
    public void apply(Scene scene) {
        loadFonts();
        for (Theme any : registry.all()) {
            URL url = Appearance.class.getResource(any.stylesheet());
            if (url != null) {
                scene.getStylesheets().remove(url.toExternalForm());
            }
        }
        URL url = Appearance.class.getResource(theme.stylesheet());
        if (url == null) {
            throw new IllegalStateException("Missing bundled resource " + theme.stylesheet());
        }
        scene.getStylesheets().add(url.toExternalForm());
    }

    public void burnStarted() {
        sounds.play(Sound.SPIN_UP);
        sounds.loop(Sound.BURNING_LOOP);
    }

    public void burnFinished(Ending ending) {
        sounds.stopAll();
        switch (ending) {
            case SUCCESS -> sounds.play(Sound.SUCCESS);
            case ERROR -> sounds.play(Sound.ERROR);
            case CANCELLED -> {
                // Silence: the user stopped it.
            }
        }
    }

    public DiscAnimation newDisc(double radius) {
        return new DiscAnimation(radius, this::animateDisc);
    }

    @Override
    public void close() {
        sounds.close();
    }

    private void loadFonts() {
        for (String font : theme.fonts()) {
            if (loadedFonts.add(font)) {
                try (InputStream input = Appearance.class.getResourceAsStream(font)) {
                    if (input == null || Font.loadFont(input, 12) == null) {
                        LOG.warning("Could not load the theme font " + font);
                    }
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Could not load the theme font " + font, e);
                }
            }
        }
    }
}
