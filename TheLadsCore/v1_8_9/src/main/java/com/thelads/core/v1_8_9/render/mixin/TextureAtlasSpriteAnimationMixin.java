package com.thelads.core.v1_8_9.render.mixin;

import com.thelads.core.v1_8_9.render.AnimationCache189;
import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lads Kerria (1.8.5): a frame change of an atlas animation is a GPU copy from the sprite's cached frames (AnimationCache189). */
@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteAnimationMixin {
    @Shadow protected List<int[][]> framesTextureData;

    @Redirect(method = "updateAnimation", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureMipmap([[IIIIIZZ)V"))
    private void lads$frame(int[][] data, int width, int height, int x, int y, boolean blur, boolean clamp) {
        int frame = -1;
        for (int i = 0; i < framesTextureData.size(); i++) if (framesTextureData.get(i) == data) { frame = i; break; }
        if (frame < 0 || !AnimationCache189.copy((TextureAtlasSprite) (Object) this, framesTextureData, frame, width, height, x, y))
            TextureUtil.uploadTextureMipmap(data, width, height, x, y, blur, clamp);
    }
}
