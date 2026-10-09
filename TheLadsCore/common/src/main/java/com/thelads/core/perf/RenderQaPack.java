package com.thelads.core.perf;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * QA only (1.8.5 render capture): a resource pack of high-resolution animated block textures, generated on the spot, so the
 * Kerria remake can be timed where texture uploads are heavy (an HD pack), not only with the game's 16x textures.
 */
public final class RenderQaPack {
    public static final String NAME = "lads-qa-hd-anim";

    private RenderQaPack() {}

    /** One animated texture: its path under assets/minecraft/textures (no ".png"), frame count, ticks per frame, interpolation. */
    public record Sprite(String path, int frames, int frameTime, boolean interpolate) {}

    /**
     * Writes {@code <resourcepacks>/lads-qa-hd-anim} with every sprite at size x size per frame; {@code formatFields} is the pack
     * format part of pack.mcmeta for the game version (e.g. {@code "pack_format":15}). Returns the pack folder.
     */
    public static Path write(Path resourcepacks, String formatFields, int size, Sprite... sprites) throws Exception {
        Path pack = resourcepacks.resolve(NAME);
        Files.createDirectories(pack);
        Files.write(pack.resolve("pack.mcmeta"), ("{\"pack\":{" + formatFields
            + ",\"description\":\"Lads QA: HD animated textures\"}}").getBytes(StandardCharsets.UTF_8));
        for (Sprite sprite : sprites) {
            Path png = pack.resolve("assets/minecraft/textures/" + sprite.path() + ".png");
            Files.createDirectories(png.getParent());
            BufferedImage image = new BufferedImage(size, size * sprite.frames(), BufferedImage.TYPE_INT_ARGB);
            int seed = sprite.path().hashCode();
            for (int frame = 0; frame < sprite.frames(); frame++)
                for (int y = 0; y < size; y++)
                    for (int x = 0; x < size; x++)
                        image.setRGB(x, frame * size + y, colour(seed, x, y, frame, size));
            ImageIO.write(image, "png", png.toFile());
            Files.write(png.resolveSibling(png.getFileName() + ".mcmeta"), ("{\"animation\":{\"frametime\":" + sprite.frameTime()
                + ",\"interpolate\":" + sprite.interpolate() + "}}").getBytes(StandardCharsets.UTF_8));
        }
        return pack;
    }

    /** Opaque, smooth bands that move from frame to frame, different per sprite. */
    static int colour(int seed, int x, int y, int frame, int size) {
        int r = (x * 255 / size + frame * 8 + (seed & 63)) & 255;
        int g = (y * 255 / size + frame * 5 + ((seed >> 6) & 63)) & 255;
        int b = ((x + y) * 127 / size + frame * 11 + ((seed >> 12) & 63)) & 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
