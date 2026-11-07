// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesignFileTest {

    @TempDir
    Path folder;

    static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFF0000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    static DesignDocument withImage(String imageId) {
        PrintTemplate front = PrintTemplates.builtIn().get("cd-front");
        DesignDocument document = Presets.create(
                "classic", front, new CoverContent("Audio CD", "Road Trip", "2026", List.of("1. One", "2. Two")));
        List<DesignElement> elements = new ArrayList<>(document.elements());
        elements.add(new DesignElement.Image("image-1", Frame.of(10, 10, 40, 30), imageId));
        return document.withElements(elements);
    }

    @Test
    void savesAndOpensADesignWithItsImages() throws IOException {
        ImageStore images = new ImageStore();
        byte[] picture = png(40, 30);
        DesignDocument document = withImage(images.add(picture));
        Path file = folder.resolve("cover" + DesignFile.EXTENSION);
        DesignFile.save(document, images, file);

        DesignFile.Opened opened = DesignFile.open(file);
        assertEquals(document, opened.document());
        assertArrayEquals(picture, opened.images().bytes(document.imageIds().getFirst()));
        assertEquals(
                List.of(),
                Files.list(folder)
                        .filter(path -> path.toString().endsWith(".part"))
                        .toList());
    }

    @Test
    void unknownFieldsFromNewerVersionsAreIgnored() throws IOException {
        String json = """
                {"schemaVersion":1,"templateId":"cd-front","spineMm":0,"holeDiameterMm":0,"preset":"classic",
                 "background":"#FFFFFF","futureField":true,"elements":[]}""";
        DesignFile.Opened opened = DesignFile.read(zip(List.of(new Entry("design.json", bytes(json)))));
        assertEquals("cd-front", opened.document().templateId());
        assertEquals(CoverContent.EMPTY, opened.document().content());
    }

    @Test
    void refusesAFileFromANewerVersion() throws IOException {
        String json = """
                {"schemaVersion":2,"templateId":"cd-front","preset":"classic","background":"#FFFFFF","elements":[]}""";
        DesignFile.DesignFileException error = assertThrows(
                DesignFile.DesignFileException.class,
                () -> DesignFile.read(zip(List.of(new Entry("design.json", bytes(json))))));
        assertEquals("design.open.newer", error.reasonKey());
    }

    @Test
    void refusesEntriesOutsideTheLayout() throws IOException {
        for (String name : List.of("../evil.png", "images/../../evil.png", "design2.json", "images/x.png")) {
            DesignFile.DesignFileException error = assertThrows(
                    DesignFile.DesignFileException.class,
                    () -> DesignFile.read(zip(List.of(new Entry(name, bytes("x"))))),
                    name);
            assertEquals("design.open.damaged", error.reasonKey(), name);
        }
    }

    @Test
    void refusesAnImageThatDoesNotMatchItsName() throws IOException {
        byte[] picture = png(4, 4);
        String wrong = "0".repeat(64);
        assertThrows(
                DesignFile.DesignFileException.class,
                () -> DesignFile.read(zip(List.of(
                        new Entry("design.json", bytes("{}")), new Entry("images/" + wrong + ".png", picture)))));
    }

    @Test
    void refusesAMissingImageOrDocument() throws IOException {
        String json = new String(
                tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsBytes(withImage("a".repeat(64))),
                StandardCharsets.UTF_8);
        assertThrows(
                DesignFile.DesignFileException.class,
                () -> DesignFile.read(zip(List.of(new Entry("design.json", bytes(json))))));
        assertThrows(DesignFile.DesignFileException.class, () -> DesignFile.read(zip(List.of())));
        assertThrows(
                DesignFile.DesignFileException.class,
                () -> DesignFile.read(zip(List.of(new Entry("design.json", bytes("not json"))))));
    }

    @Test
    void theImageStoreAcceptsOnlyPictures() throws IOException {
        ImageStore images = new ImageStore();
        assertThrows(IllegalArgumentException.class, () -> images.add(bytes("hello")));
        String id = images.add(png(2, 2));
        assertEquals(id, images.add(png(2, 2)), "same bytes, same id");
        assertEquals(ImageStore.Format.PNG, images.format(id));
        assertEquals(2, images.image(id).getWidth());
    }

    record Entry(String name, byte[] bytes) {}

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static ByteArrayInputStream zip(List<Entry> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.bytes());
                zip.closeEntry();
            }
        }
        return new ByteArrayInputStream(out.toByteArray());
    }
}
