package com.thelads.core.v1_8_9.adapter;

import com.thelads.core.client.bridge.LadsGraphics;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * LadsGraphics over 1.8.9's immediate-mode Gui, FontRenderer and GlStateManager, in scaled GUI coordinates. Every call leaves
 * 1.8.9's GUI state contract: alpha test on (text needs it once a fill has turned blending off) and lighting off.
 */
public class GuiLadsAdapter implements LadsGraphics {
    /** QA only (CoreProbe): every text and item drawn while non-null. */
    public static List<String> recording;
    private final FontRenderer font;
    private final int width, height;
    /** Nested scissors intersect, as GuiGraphics' scissor stack does on the other versions. */
    private final Deque<int[]> scissors = new ArrayDeque<>();

    public GuiLadsAdapter(FontRenderer font, int width, int height) {
        this.font = font != null ? font : Minecraft.getMinecraft().fontRendererObj;
        this.width = width;
        this.height = height;
    }

    /** As vanilla's drawGradientRect: the alpha test (GREATER 0.1) would drop faint fills such as the editor's grid. */
    @Override
    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        GlStateManager.disableAlpha();
        Gui.drawRect(minX, minY, maxX, maxY, color);
        GlStateManager.enableAlpha();
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow) {
        if (text == null) return;
        if (recording != null) recording.add(text);
        font.drawString(text, x, y, color, shadow);
    }

    @Override
    public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) {
        if (text == null) return;
        if (recording != null) recording.add(text);
        font.drawString(text, centerX - font.getStringWidth(text) / 2, y, color, shadow);
    }

    /** The index-th worn piece (feet first, as the bridge lists them) through RenderItem with GUI item lighting, as the hotbar draws items. */
    @Override
    public void drawArmorItem(int index, int x, int y, boolean preview) {
        ItemStack stack = armorStack(index, preview);
        if (stack == null) return;
        if (recording != null) recording.add("item:" + stack.getDisplayName());
        RenderItem items = Minecraft.getMinecraft().getRenderItem();
        GlStateManager.enableDepth(); // block models (a pumpkin) need it; the Lads HUD draws the rest without
        RenderHelper.enableGUIStandardItemLighting();
        items.renderItemAndEffectIntoGUI(stack, x, y);
        items.renderItemOverlays(font, stack, x, y);
        // RenderItem leaves lighting and depth on and, without a durability bar, the alpha test off.
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /** A damaged diamond sample in the editor when nothing is worn (as on 1.21.1). */
    static ItemStack armorStack(int index, boolean preview) {
        if (preview) {
            ItemStack stack = new ItemStack(index == 0 ? Items.diamond_helmet : Items.diamond_chestplate);
            stack.setItemDamage(stack.getMaxDamage() / 4);
            return stack;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return null;
        int visible = 0;
        for (ItemStack stack : mc.thePlayer.inventory.armorInventory)
            if (stack != null && visible++ == index) return stack;
        return null;
    }

    @Override
    public int textWidth(String text) {
        return text != null ? font.getStringWidth(text) : 0;
    }

    @Override
    public int fontHeight() {
        return font.FONT_HEIGHT;
    }

    @Override
    public void pushPose() {
        GlStateManager.pushMatrix();
    }

    @Override
    public void popPose() {
        GlStateManager.popMatrix();
    }

    @Override
    public void translate(float x, float y) {
        GlStateManager.translate(x, y, 0.0f);
    }

    @Override
    public void scale(float sx, float sy) {
        GlStateManager.scale(sx, sy, 1.0f);
    }

    @Override
    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        int[] box = {minX, minY, maxX, maxY};
        int[] outer = scissors.peek();
        if (outer != null) box = new int[] {Math.max(minX, outer[0]), Math.max(minY, outer[1]), Math.min(maxX, outer[2]), Math.min(maxY, outer[3])};
        scissors.push(box);
        apply(box);
    }

    @Override
    public void disableScissor() {
        scissors.poll();
        int[] outer = scissors.peek();
        if (outer != null) apply(outer);
        else GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /** GUI rectangle to window pixels; OpenGL's scissor origin is the bottom-left corner. */
    private static void apply(int[] box) {
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(box[0] * scale, mc.displayHeight - box[3] * scale,
            Math.max(0, box[2] - box[0]) * scale, Math.max(0, box[3] - box[1]) * scale);
    }

    @Override
    public void blit(String texture, int x, int y, int u, int v, int width, int height) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation(texture != null ? texture : "minecraft:textures/gui/icons.png"));
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, width, height, 256.0f, 256.0f);
    }

    /** Face and hat layer of the local player's skin, or the default skin for anyone else. */
    @Override
    public void drawHead(String username, String uuid, int x, int y, int size) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            UUID id = uuid != null && !uuid.trim().isEmpty() ? UUID.fromString(uuid) : UUID.nameUUIDFromBytes(String.valueOf(username).getBytes("UTF-8"));
            ResourceLocation skin = mc.thePlayer != null && id.equals(mc.thePlayer.getUniqueID())
                ? mc.thePlayer.getLocationSkin() : DefaultPlayerSkin.getDefaultSkin(id);
            mc.getTextureManager().bindTexture(skin);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.enableBlend();
            Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, size, size, 64, 64);
            Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, size, size, 64, 64);
        } catch (Exception e) {
            fill(x, y, x + size, y + size, 0xFF6C63FF);
        }
    }

    @Override
    public int getScaledWidth() {
        return width;
    }

    @Override
    public int getScaledHeight() {
        return height;
    }
}
