package com.thelads.core.v1_8_9.cosmetics.anim;

import com.thelads.core.client.cosmetics.anim.EmoteWheel;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/** The emote wheel (common EmoteWheel) while the Emotes key is held; the world keeps running and showing behind it. */
public final class EmoteWheelScreen189 extends GuiScreen {
    private final EmoteWheel wheel = new EmoteWheel(System.currentTimeMillis());
    /** QA only (EmoteProbe189): where the pointer is taken to be. */
    static int[] qaPointer;
    private int lastX, lastY;
    private boolean released;

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        if (qaPointer != null) { mx = qaPointer[0]; my = qaPointer[1]; }
        lastX = mx; lastY = my;
        wheel.render(new GuiLadsAdapter(fontRendererObj, width, height), mx, my, System.currentTimeMillis());
    }

    @Override
    public void handleKeyboardInput() throws IOException {
        int key = Keyboard.getEventKey();
        if (key == 0 || key != EmoteAnim189.KEY.getKeyCode()) { super.handleKeyboardInput(); return; }
        if (!Keyboard.getEventKeyState()) release();
        else if (released && !Keyboard.isRepeatEvent()) finish(""); // the key again closes a tapped-open wheel
    }

    /** The Emotes key went up (also QA). */
    void release() {
        released = true;
        finish(wheel.release(System.currentTimeMillis(), lastX, lastY));
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        finish(wheel.click(x, y));
    }

    /** null: stay open; "": close; else close and play it. */
    private void finish(String id) {
        if (id == null) return;
        mc.displayGuiScreen(null);
        if (!id.isEmpty()) EmoteAnim189.play(id);
    }
}
