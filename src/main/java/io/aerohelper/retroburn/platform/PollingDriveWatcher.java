// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform;

import io.aerohelper.retroburn.engine.DriveInfo;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link DriveWatcher} that polls a cheap "signature" of the attached drives and asks for full
 * drive details only when the signature changes.
 *
 * <p>OS-neutral: the platform supplies both functions. On Linux the signature is the list of {@code
 * sr} devices in {@code /sys/block}, which costs nothing to read and does not touch the drives.
 */
public final class PollingDriveWatcher implements DriveWatcher {

    private static final Logger LOG = Logger.getLogger(PollingDriveWatcher.class.getName());

    private final Supplier<Object> signature;
    private final Supplier<List<DriveInfo>> drives;
    private final Duration interval;
    private final ScheduledExecutorService scheduler;

    private volatile boolean paused;
    private volatile Consumer<List<DriveInfo>> listener;
    private Object lastSignature;

    /**
     * @param signature cheap value that changes when drives are added or removed
     * @param drives full drive list; may be slow
     * @param interval how often to poll; 2 seconds meets the "within about 3 seconds" target
     */
    public PollingDriveWatcher(Supplier<Object> signature, Supplier<List<DriveInfo>> drives, Duration interval) {
        this.signature = Objects.requireNonNull(signature, "signature");
        this.drives = Objects.requireNonNull(drives, "drives");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("drive-watcher").factory());
    }

    @Override
    public void start(Consumer<List<DriveInfo>> newListener) {
        listen(newListener);
        scheduler.scheduleWithFixedDelay(this::poll, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Sets the listener without scheduling polls, so tests can call {@link #poll()} themselves. */
    void listen(Consumer<List<DriveInfo>> newListener) {
        this.listener = Objects.requireNonNull(newListener, "listener");
    }

    @Override
    public void pause() {
        paused = true;
    }

    @Override
    public void resume() {
        paused = false;
        scheduler.execute(this::poll);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    /** One polling round. Runs only on the watcher thread, so {@code lastSignature} needs no lock. */
    void poll() {
        if (paused || listener == null) {
            return;
        }
        try {
            Object current = signature.get();
            if (current.equals(lastSignature)) {
                return;
            }
            List<DriveInfo> found = drives.get();
            // Re-check after the slow call: a burn may have started in the meantime.
            if (!paused) {
                lastSignature = current;
                listener.accept(found);
            }
        } catch (RuntimeException e) {
            // A failed poll must not stop future polls; the next round tries again.
            LOG.log(Level.WARNING, "Drive polling failed", e);
        }
    }
}
