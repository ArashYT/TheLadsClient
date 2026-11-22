package com.thelads.core.v1_8_9.perf.mixin;

import com.thelads.core.v1_8_9.perf.LoadPerf189;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.IImageBuffer;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.resources.IResourceManager;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skins and capes already in the cache: Minecraft decoded the PNG on the main thread (a hitch when a full tab list loads); now on
 * LoadPerf189's skin threads, like a download. The player shows the default skin for those few milliseconds, as before a download.
 * Off with -Dlads.asyncskins=false.
 */
@Mixin(ThreadDownloadImageData.class)
public abstract class ThreadDownloadImageDataMixin189 {
    @Shadow @Final private File cacheFile;
    @Shadow @Final private IImageBuffer imageBuffer;
    @Shadow private BufferedImage bufferedImage;
    @Shadow private Thread imageThread;

    @Shadow public abstract void setBufferedImage(BufferedImage image);
    @Shadow protected abstract void loadTextureFromServer();

    @Inject(method = "loadTexture", cancellable = true, at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETFIELD, ordinal = 0,
        target = "Lnet/minecraft/client/renderer/ThreadDownloadImageData;imageThread:Ljava/lang/Thread;"))
    private void lads$decodeOffThread(IResourceManager manager, CallbackInfo ci) {
        if (imageThread != null || cacheFile == null || !cacheFile.isFile()) return;
        imageThread = new Thread("Lads cached skin"); // never started: marks this texture as loading, like Minecraft's downloader
        File file = cacheFile;
        LoadPerf189.skinPool().execute(() -> {
            try {
                BufferedImage image = ImageIO.read(file);
                if (image == null) throw new IOException("not an image");
                if (imageBuffer != null) setBufferedImage(imageBuffer.parseUserSkin(image));
                else bufferedImage = image;
            } catch (Exception e) {
                LogManager.getLogger("TheLadsCore-1.8.9").error("Couldn't load skin " + file, e);
                loadTextureFromServer();
            }
        });
        ci.cancel();
    }
}
