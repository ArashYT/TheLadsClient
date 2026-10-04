package com.thelads.core.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** QA only: a 64x64 skin whose outer layers (hat, jacket, sleeves, trousers) are a checkerboard, so 3D layers stand out in a capture. */
public final class QaSkin {
    private static final int[][] BASE = {{0, 0, 32, 16}, {16, 16, 40, 32}, {40, 16, 56, 32}, {0, 16, 16, 32}, {16, 48, 32, 64}, {32, 48, 48, 64}};
    private static final int[][] OUTER = {{32, 0, 64, 16}, {16, 32, 40, 48}, {40, 32, 56, 48}, {0, 32, 16, 48}, {0, 48, 16, 64}, {48, 48, 64, 64}};

    private QaSkin() {}

    public static BufferedImage image() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int[] box : BASE) fill(skin, box, false);
        for (int[] box : OUTER) fill(skin, box, true);
        return skin;
    }

    public static byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image(), "png", out);
        return out.toByteArray();
    }

    private static void fill(BufferedImage skin, int[] box, boolean checker) {
        for (int y = box[1]; y < box[3]; y++)
            for (int x = box[0]; x < box[2]; x++)
                if (!checker || (x + y) % 2 == 0) skin.setRGB(x, y, checker ? 0xFFFFC000 : box[1] == 0 ? 0xFFC89070 : 0xFF2A4F80);
    }
}
