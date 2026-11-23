// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform;

import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.UserMessage;

/**
 * The OS could not be asked to stay awake. The burn goes ahead; the user is warned to keep the
 * computer awake themselves.
 */
public class SleepInhibitionUnavailableException extends RetroBurnException {

    private static final long serialVersionUID = 1L;

    public SleepInhibitionUnavailableException(String technicalDetail) {
        super(UserMessage.of("warning.sleepInhibitionUnavailable"), technicalDetail);
    }
}
