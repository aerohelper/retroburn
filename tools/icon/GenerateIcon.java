// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Draws RetroBurn's icon: an original disc with a hub and an orange "burn" arc. Writes a 256 px PNG
 * for Linux and an ICO with 16, 32, 48 and 256 px PNG-compressed images for Windows.
 *
 * <pre>java tools/icon/GenerateIcon.java packaging/icons</pre>
 */
public final class GenerateIcon {

    public static void main(String[] args) throws IOException {
        Path folder = Files.createDirectories(Path.of(args.length > 0 ? args[0] : "packaging/icons"));
        Files.write(folder.resolve("retroburn.png"), png(draw(256)));
        List<byte[]> images = new ArrayList<>();
        int[] sizes = {16, 32, 48, 256};
        for (int size : sizes) {
            images.add(png(draw(size)));
        }
        Files.write(folder.resolve("retroburn.ico"), ico(sizes, images));
    }

    static BufferedImage draw(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double s = size / 256.0;
        double c = size / 2.0;
        double r = 118 * s;
        g.setPaint(new RadialGradientPaint(
                (float) c,
                (float) c,
                (float) r,
                new float[] {0f, 0.6f, 1f},
                new Color[] {new Color(0xF4F6F9), new Color(0xCDD3DC), new Color(0x9AA4B2)}));
        g.fill(circle(c, r));
        g.setColor(new Color(0x1F3B66));
        g.setStroke(new BasicStroke((float) Math.max(1, 6 * s)));
        g.draw(circle(c, r));
        // The burn: a thick orange arc around the upper right.
        g.setColor(new Color(0xF28C28));
        g.setStroke(new BasicStroke((float) (26 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double a = 84 * s;
        g.draw(new Arc2D.Double(c - a, c - a, 2 * a, 2 * a, -20, 150, Arc2D.OPEN));
        g.setColor(new Color(0x1F3B66));
        g.fill(circle(c, 34 * s));
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fill(circle(c, 13 * s));
        g.dispose();
        return image;
    }

    static Ellipse2D circle(double centre, double radius) {
        return new Ellipse2D.Double(centre - radius, centre - radius, 2 * radius, 2 * radius);
    }

    static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** An ICO file whose entries are PNG images (supported since Windows Vista). */
    static byte[] ico(int[] sizes, List<byte[]> images) {
        int headerSize = 6 + 16 * sizes.length;
        int total = headerSize + images.stream().mapToInt(image -> image.length).sum();
        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        out.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = headerSize;
        for (int index = 0; index < sizes.length; index++) {
            int size = sizes[index];
            out.put((byte) (size >= 256 ? 0 : size)).put((byte) (size >= 256 ? 0 : size));
            out.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
            out.putInt(images.get(index).length).putInt(offset);
            offset += images.get(index).length;
        }
        images.forEach(out::put);
        return out.array();
    }
}
