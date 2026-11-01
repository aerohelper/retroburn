// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.tools.ProcessRequest;
import io.aerohelper.retroburn.tools.ProcessRunner;
import io.aerohelper.retroburn.tools.RunningProcess;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Runs the Windows helper: starts PowerShell with the bundled script, sends one request, and passes
 * the parsed events on. See {@code docs/design/LLD-windows-platform.md} sections 3 and 4.
 */
final class HelperClient {

    private static final Logger LOG = Logger.getLogger(HelperClient.class.getName());
    private static final Duration POLL = Duration.ofMillis(100);

    /** How long a helper may take to stop after its input closes before it is stopped forcibly. */
    private static final Duration STOP_GRACE = Duration.ofSeconds(30);

    private final ProcessRunner runner;
    private final Supplier<Launch> launch;

    /** @param launch how to start the helper; see {@link #powerShell} */
    HelperClient(ProcessRunner runner, Supplier<Launch> launch) {
        this.runner = runner;
        this.launch = launch;
    }

    /**
     * How to start a helper process.
     *
     * @param executable the program
     * @param arguments its arguments
     * @param workingDirectory the folder it runs in
     */
    record Launch(Path executable, List<String> arguments, Path workingDirectory) {

        Launch {
            Objects.requireNonNull(executable, "executable");
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(workingDirectory, "workingDirectory");
        }
    }

    /**
     * Starts the bundled script with Windows PowerShell; see the LLD section 3.
     *
     * @param powerShell {@code powershell.exe} under the Windows folder, if found
     * @param script supplies the helper script, ready to run (see {@link HelperFiles#script()})
     */
    static Supplier<Launch> powerShell(Optional<Path> powerShell, Supplier<Path> script) {
        return () -> {
            Path executable = powerShell.orElseThrow(
                    () -> HelperErrors.unavailable("powershell.exe was not found in the Windows folder"));
            Path helper = script.get();
            List<String> arguments = List.of(
                    // No banner, no profile scripts, no prompts.
                    "-NoLogo",
                    "-NoProfile",
                    "-NonInteractive",
                    // IMAPI2's objects are free-threaded; one multi-threaded apartment avoids proxies.
                    "-Mta",
                    // For this process only; machine and user policy stay as they are.
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    helper.toString());
            return new Launch(executable, arguments, helper.getParent());
        };
    }

    /**
     * Time limits for one run.
     *
     * @param total the longest the whole run may take, if limited
     * @param idle the longest the helper may stay silent
     */
    record Limits(Optional<Duration> total, Duration idle) {

        Limits {
            Objects.requireNonNull(total, "total");
            Objects.requireNonNull(idle, "idle");
        }

        /** Questions that a drive answers within a minute, even when spinning up. */
        static Limits query() {
            return new Limits(Optional.of(Duration.ofSeconds(60)), Duration.ofSeconds(60));
        }

        /** Long tasks that report progress; only silence counts. */
        static Limits silentFor(Duration idle) {
            return new Limits(Optional.empty(), idle);
        }
    }

    /**
     * Runs one request to the end. Events other than the final {@code result} or {@code error} go to
     * {@code events}, on a background thread.
     *
     * <p>Cancelling closes the helper's input, which asks it to stop at the next safe point; a helper
     * that does not stop within 30 seconds is stopped forcibly.
     *
     * @throws io.aerohelper.retroburn.common.RetroBurnException if the helper reports an error, cannot
     *     start, or times out
     * @throws OperationCancelledException if cancelled before the helper finished
     */
    void run(HelperRequest request, Consumer<HelperEvent> events, CancelToken cancel, Limits limits) {
        AtomicReference<HelperEvent> outcome = new AtomicReference<>();
        RunningProcess process = startProcess(event -> {
            if (event instanceof HelperEvent.Result || event instanceof HelperEvent.Error) {
                outcome.compareAndSet(null, event);
            } else {
                events.accept(event);
            }
        });
        try {
            send(process, request);
            waitForEnd(process, cancel, limits);
        } finally {
            if (process.isAlive()) {
                process.stop();
            }
        }
        HelperEvent end = outcome.get();
        if (end instanceof HelperEvent.Result) {
            return;
        }
        if (cancel.isCancelled()) {
            throw new OperationCancelledException();
        }
        if (end instanceof HelperEvent.Error error) {
            throw HelperErrors.fromEvent(error, process.technicalDetail());
        }
        throw HelperErrors.unavailable(process.technicalDetail());
    }

    /**
     * Starts a long-running request, such as {@code watch-drives} or {@code keep-awake}. Closing the
     * returned process closes the helper's input, which ends it.
     */
    RunningProcess start(HelperRequest request, Consumer<HelperEvent> events) {
        RunningProcess process = startProcess(events);
        try {
            send(process, request);
        } catch (RuntimeException e) {
            process.close();
            throw e;
        }
        return process;
    }

    private RunningProcess startProcess(Consumer<HelperEvent> events) {
        Launch how = launch.get();
        ProcessRequest request = ProcessRequest.builder(how.executable(), how.arguments())
                .workingDirectory(how.workingDirectory())
                .onStdoutLine(line -> events.accept(HelperEventParser.parse(line)))
                // PowerShell reports script and compiler errors on stderr; keep them for details.
                .onStderrLine(line -> events.accept(new HelperEvent.Log(line)))
                .build();
        return runner.startLongRunning(request);
    }

    private static void send(RunningProcess process, HelperRequest request) {
        LOG.info(() -> "Windows helper request: " + request.json());
        try {
            process.send(request.json());
        } catch (UncheckedIOException e) {
            // The helper died before reading its request; its exit and output explain why.
            LOG.warning(() -> "Windows helper did not accept its request: " + e.getMessage());
        }
    }

    private static void waitForEnd(RunningProcess process, CancelToken cancel, Limits limits) {
        Instant started = Instant.now();
        Instant stopAsked = null;
        while (process.waitForExit(POLL).isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new OperationCancelledException();
            }
            Instant now = Instant.now();
            if (cancel.isCancelled() && stopAsked == null) {
                process.closeInput();
                stopAsked = now;
            }
            if (stopAsked != null && now.isAfter(stopAsked.plus(STOP_GRACE))) {
                process.stop();
                return;
            }
            boolean totalExpired = limits.total()
                    .map(limit -> now.isAfter(started.plus(limit)))
                    .orElse(false);
            boolean idleExpired = now.isAfter(process.lastOutput().plus(limits.idle()));
            if (stopAsked == null && (totalExpired || idleExpired)) {
                process.stop();
                throw HelperErrors.timedOut(process.technicalDetail());
            }
        }
    }
}
