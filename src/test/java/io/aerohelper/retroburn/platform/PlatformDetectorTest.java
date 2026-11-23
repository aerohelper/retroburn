// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.aerohelper.retroburn.common.OperatingSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlatformDetectorTest {

    @ParameterizedTest
    @CsvSource({
        "Linux, LINUX",
        "Windows 11, WINDOWS",
        "Windows 10, WINDOWS",
        "WINDOWS SERVER 2022, WINDOWS",
        "Mac OS X, OTHER",
        "FreeBSD, OTHER",
        "'', OTHER"
    })
    void mapsOsNamePropertyToOperatingSystem(String osName, OperatingSystem expected) {
        assertEquals(expected, PlatformDetector.fromOsName(osName));
    }

    @Test
    void detectsTheRunningOperatingSystem() {
        assertNotNull(PlatformDetector.currentOperatingSystem());
    }
}
