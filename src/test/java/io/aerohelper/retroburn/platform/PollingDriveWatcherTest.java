// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PollingDriveWatcherTest {

    private static final DriveInfo USB_DRIVE =
            new DriveInfo(new DriveId("/dev/sr0"), "ASUS", "SDRW", ConnectionType.USB, true);

    private final List<String> signature = new CopyOnWriteArrayList<>();
    private final AtomicInteger fullListings = new AtomicInteger();
    private final List<List<DriveInfo>> notifications = new ArrayList<>();

    @Test
    void reportsTheInitialDrivesAndThenOnlyChanges() {
        PollingDriveWatcher watcher = watcher();
        watcher.listen(notifications::add);

        watcher.poll();
        watcher.poll();
        signature.add("sr0");
        watcher.poll();
        watcher.poll();
        signature.clear();
        watcher.poll();

        assertEquals(List.of(List.of(), List.of(USB_DRIVE), List.of()), notifications);
        assertEquals(3, fullListings.get());
    }

    @Test
    void neverLooksAtDrivesWhilePaused() {
        PollingDriveWatcher watcher = watcher();
        watcher.listen(notifications::add);
        watcher.poll();
        int listingsBefore = fullListings.get();

        watcher.pause();
        signature.add("sr0");
        watcher.poll();

        assertEquals(listingsBefore, fullListings.get());
    }

    @Test
    void aFailedPollDoesNotStopLaterOnes() {
        AtomicInteger calls = new AtomicInteger();
        PollingDriveWatcher watcher = new PollingDriveWatcher(
                () -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new IllegalStateException("sysfs hiccup");
                    }
                    return "stable";
                },
                List::of,
                Duration.ofDays(1));
        watcher.listen(notifications::add);

        watcher.poll();
        watcher.poll();

        assertEquals(List.of(List.of()), notifications);
    }

    @Test
    void startPollsInTheBackground() throws InterruptedException {
        List<List<DriveInfo>> received = new CopyOnWriteArrayList<>();
        PollingDriveWatcher watcher = watcher();
        watcher.start(received::add);
        for (int i = 0; i < 50 && received.isEmpty(); i++) {
            Thread.sleep(20);
        }
        watcher.close();

        assertEquals(1, received.size());
    }

    @Test
    void createsTheAppFolders(@TempDir Path root) {
        AppPaths paths = new AppPaths(
                root.resolve("settings"),
                root.resolve("data"),
                root.resolve("logs"),
                root.resolve("work"),
                root.resolve("reels"));

        paths.ensureExists();

        assertTrue(Files.isDirectory(paths.work()));
        assertTrue(Files.notExists(paths.reels()));
    }

    private PollingDriveWatcher watcher() {
        return new PollingDriveWatcher(
                () -> List.copyOf(signature),
                () -> {
                    fullListings.incrementAndGet();
                    return signature.isEmpty() ? List.of() : List.of(USB_DRIVE);
                },
                Duration.ofDays(1));
    }
}
