package com.thelads.core.v1_20_1.cosmetics.anim;

import com.thelads.core.client.cosmetics.anim.EmoteWheel;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The emote wheel (common EmoteWheel) while the Emotes key is held; the world keeps running and showing behind it. */
public final class EmoteWheelScreen1201 extends Screen {
    private final EmoteWheel wheel = new EmoteWheel(System.currentTimeMillis());
    /** QA only (EmoteCapture1201): where the pointer is taken to be. */
    static int[] qaPointer;
    private int mouseX, mouseY;
    private boolean released;

    public EmoteWheelScreen1201() { super(Component.literal("Emotes")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        if (qaPointer != null) { mx = qaPointer[0]; my = qaPointer[1]; }
        mouseX = mx; mouseY = my;
        wheel.render(new GuiGraphicsLadsAdapter1201(g), mx, my, System.currentTimeMillis());
    }

    @Override
    public boolean keyReleased(int key, int scancode, int modifiers) {
        if (!EmoteAnim1201.KEY.matches(key, scancode)) return super.keyReleased(key, scancode, modifiers);
        released = true;
        finish(wheel.release(System.currentTimeMillis(), mouseX, mouseY));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (!EmoteAnim1201.KEY.matches(key, scancode)) return super.keyPressed(key, scancode, modifiers);
        if (released) finish(""); // the key again closes a tapped-open wheel; repeats of the held key are ignored
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        finish(wheel.click(x, y));
        return true;
    }

    /** null: stay open; "": close; else close and play it. */
    void finish(String id) {
        if (id == null) return;
        onClose();
        if (!id.isEmpty()) EmoteAnim1201.play(id);
    }
}
