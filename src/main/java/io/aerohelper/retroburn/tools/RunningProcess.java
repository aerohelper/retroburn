// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A long-running helper process started by {@link ProcessRunner#startLongRunning}.
 *
 * <p>Its standard input stays open until {@link #closeInput()} or {@link #close()}; helpers that run
 * until their input ends therefore stop when RetroBurn closes it, or when RetroBurn itself exits or
 * crashes.
 */
public final class RunningProcess implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(RunningProcess.class.getName());
    private static final Duration EXIT_GRACE = Duration.ofSeconds(3);

    private final Process process;
    private final OutputPump pump;
    private final List<Thread> outputDrains;
    private final String commandLine;

    RunningProcess(Process process, OutputPump pump, List<Thread> outputDrains, String commandLine) {
        this.process = process;
        this.pump = pump;
        this.outputDrains = List.copyOf(outputDrains);
        this.commandLine = commandLine;
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    /**
     * Writes one line to the process's standard input, as UTF-8.
     *
     * @throws UncheckedIOException if the process no longer reads its input
     */
    public void send(String line) {
        try {
            OutputStream input = process.getOutputStream();
            input.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            input.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write to " + commandLine, e);
        }
    }

    /** Closes the process's standard input, which tells helpers that read it to stop. */
    public void closeInput() {
        try {
            process.getOutputStream().close();
        } catch (IOException e) {
            LOG.fine(() -> "Could not close stdin of " + commandLine + ": " + e.getMessage());
        }
    }

    /**
     * Waits up to {@code timeout} for the process to exit. When it has exited, every output line has
     * also been passed to the line consumers.
     *
     * @return the exit code, or empty if it is still running
     */
    public OptionalInt waitForExit(Duration timeout) {
        try {
            if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                for (Thread drain : outputDrains) {
                    // The streams end with the process; the bound only guards against stray
                    // grandchildren that inherited the pipes.
                    drain.join(EXIT_GRACE.toMillis());
                }
                return OptionalInt.of(process.exitValue());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return OptionalInt.empty();
    }

    /** When the process last wrote any output (or started), for idle timeouts. */
    public Instant lastOutput() {
        return pump.lastOutput();
    }

    /** The command line and recent output as text, for logs and error details. */
    public String technicalDetail() {
        List<String> tail = pump.tail();
        return "$ " + commandLine + "\n" + String.join("\n", tail);
    }

    /** Stops the process and its children at once, politely first. */
    public void stop() {
        ProcessRunner.destroyTree(process);
    }

    /** Closes the process's input, waits briefly for it to exit, then stops it if necessary. */
    @Override
    public void close() {
        closeInput();
        if (waitForExit(EXIT_GRACE).isEmpty()) {
            stop();
        }
        LOG.info(() -> "Stopped: " + commandLine);
    }
}
