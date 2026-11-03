package com.thelads.core.v26_2.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.ServerListEntryUi;
import com.thelads.core.client.gui.ListContextMenu;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right-click menu of the world list. Right click selects the world, so vanilla's footer buttons then say what is allowed (locked and
 * unsupported worlds); each item presses that button, with its confirm screens. Open folder and Copy name are the two with no button.
 */
@Mixin(SelectWorldScreen.class)
public abstract class WorldListMenuMixin extends Screen {
    @Shadow private Button deleteButton;
    @Shadow private Button playWorldButton;
    @Shadow private Button editButton;
    @Shadow private Button recreateButton;
    @Shadow private WorldSelectionList list;
    @Unique private final ListContextMenu ladsMenu = new ListContextMenu();

    protected WorldListMenuMixin() {
        super(Component.empty());
    }

    /** A resize rebuilds the rows: the menu goes with them. */
    @Inject(method = "init", at = @At("TAIL"))
    private void ladsCloseMenu(CallbackInfo ci) {
        ladsMenu.close();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (ladsMenu.mouseClicked(event.x(), event.y())) return true;
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && list != null && list.isMouseOver(event.x(), event.y())
            && list.getChildAt(event.x(), event.y()).orElse(null) instanceof WorldSelectionList.WorldListEntry entry) {
            list.setSelected(entry);
            ladsMenu.open((int) event.x(), (int) event.y(),
                ServerListEntryUi.worldMenu(playWorldButton.active, editButton.active, deleteButton.active, recreateButton.active), id -> ladsPick(id, entry));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Unique
    private void ladsPick(String id, WorldSelectionList.WorldListEntry entry) {
        KeyEvent press = new KeyEvent(InputConstants.KEY_RETURN, 0, 0);
        switch (id) {
            case "play" -> playWorldButton.onPress(press);
            case "edit" -> editButton.onPress(press);
            case "delete" -> deleteButton.onPress(press);
            case "recreate" -> recreateButton.onPress(press);
            case "folder" -> net.minecraft.util.Util.getPlatform().openPath(minecraft.getLevelSource().getBaseDir().resolve(entry.getLevelSummary().getLevelId()));
            case "copy" -> minecraft.keyboardHandler.setClipboard(entry.getLevelName());
            default -> {}
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (ladsMenu.isOpen()) {
            if (event.isEscape()) ladsMenu.close();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        ladsMenu.close();
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (ServerListDrag.qaMouseX >= 0) {
            mouseX = ServerListDrag.qaMouseX;
            mouseY = ServerListDrag.qaMouseY;
        }
        boolean open = ladsMenu.isOpen();
        super.extractRenderState(graphics, open ? -1 : mouseX, open ? -1 : mouseY, partialTick);
        ladsMenu.render(new GuiGraphicsExtractorLadsAdapter(graphics, font), mouseX, mouseY);
    }
}
