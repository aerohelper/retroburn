// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.ToolFailedException;
import io.aerohelper.retroburn.common.UnsupportedInputException;
import io.aerohelper.retroburn.common.UserMessage;

/** Turns the Windows helper's failures into RetroBurn's typed errors. See the LLD section 8. */
final class HelperErrors {

    private HelperErrors() {}

    /**
     * The exception for an {@code error} event.
     *
     * @param technicalDetail command line and output, for "Show details"
     */
    static RuntimeException fromEvent(HelperEvent.Error error, String technicalDetail) {
        String detail = technicalDetail + "\n[" + error.code()
                + error.hresult().map(code -> " " + code).orElse("") + "] " + error.message();
        return switch (error.code()) {
            case "CANCELLED" -> new OperationCancelledException();
            case "DRIVE_NOT_FOUND" -> new RetroBurnException(UserMessage.of("error.driveNotFound"), detail);
            case "NO_MEDIA" -> new RetroBurnException(UserMessage.of("error.noMedia"), detail);
            case "MEDIA_NOT_SUPPORTED" ->
                new UnsupportedInputException(UserMessage.of("error.mediaNotSupported"), detail);
            case "NOT_ENOUGH_SPACE" -> new RetroBurnException(UserMessage.of("error.discTooSmall"), detail);
            case "SOURCE_MISSING" ->
                new RetroBurnException(UserMessage.of("error.sourceMissing", error.message()), detail);
            case "DRIVE_BUSY" -> new RetroBurnException(UserMessage.of("error.driveBusy"), detail);
            case "VERIFY_MISMATCH" -> new RetroBurnException(UserMessage.of("error.verifyMismatch"), detail);
            case "TOO_MANY_TRACKS" -> new RetroBurnException(UserMessage.of("error.tooManyTracks"), detail);
            // A request RetroBurn built wrongly is a bug in RetroBurn, not a drive problem.
            case "BAD_REQUEST" -> new RetroBurnException(UserMessage.of("error.unexpected"), detail);
            default -> new ToolFailedException(UserMessage.of("error.imapiFailed", error.message()), detail);
        };
    }

    /** The helper could not start or ended without saying why (policy, antivirus, crash). */
    static ToolFailedException unavailable(String technicalDetail) {
        return new ToolFailedException(UserMessage.of("error.windowsHelperUnavailable"), technicalDetail);
    }

    /** The helper went silent for too long, which normally means the drive stopped answering. */
    static ToolFailedException timedOut(String technicalDetail) {
        return new ToolFailedException(UserMessage.of("error.windowsHelperTimedOut"), technicalDetail);
    }
}
