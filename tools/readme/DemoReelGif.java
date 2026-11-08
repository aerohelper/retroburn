// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.reel.DemoSession;
import io.aerohelper.retroburn.reel.ReelRenderer;
import io.aerohelper.retroburn.reel.ReelStyle;
import io.aerohelper.retroburn.reel.ReelTimeline;
import io.aerohelper.retroburn.reel.BurnSession;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * Writes the README's demo reel: the made-up session of {@link DemoSession} drawn by the real reel
 * scenes, scaled down and saved as a looping GIF. Needs RetroBurn's classes on the class path:
 *
 * <pre>java -cp target/classes:&lt;dependencies&gt; tools/readme/DemoReelGif.java docs/images/demo-reel.gif</pre>
 */
public final class DemoReelGif {

    static final int STEP = 4; // every 4th frame: 7.5 frames a second
    static final int WIDTH = 180;
    static final int HEIGHT = 320;

    public static void main(String[] args) throws Exception {
        BurnSession session = DemoSession.create("y2k");
        ReelTimeline timeline = ReelTimeline.of(15);
        ReelRenderer renderer =
                new ReelRenderer(timeline, session, ReelStyle.of(session, Messages.load(Locale.ENGLISH), true));
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(new File(args[0]))) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (int index = 2 * STEP; index < timeline.frameCount(); index += STEP) {
                BufferedImage small = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = small.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(renderer.draw(index), 0, 0, WIDTH, HEIGHT, null);
                g.dispose();
                writer.writeToSequence(new IIOImage(small, null, metadata(writer, small, index == 2 * STEP)), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    /** Frame delay (hundredths of a second) and, on the first frame, loop forever. */
    static IIOMetadata metadata(ImageWriter writer, BufferedImage image, boolean first) throws Exception {
        IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), null);
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
        IIOMetadataNode control = new IIOMetadataNode("GraphicControlExtension");
        control.setAttribute("disposalMethod", "none");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", "FALSE");
        control.setAttribute("delayTime", Integer.toString(Math.round(100f * STEP / ReelTimeline.FPS)));
        control.setAttribute("transparentColorIndex", "0");
        root.appendChild(control);
        if (first) {
            IIOMetadataNode extensions = new IIOMetadataNode("ApplicationExtensions");
            IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
            loop.setAttribute("applicationID", "NETSCAPE");
            loop.setAttribute("authenticationCode", "2.0");
            loop.setUserObject(new byte[] {1, 0, 0});
            extensions.appendChild(loop);
            root.appendChild(extensions);
        }
        metadata.setFromTree(format, root);
        return metadata;
    }
}
