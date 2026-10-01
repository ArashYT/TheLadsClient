package com.thelads.core.v26_2.embedded.etf.features.texture_handlers;


import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.trim.ArmorTrim;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.Model;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import com.thelads.core.v26_2.embedded.etf.ETF;
import com.thelads.core.v26_2.embedded.etf.features.ETFManager;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFState;
import com.thelads.core.v26_2.embedded.etf.utils.ETFUtils2;
import com.thelads.core.v26_2.embedded.etf.utils.URenderTypeToVertexConsumer;

//todo 2 better cancelling out for post 1.21.2?
//todo is the patching still required?
// this might have been only used for iris issues that were fixed by inflating the matrices?
@SuppressWarnings("unused")
public class ETFArmorHandler {


    private ETFTexture trimTexture = null;


    public void start(){
        ETFState.allowTexturePatching = true;
    }
    public void end(){
        ETFState.allowTexturePatching = false;
    }

    public void renderTrimEmissive(final PoseStack matrices, final URenderTypeToVertexConsumer vertexConsumers, final Model model) {
        if(trimTexture != null && ETF.config().getConfig().canDoEmissiveTextures()){
            Identifier emissive = trimTexture.getEmissiveIdentifierOfCurrentState();
            if (emissive != null) {
                ETFState.startSpecialRenderOverlayPhase();
                VertexConsumer textureVert = vertexConsumers.getBuffer(
                        net.minecraft.client.renderer.rendertype.RenderTypes
                                .armorCutoutNoCull(emissive));
//                if (ETF.IRIS_DETECTED)
                    matrices.scale(1.001f,1.001f,1.001f);//inflate
                model.renderToBuffer(matrices, textureVert, ETF.EMISSIVE_FEATURE_LIGHT_VALUE, OverlayTexture.NO_OVERLAY
                , -1
                );
                ETFState.endSpecialRenderOverlayPhase();
            }
        }
        trimTexture = null;
    }


    public void setTrim(Identifier trimBaseId) {
        if(ETF.config().getConfig().enableArmorAndTrims) {
            //support modded trims with namespace
            Identifier trimMaterialIdentifier = ETFUtils2.res(trimBaseId.getNamespace(), "textures/" + trimBaseId.getPath() + ".png");
            var trim = ETFManager.getInstance().getETFTextureNoVariation(trimMaterialIdentifier);
            if (trim.isEmissive())
                trimTexture = trim;
        }
    }


}
