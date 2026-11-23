// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.simulated;

import io.aerohelper.retroburn.common.OperatingSystem;
import io.aerohelper.retroburn.platform.PlatformServices;
import java.util.Objects;

/**
 * A platform with a fake drive, for UI development, demos and tests.
 *
 * <p>It never touches real hardware. It reports the host OS so that OS-dependent information that
 * is not about drives, such as the credits list, stays correct.
 */
public final class SimulatedPlatform implements PlatformServices {

    private final OperatingSystem hostOperatingSystem;

    /** @param hostOperatingSystem the OS the app is actually running on */
    public SimulatedPlatform(OperatingSystem hostOperatingSystem) {
        this.hostOperatingSystem = Objects.requireNonNull(hostOperatingSystem, "hostOperatingSystem");
    }

    @Override
    public OperatingSystem operatingSystem() {
        return hostOperatingSystem;
    }

    @Override
    public String displayName() {
        return "Simulated drive";
    }
}
