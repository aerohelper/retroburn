// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the reason for a failed xorriso run.
 *
 * <p>libburnia programs report problems as {@code "<program> : <SEVERITY> : <text>"}, for example
 * {@code xorriso : FAILURE : Cannot determine attributes of source file '/x' : No such file or
 * directory}. The most severe message is the best explanation; among equals, the first one.
 */
final class XorrisoErrorParser {

    private static final Pattern PROBLEM =
            Pattern.compile("^(?:xorriso|libburn|libisofs|libisoburn) : (ABORT|FATAL|FAILURE|MISHAP|SORRY) : (.+)$");

    /** Most severe first, from the xorriso manual's description of -abort_on. */
    private static final List<String> SEVERITIES = List.of("ABORT", "FATAL", "FAILURE", "MISHAP", "SORRY");

    private XorrisoErrorParser() {}

    /** The text of the most severe problem message, if any. */
    static Optional<String> mainProblem(List<String> outputLines) {
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        for (String line : outputLines) {
            Matcher matcher = PROBLEM.matcher(line.strip());
            if (matcher.find()) {
                int rank = SEVERITIES.indexOf(matcher.group(1));
                if (rank < bestRank) {
                    bestRank = rank;
                    best = matcher.group(2).strip();
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
