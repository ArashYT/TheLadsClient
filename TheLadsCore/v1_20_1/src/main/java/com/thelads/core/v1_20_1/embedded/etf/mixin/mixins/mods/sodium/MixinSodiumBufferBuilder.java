package com.thelads.core.v1_20_1.embedded.etf.mixin.mixins.mods.sodium;
import me.jellysquid.mods.sodium.client.render.vertex.buffer.SodiumBufferBuilder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import com.thelads.core.v1_20_1.embedded.etf.features.ETFManager;
import com.thelads.core.v1_20_1.embedded.etf.features.texture_handlers.ETFTexture;
import com.thelads.core.v1_20_1.embedded.etf.utils.ETFRenderLayerWithTexture;
import com.thelads.core.v1_20_1.embedded.etf.utils.ETFVertexConsumer;

import java.util.Optional;

//class no longer exists post sodium 0.5.9 which is always true past 1.21
@Pseudo
@Mixin(SodiumBufferBuilder.class)
public class MixinSodiumBufferBuilder implements ETFVertexConsumer {

    @Unique
    com.thelads.core.v1_20_1.embedded.etf.utils.URenderTypeToVertexConsumer etf$provider = null;
    @Unique
    RenderType etf$renderLayer = null;
    @Unique
    ETFTexture etf$ETFTexture = null;

    @Override
    public ETFTexture etf$getETFTexture() {
        return etf$ETFTexture;
   }

    @Override
    public com.thelads.core.v1_20_1.embedded.etf.utils.URenderTypeToVertexConsumer etf$getProvider() {
        return etf$provider;
    }

    @Override
    public RenderType etf$getRenderLayer() {
        return etf$renderLayer;
    }

    @Override
    public void etf$initETFVertexConsumer(com.thelads.core.v1_20_1.embedded.etf.utils.URenderTypeToVertexConsumer provider, RenderType renderLayer) {
        etf$provider = provider;

        etf$renderLayer = renderLayer;

        //todo sprites give atlas texture here
        if (renderLayer instanceof ETFRenderLayerWithTexture etfRenderLayerWithTexture) {
            Optional<ResourceLocation> possibleId = etfRenderLayerWithTexture.etf$getId();
            possibleId.ifPresent(identifier -> etf$ETFTexture = ETFManager.getInstance().getETFTextureNoVariation(identifier));
        }
//        else {
//            etf$ETFTexture = null;
//        }
    }
}
