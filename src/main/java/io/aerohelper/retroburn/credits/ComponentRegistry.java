// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.credits;

import io.aerohelper.retroburn.common.OperatingSystem;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The third-party component register bundled with the app, plus the licence and notice texts it
 * refers to.
 *
 * <p>Loaded once at start-up from {@code credits/components.json}; immutable afterwards and safe to
 * share between threads. See {@code docs/design/LLD-credits.md}.
 */
public final class ComponentRegistry {

    static final String COMPONENTS_RESOURCE = "/credits/components.json";
    static final String LICENSES_FOLDER = "/credits/licenses/";
    static final String NOTICES_FOLDER = "/credits/notices/";
    static final int SUPPORTED_SCHEMA_VERSION = 1;

    private static final String TEXT_SEPARATOR = "\n\n----------------------------------------\n\n";

    private final List<ThirdPartyComponent> components;

    private ComponentRegistry(List<ThirdPartyComponent> components) {
        this.components = List.copyOf(components);
    }

    /**
     * Loads the register bundled with the application.
     *
     * @throws IllegalStateException if the register is missing or invalid, which means the build is
     *     broken
     */
    public static ComponentRegistry loadBundled() {
        try (InputStream input = openResource(COMPONENTS_RESOURCE)) {
            return fromJson(input);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + COMPONENTS_RESOURCE, e);
        }
    }

    /**
     * Reads a register from JSON.
     *
     * @throws IllegalStateException if the schema version is unsupported or ids are duplicated
     * @throws tools.jackson.core.JacksonException if the JSON does not match the expected shape
     */
    static ComponentRegistry fromJson(InputStream input) {
        JsonMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        ComponentsFile file = mapper.readValue(input, ComponentsFile.class);
        if (file.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported components.json schemaVersion " + file.schemaVersion());
        }
        rejectDuplicateIds(file.components());
        return new ComponentRegistry(file.components());
    }

    /** Every registered component, in register order. */
    public List<ThirdPartyComponent> allComponents() {
        return components;
    }

    /**
     * The components used on an operating system, sorted by name. For an OS RetroBurn does not
     * support yet, every component is returned so nothing goes uncredited.
     */
    public List<ThirdPartyComponent> componentsFor(OperatingSystem operatingSystem) {
        return components.stream()
                .filter(component -> operatingSystem == OperatingSystem.OTHER || component.isUsedOn(operatingSystem))
                .sorted(Comparator.comparing(ThirdPartyComponent::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** The full licence text of a component; several licence files are joined with a separator line. */
    public String licenseText(ThirdPartyComponent component) {
        return joinTexts(LICENSES_FOLDER, component.licenseFiles());
    }

    /** The upstream notice texts of a component joined together, or empty if it has none. */
    public Optional<String> noticeText(ThirdPartyComponent component) {
        if (component.noticeFiles().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(joinTexts(NOTICES_FOLDER, component.noticeFiles()));
    }

    private static String joinTexts(String folder, List<String> fileNames) {
        return fileNames.stream()
                .map(fileName -> readText(folder + fileName))
                .collect(Collectors.joining(TEXT_SEPARATOR));
    }

    static String readText(String resourcePath) {
        try (InputStream input = openResource(resourcePath)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resourcePath, e);
        }
    }

    private static InputStream openResource(String resourcePath) {
        InputStream input = ComponentRegistry.class.getResourceAsStream(resourcePath);
        if (input == null) {
            throw new IllegalStateException("Missing bundled resource " + resourcePath);
        }
        return input;
    }

    private static void rejectDuplicateIds(List<ThirdPartyComponent> components) {
        Set<String> seen = new HashSet<>();
        for (ThirdPartyComponent component : components) {
            if (!seen.add(component.id())) {
                throw new IllegalStateException("Duplicate component id '" + component.id() + "'");
            }
        }
    }

    /** The top-level shape of {@code components.json}. */
    private record ComponentsFile(int schemaVersion, List<ThirdPartyComponent> components) {}
}
