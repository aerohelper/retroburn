// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A long-running helper process started by {@link ProcessRunner#startLongRunning}.
 *
 * <p>Its standard input stays open until {@link #close()}; helpers that run until their input ends
 * therefore stop when RetroBurn closes it, or when RetroBurn itself exits or crashes.
 */
public final class RunningProcess implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(RunningProcess.class.getName());
    private static final Duration EXIT_GRACE = Duration.ofSeconds(3);

    private final Process process;
    private final OutputPump pump;
    private final String commandLine;

    RunningProcess(Process process, OutputPump pump, String commandLine) {
        this.process = process;
        this.pump = pump;
        this.commandLine = commandLine;
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    /**
     * Waits up to {@code timeout} for the process to exit.
     *
     * @return the exit code, or empty if it is still running
     */
    public OptionalInt waitForExit(Duration timeout) {
        try {
            if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return OptionalInt.of(process.exitValue());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return OptionalInt.empty();
    }

    /** The command line and recent output as text, for logs and error details. */
    public String technicalDetail() {
        List<String> tail = pump.tail();
        return "$ " + commandLine + "\n" + String.join("\n", tail);
    }

    /** Closes the process's input, waits briefly for it to exit, then stops it if necessary. */
    @Override
    public void close() {
        try {
            process.getOutputStream().close();
        } catch (IOException e) {
            LOG.fine(() -> "Could not close stdin of " + commandLine + ": " + e.getMessage());
        }
        if (waitForExit(EXIT_GRACE).isEmpty()) {
            ProcessRunner.destroyTree(process);
        }
        LOG.info(() -> "Stopped: " + commandLine);
    }
}
