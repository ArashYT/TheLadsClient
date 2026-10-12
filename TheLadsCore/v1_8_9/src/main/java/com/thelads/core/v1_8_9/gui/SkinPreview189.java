package com.thelads.core.v1_8_9.gui;

import com.thelads.core.v1_8_9.feature.GlState189;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.util.ResourceLocation;

/**
 * A player model wearing a skin, drag to turn it (PlayerPreview1201 and 26.3's PlayerSkinWidget): vanilla's ModelPlayer drawn straight
 * from the skin texture, so it works on screens without a world. The outer layers follow the Skin Customization toggles.
 */
final class SkinPreview189 {
    private final int x, y, width, height;
    private final Supplier<ResourceLocation> texture;
    private final BooleanSupplier slim;
    private final ModelPlayer wide = new ModelPlayer(0.0F, false), slender = new ModelPlayer(0.0F, true);
    private float yaw = -25f;
    private boolean dragging;
    private int lastX;

    SkinPreview189(int x, int y, int width, int height, Supplier<ResourceLocation> texture, BooleanSupplier slim) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.texture = texture;
        this.slim = slim;
    }

    float yaw() {
        return yaw;
    }

    void draw(Minecraft mc) {
        ModelPlayer model = slim.getAsBoolean() ? slender : wide;
        Set<EnumPlayerModelParts> parts = mc.gameSettings.getModelParts();
        model.bipedHeadwear.showModel = parts.contains(EnumPlayerModelParts.HAT);
        model.bipedBodyWear.showModel = parts.contains(EnumPlayerModelParts.JACKET);
        model.bipedLeftArmwear.showModel = parts.contains(EnumPlayerModelParts.LEFT_SLEEVE);
        model.bipedRightArmwear.showModel = parts.contains(EnumPlayerModelParts.RIGHT_SLEEVE);
        model.bipedLeftLegwear.showModel = parts.contains(EnumPlayerModelParts.LEFT_PANTS_LEG);
        model.bipedRightLegwear.showModel = parts.contains(EnumPlayerModelParts.RIGHT_PANTS_LEG);
        model.isSneak = false;
        model.isRiding = false;
        float scale = Math.min(height / 2.1f, width / 1.1f); // a player is 2 blocks high and, arms out, 1 wide
        boolean blend = GlState189.blend(), alpha = GlState189.alpha(), depth = GlState189.depth();
        mc.getTextureManager().bindTexture(texture.get());
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha(); // the outer layers' empty pixels are cut out, not drawn black
        GlStateManager.alphaFunc(516, 0.1f);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableColorMaterial();
        GlStateManager.disableCull(); // the mirrored matrix below turns the faces around
        RenderHelper.enableGUIStandardItemLighting(); // the light is fixed to the screen, not turned with the model
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + width / 2f, y + height - 4f - 1.5f * scale, 50f); // the model's origin is its neck, its feet 1.5 blocks down
        GlStateManager.scale(scale, scale, -scale);
        GlStateManager.rotate(-yaw, 0f, 1f, 0f); // dragging right turns the front to the right, as on 1.20.1
        GlStateManager.color(1f, 1f, 1f, 1f);
        // ModelPlayer.render would ask its entity whether it sneaks: the parts are drawn straight, in the idle pose.
        float unit = 0.0625f;
        model.setRotationAngles(0f, 0f, 0f, 0f, 0f, unit, null);
        model.bipedHead.render(unit);
        model.bipedBody.render(unit);
        model.bipedRightArm.render(unit);
        model.bipedLeftArm.render(unit);
        model.bipedRightLeg.render(unit);
        model.bipedLeftLeg.render(unit);
        model.bipedHeadwear.render(unit);
        model.bipedLeftLegwear.render(unit);
        model.bipedRightLegwear.render(unit);
        model.bipedLeftArmwear.render(unit);
        model.bipedRightArmwear.render(unit);
        model.bipedBodyWear.render(unit);
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableColorMaterial();
        GlStateManager.enableCull();
        if (!depth) GlStateManager.disableDepth();
        if (!alpha) GlStateManager.disableAlpha();
        if (!blend) GlStateManager.disableBlend();
    }

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || mouseX < x || mouseY < y || mouseX >= x + width || mouseY >= y + height) return false;
        dragging = true;
        lastX = mouseX;
        return true;
    }

    void mouseDragged(int mouseX) {
        if (!dragging) return;
        yaw += (mouseX - lastX) * 2.5f;
        lastX = mouseX;
    }

    void mouseReleased() {
        dragging = false;
    }
}
