// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.platform.PowerStatus;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Battery or mains on Windows, from the helper's {@code power} operation (Windows' power line
 * status). See {@code docs/design/LLD-windows-platform.md} section 7.
 */
final class HelperPowerStatus implements PowerStatus {

    private static final Logger LOG = Logger.getLogger(HelperPowerStatus.class.getName());

    private final HelperClient helper;

    HelperPowerStatus(HelperClient helper) {
        this.helper = helper;
    }

    @Override
    public PowerSource current() {
        AtomicReference<String> source = new AtomicReference<>("unknown");
        try {
            helper.run(
                    HelperRequest.power(),
                    event -> {
                        if (event instanceof HelperEvent.Power power) {
                            source.set(power.source());
                        }
                    },
                    CancelToken.none(),
                    HelperClient.Limits.query());
        } catch (RetroBurnException e) {
            // Unknown means "do not warn", which is the right outcome when the answer is missing.
            LOG.log(Level.WARNING, "Could not read the power status", e);
            return PowerSource.UNKNOWN;
        }
        return toPowerSource(source.get());
    }

    static PowerSource toPowerSource(String source) {
        return switch (source) {
            case "mains" -> PowerSource.MAINS;
            case "battery" -> PowerSource.BATTERY;
            default -> PowerSource.UNKNOWN;
        };
    }
}
