// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.simulated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.aerohelper.retroburn.common.OperatingSystem;
import org.junit.jupiter.api.Test;

class SimulatedPlatformTest {

    @Test
    void reportsTheHostOperatingSystemSoCreditsStayCorrect() {
        SimulatedPlatform platform = new SimulatedPlatform(OperatingSystem.WINDOWS);

        assertEquals(OperatingSystem.WINDOWS, platform.operatingSystem());
    }

    @Test
    void hasADisplayName() {
        assertFalse(new SimulatedPlatform(OperatingSystem.LINUX).displayName().isBlank());
    }
}
