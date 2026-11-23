// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.aerohelper.retroburn.testsupport.ToolOutput;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HelperEventParserTest {

    private static final String USB_DRIVE_ID = "\\\\?\\usbstor#cdrom&ven_slimtype&prod_ebau108__7_l&rev_yl0m"
            + "#00_000000000000&0#{53f56308-b6bf-11d0-94f2-00a0c91efb8b}";

    @Test
    void parsesACapturedDriveList() {
        List<HelperEvent> events = parseAll("windows-helper/list-drives-usb.txt");

        assertEquals(
                List.of(
                        new HelperEvent.Drive(USB_DRIVE_ID, "Slimtype", "eBAU108  7 L    ", "YL0M", "D:", "usb", true),
                        new HelperEvent.Result()),
                events);
    }

    @Test
    void parsesAnEmptyDrive() {
        assertEquals(
                List.of(new HelperEvent.Media(false, 0, 0, 0, 0), new HelperEvent.Result()),
                parseAll("windows-helper/read-media-no-media.txt"));
    }

    @Test
    void parsesMediaDetails() {
        assertEquals(
                new HelperEvent.Media(true, 7, 2, 2295104, 2295104),
                HelperEventParser.parse("{\"event\":\"media\",\"present\":true,\"physicalType\":7,\"status\":2,"
                        + "\"freeSectors\":2295104,\"totalSectors\":2295104}"));
    }

    @Test
    void parsesImageProgress() {
        List<HelperEvent> events = parseAll("windows-helper/build-image.txt");

        assertEquals(new HelperEvent.Progress("image", 0, 9568256), events.get(0));
        assertEquals(new HelperEvent.Progress("image", 9568256, 9568256), events.get(1));
        assertInstanceOf(HelperEvent.Result.class, events.get(2));
    }

    @Test
    void parsesErrorsWithAndWithoutHresult() {
        assertEquals(
                new HelperEvent.Error(
                        "SOURCE_MISSING",
                        "C:\\Users\\user\\AppData\\Local\\Temp\\rb-capture\\src\\gone.txt",
                        Optional.empty()),
                parseAll("windows-helper/build-image-missing-source.txt").getFirst());
        assertEquals(
                new HelperEvent.Error("NO_MEDIA", "There is no media in the device.", Optional.of("0xC0AA0202")),
                HelperEventParser.parse("{\"event\":\"error\",\"code\":\"NO_MEDIA\","
                        + "\"message\":\"There is no media in the device.\",\"hresult\":\"0xC0AA0202\"}"));
    }

    @Test
    void parsesDriveIdsPowerAndReadiness() {
        assertEquals(
                new HelperEvent.Drives(List.of(USB_DRIVE_ID)),
                parseAll("windows-helper/watch-drives.txt").getFirst());
        assertEquals(
                new HelperEvent.Power("mains"),
                parseAll("windows-helper/power.txt").getFirst());
        assertEquals(
                new HelperEvent.Ready("5.1.26100.9711", "4.0.30319.42000", "FullLanguage"),
                parseAll("windows-helper/probe.txt").getFirst());
        assertEquals(
                List.of(
                        new HelperEvent.Log("Keeping the computer awake: Burning a disc"),
                        new HelperEvent.Ready("", "4.0.30319.42000", ""),
                        new HelperEvent.Result()),
                parseAll("windows-helper/keep-awake.txt"));
    }

    @Test
    void keepsAnythingElseAsALogLine() {
        for (String line : List.of(
                "Add-Type : Cannot add type. Compilation errors occurred.",
                "",
                "[1, 2]",
                "{\"event\":\"something-new\"}",
                "{\"no-event\":true}")) {
            assertEquals(new HelperEvent.Log(line), HelperEventParser.parse(line));
        }
    }

    @Test
    void toleratesMissingFields() {
        assertEquals(
                new HelperEvent.Drive("", "", "", "", "", "", true), HelperEventParser.parse("{\"event\":\"drive\"}"));
        assertEquals(new HelperEvent.Progress("", 0, 0), HelperEventParser.parse("{\"event\":\"progress\"}"));
    }

    private static List<HelperEvent> parseAll(String fixture) {
        return ToolOutput.lines(fixture).stream()
                .filter(line -> !line.isBlank())
                .map(HelperEventParser::parse)
                .toList();
    }
}
