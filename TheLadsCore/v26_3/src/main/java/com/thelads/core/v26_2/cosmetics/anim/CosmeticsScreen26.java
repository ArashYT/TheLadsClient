package com.thelads.core.v26_2.cosmetics.anim;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.cosmetics.CosmeticsScreen;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import com.thelads.core.v26_2.gui.CommonInput;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Host screen for common CosmeticsScreen on 26.3. */
public final class CosmeticsScreen26 extends Screen {
    private final Screen parent;
    private final CosmeticsScreen ui;
    private boolean closed;

    public CosmeticsScreen26(Screen parent) {
        super(Component.literal("Cosmetics"));
        this.parent = parent;
        this.ui = new CosmeticsScreen(this::keyLabel, this::openControls, this::closeScreen);
    }

    public CosmeticsScreen ui() { return ui; }

    private String keyLabel(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.keyMappings != null) {
            for (KeyMapping mapping : mc.options.keyMappings) {
                if (mapping.getName().equals(name)) {
                    if (mapping.isUnbound()) return "";
                    return mapping.getTranslatedKeyMessage().getString();
                }
            }
        }
        return "";
    }

    private void openControls() {
        Minecraft.getInstance().setScreenAndShow(new KeyBindsScreen(this, Minecraft.getInstance().options));
    }

    private void closeScreen() {
        if (closed) return;
        closed = true;
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return parent != null && parent.isPauseScreen();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        // The common CosmeticsScreen paints the full background; avoid the separate vanilla blur/panorama pass.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        ui.render(new GuiGraphicsExtractorLadsAdapter(g, this.font), mx, my);
        super.extractRenderState(g, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (ui.mouseClicked(event.x(), event.y(), CommonInput.button(event.button()))) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            ui.close();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        ui.close();
    }
}
