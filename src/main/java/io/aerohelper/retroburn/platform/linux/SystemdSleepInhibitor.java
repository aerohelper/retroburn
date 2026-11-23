// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.platform.SleepInhibitionUnavailableException;
import io.aerohelper.retroburn.platform.SleepInhibitor;
import io.aerohelper.retroburn.tools.ProcessRequest;
import io.aerohelper.retroburn.tools.ProcessRunner;
import io.aerohelper.retroburn.tools.RunningProcess;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Keeps the computer awake with a {@code systemd-inhibit} lock.
 *
 * <p>{@code systemd-inhibit} holds its lock while its command runs. The command is {@code cat}
 * reading a pipe from RetroBurn: closing the pipe ends {@code cat} and releases the lock, and if
 * RetroBurn crashes the pipe closes too, so a lock never outlives the app. Options from {@code
 * systemd-inhibit --help} (systemd 255); see {@code docs/design/LLD-linux-platform.md} section 4.
 */
final class SystemdSleepInhibitor implements SleepInhibitor {

    /** A lock that is refused (no logind, not a local session) makes the command exit at once. */
    private static final Duration REFUSAL_WINDOW = Duration.ofSeconds(1);

    private final ProcessRunner runner;
    private final Optional<Path> systemdInhibit;

    /** @param systemdInhibit the {@code systemd-inhibit} executable, if installed */
    SystemdSleepInhibitor(ProcessRunner runner, Optional<Path> systemdInhibit) {
        this.runner = runner;
        this.systemdInhibit = systemdInhibit;
    }

    @Override
    public SleepInhibition acquire(String reason) {
        Path executable = systemdInhibit.orElseThrow(
                () -> new SleepInhibitionUnavailableException("systemd-inhibit is not installed"));
        List<String> arguments = List.of(
                // Block both suspend/hibernate and idle-triggered sleep.
                "--what=sleep:idle",
                // Shown by "systemd-inhibit --list" and desktop power dialogs.
                "--who=RetroBurn",
                "--why=" + reason,
                // "block" prevents sleep outright; "delay" would only postpone it briefly.
                "--mode=block",
                // Runs until RetroBurn closes its standard input.
                "cat");
        RunningProcess process;
        try {
            process = runner.startLongRunning(
                    ProcessRequest.builder(executable, arguments).build());
        } catch (RetroBurnException e) {
            throw new SleepInhibitionUnavailableException(e.technicalDetail());
        }
        if (process.waitForExit(REFUSAL_WINDOW).isPresent()) {
            throw new SleepInhibitionUnavailableException(process.technicalDetail());
        }
        return process::close;
    }
}
