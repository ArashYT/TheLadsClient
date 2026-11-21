package com.thelads.core.v1_8_9.feature.packs.mixin;

import com.thelads.core.perf.FastPackScreen;
import com.thelads.core.v1_8_9.feature.packs.PackIcons189;
import java.awt.image.BufferedImage;
import java.io.File;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * updateResourcePack still reads pack.mcmeta right away (the list and its validity stay as they were) but no longer decodes pack.png;
 * the icon decodes on a background thread when the entry is first drawn, with the default icon shown until then.
 * Off with -Dlads.fastpackscreen=false.
 */
@Mixin(ResourcePackRepository.Entry.class)
public abstract class PackEntryIconMixin189 {
    @Shadow private File resourcePackFile;
    @Shadow private BufferedImage texturePackIcon;
    @Shadow private ResourceLocation locationTexturePackIcon;

    @Unique private volatile BufferedImage lads$decoded;
    @Unique private boolean lads$started;

    @Redirect(method = "updateResourcePack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/IResourcePack;getPackImage()Ljava/awt/image/BufferedImage;"))
    private BufferedImage lads$skipDecode(IResourcePack pack) throws java.io.IOException {
        return FastPackScreen.enabled() ? null : pack.getPackImage();
    }

    @Inject(method = "bindTexturePackIcon", at = @At("HEAD"), cancellable = true)
    private void lads$lazyIcon(TextureManager textures, CallbackInfo ci) {
        if (locationTexturePackIcon != null || texturePackIcon != null || !FastPackScreen.enabled()) return;
        BufferedImage image = lads$decoded;
        if (image != null) {
            texturePackIcon = image; // vanilla uploads it and binds it below
            return;
        }
        if (!lads$started) {
            lads$started = true;
            PackIcons189.decodeAsync(resourcePackFile, img -> lads$decoded = img);
        }
        PackIcons189.bindPlaceholder(textures);
        ci.cancel();
    }
}
