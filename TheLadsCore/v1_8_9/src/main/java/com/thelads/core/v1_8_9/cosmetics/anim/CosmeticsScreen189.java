package com.thelads.core.v1_8_9.cosmetics.anim;

import com.thelads.core.client.cosmetics.CosmeticsScreen;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * Host screen for the common CosmeticsScreen on 1.8.9: wheel slot configuration, sharing switches, and a link to Controls.
 */
public final class CosmeticsScreen189 extends GuiScreen {
    private final GuiScreen parent;
    private final CosmeticsScreen ui;

    public CosmeticsScreen189(GuiScreen parent) {
        this.parent = parent;
        this.ui = new CosmeticsScreen(this::keyLabel,
            () -> Minecraft.getMinecraft().displayGuiScreen(new GuiControls(this, Minecraft.getMinecraft().gameSettings)),
            () -> Minecraft.getMinecraft().displayGuiScreen(parent));
    }

    public GuiScreen parent() { return parent; }

    public CosmeticsScreen ui() { return ui; }

    private String keyLabel(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || mc.gameSettings.keyBindings == null) return "";
        for (KeyBinding binding : mc.gameSettings.keyBindings) {
            if (binding != null && name.equals(binding.getKeyDescription())) {
                if (binding.getKeyCode() == 0) return "";
                String label = GameSettings.getKeyDisplayString(binding.getKeyCode());
                return label != null ? label : "";
            }
        }
        return "";
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        ui.render(new GuiLadsAdapter(fontRendererObj, width, height), mx, my);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (!ui.mouseClicked(mx, my, button)) super.mouseClicked(mx, my, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            ui.close();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}
