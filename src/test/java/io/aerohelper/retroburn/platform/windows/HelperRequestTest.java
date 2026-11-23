// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.engine.DriveId;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class HelperRequestTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final DriveId DRIVE = new DriveId("\\\\?\\usbstor#cdrom&ven_x#{53f56308}");

    @Test
    void everyRequestNamesTheProtocolAndOperation() {
        for (HelperRequest request : List.of(
                HelperRequest.probe(),
                HelperRequest.listDrives(),
                HelperRequest.readMedia(DRIVE),
                HelperRequest.eject(DRIVE),
                HelperRequest.watchDrives(),
                HelperRequest.power())) {
            JsonNode json = MAPPER.readTree(request.json());
            assertEquals(1, json.get("protocol").asInt());
            assertEquals(request.operation(), json.get("operation").stringValue());
        }
    }

    @Test
    void driveIdsTravelUnchanged() {
        JsonNode json = MAPPER.readTree(HelperRequest.readMedia(DRIVE).json());

        assertEquals("read-media", json.get("operation").stringValue());
        assertEquals(DRIVE.value(), json.get("drive").stringValue());
    }

    @Test
    void imageRequestListsEveryEntryWithAbsoluteSources() {
        Path source = Path.of("relative", "café हि.txt");
        HelperRequest request = HelperRequest.buildImage(
                DiscTree.of(List.of(new DiscEntry("/Holiday = 2026/a.txt", source))), "LABEL", Path.of("out.iso"));

        JsonNode json = MAPPER.readTree(request.json());
        assertEquals("build-image", json.get("operation").stringValue());
        assertEquals("LABEL", json.get("volumeLabel").stringValue());
        assertEquals(
                Path.of("out.iso").toAbsolutePath().toString(),
                json.get("target").stringValue());
        JsonNode entry = json.get("entries").get(0);
        assertEquals("/Holiday = 2026/a.txt", entry.get("discPath").stringValue());
        assertEquals(source.toAbsolutePath().toString(), entry.get("source").stringValue());
    }

    @Test
    void writingAndErasingCarryTheirOptions() {
        JsonNode burn = MAPPER.readTree(
                HelperRequest.burnImage(DRIVE, Path.of("disc.iso"), true).json());
        JsonNode blank = MAPPER.readTree(HelperRequest.blank(DRIVE, false).json());
        JsonNode verify = MAPPER.readTree(
                HelperRequest.verifyImage(DRIVE, Path.of("disc.iso")).json());

        assertEquals(true, burn.get("closeMedia").asBoolean());
        assertEquals(
                Path.of("disc.iso").toAbsolutePath().toString(),
                burn.get("image").stringValue());
        assertEquals(false, blank.get("full").asBoolean());
        assertEquals(DRIVE.value(), verify.get("drive").stringValue());
    }

    @Test
    void aRequestIsAlwaysOneLine() {
        HelperRequest request = HelperRequest.keepAwake("line one\nline two\r\nline three");

        assertFalse(request.json().contains("\n"));
        assertFalse(request.json().contains("\r"));
        assertEquals(
                "line one\nline two\r\nline three",
                MAPPER.readTree(request.json()).get("reason").stringValue());
    }
}
