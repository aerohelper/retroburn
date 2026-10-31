// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import java.util.List;
import java.util.Objects;

/**
 * How a finished external program run ended.
 *
 * @param exitCode the process exit code; meaningless when {@code timedOut} is true
 * @param timedOut whether RetroBurn stopped the program because a timeout expired
 * @param commandLine the command as readable text, for logs and "Show details"
 * @param outputTail the last lines of stdout and stderr, interleaved in arrival order
 */
public record ProcessResult(int exitCode, boolean timedOut, String commandLine, List<String> outputTail) {

    public ProcessResult {
        Objects.requireNonNull(commandLine, "commandLine");
        outputTail = List.copyOf(outputTail);
    }

    /** True when the program exited by itself with exit code 0. */
    public boolean succeeded() {
        return !timedOut && exitCode == 0;
    }

    /** Command line, outcome and output tail as one text block for error details. */
    public String technicalDetail() {
        String outcome = timedOut ? "stopped after a timeout" : "exit code " + exitCode;
        return "$ " + commandLine + "\n" + String.join("\n", outputTail) + "\n[" + outcome + "]";
    }
}
