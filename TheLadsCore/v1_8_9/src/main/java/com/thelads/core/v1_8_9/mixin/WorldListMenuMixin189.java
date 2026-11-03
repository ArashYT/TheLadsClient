package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.ServerListEntryUi;
import com.thelads.core.client.gui.ListContextMenu;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.GuiSlot;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right-click menu of the world list. Right click selects the world as a click does, so vanilla's footer buttons apply; each item presses that
 * button, with its confirm screens (1.8.9 disables nothing per world). Open folder and Copy name are the two with no button.
 */
@Mixin(GuiSelectWorld.class)
public abstract class WorldListMenuMixin189 extends GuiScreen {
    @Shadow private int selectedIndex;
    @Shadow private GuiButton deleteButton;
    @Shadow private GuiButton selectButton;
    @Shadow private GuiButton renameButton;
    @Shadow private GuiButton recreateButton;
    @Shadow protected abstract String func_146621_a(int world);
    @Shadow protected abstract String func_146614_d(int world);
    @Unique private final ListContextMenu ladsMenu = new ListContextMenu();

    /** The list's private inner class can't be named by an accessor; a right click is rare enough to look it up. */
    @Unique
    private GuiSlot lads$list() {
        try {
            for (java.lang.reflect.Field field : GuiSelectWorld.class.getDeclaredFields()) {
                if (GuiSlot.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (GuiSlot) field.get(this);
                }
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
        throw new IllegalStateException("GuiSelectWorld has no list");
    }

    /** A resize rebuilds the rows: the menu goes with them. */
    @Inject(method = "initGui", at = @At("TAIL"))
    private void ladsCloseMenu(CallbackInfo ci) {
        ladsMenu.close();
    }

    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException {
        if (ladsMenu.mouseClicked(x, y)) return;
        if (button == 1 && lads$list().isMouseYWithinSlotBounds(y)) {
            int world = lads$list().getSlotIndexFromScreenCoords(x, y);
            if (world >= 0) {
                selectedIndex = world;
                selectButton.enabled = deleteButton.enabled = renameButton.enabled = recreateButton.enabled = true;
                ladsMenu.open(x, y, ServerListEntryUi.worldMenu(selectButton.enabled, renameButton.enabled, deleteButton.enabled, recreateButton.enabled), id -> lads$pick(id, world));
                return;
            }
        }
        super.mouseClicked(x, y, button);
    }

    @Unique
    private void lads$pick(String id, int world) {
        try {
            switch (id) {
                case "play": actionPerformed(selectButton); break;
                case "edit": actionPerformed(renameButton); break;
                case "delete": actionPerformed(deleteButton); break;
                case "recreate": actionPerformed(recreateButton); break;
                case "folder": Desktop.getDesktop().open(new File(new File(mc.mcDataDir, "saves"), func_146621_a(world))); break;
                case "copy": setClipboardString(func_146614_d(world)); break;
                default: break;
            }
        } catch (IOException failure) {
            org.apache.logging.log4j.LogManager.getLogger("TheLadsCore").warn("World menu: {} failed", id, failure);
        }
    }

    @Override
    protected void keyTyped(char typed, int key) throws IOException {
        if (ladsMenu.isOpen()) {
            if (key == Keyboard.KEY_ESCAPE) ladsMenu.close();
            return;
        }
        super.keyTyped(typed, key);
    }

    /** Nothing under the open menu takes hover, and QA draws its own mouse. */
    @ModifyVariable(method = "drawScreen", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ladsMouseX(int mouseX) {
        return ladsMenu.isOpen() ? -1 : ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseX : mouseX;
    }

    @ModifyVariable(method = "drawScreen", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int ladsMouseY(int mouseY) {
        return ladsMenu.isOpen() ? -1 : ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseY : mouseY;
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void ladsDrawMenu(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        // The handlers above hid the mouse from the rows; the menu reads the real one.
        int x = ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseX : org.lwjgl.input.Mouse.getX() * width / mc.displayWidth;
        int y = ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseY : height - org.lwjgl.input.Mouse.getY() * height / mc.displayHeight - 1;
        ladsMenu.render(new GuiLadsAdapter(fontRendererObj, width, height), x, y);
    }
}
