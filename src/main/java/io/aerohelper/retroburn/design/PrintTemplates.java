// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.NoSuchElementException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** The built-in print templates, in the order the designer offers them. */
public final class PrintTemplates {

    /** Ids of the built-in templates; each has a file {@code /print-templates/<id>.json}. */
    public static final List<String> IDS = List.of("cd-front", "cd-back", "cd-slim", "dvd-wrap", "disc-label");

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final List<PrintTemplate> templates;

    private PrintTemplates(List<PrintTemplate> templates) {
        this.templates = List.copyOf(templates);
    }

    /** Reads the built-in templates from the classpath. */
    public static PrintTemplates builtIn() {
        return new PrintTemplates(IDS.stream().map(PrintTemplates::read).toList());
    }

    public List<PrintTemplate> all() {
        return templates;
    }

    public PrintTemplate get(String id) {
        return templates.stream()
                .filter(template -> template.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No print template " + id));
    }

    static PrintTemplate read(String id) {
        String resource = "/print-templates/" + id + ".json";
        try (InputStream input = PrintTemplates.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing " + resource);
            }
            PrintTemplate template = MAPPER.readValue(input, PrintTemplate.class);
            if (!template.id().equals(id)) {
                throw new IllegalStateException(resource + " has id " + template.id());
            }
            return template;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource, e);
        }
    }
}
