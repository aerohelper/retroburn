// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.app;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Sends RetroBurn's {@code java.util.logging} output to rotating files in the platform's log folder,
 * so command lines and tool output are available when a user reports a problem.
 */
final class LoggingSetup {

    private static final String ROOT_LOGGER = "io.aerohelper.retroburn";
    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final int FILE_COUNT = 5;

    private LoggingSetup() {}

    /** Adds a handler writing {@code retroburn-0.log} ... {@code retroburn-4.log}, 5 MB each. */
    static void writeLogsTo(Path folder) {
        Logger logger = Logger.getLogger(ROOT_LOGGER);
        try {
            FileHandler handler =
                    new FileHandler(folder.resolve("retroburn-%g.log").toString(), MAX_FILE_BYTES, FILE_COUNT, true);
            handler.setFormatter(new SimpleFormatter());
            handler.setEncoding("UTF-8");
            // FINE includes every line of tool output, which is what problem reports need.
            handler.setLevel(Level.FINE);
            logger.setLevel(Level.FINE);
            logger.addHandler(handler);
        } catch (IOException e) {
            // Logging is a diagnostic aid; the app works without it.
            logger.log(Level.WARNING, "Could not open log files in " + folder, e);
        }
    }
}
