// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.aerohelper.retroburn.engine.ConnectionType;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.DriveInfo;
import io.aerohelper.retroburn.tools.ProcessRunner;
import io.aerohelper.retroburn.tools.RunningProcess;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Drives {@link HelperDriveWatcher} with {@link FakeHelper} processes replaying a captured report. */
class HelperDriveWatcherTest {

    private static final List<DriveInfo> DRIVES =
            List.of(new DriveInfo(new DriveId("drive-1"), "Vendor", "Product", ConnectionType.USB, true));

    private final HelperClient client = new HelperClient(
            new ProcessRunner(), () -> FakeHelper.launch("replay-then-wait", "windows-helper/watch-drives.txt"));
    private final List<RunningProcess> sessions = new CopyOnWriteArrayList<>();
    private final BlockingQueue<List<DriveInfo>> notifications = new LinkedBlockingQueue<>();

    @Test
    void reportsTheDrivesOnceAtStart() throws InterruptedException {
        try (HelperDriveWatcher watcher = watcher()) {
            watcher.start(notifications::add);

            assertEquals(DRIVES, notifications.poll(30, TimeUnit.SECONDS));
            // The same ids again would not notify; the fake reports only once anyway.
            assertNull(notifications.poll(500, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void pausingStopsTheHelperAndResumingRefreshes() throws InterruptedException {
        try (HelperDriveWatcher watcher = watcher()) {
            watcher.start(notifications::add);
            assertNotNull(notifications.poll(30, TimeUnit.SECONDS));

            watcher.pause();
            awaitStopped(sessions.getFirst());

            watcher.resume();
            assertEquals(DRIVES, notifications.poll(30, TimeUnit.SECONDS));
            assertEquals(2, sessions.size());
        }
    }

    @Test
    void closingStopsTheHelper() throws InterruptedException {
        HelperDriveWatcher watcher = watcher();
        watcher.start(notifications::add);
        assertNotNull(notifications.poll(30, TimeUnit.SECONDS));

        watcher.close();

        awaitStopped(sessions.getFirst());
    }

    private HelperDriveWatcher watcher() {
        return new HelperDriveWatcher(
                events -> {
                    RunningProcess session = client.start(HelperRequest.watchDrives(), events);
                    sessions.add(session);
                    return session;
                },
                () -> DRIVES);
    }

    private static void awaitStopped(RunningProcess session) throws InterruptedException {
        for (int i = 0; i < 100 && session.isAlive(); i++) {
            Thread.sleep(100);
        }
        assertFalse(session.isAlive());
    }
}
