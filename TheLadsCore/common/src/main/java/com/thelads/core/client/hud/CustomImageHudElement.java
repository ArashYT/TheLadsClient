package com.thelads.core.client.hud;

import com.thelads.core.client.CrosshairBackground;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.TextOption;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom Image (OneClient's EvergreenHUD Image): a PNG or JPG you choose, at your width and its own shape. The pick is copied (scaled to
 * 1024 at most) into the shared Lads backgrounds folder like the crosshair background, so moving the original does not lose it.
 */
public class CustomImageHudElement extends HudElement {
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");
    private static volatile String picked;
    private String name, path;
    private boolean editor;
    private int pictureWidth = 1, pictureHeight = 1;

    public CustomImageHudElement() {
        width = 64;
        height = 64;
    }

    /** The Choose Image button: the file dialog, then a copy of the pick off the game thread; the option takes it on the next frame. */
    public static void choose() {
        LadsGameBridge.get().chooseImageFile().whenComplete((file, failure) -> {
            if (failure != null || file == null) return;
            try {
                picked = CrosshairBackground.store(file, "hud-image");
            } catch (Throwable unusable) {
                LOG.warn("Custom Image: {} unusable", file, unusable);
            }
        });
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        this.editor = editor;
        TextOption image = option("Image") instanceof TextOption t ? t : null;
        String chosen = picked;
        if (chosen != null && image != null) {
            picked = null;
            image.setValue(chosen);
            ConfigManager.save();
        }
        String now = image == null ? "" : image.getValue();
        if (!now.equals(name)) { // measured once per picture, not per frame
            name = now;
            File file = now.isEmpty() ? null : CrosshairBackground.imageFile(now);
            path = file != null && file.isFile() ? file.getPath() : null;
            int[] size = path == null ? null : size(file);
            pictureWidth = size == null ? 1 : size[0];
            pictureHeight = size == null ? 1 : size[1];
        }
        width = option("Width") instanceof SliderOption w ? w.getIntValue() : 64;
        height = Math.max(1, Math.round(width * pictureHeight / (float) pictureWidth));
    }

    private static int[] size(File file) {
        try (javax.imageio.stream.ImageInputStream in = javax.imageio.ImageIO.createImageInputStream(file)) {
            var readers = in == null ? null : javax.imageio.ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (Exception unreadable) {
            return null;
        }
    }

    @Override
    public void render(LadsGraphics g) {
        if (path != null && g.drawImage(path, x, y, width, height) || !editor) return;
        // No picture yet: the editor outlines where it will go.
        g.fill(x, y, x + width, y + 1, 0x80FFFFFF);
        g.fill(x, y + height - 1, x + width, y + height, 0x80FFFFFF);
        g.fill(x, y, x + 1, y + height, 0x80FFFFFF);
        g.fill(x + width - 1, y, x + width, y + height, 0x80FFFFFF);
    }
}
