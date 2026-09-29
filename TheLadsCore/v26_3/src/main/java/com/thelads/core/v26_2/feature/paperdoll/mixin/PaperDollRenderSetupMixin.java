package com.thelads.core.v26_2.feature.paperdoll.mixin;

import com.thelads.core.v26_2.feature.paperdoll.PaperDollTexture;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderSetup.class)
public abstract class PaperDollRenderSetupMixin implements PaperDollTexture {
    @Shadow @Final private Map<String, ?> textures;
    @Override public Identifier ladsPaperDollTexture() {
        var texture = textures.get("Sampler0");
        return texture instanceof PaperDollTexture binding ? binding.ladsPaperDollTexture() : null;
    }
}
