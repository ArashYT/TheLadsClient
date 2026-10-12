package com.thelads.core.v1_8_9.gui;

import com.thelads.core.v1_8_9.feature.LocalSkins189;
import com.thelads.core.v1_8_9.feature.SkinLayers189;
import com.thelads.core.v1_8_9.mixin.GuiCustomizeSkinAccessor;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCustomizeSkin;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EnumPlayerModelParts;
import org.lwjgl.input.Keyboard;

/**
 * Options > Skin Customization as on 26.3 and 1.20.1: the Lads backdrop, a turnable preview of the skin (its outer layers in 3D
 * as on 26.3, with its 3D layers button), a toggle per outer layer and Change skin (SkinChangerScreen189). Every vanilla Skin Customization screen opens as this one (replace). 1.8.9 has no main hand to
 * pick and no embedded Capes (its Choose cape), so those two are not here.
 */
public final class SkinCustomizationScreen189 extends GuiScreen {
    private static final int CHANGE_SKIN = 200, LAYERS_3D = 201, DONE = 199;
    private final GuiScreen parent;
    private SkinPreview189 preview;
    private int left, previewWidth;

    public SkinCustomizationScreen189(GuiScreen parent) {
        this.parent = parent;
    }

    /** Forge's GuiOpenEvent: vanilla's Skin Customization screen becomes this one, with the same parent. */
    public static GuiScreen replace(GuiScreen next) {
        return next != null && next.getClass() == GuiCustomizeSkin.class
            ? new SkinCustomizationScreen189(((GuiCustomizeSkinAccessor) next).getParentScreen()) : next;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        left = Math.max(12, width / 2 - 180);
        int top = 55;
        previewWidth = Math.min(150, width / 3);
        preview = new SkinPreview189(left, top, previewWidth, Math.max(50, Math.min(height - 150, (int) (previewWidth * 1.6))),
            () -> LocalSkins189.current() == null ? LocalSkins189.accountSkin() : LocalSkins189.current().texture,
            () -> LocalSkins189.current() == null ? LocalSkins189.accountSlim() : LocalSkins189.current().slim);
        buttonList.add(new SkinButton189(CHANGE_SKIN, left, height - 60, previewWidth, 20, "Change skin"));
        buttonList.add(new SkinButton189(LAYERS_3D, left, height - 85, previewWidth, 20, layersLabel()));
        int x = left + previewWidth + 20, w = Math.max(90, Math.min(195, width - x - 12));
        int row = 0, step = Math.max(20, Math.min(27, (height - 100) / 7));
        for (EnumPlayerModelParts part : EnumPlayerModelParts.values())
            buttonList.add(new SkinButton189(part.getPartId(), x, top + row++ * step, w, 20, label(part)));
        buttonList.add(new SkinButton189(DONE, width / 2 - 55, height - 29, 110, 20, I18n.format("gui.done")));
    }

    private static String layersLabel() {
        return "3D layers: " + (SkinLayers189.previewOn() ? "ON" : "OFF");
    }

    private String label(EnumPlayerModelParts part) {
        return I18n.format("options.modelPart." + part.getPartName()) + ": " + I18n.format(mc.gameSettings.getModelParts().contains(part) ? "options.on" : "options.off");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        if (button.id == DONE) mc.displayGuiScreen(parent);
        else if (button.id == CHANGE_SKIN) mc.displayGuiScreen(new SkinChangerScreen189(this));
        else if (button.id == LAYERS_3D) {
            SkinLayers189.togglePreview();
            button.displayString = layersLabel();
        }
        else for (EnumPlayerModelParts part : EnumPlayerModelParts.values())
            if (part.getPartId() == button.id) {
                mc.gameSettings.switchModelPartEnabled(part);
                button.displayString = label(part);
            }
    }

    @Override
    public void onGuiClosed() {
        preview.release();
        mc.gameSettings.saveOptions(); // vanilla saves only through Done
    }

    @Override
    protected void keyTyped(char typed, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        else super.keyTyped(typed, key);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        preview.mouseClicked(mouseX, mouseY, button);
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        preview.mouseDragged(mouseX);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        preview.mouseReleased();
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0xFF100B10);
        drawRect(0, 0, width, 2, 0xFFCF1535);
        drawRect(left - 4, 49, left + previewWidth + 4, height - 40, 0xFF24151D);
        preview.draw(mc);
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, "THE LADS / SKIN CUSTOMIZATION", width / 2, 15, 0xFFFFFFFF);
        drawCenteredString(fontRendererObj, "Drag your skin preview to rotate", width / 2, 32, 0xFFB7A5AF);
    }

    /** QA: how far the preview is turned, how many 3D layer boxes it drew, and a button by its label. */
    public float previewYaw() {
        return preview.yaw();
    }

    public int previewBoxes() {
        return preview.boxesShown();
    }

    public GuiButton button(String label) {
        for (GuiButton button : buttonList) if (button.displayString.startsWith(label)) return button;
        return null;
    }
}
