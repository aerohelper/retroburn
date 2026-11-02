// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.video;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Where the buttons of each menu page go: up to six title buttons in a 3 × 2 grid, then "Play all"
 * and, with more than one page, "Previous" and "Next". Coordinates are in the square-pixel menu
 * ({@link TvStandard#squareMenuWidth()} × height). See {@code docs/design/LLD-video-dvd.md} section 4.
 */
public final class MenuLayout {

    public static final int TITLES_PER_PAGE = 6;
    static final int COLUMNS = 3;

    private MenuLayout() {}

    /**
     * A rectangle.
     *
     * @param x left edge
     * @param y top edge
     * @param width width
     * @param height height
     */
    public record Rect(int x, int y, int width, int height) {

        /** This rectangle in the 720-pixel DVD frame, with even top and bottom edges as spumux asks. */
        public Rect toFrame(TvStandard standard) {
            double scale = (double) TvStandard.WIDTH / standard.squareMenuWidth();
            int x0 = (int) Math.floor(x * scale);
            int x1 = (int) Math.ceil((x + width) * scale);
            int y0 = y & ~1;
            int y1 = Math.min(standard.height(), (y + height + 1) & ~1);
            return new Rect(x0, y0, x1 - x0, y1 - y0);
        }
    }

    /**
     * One button.
     *
     * @param name its name in spumux and dvdauthor: t1…t6, all, prev, next
     * @param area where it is, in the square-pixel menu
     * @param label the text under or on it
     * @param title the 1-based title it plays, empty for the other buttons
     */
    public record Button(String name, Rect area, String label, OptionalInt title) {}

    /**
     * One menu page.
     *
     * @param number 1-based page number, which is also its VMGM menu number
     * @param buttons the buttons, title buttons first
     * @param thumbnails the thumbnail box of each title button, by button name
     * @param navigation up, down, left and right neighbours, by button name
     */
    public record Page(
            int number, List<Button> buttons, Map<String, Rect> thumbnails, Map<String, Neighbours> navigation) {}

    /**
     * Which button the remote's arrows move to; null where an arrow stays put.
     *
     * @param up neighbour above
     * @param down neighbour below
     * @param left neighbour to the left
     * @param right neighbour to the right
     */
    public record Neighbours(String up, String down, String left, String right) {}

    /** The pages for {@code titleNames}, in order. */
    public static List<Page> pages(
            List<String> titleNames, TvStandard standard, String playAll, String previous, String next) {
        int width = standard.squareMenuWidth();
        int height = standard.height();
        int pages = Math.max(1, Math.ceilDiv(titleNames.size(), TITLES_PER_PAGE));
        int margin = width / 12;
        int cellWidth = (width - 2 * margin) / COLUMNS;
        int gridTop = height * 22 / 100;
        int cellHeight = height * 29 / 100;
        int thumbWidth = cellWidth - 24;
        int thumbHeight = thumbWidth * 9 / 16;
        int barY = height * 84 / 100;
        int barHeight = height * 8 / 100;
        List<Page> result = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            List<Button> buttons = new ArrayList<>();
            Map<String, Rect> thumbnails = new LinkedHashMap<>();
            int first = (page - 1) * TITLES_PER_PAGE;
            int count = Math.min(TITLES_PER_PAGE, titleNames.size() - first);
            for (int index = 0; index < count; index++) {
                int column = index % COLUMNS;
                int row = index / COLUMNS;
                int x = margin + column * cellWidth + 12;
                int y = gridTop + row * cellHeight;
                String name = "t" + (index + 1);
                thumbnails.put(name, new Rect(x, y, thumbWidth, thumbHeight));
                buttons.add(new Button(
                        name,
                        new Rect(x - 6, y - 6, thumbWidth + 12, cellHeight - 12),
                        titleNames.get(first + index),
                        OptionalInt.of(first + index + 1)));
            }
            int barWidth = width / 4;
            buttons.add(new Button(
                    "all", new Rect((width - barWidth) / 2, barY, barWidth, barHeight), playAll, OptionalInt.empty()));
            if (page > 1) {
                buttons.add(new Button(
                        "prev", new Rect(margin, barY, barWidth * 3 / 4, barHeight), previous, OptionalInt.empty()));
            }
            if (page < pages) {
                buttons.add(new Button(
                        "next",
                        new Rect(width - margin - barWidth * 3 / 4, barY, barWidth * 3 / 4, barHeight),
                        next,
                        OptionalInt.empty()));
            }
            result.add(new Page(
                    page, List.copyOf(buttons), Map.copyOf(thumbnails), navigation(count, page > 1, page < pages)));
        }
        return List.copyOf(result);
    }

    /** Grid neighbours for the title buttons; the bottom row leads down to the bar. */
    private static Map<String, Neighbours> navigation(int titles, boolean hasPrevious, boolean hasNext) {
        Map<String, Neighbours> map = new LinkedHashMap<>();
        int rows = Math.ceilDiv(Math.max(titles, 1), COLUMNS);
        for (int index = 0; index < titles; index++) {
            int column = index % COLUMNS;
            int row = index / COLUMNS;
            String up = row > 0 ? "t" + (index - COLUMNS + 1) : null;
            String down = index + COLUMNS < titles ? "t" + (index + COLUMNS + 1) : "all";
            String left = column > 0 ? "t" + index : null;
            String right = column < COLUMNS - 1 && index + 1 < titles ? "t" + (index + 2) : null;
            map.put("t" + (index + 1), new Neighbours(up, down, left, right));
        }
        String lastRowFirst = titles == 0 ? null : "t" + ((rows - 1) * COLUMNS + 1);
        map.put("all", new Neighbours(lastRowFirst, null, hasPrevious ? "prev" : null, hasNext ? "next" : null));
        if (hasPrevious) {
            map.put("prev", new Neighbours(lastRowFirst, null, null, "all"));
        }
        if (hasNext) {
            map.put("next", new Neighbours(lastRowFirst, null, "all", null));
        }
        return Map.copyOf(map);
    }
}
