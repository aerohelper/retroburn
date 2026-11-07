// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.theme;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Plays the current theme's sounds with Java Sound (ADR-0017). Everything happens on one background
 * thread, so callers never wait for the audio system. Without a usable audio device the player
 * logs once and stays silent; sounds never stop a burn.
 */
public final class SoundPlayer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SoundPlayer.class.getName());

    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "retroburn-sounds");
        thread.setDaemon(true);
        return thread;
    });

    // Used only on the worker thread.
    private final Map<String, Clip> clips = new HashMap<>();
    private boolean unavailable;

    private volatile Theme theme;
    private volatile boolean enabled;

    public SoundPlayer(Theme theme, boolean enabled) {
        this.theme = theme;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        if (!on) {
            stopAll();
        }
    }

    /** Switches to another theme's sounds; whatever is playing stops. */
    public void setTheme(Theme next) {
        theme = next;
        worker.execute(() -> {
            clips.values().forEach(Clip::close);
            clips.clear();
        });
    }

    public void play(Sound sound) {
        String resource = theme.sound(sound);
        worker.execute(() -> clip(resource).ifPresent(clip -> {
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        }));
    }

    public void loop(Sound sound) {
        String resource = theme.sound(sound);
        worker.execute(() -> clip(resource).ifPresent(clip -> {
            clip.setFramePosition(0);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        }));
    }

    public void stopAll() {
        worker.execute(() -> clips.values().forEach(Clip::stop));
    }

    @Override
    public void close() {
        worker.execute(() -> clips.values().forEach(Clip::close));
        worker.shutdown();
    }

    private Optional<Clip> clip(String resource) {
        if (!enabled || unavailable) {
            return Optional.empty();
        }
        Clip clip = clips.get(resource);
        if (clip != null) {
            return Optional.of(clip);
        }
        try (InputStream raw = SoundPlayer.class.getResourceAsStream(resource)) {
            if (raw == null) {
                LOG.warning("Missing sound " + resource);
                return Optional.empty();
            }
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                clip = AudioSystem.getClip();
                clip.open(audio);
                clips.put(resource, clip);
                return Optional.of(clip);
            }
        } catch (LineUnavailableException | UnsupportedAudioFileException | IOException | RuntimeException e) {
            unavailable = true;
            LOG.log(Level.INFO, "Sounds are off: the audio system could not play " + resource, e);
            return Optional.empty();
        }
    }
}
