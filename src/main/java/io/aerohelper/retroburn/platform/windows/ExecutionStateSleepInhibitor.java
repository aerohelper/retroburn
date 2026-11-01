// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.platform.SleepInhibitionUnavailableException;
import io.aerohelper.retroburn.platform.SleepInhibitor;
import io.aerohelper.retroburn.tools.RunningProcess;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Keeps Windows awake with {@code SetThreadExecutionState}, held by a long-running {@code
 * keep-awake} helper.
 *
 * <p>The request belongs to the helper's thread: closing the helper's input ends it, and if RetroBurn
 * crashes the input closes too, so a request never outlives the app. See {@code
 * docs/design/LLD-windows-platform.md} section 7.
 */
final class ExecutionStateSleepInhibitor implements SleepInhibitor {

    /** Start-up and compilation take about 1.3 s; allow for a slow or busy computer. */
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(15);

    private static final Duration POLL = Duration.ofMillis(100);

    private final HelperClient helper;

    ExecutionStateSleepInhibitor(HelperClient helper) {
        this.helper = helper;
    }

    @Override
    public SleepInhibition acquire(String reason) {
        CountDownLatch ready = new CountDownLatch(1);
        RunningProcess process;
        try {
            process = helper.start(HelperRequest.keepAwake(reason), event -> {
                if (event instanceof HelperEvent.Ready) {
                    ready.countDown();
                }
            });
        } catch (RetroBurnException e) {
            throw new SleepInhibitionUnavailableException(e.technicalDetail());
        }
        if (!awaitReady(ready, process)) {
            String detail = process.technicalDetail();
            process.close();
            throw new SleepInhibitionUnavailableException(detail);
        }
        return process::close;
    }

    /** Waits for {@code ready}, giving up early when the helper has already ended. */
    private static boolean awaitReady(CountDownLatch ready, RunningProcess process) {
        long deadline = System.nanoTime() + READY_TIMEOUT.toNanos();
        try {
            while (System.nanoTime() < deadline) {
                if (ready.await(POLL.toMillis(), TimeUnit.MILLISECONDS)) {
                    return true;
                }
                if (!process.isAlive()) {
                    // Its last lines may still be on the way.
                    return ready.await(POLL.toMillis(), TimeUnit.MILLISECONDS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }
}
