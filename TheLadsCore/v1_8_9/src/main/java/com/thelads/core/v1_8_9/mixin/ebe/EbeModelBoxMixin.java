package com.thelads.core.v1_8_9.mixin.ebe;

import com.thelads.core.v1_8_9.feature.ebe.BakedBlockEntities189;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.TexturedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ModelBox.class)
public abstract class EbeModelBoxMixin implements BakedBlockEntities189.ModelBoxQuads {
    @Shadow private TexturedQuad[] quadList;

    @Override
    public TexturedQuad[] lads$quads() { return quadList; }
}
