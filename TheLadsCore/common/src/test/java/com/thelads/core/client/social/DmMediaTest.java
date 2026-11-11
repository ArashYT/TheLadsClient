package com.thelads.core.client.social;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DmMediaTest {
    @TempDir Path dir;

    @Test
    void gifLinksOnlyFromTenorAndGiphy() {
        assertEquals("https://media.tenor.com/abcAAAAC/cat.gif", DmMedia.gifLink(" https://media.tenor.com/abcAAAAC/cat.gif "));
        assertNotNull(DmMedia.gifLink("https://tenor.com/view/happy-cat-gif-10804346947536782797"));
        assertNotNull(DmMedia.gifLink("https://giphy.com/gifs/cat-3o7aD2saalBwwftBIY"));
        assertNull(DmMedia.gifLink("look https://media.tenor.com/a/b.gif"));
        assertNull(DmMedia.gifLink("https://evil.example/cat.gif"));
        assertNull(DmMedia.gifLink("http://media.tenor.com/a/b.gif"));
        assertNull(DmMedia.gifLink("https://media.tenor.com@evil.example/b.gif"));
        assertNull(DmMedia.gifLink("https://i.giphy.com.evil.example/b.gif"));

        assertEquals("https://i.giphy.com/3o7aD2saalBwwftBIY.gif", DmMedia.resolveGif("https://giphy.com/gifs/cat-3o7aD2saalBwwftBIY"));
        assertEquals("https://i.giphy.com/3o7aD2saalBwwftBIY.gif", DmMedia.resolveGif("https://giphy.com/gifs/3o7aD2saalBwwftBIY"));
        assertEquals("https://media1.tenor.com/m/x/y.gif", DmMedia.resolveGif("https://media1.tenor.com/m/x/y.gif"));
        assertNull(DmMedia.resolveGif("https://example.com/x.gif"));
        assertNull(DmMedia.gif("https://example.com/x.gif"));
        assertNull(DmMedia.image("../../etc/passwd"));
        assertNull(DmMedia.image("file:" + dir.resolve("outside.jpg")));
    }

    @Test
    void onlyFilesDirectlyInTheScreenshotsFolder() throws Exception {
        Path shots = Files.createDirectories(dir.resolve("screenshots"));
        Path older = Files.write(shots.resolve("2026-11-10_01.png"), png(64, 36));
        Path newer = Files.write(shots.resolve("2026-11-11_02.png"), png(64, 36));
        Files.setLastModifiedTime(older, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(newer, FileTime.fromMillis(2_000));
        Files.writeString(shots.resolve("notes.txt"), "x");
        Path nested = Files.createDirectories(shots.resolve("sub"));
        Path deep = Files.write(nested.resolve("deep.png"), png(8, 8));
        Path elsewhere = Files.write(dir.resolve("elsewhere.png"), png(8, 8));

        assertEquals(List.of(newer, older), DmMedia.screenshots(dir));
        assertTrue(DmMedia.isScreenshot(dir, newer));
        assertTrue(DmMedia.isScreenshot(dir, shots.resolve("sub/../2026-11-10_01.png")));
        assertFalse(DmMedia.isScreenshot(dir, deep));
        assertFalse(DmMedia.isScreenshot(dir, elsewhere));
        assertFalse(DmMedia.isScreenshot(dir, shots.resolve("notes.txt")));
        assertFalse(DmMedia.isScreenshot(dir, shots.resolve("missing.png")));
        assertEquals(List.of(), DmMedia.screenshots(dir.resolve("nowhere")));
    }

    @Test
    void screenshotEncodedUnderTheUploadCapAndSplitInParts() throws Exception {
        BufferedImage noisy = new BufferedImage(3840, 2160, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(1);
        for (int y = 0; y < 2160; y++) for (int x = 0; x < 3840; x++) noisy.setRGB(x, y, r.nextInt());
        Path shot = dir.resolve("big.png");
        ImageIO.write(noisy, "png", shot.toFile());

        byte[] jpeg = DmMedia.encodeScreenshot(shot);
        assertTrue(jpeg.length <= DmMedia.MAX_UPLOAD_BYTES, "size " + jpeg.length);
        assertEquals((byte) 0xFF, jpeg[0]);
        assertEquals((byte) 0xD8, jpeg[1]);
        BufferedImage back = ImageIO.read(new java.io.ByteArrayInputStream(jpeg));
        assertTrue(Math.max(back.getWidth(), back.getHeight()) <= 1600);

        List<String> parts = DmMedia.parts(jpeg);
        assertTrue(parts.size() <= 30, "parts " + parts.size());
        parts.forEach(p -> assertTrue(p.length() <= DmMedia.PART_B64));
        assertArrayEquals(jpeg, Base64.getDecoder().decode(String.join("", parts)));
    }

    @Test
    void gifFramesLaidOutAsAGridWithTheirTiming() throws Exception {
        DmMedia.Sheet sheet = DmMedia.sheet(gif(new Color[]{Color.RED, Color.GREEN, Color.BLUE}, 40, 30, 5));
        assertEquals(3, sheet.frames());
        assertEquals(2, sheet.cols());
        assertEquals(2, sheet.rows());
        assertEquals(40, sheet.cellW());
        assertArrayEquals(new int[]{50, 50, 50}, sheet.delays());
        assertEquals(Color.RED.getRGB(), sheet.image().getRGB(20, 15));
        assertEquals(Color.GREEN.getRGB(), sheet.image().getRGB(60, 15));
        assertEquals(Color.BLUE.getRGB(), sheet.image().getRGB(20, 45));

        DmMedia.Media m = new DmMedia.Media();
        m.frames = 3;
        m.delays = sheet.delays();
        m.totalMs = 150;
        assertEquals(0, m.frame(10));
        assertEquals(1, m.frame(60));
        assertEquals(2, m.frame(149));
        assertEquals(0, m.frame(151));

        // 120 frames: every third kept, so at most 48 and the delays add up
        Color[] many = new Color[120];
        for (int i = 0; i < many.length; i++) many[i] = new Color(i * 2, 0, 0);
        DmMedia.Sheet big = DmMedia.sheet(gif(many, 300, 200, 2));
        assertEquals(40, big.frames());
        assertTrue(big.image().getWidth() <= 1024 && big.image().getHeight() <= 1024);
        assertEquals(60, big.delays()[0]); // three 20 ms frames in one
    }

    @Test
    void fitKeepsTheShape() {
        DmMedia.Media m = new DmMedia.Media();
        m.width = 1920;
        m.height = 1080;
        assertArrayEquals(new int[]{160, 90}, DmMedia.fit(m, 160, 120));
        assertArrayEquals(new int[]{106, 60}, DmMedia.fit(m, 200, 60));
    }

    private static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    /** An animated GIF of solid frames, delay in hundredths of a second. */
    private static byte[] gif(Color[] colors, int w, int h, int delayCs) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (Color c : colors) {
                BufferedImage frame = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = frame.createGraphics();
                g.setColor(c);
                g.fillRect(0, 0, w, h);
                g.dispose();
                IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame), null);
                String format = meta.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);
                IIOMetadataNode control = new IIOMetadataNode("GraphicControlExtension");
                control.setAttribute("disposalMethod", "none");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", Integer.toString(delayCs));
                control.setAttribute("transparentColorIndex", "0");
                root.appendChild(control);
                meta.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frame, null, meta), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
