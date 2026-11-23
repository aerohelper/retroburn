// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.platform.DriveWatcher;
import io.aerohelper.retroburn.tools.RunningProcess;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Notices drives being plugged in or removed on Windows.
 *
 * <p>A long-running {@code watch-drives} helper lists the drives' ids every 2 seconds and reports
 * changes; full details are then fetched with {@code list-drives}. Pausing stops the helper
 * altogether, so nothing touches the drives during a burn. See {@code
 * docs/design/LLD-windows-platform.md} section 6.
 */
final class HelperDriveWatcher implements DriveWatcher {

    private static final Logger LOG = Logger.getLogger(HelperDriveWatcher.class.getName());
    private static final Duration RESTART_CHECK = Duration.ofSeconds(10);

    private final Function<Consumer<HelperEvent>, RunningProcess> startWatcher;
    private final Supplier<List<DriveInfo>> drives;
    private final ScheduledExecutorService worker;

    // Touched only on the worker thread.
    private RunningProcess session;
    private List<String> lastIds;
    private boolean paused;
    private boolean closed;
    private Consumer<List<DriveInfo>> listener;

    /**
     * @param startWatcher starts a {@code watch-drives} helper that sends its events to the consumer
     * @param drives full drive details; slow
     */
    HelperDriveWatcher(Function<Consumer<HelperEvent>, RunningProcess> startWatcher, Supplier<List<DriveInfo>> drives) {
        this.startWatcher = Objects.requireNonNull(startWatcher, "startWatcher");
        this.drives = Objects.requireNonNull(drives, "drives");
        this.worker = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("drive-watcher").factory());
    }

    @Override
    public void start(Consumer<List<DriveInfo>> newListener) {
        Objects.requireNonNull(newListener, "listener");
        worker.execute(() -> {
            listener = newListener;
            startSession();
        });
        worker.scheduleWithFixedDelay(
                this::restartIfDead, RESTART_CHECK.toMillis(), RESTART_CHECK.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void pause() {
        worker.execute(() -> {
            paused = true;
            stopSession();
        });
    }

    @Override
    public void resume() {
        worker.execute(() -> {
            paused = false;
            // The first report after resuming always refreshes the list.
            lastIds = null;
            startSession();
        });
    }

    @Override
    public void close() {
        worker.execute(() -> {
            closed = true;
            stopSession();
        });
        worker.shutdown();
    }

    private void startSession() {
        if (paused || closed || listener == null || session != null) {
            return;
        }
        try {
            session = startWatcher.apply(event -> {
                if (event instanceof HelperEvent.Drives report) {
                    worker.execute(() -> onReport(report.ids()));
                }
            });
        } catch (RuntimeException e) {
            // Retried by the next restart check.
            LOG.log(Level.WARNING, "Could not start the drive watcher", e);
        }
    }

    private void stopSession() {
        if (session != null) {
            session.close();
            session = null;
        }
    }

    private void restartIfDead() {
        if (session != null && !session.isAlive()) {
            LOG.warning(() -> "The drive watcher stopped; restarting it. " + session.technicalDetail());
            session = null;
        }
        startSession();
    }

    private void onReport(List<String> ids) {
        if (paused || closed || ids.equals(lastIds)) {
            return;
        }
        try {
            List<DriveInfo> found = drives.get();
            lastIds = ids;
            listener.accept(found);
        } catch (RuntimeException e) {
            // The next report tries again.
            LOG.log(Level.WARNING, "Could not list drives after a change", e);
        }
    }
}
