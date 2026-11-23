// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.credits;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Makes sure every library that ships with RetroBurn is credited, with its licence text bundled.
 *
 * <p>Maven writes the runtime dependency list to {@code target/runtime-dependencies.txt} before the
 * tests run (see the {@code list-runtime-dependencies} execution in {@code pom.xml}).
 */
class CreditsCompletenessTest {

    /** Matches lines like "   tools.jackson.core:jackson-core:jar:3.2.4:compile -- module ...". */
    private static final Pattern DEPENDENCY_LINE = Pattern.compile("^\\s+([\\w.\\-]+):([\\w.\\-]+):.*$");

    private final ComponentRegistry registry = ComponentRegistry.loadBundled();

    @Test
    void everyRuntimeDependencyIsRegistered() throws IOException {
        Set<String> registered = registry.allComponents().stream()
                .flatMap(component -> component.mavenArtifacts().stream())
                .collect(Collectors.toSet());

        Set<String> missing = new TreeSet<>(runtimeDependencies());
        missing.removeAll(registered);

        assertTrue(
                missing.isEmpty(),
                "Runtime dependencies missing from credits/components.json (and THIRD_PARTY_NOTICES.md): " + missing);
    }

    @Test
    void everyLicenceAndNoticeFileExistsAndIsNotEmpty() {
        for (ThirdPartyComponent component : registry.allComponents()) {
            assertFalse(registry.licenseText(component).isBlank(), "Empty licence text for " + component.id());
            registry.noticeText(component)
                    .ifPresent(text -> assertFalse(text.isBlank(), "Empty notice for " + component.id()));
        }
    }

    private static Set<String> runtimeDependencies() throws IOException {
        String location = System.getProperty("runtimeDependenciesFile");
        if (location == null) {
            fail("System property runtimeDependenciesFile is not set; run the tests through Maven");
        }
        List<String> lines = Files.readAllLines(Path.of(location), StandardCharsets.UTF_8);
        Set<String> dependencies = new TreeSet<>();
        for (String line : lines) {
            Matcher matcher = DEPENDENCY_LINE.matcher(line);
            if (matcher.matches()) {
                dependencies.add(matcher.group(1) + ":" + matcher.group(2));
            }
        }
        assertFalse(dependencies.isEmpty(), "No dependencies found in " + location);
        return dependencies;
    }
}
