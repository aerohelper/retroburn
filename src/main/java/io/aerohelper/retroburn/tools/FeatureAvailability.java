// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import io.aerohelper.retroburn.common.UserMessage;
import java.util.List;
import java.util.Objects;

/**
 * Whether a feature can be offered, and if not, why.
 *
 * @param feature the feature
 * @param reasons why it is unavailable; empty when it is available
 */
public record FeatureAvailability(Feature feature, List<UserMessage> reasons) {

    public FeatureAvailability {
        Objects.requireNonNull(feature, "feature");
        reasons = List.copyOf(reasons);
    }

    public boolean isAvailable() {
        return reasons.isEmpty();
    }
}
