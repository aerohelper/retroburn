// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.video;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The XML files spumux and dvdauthor read. Element and attribute names from the spumux and dvdauthor
 * manuals; the disc structure is ADR-0015. See {@code docs/design/LLD-video-dvd.md} sections 3 and 4.
 */
public final class DvdXml {

    private DvdXml() {}

    /**
     * One title on the disc.
     *
     * @param mpeg the converted title
     * @param chapters dvdauthor's chapters attribute, for example "0,5:00"
     * @param menuPage the menu page that lists it
     */
    public record Title(Path mpeg, String chapters, int menuPage) {}

    /**
     * Everything dvdauthor needs.
     *
     * @param output the folder that will hold VIDEO_TS
     * @param standard PAL or NTSC
     * @param aspect the titles' aspect ratio, "16:9" or "4:3"
     * @param menus each menu page's MPEG with buttons, in page order
     * @param pages the pages, for their buttons
     * @param titles the titles, in order
     * @param playNext play the next title after one ends, instead of returning to the menu
     */
    public record Authoring(
            Path output,
            TvStandard standard,
            String aspect,
            List<Path> menus,
            List<MenuLayout.Page> pages,
            List<Title> titles,
            boolean playNext) {}

    /** spumux's description of one page's buttons, drawn on the highlight and select images. */
    public static String spumux(MenuLayout.Page page, TvStandard standard, Path highlight, Path select) {
        StringBuilder xml = new StringBuilder("<subpictures>\n  <stream>\n");
        xml.append("    <spu start=\"00:00:00.00\" force=\"yes\" highlight=\"")
                .append(escape(highlight.toString()))
                .append("\" select=\"")
                .append(escape(select.toString()))
                .append("\">\n");
        Map<String, MenuLayout.Neighbours> navigation = page.navigation();
        for (MenuLayout.Button button : page.buttons()) {
            MenuLayout.Rect r = button.area().toFrame(standard);
            xml.append("      <button name=\"").append(button.name()).append('"');
            xml.append(" x0=\"").append(r.x()).append("\" y0=\"").append(r.y()).append('"');
            xml.append(" x1=\"")
                    .append(r.x() + r.width())
                    .append("\" y1=\"")
                    .append(r.y() + r.height())
                    .append('"');
            MenuLayout.Neighbours neighbours = navigation.get(button.name());
            if (neighbours != null) {
                attribute(xml, "up", neighbours.up());
                attribute(xml, "down", neighbours.down());
                attribute(xml, "left", neighbours.left());
                attribute(xml, "right", neighbours.right());
            }
            xml.append("/>\n");
        }
        xml.append("    </spu>\n  </stream>\n</subpictures>\n");
        return xml.toString();
    }

    /** dvdauthor's project file: 4:3 menus in the VMGM, every title in one titleset. */
    public static String dvdauthor(Authoring authoring) {
        String format = authoring.standard().dvdauthorFormat();
        StringBuilder xml = new StringBuilder();
        xml.append("<dvdauthor dest=\"")
                .append(escape(authoring.output().toString()))
                .append("\" format=\"")
                .append(format)
                .append("\">\n");
        xml.append("  <vmgm>\n    <menus>\n");
        xml.append("      <video format=\"").append(format).append("\" aspect=\"4:3\"/>\n");
        for (int index = 0; index < authoring.pages().size(); index++) {
            MenuLayout.Page page = authoring.pages().get(index);
            xml.append(index == 0 ? "      <pgc entry=\"title\">\n" : "      <pgc>\n");
            xml.append("        <vob file=\"")
                    .append(escape(authoring.menus().get(index).toString()))
                    .append("\" pause=\"inf\"/>\n");
            for (MenuLayout.Button button : page.buttons()) {
                xml.append("        <button name=\"").append(button.name()).append("\">");
                xml.append(command(button, page.number())).append("</button>\n");
            }
            xml.append("      </pgc>\n");
        }
        xml.append("    </menus>\n  </vmgm>\n");
        xml.append("  <titleset>\n    <titles>\n");
        xml.append("      <video format=\"")
                .append(format)
                .append("\" aspect=\"")
                .append(authoring.aspect())
                .append("\"/>\n");
        xml.append("      <audio format=\"ac3\" lang=\"en\"/>\n");
        List<Title> titles = authoring.titles();
        for (int index = 0; index < titles.size(); index++) {
            Title title = titles.get(index);
            boolean last = index == titles.size() - 1;
            xml.append("      <pgc>\n        <vob file=\"")
                    .append(escape(title.mpeg().toString()))
                    .append("\" chapters=\"")
                    .append(title.chapters())
                    .append("\"/>\n");
            xml.append("        <post>")
                    .append(post(index + 1, last, title.menuPage(), authoring.playNext()))
                    .append("</post>\n");
            xml.append("      </pgc>\n");
        }
        xml.append("    </titles>\n  </titleset>\n</dvdauthor>\n");
        return xml.toString();
    }

    static String command(MenuLayout.Button button, int page) {
        if (button.title().isPresent()) {
            return "g1 = 0; jump title " + button.title().getAsInt() + ";";
        }
        return switch (button.name()) {
            case "all" -> "g1 = 1; jump title 1;";
            case "prev" -> "jump menu " + (page - 1) + ";";
            case "next" -> "jump menu " + (page + 1) + ";";
            default -> throw new IllegalArgumentException("Unknown button " + button.name());
        };
    }

    /** After title {@code number}: the next title when playing all (or always), else back to its page. */
    static String post(int number, boolean last, int menuPage, boolean playNext) {
        String back = "g1 = 0; call vmgm menu " + menuPage + ";";
        if (last) {
            return back;
        }
        if (playNext) {
            return "jump title " + (number + 1) + ";";
        }
        return "if (g1 eq 1) jump title " + (number + 1) + "; " + back;
    }

    private static void attribute(StringBuilder xml, String name, String value) {
        if (value != null) {
            xml.append(' ').append(name).append("=\"").append(value).append('"');
        }
    }

    static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
