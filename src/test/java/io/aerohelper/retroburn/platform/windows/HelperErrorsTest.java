// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.ToolFailedException;
import io.aerohelper.retroburn.common.UnsupportedInputException;
import io.aerohelper.retroburn.common.UserMessage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HelperErrorsTest {

    @Test
    void eachCodeHasItsOwnMessage() {
        Map<String, String> expected = Map.of(
                "DRIVE_NOT_FOUND", "error.driveNotFound",
                "NO_MEDIA", "error.noMedia",
                "MEDIA_NOT_SUPPORTED", "error.mediaNotSupported",
                "NOT_ENOUGH_SPACE", "error.discTooSmall",
                "DRIVE_BUSY", "error.driveBusy",
                "VERIFY_MISMATCH", "error.verifyMismatch",
                "BAD_REQUEST", "error.unexpected");
        expected.forEach(
                (code, key) -> assertEquals(key, messageOf(code, "text").key(), code));
    }

    @Test
    void unsupportedMediaIsAnInputProblem() {
        assertInstanceOf(UnsupportedInputException.class, error("MEDIA_NOT_SUPPORTED", "x", Optional.empty()));
    }

    @Test
    void cancelledBecomesTheCancelException() {
        assertInstanceOf(OperationCancelledException.class, error("CANCELLED", "Stopped on request", Optional.empty()));
    }

    @Test
    void aMissingSourceNamesThePath() {
        assertEquals(
                new UserMessage("error.sourceMissing", List.of("C:\\photos\\gone.jpg")),
                messageOf("SOURCE_MISSING", "C:\\photos\\gone.jpg"));
    }

    @Test
    void otherFailuresKeepImapiMessageAndHresult() {
        RuntimeException error = error("IMAPI_ERROR", "The media is write protected.", Optional.of("0xC0AA0206"));

        ToolFailedException failed = assertInstanceOf(ToolFailedException.class, error);
        assertEquals(
                new UserMessage("error.imapiFailed", List.of("The media is write protected.")), failed.userMessage());
        assertTrue(failed.technicalDetail().startsWith("$ powershell.exe"));
        assertTrue(failed.technicalDetail().contains("[IMAPI_ERROR 0xC0AA0206] The media is write protected."));
    }

    private static UserMessage messageOf(String code, String message) {
        return ((RetroBurnException) error(code, message, Optional.empty())).userMessage();
    }

    private static RuntimeException error(String code, String message, Optional<String> hresult) {
        return HelperErrors.fromEvent(new HelperEvent.Error(code, message, hresult), "$ powershell.exe ...");
    }
}
