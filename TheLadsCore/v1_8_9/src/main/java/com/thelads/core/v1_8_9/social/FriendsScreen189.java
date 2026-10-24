package com.thelads.core.v1_8_9.social;

import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/**
 * Host screen wrapper for the common FriendsScreenLogic (Task S4) on Minecraft 1.8.9.
 */
public final class FriendsScreen189 extends GuiScreen {
    private final GuiScreen parent;
    // TODO(S4): private FriendsScreenLogic logic;

    public FriendsScreen189(GuiScreen parent) {
        this.parent = parent;
        // TODO(S4): this.logic = new FriendsScreenLogic(new FriendsScreenLogic.Callbacks() { ... });
    }

    public GuiScreen parent() {
        return parent;
    }

    @Override
    public void initGui() {
        super.initGui();
        // TODO(S4): if (logic != null) logic.init(width, height);
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        drawDefaultBackground();
        GuiLadsAdapter g = new GuiLadsAdapter(fontRendererObj, width, height);
        // TODO(S4): if (logic != null) { logic.render(g, mx, my); } else { ... }
        renderPlaceholder(g, mx, my);
        super.drawScreen(mx, my, partialTicks);
    }

    private void renderPlaceholder(GuiLadsAdapter g, int mx, int my) {
        int cardW = 340, cardH = 220;
        int left = width / 2 - cardW / 2;
        int top = height / 2 - cardH / 2;
        g.fill(left, top, left + cardW, top + cardH, 0xD0111116);
        g.fill(left, top, left + cardW, top + 2, 0xFFCF1535);

        g.drawCenteredText("The Lads Friends", width / 2, top + 16, 0xFFFFFFFF, true);
        g.drawCenteredText("Connecting to social service...", width / 2, height / 2 - 20, 0xFFAAAAAA, true);
        g.drawCenteredText("SocialState and UI logic arriving from S3/S4 lanes.", width / 2, height / 2, 0xFF777777, true);
        g.drawCenteredText("Press ESC to return", width / 2, top + cardH - 24, 0xFF555555, true);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        // TODO(S4): if (logic != null && logic.mouseClicked(mx, my, button)) return;
        super.mouseClicked(mx, my, button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }
        // TODO(S4): if (logic != null && logic.keyPressed(keyCode, typedChar)) return;
        super.keyTyped(typedChar, keyCode);
    }
}
