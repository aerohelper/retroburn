// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.credits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.OperatingSystem;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

class ComponentRegistryTest {

    private static final String LINUX_ONLY_TOOL = """
            {
              "id": "zz-linux-tool", "name": "zz Linux tool", "role": "Burning", "authors": "Someone",
              "license": "GPL-3.0-or-later", "homepage": "https://example.org/", "provision": "OS_PACKAGE",
              "platforms": ["LINUX"], "licenseFiles": ["Apache-2.0.txt"], "noticeFiles": [],
              "mavenArtifacts": []
            }
            """;

    private static final String WINDOWS_ONLY_TOOL = """
            {
              "id": "aa-windows-tool", "name": "AA Windows tool", "role": "Burning", "authors": "Someone",
              "license": "GPL-2.0-or-later", "homepage": "https://example.org/", "provision": "BUNDLED_TOOL",
              "platforms": ["WINDOWS"], "licenseFiles": ["Apache-2.0.txt"], "noticeFiles": ["jackson-NOTICE.txt"],
              "mavenArtifacts": []
            }
            """;

    @Test
    void loadsTheBundledRegister() {
        ComponentRegistry registry = ComponentRegistry.loadBundled();

        assertFalse(registry.allComponents().isEmpty());
    }

    @Test
    void listsOnlyComponentsUsedOnTheRequestedOperatingSystem() {
        ComponentRegistry registry = registryOf(LINUX_ONLY_TOOL, WINDOWS_ONLY_TOOL);

        assertEquals(List.of("zz-linux-tool"), ids(registry.componentsFor(OperatingSystem.LINUX)));
        assertEquals(List.of("aa-windows-tool"), ids(registry.componentsFor(OperatingSystem.WINDOWS)));
    }

    @Test
    void listsEverythingSortedByNameForAnUnsupportedOperatingSystem() {
        ComponentRegistry registry = registryOf(LINUX_ONLY_TOOL, WINDOWS_ONLY_TOOL);

        assertEquals(List.of("aa-windows-tool", "zz-linux-tool"), ids(registry.componentsFor(OperatingSystem.OTHER)));
    }

    @Test
    void readsLicenceAndNoticeTexts() {
        ComponentRegistry registry = registryOf(WINDOWS_ONLY_TOOL);
        ThirdPartyComponent component = registry.allComponents().getFirst();

        assertTrue(registry.licenseText(component).contains("Apache License"));
        assertTrue(registry.noticeText(component).orElseThrow().contains("Jackson"));
    }

    @Test
    void reportsNoNoticeWhenAComponentHasNone() {
        ComponentRegistry registry = registryOf(LINUX_ONLY_TOOL);

        assertTrue(registry.noticeText(registry.allComponents().getFirst()).isEmpty());
    }

    @Test
    void joinsSeveralLicenceFilesInOrder() {
        String twoLicences = LINUX_ONLY_TOOL.replace(
                "\"licenseFiles\": [\"Apache-2.0.txt\"]",
                "\"licenseFiles\": [\"Apache-2.0.txt\", \"jackson-core-Schubfach-LICENSE.txt\"]");
        ComponentRegistry registry = registryOf(twoLicences);

        String text = registry.licenseText(registry.allComponents().getFirst());

        assertTrue(text.indexOf("Apache License") < text.indexOf("Raffaello Giulietti"));
    }

    @Test
    void rejectsAnUnsupportedSchemaVersion() {
        String json = "{\"schemaVersion\": 2, \"components\": []}";

        assertThrows(IllegalStateException.class, () -> ComponentRegistry.fromJson(stream(json)));
    }

    @Test
    void rejectsDuplicateIds() {
        assertThrows(IllegalStateException.class, () -> registryOf(LINUX_ONLY_TOOL, LINUX_ONLY_TOOL));
    }

    @Test
    void rejectsMisspelledFieldsSoTyposDoNotSilentlyDropData() {
        String misspelled = LINUX_ONLY_TOOL.replace("\"homepage\"", "\"homePage\"");

        assertThrows(JacksonException.class, () -> registryOf(misspelled));
    }

    @Test
    void rejectsAComponentWithoutLicenceFiles() {
        String noLicence = LINUX_ONLY_TOOL.replace("[\"Apache-2.0.txt\"]", "[]");

        assertThrows(JacksonException.class, () -> registryOf(noLicence));
    }

    @Test
    void failsClearlyWhenALicenceFileIsMissing() {
        String missingFile = LINUX_ONLY_TOOL.replace("Apache-2.0.txt", "does-not-exist.txt");
        ComponentRegistry registry = registryOf(missingFile);

        assertThrows(
                IllegalStateException.class,
                () -> registry.licenseText(registry.allComponents().getFirst()));
    }

    private static ComponentRegistry registryOf(String... componentJson) {
        String json = "{\"schemaVersion\": 1, \"components\": [" + String.join(",", componentJson) + "]}";
        return ComponentRegistry.fromJson(stream(json));
    }

    private static InputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> ids(List<ThirdPartyComponent> components) {
        return components.stream().map(ThirdPartyComponent::id).toList();
    }
}
