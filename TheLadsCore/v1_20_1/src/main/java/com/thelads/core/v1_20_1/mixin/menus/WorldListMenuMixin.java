package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.ServerListEntryUi;
import com.thelads.core.client.gui.ListContextMenu;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Right-click menu of the world list. Right click selects the world, so vanilla's footer buttons then say what is allowed (locked and
 * unsupported worlds); each item presses that button, with its confirm screens. Open folder and Copy name are the two with no button.
 */
@Mixin(SelectWorldScreen.class)
public abstract class WorldListMenuMixin extends Screen {
    @Shadow private Button deleteButton;
    @Shadow private Button selectButton;
    @Shadow private Button renameButton;
    @Shadow private Button copyButton;
    @Shadow private WorldSelectionList list;
    @Unique private final ListContextMenu ladsMenu = new ListContextMenu();

    protected WorldListMenuMixin() {
        super(Component.empty());
    }

    /** A resize rebuilds the rows: the menu goes with them. */
    @Inject(method = "init()V", at = @At("TAIL"))
    private void ladsCloseMenu(CallbackInfo ci) {
        ladsMenu.close();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (ladsMenu.mouseClicked(x, y)) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && list != null && list.isMouseOver(x, y)
            && list.getChildAt(x, y).orElse(null) instanceof WorldSelectionList.WorldListEntry entry) {
            list.setSelected(entry);
            ladsMenu.open((int) x, (int) y, ServerListEntryUi.worldMenu(selectButton.active, renameButton.active, deleteButton.active, copyButton.active), id -> ladsPick(id, entry));
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Unique
    private void ladsPick(String id, WorldSelectionList.WorldListEntry entry) {
        switch (id) {
            case "play" -> selectButton.onPress();
            case "edit" -> renameButton.onPress();
            case "delete" -> deleteButton.onPress();
            case "recreate" -> copyButton.onPress();
            case "folder" -> Util.getPlatform().openFile(minecraft.getLevelSource().getBaseDir().resolve(((WorldListEntryAccessor) (Object) entry).ladsSummary().getLevelId()).toFile());
            case "copy" -> minecraft.keyboardHandler.setClipboard(entry.getLevelName());
            default -> {}
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void ladsMenuKeys(int key, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!ladsMenu.isOpen()) return;
        if (key == GLFW.GLFW_KEY_ESCAPE) ladsMenu.close();
        cir.setReturnValue(true);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scroll) {
        ladsMenu.close();
        return super.mouseScrolled(x, y, scroll);
    }

    /** Nothing under the open menu takes hover, and QA draws its own mouse. */
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ladsMouseX(int mouseX) {
        return ladsMenu.isOpen() ? -1 : ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseX : mouseX;
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int ladsMouseY(int mouseY) {
        return ladsMenu.isOpen() ? -1 : ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseY : mouseY;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ladsDrawMenu(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        // The handlers above hid the mouse from the rows; the menu reads the real one.
        double scale = minecraft.getWindow().getGuiScale();
        int x = ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseX : (int) (minecraft.mouseHandler.xpos() / scale);
        int y = ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseY : (int) (minecraft.mouseHandler.ypos() / scale);
        ladsMenu.render(new GuiGraphicsLadsAdapter1201(graphics, font), x, y);
    }
}
