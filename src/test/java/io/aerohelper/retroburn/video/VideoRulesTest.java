// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Standards, bitrate, chapters, file order, menu layout and the XML files. */
class VideoRulesTest {

    @Test
    void tvStandardFollowsTheCountry() {
        assertEquals(TvStandard.PAL, TvStandard.forLocale(Locale.of("en", "IN")));
        assertEquals(TvStandard.PAL, TvStandard.forLocale(Locale.of("de", "DE")));
        assertEquals(TvStandard.NTSC, TvStandard.forLocale(Locale.US));
        assertEquals(TvStandard.NTSC, TvStandard.forLocale(Locale.JAPAN));
        assertEquals(TvStandard.PAL, TvStandard.forLocale(Locale.ENGLISH));
    }

    @Test
    void bitrateFillsTheDiscWithinDvdLimits() {
        // Two hours on 4.7 GB with one menu page: (4.7e9 × 0.97 − 2e6) × 8 / 7200 / 1000 = 5063 kbps total.
        DvdBitrate twoHours = DvdBitrate.fit(DvdBitrate.SINGLE_LAYER_BYTES, Duration.ofHours(2), 1);
        assertEquals((int) Math.floor(5063.5 * 0.98 - 192), twoHours.videoKbps(), 2);
        assertFalse(twoHours.lowQuality());
        assertEquals(
                8000,
                DvdBitrate.fit(DvdBitrate.SINGLE_LAYER_BYTES, Duration.ofMinutes(10), 1)
                        .videoKbps());
        DvdBitrate fourHours = DvdBitrate.fit(DvdBitrate.SINGLE_LAYER_BYTES, Duration.ofHours(4), 1);
        assertTrue(fourHours.lowQuality());
        assertTrue(fourHours.titleBytes(Duration.ofHours(4)) < DvdBitrate.SINGLE_LAYER_BYTES);
        assertEquals(
                1000,
                DvdBitrate.fit(DvdBitrate.SINGLE_LAYER_BYTES, Duration.ofHours(40), 1)
                        .videoKbps());
    }

    @Test
    void chaptersEveryFewMinutesButNotAtTheVeryEnd() {
        assertEquals("0,5:00,10:00", DvdChapters.attribute(DvdChapters.every(5, Duration.ofSeconds(905))));
        assertEquals("0,5:00", DvdChapters.attribute(DvdChapters.every(5, Duration.ofSeconds(605))));
        assertEquals("0", DvdChapters.attribute(DvdChapters.every(0, Duration.ofHours(1))));
        assertEquals("0,30:00,1:00:00", DvdChapters.attribute(DvdChapters.every(30, Duration.ofMinutes(61))));
    }

    @Test
    void videoTsFilesSortIntoDvdOrder() {
        assertEquals(
                List.of(
                        "VIDEO_TS.IFO",
                        "VIDEO_TS.VOB",
                        "VIDEO_TS.BUP",
                        "VTS_01_0.IFO",
                        "VTS_01_0.VOB",
                        "VTS_01_1.VOB",
                        "VTS_01_2.VOB",
                        "VTS_01_0.BUP",
                        "VTS_02_0.IFO",
                        "VTS_02_1.VOB",
                        "VTS_02_0.BUP"),
                VideoTsOrder.sort(List.of(
                        "VTS_02_0.BUP",
                        "VTS_01_2.VOB",
                        "VIDEO_TS.BUP",
                        "VTS_01_0.IFO",
                        "VTS_02_1.VOB",
                        "VIDEO_TS.VOB",
                        "VTS_01_0.BUP",
                        "VTS_01_1.VOB",
                        "VIDEO_TS.IFO",
                        "VTS_02_0.IFO",
                        "VTS_01_0.VOB")));
    }

    @Test
    void menuPagesHoldSixTitlesWithPlayAllAndPageButtons() {
        List<String> titles =
                IntStream.rangeClosed(1, 8).mapToObj(i -> "Title " + i).toList();
        List<MenuLayout.Page> pages = MenuLayout.pages(titles, TvStandard.PAL, "Play all", "Back", "More");
        assertEquals(2, pages.size());
        assertEquals(
                List.of("t1", "t2", "t3", "t4", "t5", "t6", "all", "next"),
                pages.get(0).buttons().stream().map(MenuLayout.Button::name).toList());
        assertEquals(
                List.of("t1", "t2", "all", "prev"),
                pages.get(1).buttons().stream().map(MenuLayout.Button::name).toList());
        assertEquals(7, pages.get(1).buttons().getFirst().title().getAsInt());
        MenuLayout.Neighbours first = pages.get(0).navigation().get("t1");
        assertEquals(new MenuLayout.Neighbours(null, "t4", null, "t2"), first);
        assertEquals("all", pages.get(0).navigation().get("t5").down());
        for (MenuLayout.Button button : pages.get(0).buttons()) {
            MenuLayout.Rect frame = button.area().toFrame(TvStandard.PAL);
            assertEquals(0, frame.y() % 2, button.name());
            assertEquals(0, (frame.y() + frame.height()) % 2, button.name());
            assertTrue(frame.x() + frame.width() <= TvStandard.WIDTH, button.name());
        }
    }

    @Test
    void overlaysHaveOneColourOnTransparencyAndTheBackgroundIsTheDvdFrame() {
        MenuLayout.Page page = MenuLayout.pages(List.of("One", "Two"), TvStandard.NTSC, "Play all", "Back", "More")
                .getFirst();
        MenuRenderer.Images images =
                MenuRenderer.render(page, "Holiday 2026", MenuTemplate.Y2K, TvStandard.NTSC, Map.of());
        assertEquals(720, images.background().getWidth());
        assertEquals(480, images.background().getHeight());
        for (BufferedImage overlay : List.of(images.highlight(), images.select())) {
            Set<Integer> colours = new HashSet<>();
            for (int y = 0; y < overlay.getHeight(); y++) {
                for (int x = 0; x < overlay.getWidth(); x++) {
                    colours.add(overlay.getRGB(x, y));
                }
            }
            assertEquals(2, colours.size(), "transparent plus one colour");
        }
    }

    @Test
    void dvdauthorFileLinksMenusTitlesAndChapters() {
        List<MenuLayout.Page> pages = MenuLayout.pages(List.of("A", "B"), TvStandard.PAL, "Play all", "Back", "More");
        String xml = DvdXml.dvdauthor(new DvdXml.Authoring(
                Path.of("dvd"),
                TvStandard.PAL,
                "16:9",
                List.of(Path.of("menu1.mpg")),
                pages,
                List.of(new DvdXml.Title(Path.of("1.mpg"), "0,5:00", 1), new DvdXml.Title(Path.of("2.mpg"), "0", 1)),
                false));
        assertTrue(xml.contains("<pgc entry=\"title\">"), xml);
        assertTrue(xml.contains("<video format=\"pal\" aspect=\"4:3\"/>"), xml);
        assertTrue(xml.contains("<video format=\"pal\" aspect=\"16:9\"/>"), xml);
        assertTrue(xml.contains("<button name=\"t2\">g1 = 0; jump title 2;</button>"), xml);
        assertTrue(xml.contains("<button name=\"all\">g1 = 1; jump title 1;</button>"), xml);
        assertTrue(xml.contains("<vob file=\"1.mpg\" chapters=\"0,5:00\"/>"), xml);
        assertTrue(xml.contains("<post>if (g1 eq 1) jump title 2; g1 = 0; call vmgm menu 1;</post>"), xml);
        assertTrue(xml.contains("<post>g1 = 0; call vmgm menu 1;</post>"), xml);
        assertEquals("jump title 3;", DvdXml.post(2, false, 1, true));
    }

    @Test
    void spumuxFileListsButtonsWithNavigation() {
        MenuLayout.Page page = MenuLayout.pages(List.of("A", "B"), TvStandard.PAL, "Play all", "Back", "More")
                .getFirst();
        String xml = DvdXml.spumux(page, TvStandard.PAL, Path.of("hl.png"), Path.of("sel.png"));
        assertTrue(xml.contains("<spu start=\"00:00:00.00\" force=\"yes\" highlight=\"hl.png\" select=\"sel.png\">"));
        assertEquals(3, xml.split("<button ").length - 1);
        assertTrue(
                xml.matches(
                        "(?s).*<button name=\"t1\" x0=\"\\d+\" y0=\"\\d+\" x1=\"\\d+\" y1=\"\\d+\" down=\"all\" right=\"t2\"/>.*"),
                xml);
    }

    @Test
    void ffmpegTitleArgumentsUseTheDvdTarget() {
        List<String> arguments = FfmpegVideo.title(Path.of("in.mp4"), Path.of("1.mpg"), TvStandard.NTSC, "16:9", 5000);
        String line = String.join(" ", arguments);
        assertTrue(line.contains("-target ntsc-dvd -aspect 16:9 -vf scale=720:480 -b:v 5000k -maxrate 8000k"), line);
        assertTrue(line.contains("-c:a ac3 -b:a 192k -ar 48000 -ac 2"), line);
        assertTrue(String.join(" ", FfmpegVideo.thumbnail(Path.of("in.mp4"), Duration.ofSeconds(100), Path.of("t.png")))
                .contains("-ss 10.000 -i in.mp4 -frames:v 1"));
    }

    @Test
    void probedVideoGivesAspectAndAudio() {
        String json = """
                {"streams": [{"codec_type": "video", "width": 720, "height": 576, "sample_aspect_ratio": "64:45"},
                             {"codec_type": "audio"}],
                 "format": {"duration": "20.000"}}""";
        ProbedVideo video = ProbedVideo.parse(json).orElseThrow();
        assertEquals(Duration.ofSeconds(20), video.duration());
        assertEquals("16:9", video.dvdAspect());
        assertTrue(video.hasAudio());
        assertEquals(
                "4:3",
                ProbedVideo.parse(json.replace("64:45", "1:1")).orElseThrow().dvdAspect());
        assertTrue(ProbedVideo.parse("{\"streams\": [{\"codec_type\": \"audio\"}], \"format\": {}}")
                .isEmpty());
    }
}
