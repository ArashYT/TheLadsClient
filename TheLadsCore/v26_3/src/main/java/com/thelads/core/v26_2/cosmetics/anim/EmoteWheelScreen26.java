package com.thelads.core.v26_2.cosmetics.anim;

import com.thelads.core.client.cosmetics.anim.EmoteWheel;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** The emote wheel (common EmoteWheel) while the Emotes key is held; the world keeps running and showing behind it. */
public final class EmoteWheelScreen26 extends Screen {
    private final EmoteWheel wheel = new EmoteWheel(System.currentTimeMillis());
    /** QA only (EmoteCapture26): where the pointer is taken to be. */
    static int[] qaPointer;
    private int mouseX, mouseY;
    private boolean released;

    public EmoteWheelScreen26() { super(Component.literal("Emotes")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (qaPointer != null) { mx = qaPointer[0]; my = qaPointer[1]; }
        mouseX = mx; mouseY = my;
        wheel.render(new GuiGraphicsExtractorLadsAdapter(g), mx, my, System.currentTimeMillis());
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (!EmoteAnim26.KEY.matches(event)) return super.keyReleased(event);
        released = true;
        finish(wheel.release(System.currentTimeMillis(), mouseX, mouseY));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!EmoteAnim26.KEY.matches(event)) return super.keyPressed(event);
        if (released) finish(""); // the key again closes a tapped-open wheel; repeats of the held key are ignored
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        finish(wheel.click(event.x(), event.y()));
        return true;
    }

    /** null: stay open; "": close; else close and play it. */
    void finish(String id) {
        if (id == null) return;
        onClose();
        if (!id.isEmpty()) EmoteAnim26.play(id);
    }
}
