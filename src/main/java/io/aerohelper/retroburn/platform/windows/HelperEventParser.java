// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the Windows helper's JSON Lines into {@link HelperEvent}s.
 *
 * <p>Never fails: a line that is not a known event (PowerShell's own error text, for example) becomes
 * a {@link HelperEvent.Log} so it still reaches "Show details".
 */
final class HelperEventParser {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private HelperEventParser() {}

    static HelperEvent parse(String line) {
        JsonNode node;
        try {
            node = MAPPER.readTree(line);
        } catch (JacksonException e) {
            return new HelperEvent.Log(line);
        }
        if (node == null || !node.isObject()) {
            return new HelperEvent.Log(line);
        }
        return switch (text(node, "event")) {
            case "drive" ->
                new HelperEvent.Drive(
                        text(node, "id"),
                        text(node, "vendor"),
                        text(node, "product"),
                        text(node, "revision"),
                        text(node, "volume"),
                        text(node, "bus"),
                        node.path("accessible").asBoolean(true));
            case "media" ->
                new HelperEvent.Media(
                        node.path("present").asBoolean(false),
                        node.path("physicalType").asInt(0),
                        node.path("status").asInt(0),
                        node.path("freeSectors").asLong(0),
                        node.path("totalSectors").asLong(0));
            case "progress" ->
                new HelperEvent.Progress(
                        text(node, "stage"),
                        node.path("done").asLong(0),
                        node.path("total").asLong(0));
            case "drives" -> new HelperEvent.Drives(texts(node.path("ids")));
            case "power" -> new HelperEvent.Power(text(node, "source"));
            case "ready" ->
                new HelperEvent.Ready(text(node, "powershell"), text(node, "clr"), text(node, "languageMode"));
            case "log" -> new HelperEvent.Log(text(node, "text"));
            case "result" -> new HelperEvent.Result();
            case "error" ->
                new HelperEvent.Error(
                        text(node, "code"),
                        text(node, "message"),
                        Optional.of(text(node, "hresult")).filter(value -> !value.isEmpty()));
            default -> new HelperEvent.Log(line);
        };
    }

    /** A text field, or "" when it is missing or not text. */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.stringValue() : "";
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode value : array.values()) {
                if (value.isString()) {
                    values.add(value.stringValue());
                }
            }
        }
        return values;
    }
}
