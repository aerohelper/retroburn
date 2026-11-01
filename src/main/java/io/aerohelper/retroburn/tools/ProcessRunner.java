// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.ToolFailedException;
import io.aerohelper.retroburn.common.UserMessage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Runs external programs with an argument list, drains their output, enforces timeouts and honours
 * cancellation. The only class in RetroBurn that creates processes.
 *
 * <p>Thread-safe and stateless; one instance is shared by the whole app. See {@code
 * docs/design/LLD-tools.md} section 3.
 */
public final class ProcessRunner {

    static final int OUTPUT_TAIL_LINES = 200;

    private static final Logger LOG = Logger.getLogger(ProcessRunner.class.getName());
    private static final long POLL_MILLIS = 100;
    private static final Duration DESTROY_GRACE = Duration.ofSeconds(3);

    /**
     * Runs a program to completion. Blocks the calling thread.
     *
     * @return how the run ended; a non-zero exit code is not an exception, callers decide
     * @throws OperationCancelledException if {@code cancel} was triggered (the process is stopped)
     * @throws ToolFailedException if the program cannot be started
     * @throws RuntimeException rethrown from a line consumer, after the process ended
     */
    public ProcessResult run(ProcessRequest request, CancelToken cancel) {
        Process process = start(request.executable().getFileName().toString(), request);
        OutputPump pump = new OutputPump(OUTPUT_TAIL_LINES);
        Thread stdout = drainStdout(process, request, pump);
        Thread stderr = pump.drain(process.getErrorStream(), request.charset(), request.stderrLines(), "stderr");
        closeQuietly(process);

        boolean timedOut;
        try {
            timedOut = waitForExit(process, request, pump, cancel);
        } finally {
            joinQuietly(stdout);
            joinQuietly(stderr);
        }
        if (cancel.isCancelled()) {
            throw new OperationCancelledException();
        }
        if (pump.consumerFailure() != null) {
            throw pump.consumerFailure();
        }
        int exitCode = timedOut ? -1 : process.exitValue();
        LOG.info(() -> "Finished (" + (timedOut ? "timed out" : "exit " + exitCode) + "): " + request.commandLine());
        return new ProcessResult(exitCode, timedOut, request.commandLine(), pump.tail());
    }

    /**
     * Starts a long-running helper whose standard input stays open until {@link
     * RunningProcess#close()}, for example a process that holds a lock while it runs.
     *
     * @throws ToolFailedException if the program cannot be started
     */
    public RunningProcess startLongRunning(ProcessRequest request) {
        Process process = start(request.executable().getFileName().toString(), request);
        OutputPump pump = new OutputPump(OUTPUT_TAIL_LINES);
        Thread stdout = drainStdout(process, request, pump);
        Thread stderr = pump.drain(process.getErrorStream(), request.charset(), request.stderrLines(), "stderr");
        return new RunningProcess(process, pump, List.of(stdout, stderr), request.commandLine());
    }

    private static Thread drainStdout(Process process, ProcessRequest request, OutputPump pump) {
        return request.stdoutSink()
                .map(sink -> pump.copy(process.getInputStream(), sink, "stdout"))
                .orElseGet(
                        () -> pump.drain(process.getInputStream(), request.charset(), request.stdoutLines(), "stdout"));
    }

    private static Process start(String programName, ProcessRequest request) {
        ProcessBuilder builder = new ProcessBuilder(request.command());
        request.workingDirectory().ifPresent(folder -> builder.directory(folder.toFile()));
        LOG.info(() -> "Starting: " + request.commandLine());
        try {
            return builder.start();
        } catch (IOException e) {
            throw new ToolFailedException(
                    UserMessage.of("error.toolCouldNotStart", programName),
                    "$ " + request.commandLine() + "\n" + e.getMessage(),
                    e);
        }
    }

    /** Waits for the process; returns true if it was stopped because of a timeout. */
    private static boolean waitForExit(Process process, ProcessRequest request, OutputPump pump, CancelToken cancel) {
        Instant started = Instant.now();
        while (true) {
            try {
                if (process.waitFor(POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                destroyTree(process);
                throw new OperationCancelledException();
            }
            Instant now = Instant.now();
            boolean totalExpired = request.totalTimeout()
                    .map(limit -> now.isAfter(started.plus(limit)))
                    .orElse(false);
            boolean idleExpired = request.idleTimeout()
                    .map(limit -> now.isAfter(pump.lastOutput().plus(limit)))
                    .orElse(false);
            if (cancel.isCancelled() || totalExpired || idleExpired) {
                destroyTree(process);
                return !cancel.isCancelled();
            }
        }
    }

    /** Stops the process and its children: politely first, forcibly after a grace period. */
    static void destroyTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(DESTROY_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(DESTROY_GRACE.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    /** Programs run with {@link #run} get no input; closing stdin stops any that wait for it. */
    private static void closeQuietly(Process process) {
        try {
            process.getOutputStream().close();
        } catch (IOException e) {
            LOG.fine(() -> "Could not close stdin: " + e.getMessage());
        }
    }

    private static void joinQuietly(Thread thread) {
        try {
            // The streams end when the process ends; the bound only guards against stray grandchildren
            // that inherited the pipes.
            thread.join(DESTROY_GRACE.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
