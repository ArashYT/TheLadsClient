package com.thelads.core.v1_8_9.feature.packs;

import com.thelads.core.perf.FastPackScreen;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;

/**
 * Pack icons on 1.8.9: pack.png is decoded on a background thread (vanilla decodes it inside updateResourcePack, for every new pack
 * when the screen opens), and the GPU upload stays on the render thread in bindTexturePackIcon. Until then the default icon is shown.
 */
public final class PackIcons189 {
    private static ResourceLocation placeholder;

    private PackIcons189() {}

    /** Binds the shared default-icon texture. Render thread only. */
    public static void bindPlaceholder(TextureManager textures) {
        if (placeholder == null) {
            BufferedImage image = null;
            try {
                image = Minecraft.getMinecraft().getResourcePackRepository().rprDefaultResourcePack.getPackImage();
            } catch (Exception ignored) {
            }
            if (image == null) image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            placeholder = textures.getDynamicTextureLocation("lads_pack_placeholder", new DynamicTexture(image));
        }
        textures.bindTexture(placeholder);
    }

    /** Decodes the icon of one pack file (or the default icon when it has none) on the pack decoder thread, then hands it to done. */
    public static void decodeAsync(final File file, final java.util.function.Consumer<BufferedImage> done) {
        FastPackScreen.decoder().execute(new Runnable() {
            @Override public void run() {
                BufferedImage image = null;
                IResourcePack pack = null;
                try {
                    pack = file.isDirectory() ? new FolderResourcePack(file) : new FileResourcePack(file);
                    image = pack.getPackImage();
                } catch (Exception ignored) {
                } finally {
                    if (pack instanceof Closeable) IOUtils.closeQuietly((Closeable) pack);
                }
                if (image == null) {
                    try {
                        image = Minecraft.getMinecraft().getResourcePackRepository().rprDefaultResourcePack.getPackImage();
                    } catch (Exception ignored) {
                    }
                }
                if (image == null) image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                done.accept(image);
            }
        });
    }
}
