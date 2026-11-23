// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import io.aerohelper.retroburn.common.UserMessage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decides which features are available from the tool report and the platform's requirement table,
 * so a missing tool disables only the features that need it.
 */
public final class FeatureGate {

    private FeatureGate() {}

    /**
     * Evaluates every feature. A feature the platform lists no requirements for is unavailable,
     * because the platform does not support it.
     *
     * @param report which tools work
     * @param requirements what each feature needs on this platform
     */
    public static Map<Feature, FeatureAvailability> evaluate(
            ToolReport report, Map<Feature, List<Requirement>> requirements) {
        Map<Feature, FeatureAvailability> result = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            List<Requirement> needs = requirements.get(feature);
            List<UserMessage> reasons = new ArrayList<>();
            if (needs == null) {
                reasons.add(UserMessage.of("requirement.notSupportedHere"));
            } else {
                for (Requirement requirement : needs) {
                    Optional<UserMessage> reason = requirement.unmetReason(report);
                    reason.filter(message -> !reasons.contains(message)).ifPresent(reasons::add);
                }
            }
            result.put(feature, new FeatureAvailability(feature, reasons));
        }
        return Map.copyOf(result);
    }
}
