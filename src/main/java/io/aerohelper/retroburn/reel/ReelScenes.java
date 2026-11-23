// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.reel;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The standard scenes: intro, spin-up, progress, complete and end card. All drawing is original
 * (shapes and the bundled fonts). The disc's angle comes from the frame time only, so frames are
 * pure.
 */
final class ReelScenes {

    static final int W = ReelStyle.WIDTH;
    static final int H = ReelStyle.HEIGHT;

    /** Degrees per second while burning; spin-up reaches it in 1.5 s, the end slows from it. */
    static final double SPEED = 720;

    static final double SPIN_UP_SECONDS = 1.5;

    private ReelScenes() {}

    /** Disc name and kind of disc, fading in. */
    static final class Intro implements ReelScene {

        @Override
        public String id() {
            return "intro";
        }

        @Override
        public void render(Graphics2D g, ReelFrame frame, BurnSession session, ReelStyle style) {
            background(g, style);
            fade(g, Math.min(1, frame.sceneFraction() * 2.5));
            String label = session.label().isBlank() ? style.kindName(session) : session.label();
            double y = centredLines(g, label, style.title().deriveFont(120f), style.text(), H * 0.42, 900);
            centred(g, style.kindName(session), style.body().deriveFont(64f), style.accent(), y + 70);
            centred(g, "RetroBurn", style.body().deriveFont(44f), style.text(), H - 160);
        }
    }

    /** The disc speeds up. */
    static final class SpinUp implements ReelScene {

        @Override
        public String id() {
            return "spin-up";
        }

        @Override
        public void render(Graphics2D g, ReelFrame frame, BurnSession session, ReelStyle style) {
            background(g, style);
            double t = frame.sceneFraction() * SPIN_UP_SECONDS;
            double angle = 0.5 * (SPEED / SPIN_UP_SECONDS) * t * t;
            disc(g, W / 2.0, H * 0.42, 330, angle, style);
            centred(g, style.messages().get("reel.spinningUp"), style.body().deriveFont(70f), style.text(), H * 0.72);
        }
    }

    /** The burn as a timelapse: percentage, speed, step and the item names scrolling by. */
    static final class Progress implements ReelScene {

        static final int VISIBLE_LINES = 7;

        @Override
        public String id() {
            return "progress";
        }

        @Override
        public void render(Graphics2D g, ReelFrame frame, BurnSession session, ReelStyle style) {
            background(g, style);
            double f = frame.sceneFraction();
            long at = Math.round(f * session.duration().toMillis());
            BurnSession.Sample sample = f >= 1 ? session.sampleAt(Long.MAX_VALUE) : session.sampleAt(at);
            double angle = spunUpAngle() + SPEED * frame.seconds();
            disc(g, W / 2.0, 430, 230, angle, style);

            int percent = (int) Math.floor(sample.fraction() * 100);
            centred(g, percent + " %", style.title().deriveFont(220f), style.led(), 910);

            int barX = 140;
            int barWidth = W - 2 * barX;
            g.setColor(new Color(255, 255, 255, 50));
            g.fill(new RoundRectangle2D.Double(barX, 990, barWidth, 44, 22, 22));
            g.setColor(style.led());
            g.fill(new RoundRectangle2D.Double(barX, 990, Math.max(44, barWidth * sample.fraction()), 44, 22, 22));

            String speed = sample.speed().isEmpty() ? session.lastSpeed() : sample.speed();
            String step = style.step(session.stepAt(at));
            Font info = style.body().deriveFont(52f);
            if (!step.isEmpty()) {
                centred(g, step, info, style.text(), 1130);
            }
            if (!speed.isEmpty()) {
                centred(g, style.messages().format("reel.speed", speed), info, style.accent(), 1200);
            }
            items(g, style, f);
        }

        private static void items(Graphics2D g, ReelStyle style, double f) {
            List<String> names = style.itemNames();
            if (names.isEmpty()) {
                return;
            }
            Font font = style.body().deriveFont(54f);
            double lineHeight = 74;
            double top = 1290;
            double scroll = f * Math.max(0, names.size() - VISIBLE_LINES) * lineHeight;
            int current = Math.min(names.size() - 1, (int) Math.floor(f * names.size()));
            Shape clip = g.getClip();
            g.clipRect(0, (int) top - 60, W, (int) (VISIBLE_LINES * lineHeight) + 20);
            for (int index = 0; index < names.size(); index++) {
                double y = top + index * lineHeight - scroll;
                Color colour = index == current ? style.led() : withAlpha(style.text(), index < current ? 120 : 200);
                centred(g, (index + 1) + ". " + fit(g, names.get(index), font, W - 200), font, colour, y);
            }
            g.setClip(clip);
        }
    }

    /** "Burn complete" with a tick and the figures; the disc slows down. */
    static final class Complete implements ReelScene {

        @Override
        public String id() {
            return "complete";
        }

        @Override
        public void render(Graphics2D g, ReelFrame frame, BurnSession session, ReelStyle style) {
            background(g, style);
            // Slow from full speed to a stop over 1.5 s, carrying on from the progress scene's angle.
            double into = frame.sceneFraction() * 2.5;
            double sceneStart = frame.seconds() - into;
            double t = Math.min(into, 1.5);
            double angle = spunUpAngle() + SPEED * (sceneStart + t) - 0.5 * (SPEED / 1.5) * t * t;
            disc(g, W / 2.0, 430, 230, angle, style);
            tick(g, W / 2.0, 860, 110, style, Math.min(1, frame.sceneFraction() * 3));
            centred(g, style.messages().get("reel.complete"), style.title().deriveFont(110f), style.text(), 1090);
            Font stats = style.body().deriveFont(56f);
            List<String> lines = new ArrayList<>();
            lines.add(style.messages().format("reel.stat.size", style.size(session.bytes())));
            lines.add(style.messages().format("reel.stat.time", style.duration(session.duration())));
            if (!session.lastSpeed().isEmpty()) {
                lines.add(style.messages().format("reel.stat.speed", session.lastSpeed()));
            }
            if (!session.items().isEmpty()) {
                lines.add(style.messages()
                        .format("reel.stat.items", session.items().size()));
            }
            lines.add(style.messages()
                    .get("SUCCEEDED".equals(session.outcome()) ? "reel.stat.verified" : "reel.stat.notVerified"));
            double y = 1240;
            for (String line : lines) {
                centred(g, line, stats, style.text(), y);
                y += 84;
            }
        }
    }

    /** "Burned with RetroBurn". */
    static final class EndCard implements ReelScene {

        @Override
        public String id() {
            return "end";
        }

        @Override
        public void render(Graphics2D g, ReelFrame frame, BurnSession session, ReelStyle style) {
            background(g, style);
            disc(g, W / 2.0, H * 0.40, 200, 0, style);
            centred(g, style.messages().get("reel.endCard"), style.title().deriveFont(96f), style.text(), H * 0.62);
            // Fade to black over the last half second.
            double out = Math.max(0, (frame.sceneFraction() - 0.75) / 0.25);
            if (out > 0) {
                g.setComposite(AlphaComposite.SrcOver);
                g.setColor(new Color(0, 0, 0, (int) Math.round(out * 255)));
                g.fillRect(0, 0, W, H);
            }
        }
    }

    // -------------------------------------------------------------------------------- drawing

    /** Where the spin-up ends, so later scenes carry on smoothly. */
    static double spunUpAngle() {
        return 0.5 * SPEED * SPIN_UP_SECONDS - SPEED * (2.0 + SPIN_UP_SECONDS);
    }

    static void background(Graphics2D g, ReelStyle style) {
        g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new GradientPaint(0, 0, style.top(), 0, H, style.bottom()));
        g.fillRect(0, 0, W, H);
    }

    static void fade(Graphics2D g, double alpha) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.clamp(alpha, 0, 1)));
    }

    /** An original disc drawing: silver face, two coloured sheen sectors, hub and hole. */
    static void disc(Graphics2D g, double cx, double cy, double r, double angle, ReelStyle style) {
        Graphics2D d = (Graphics2D) g.create();
        try {
            d.rotate(Math.toRadians(angle), cx, cy);
            d.setPaint(
                    new RadialGradientPaint((float) cx, (float) cy, (float) r, new float[] {0f, 0.7f, 1f}, new Color[] {
                        new Color(0xF2F4F7), new Color(0xC9CFD8), new Color(0xAEB6C2)
                    }));
            d.fill(circle(cx, cy, r));
            d.setColor(withAlpha(style.accent(), 110));
            d.fill(new Arc2D.Double(cx - r * 0.95, cy - r * 0.95, r * 1.9, r * 1.9, 20, 50, Arc2D.PIE));
            d.setColor(withAlpha(style.led(), 80));
            d.fill(new Arc2D.Double(cx - r * 0.95, cy - r * 0.95, r * 1.9, r * 1.9, 200, 50, Arc2D.PIE));
            d.setColor(new Color(0xDDE2E8));
            d.fill(circle(cx, cy, r * 0.32));
            d.setColor(new Color(0x8A94A3));
            d.setStroke(new BasicStroke(3));
            d.draw(circle(cx, cy, r));
            d.draw(circle(cx, cy, r * 0.32));
            d.setPaint(style.top());
            d.fill(circle(cx, cy, r * 0.11));
        } finally {
            d.dispose();
        }
    }

    /** A tick in a ring, drawn as a path (the fonts have no tick glyph). */
    static void tick(Graphics2D g, double cx, double cy, double r, ReelStyle style, double grow) {
        Graphics2D d = (Graphics2D) g.create();
        try {
            d.translate(cx, cy);
            d.scale(grow, grow);
            d.setColor(style.led());
            d.setStroke(new BasicStroke(14, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            d.draw(circle(0, 0, r));
            Path2D mark = new Path2D.Double();
            mark.moveTo(-r * 0.45, 0);
            mark.lineTo(-r * 0.1, r * 0.35);
            mark.lineTo(r * 0.5, -r * 0.35);
            d.draw(mark);
        } finally {
            d.dispose();
        }
    }

    static void centred(Graphics2D g, String text, Font font, Color colour, double baseline) {
        g.setFont(font);
        g.setColor(colour);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, (float) ((W - metrics.stringWidth(text)) / 2.0), (float) baseline);
    }

    /** Centred text wrapped at spaces within {@code maxWidth}; returns the baseline after the last line. */
    static double centredLines(Graphics2D g, String text, Font font, Color colour, double baseline, int maxWidth) {
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (metrics.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        lines.add(line.toString());
        double y = baseline;
        for (String each : lines.subList(0, Math.min(3, lines.size()))) {
            centred(g, fit(g, each, font, maxWidth), font, colour, y);
            y += metrics.getHeight();
        }
        return y - metrics.getHeight();
    }

    /** The text, shortened with "…" to fit {@code maxWidth}. */
    static String fit(Graphics2D g, String text, Font font, int maxWidth) {
        FontMetrics metrics = g.getFontMetrics(font);
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        String shortened = text;
        while (shortened.length() > 1 && metrics.stringWidth(shortened + "…") > maxWidth) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }

    static Color withAlpha(Color colour, int alpha) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
    }

    private static Ellipse2D circle(double cx, double cy, double r) {
        return new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
    }
}
