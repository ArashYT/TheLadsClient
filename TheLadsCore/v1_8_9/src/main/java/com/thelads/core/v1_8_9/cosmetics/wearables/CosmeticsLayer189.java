package com.thelads.core.v1_8_9.cosmetics.wearables;

import com.thelads.core.client.cosmetics.wearables.CosmeticItem;
import com.thelads.core.client.cosmetics.wearables.CosmeticLibrary;
import com.thelads.core.client.cosmetics.wearables.CosmeticModels;
import com.thelads.core.client.cosmetics.wearables.CosmeticSlot;
import com.thelads.core.client.cosmetics.wearables.CosmeticsManager;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CosmeticsLayer189 implements LayerRenderer<AbstractClientPlayer> {
    private final RenderPlayer playerRenderer;
    private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();

    public CosmeticsLayer189(RenderPlayer playerRenderer) {
        this.playerRenderer = playerRenderer;
    }

    public static ResourceLocation getResource(String path) {
        if (path == null) return null;
        return TEXTURES.computeIfAbsent(path, p -> {
            String clean = p.startsWith("theladscore:") ? p.substring("theladscore:".length()) : p;
            return new ResourceLocation("theladscore", clean);
        });
    }

    @Override
    public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                              float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        if (player.isInvisible()) return;
        CosmeticsManager manager = CosmeticsManager.get();
        UUID uuid = player.getUniqueID();

        String hatId = manager.getEquippedHat(uuid);
        String glassesId = manager.getEquippedGlasses(uuid);

        if (hatId == null && glassesId == null) return;

        GlStateManager.pushMatrix();
        if (player.isSneaking()) {
            GlStateManager.translate(0.0F, 0.2F, 0.0F);
        }
        this.playerRenderer.getMainModel().bipedHead.postRender(0.0625F);

        if (hatId != null && manager.shouldRender(uuid, CosmeticSlot.HAT)) {
            renderCosmetic(hatId);
        }
        if (glassesId != null && manager.shouldRender(uuid, CosmeticSlot.GLASSES)) {
            renderCosmetic(glassesId);
        }

        GlStateManager.popMatrix();
    }

    private void renderCosmetic(String itemId) {
        float[] mesh = CosmeticModels.getMesh(itemId);
        if (mesh == null || mesh.length == 0) return;

        CosmeticItem item = CosmeticLibrary.get(itemId);
        if (item == null || item.texture() == null) return;

        ResourceLocation texture = getResource(item.texture());

        this.playerRenderer.bindTexture(texture);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_NORMAL);

        for (int i = 0; i < mesh.length; i += CosmeticModels.STRIDE) {
            buffer.pos(mesh[i], mesh[i + 1], mesh[i + 2])
                  .tex(mesh[i + 3], mesh[i + 4])
                  .normal(mesh[i + 5], mesh[i + 6], mesh[i + 7])
                  .endVertex();
        }
        tessellator.draw();
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
