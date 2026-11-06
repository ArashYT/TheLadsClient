package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.ServerListColumns;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.client.ServerListEntryUi;
import com.thelads.core.client.WorldListOrder;
import com.thelads.core.client.gui.ListContextMenu;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import com.thelads.core.v1_8_9.feature.WorldSlotDraw189;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.world.storage.SaveFormatComparator;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Singleplayer world list on 1.8.9: side-by-side columns, hold-and-drag reordering saved to thelads_world_order.json,
 * grid arrow navigation, and the right-click context menu.
 */
@Mixin(GuiSelectWorld.class)
public abstract class WorldListMenuMixin189 extends GuiScreen {
    @Shadow private int selectedIndex;
    @Shadow private List<SaveFormatComparator> field_146639_s;
    @Shadow private GuiButton deleteButton;
    @Shadow private GuiButton selectButton;
    @Shadow private GuiButton renameButton;
    @Shadow private GuiButton recreateButton;
    @Shadow protected abstract String func_146621_a(int world);
    @Shadow protected abstract String func_146614_d(int world);
    @Shadow public abstract void func_146615_e(int world);

    @Unique private final ListContextMenu ladsMenu = new ListContextMenu();
    @Unique private GuiButton ladsColumnsButton;
    @Unique private boolean ladsPressTaken;
    @Unique private int lads$pressX, lads$pressY, lads$grabX, lads$grabY, lads$from = -1, lads$to = -1;
    @Unique private boolean lads$pressed;
    @Unique private long lads$lastClickMs;
    @Unique private int lads$lastClickSlot = -1;

    @Inject(method = "loadLevelList", at = @At("TAIL"))
    private void lads$sortLoadedWorlds(CallbackInfo ci) {
        if (field_146639_s != null) {
            field_146639_s = new ArrayList<SaveFormatComparator>(
                WorldListOrder.sortedCopy(field_146639_s, SaveFormatComparator::getFileName, SaveFormatComparator::getLastTimePlayed)
            );
        }
    }

    /** handleMouseInput hands the list every mouse event after the screen's: not the press the menu took, or a row under it would be selected. */
    @Inject(method = "handleMouseInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleMouseInput()V", shift = At.Shift.AFTER), cancellable = true)
    private void ladsNoClickThrough(CallbackInfo ci) {
        if (!ladsPressTaken) return;
        ladsPressTaken = Mouse.isButtonDown(0);
        ci.cancel();
    }

    /** While a world row is actively dragged, suppress GuiSlot's click-drag scrolling while keeping mouse wheel scrolling. */
    @Inject(method = "handleMouseInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiSelectWorld$List;handleMouseInput()V"), cancellable = true) // GuiSelectWorld$List is package-private: a @Redirect handler could not name it
    private void lads$worldListMouseInput(CallbackInfo ci) {
        if (lads$from < 0) return;
        GuiSlot list = lads$list();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) list.scrollBy(wheel > 0 ? -list.slotHeight / 2 : list.slotHeight / 2);
        ci.cancel();
    }

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

    @Inject(method = "initGui", at = @At("TAIL"))
    private void ladsCloseMenu(CallbackInfo ci) {
        ladsMenu.close();
        lads$setFrom(-1);
        lads$pressed = false;
        ladsColumnsButton = new GuiButton(0x4C414453 + 22, 8, 8, 36, 20, "");
        buttonList.add(ladsColumnsButton);
        ladsUpdateColumns();
    }

    @Unique
    private void ladsUpdateColumns() {
        if (ladsColumnsButton == null) return;
        ladsColumnsButton.visible = ServerListColumns.most(width) > 1;
        ladsColumnsButton.displayString = "    " + ServerListColumns.fit(ServerListColumns.wanted(), width);
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void ladsColumnsAction(GuiButton button, CallbackInfo ci) {
        if (button != null && button == ladsColumnsButton) {
            ServerListColumns.cycle(width);
            ladsUpdateColumns();
            ci.cancel();
        }
    }

    @Unique
    private void lads$selectWorld(int slot) {
        selectedIndex = slot;
        boolean valid = field_146639_s != null && slot >= 0 && slot < field_146639_s.size();
        selectButton.enabled = deleteButton.enabled = renameButton.enabled = recreateButton.enabled = valid;
    }

    @Unique
    private void lads$setFrom(int from) {
        lads$from = lads$to = from;
        GuiSlot list = lads$list();
        if (list instanceof ServerListColumns.Gap) {
            ((ServerListColumns.Gap) list).lads$gap(from, from);
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException {
        if (ladsMenu.mouseClicked(x, y)) {
            ladsPressTaken = true;
            return;
        }
        GuiSlot list = lads$list();
        if (button == 1 && list.isMouseYWithinSlotBounds(y)) {
            int world = list.getSlotIndexFromScreenCoords(x, y);
            if (world >= 0) {
                lads$selectWorld(world);
                ladsMenu.open(x, y, ServerListEntryUi.worldMenu(selectButton.enabled, renameButton.enabled, deleteButton.enabled, recreateButton.enabled), id -> lads$pick(id, world));
                return;
            }
        }
        super.mouseClicked(x, y, button);
        if (button == 0 && list.isMouseYWithinSlotBounds(y)) {
            int slot = list.getSlotIndexFromScreenCoords(x, y);
            if (slot >= 0 && field_146639_s != null && slot < field_146639_s.size()) {
                long now = Minecraft.getSystemTime();
                boolean doubleClick = slot == lads$lastClickSlot && now - lads$lastClickMs < 250L;
                lads$lastClickSlot = slot;
                lads$lastClickMs = now;
                lads$selectWorld(slot);
                if (doubleClick) {
                    lads$pressed = false;
                    lads$setFrom(-1);
                    func_146615_e(slot);
                    return;
                }
                lads$pressed = WorldListOrder.canDrag("");
                lads$pressX = x;
                lads$pressY = y;
                ServerListColumns.Rows rows = (ServerListColumns.Rows) list;
                lads$grabX = x - rows.lads$rowLeft(slot);
                lads$grabY = y - rows.lads$rowTop(slot);
                lads$setFrom(-1);
            } else {
                lads$pressed = false;
                lads$setFrom(-1);
            }
        } else if (button == 0) {
            lads$pressed = false;
            lads$setFrom(-1);
        }
    }

    @Override
    protected void mouseReleased(int x, int y, int state) {
        super.mouseReleased(x, y, state);
        if (state == 0) {
            lads$drop();
        }
    }

    @Unique
    private void lads$drop() {
        int from = lads$from, to = lads$to;
        lads$setFrom(-1);
        lads$pressed = false;
        if (field_146639_s == null || to < 0 || to == from || from >= field_146639_s.size() || to >= field_146639_s.size()) {
            return;
        }
        ServerListDrag.<SaveFormatComparator>move(from, to, field_146639_s::get, field_146639_s::set);
        List<String> ids = new ArrayList<String>(field_146639_s.size());
        for (SaveFormatComparator w : field_146639_s) {
            ids.add(w.getFileName());
        }
        WorldListOrder.saveOrder(ids);
        lads$selectWorld(to);
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
        } catch (IOException | RuntimeException failure) {
            org.apache.logging.log4j.LogManager.getLogger("TheLadsCore").warn("World menu: {} failed", id, failure);
        }
    }

    @Override
    protected void keyTyped(char typed, int key) throws IOException {
        if (ladsMenu.isOpen()) {
            if (key == Keyboard.KEY_ESCAPE) ladsMenu.close();
            return;
        }
        if (selectedIndex >= 0 && !isShiftKeyDown()) {
            int columns = ServerListColumns.fit(ServerListColumns.wanted(), width);
            int dx = key == Keyboard.KEY_LEFT ? -1 : key == Keyboard.KEY_RIGHT ? 1 : 0;
            int dy = key == Keyboard.KEY_UP ? -1 : key == Keyboard.KEY_DOWN ? 1 : 0;
            if (columns > 1 && (dx != 0 || dy != 0)) {
                GuiSlot list = lads$list();
                int next = ServerListColumns.next(selectedIndex, ((ServerListColumns.Gap) list).lads$size(), columns, dx, dy);
                if (next >= 0) {
                    lads$selectWorld(next);
                    int top = ((ServerListColumns.Rows) list).lads$rowTop(next);
                    if (top < list.top + 4) list.scrollBy(top - list.top - 4);
                    else if (top + list.slotHeight > list.bottom) list.scrollBy(top + list.slotHeight - list.bottom);
                }
                return;
            }
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
        GuiLadsAdapter adapter = new GuiLadsAdapter(fontRendererObj, width, height);
        if (ladsColumnsButton != null && ladsColumnsButton.visible) {
            TitleScreenTheme.icon(adapter, "columns", ladsColumnsButton.xPosition + 6, ladsColumnsButton.yPosition + 5, 10, 0xFFFFFFFF);
        }
        if (lads$pressed) {
            if (!ServerListDrag.heldForQa && !Mouse.isButtonDown(0)) {
                lads$drop();
            } else if (field_146639_s != null && !field_146639_s.isEmpty()) {
                GuiSlot list = lads$list();
                int count = field_146639_s.size();
                if (lads$from < 0 && ServerListDrag.dragging(lads$pressX, lads$pressY, mouseX, mouseY)) {
                    int at = list.getSlotIndexFromScreenCoords(lads$pressX, lads$pressY);
                    lads$setFrom(at >= 0 && at < count ? at : -1);
                    lads$pressed = lads$from >= 0;
                }
                if (lads$from >= 0) {
                    if (mouseY < list.top + ServerListDrag.SCROLL_EDGE) list.scrollBy(-ServerListDrag.SCROLL_STEP);
                    else if (mouseY > list.bottom - ServerListDrag.SCROLL_EDGE) list.scrollBy(ServerListDrag.SCROLL_STEP);
                    ServerListColumns.Rows rows = (ServerListColumns.Rows) list;
                    ((ServerListColumns.Gap) list).lads$gap(-1, -1);
                    int slot = list.getSlotIndexFromScreenCoords(mouseX, mouseY);
                    lads$to = ServerListDrag.target(slot, lads$to, count);
                    ((ServerListColumns.Gap) list).lads$gap(lads$from, lads$to);
                    int colWidth = list.getListWidth(), height = list.slotHeight;
                    int gapLeft = rows.lads$rowLeft(lads$from) - 2, gapTop = rows.lads$rowTop(lads$from);
                    GlStateManager.disableAlpha();
                    if (gapTop + height > list.top && gapTop < list.bottom) {
                        Gui.drawRect(gapLeft, Math.max(gapTop - 2, list.top), gapLeft + colWidth, Math.min(gapTop - 2 + height, list.bottom), ServerListDrag.GAP_FILL);
                    }
                    GlStateManager.enableAlpha();
                    int gx = mouseX - lads$grabX, gy = mouseY - lads$grabY;
                    Gui.drawRect(gx - 2, gy - 2, gx - 2 + colWidth, gy - 2 + height, ServerListDrag.GHOST_BACK);
                    GlStateManager.enableBlend();
                    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                    ServerListDrag.ghost(true);
                    ((WorldSlotDraw189) list).lads$drawWorldSlot(lads$from, gx, gy, height - 4, -1000, -1000);
                    ServerListDrag.ghost(false);
                    GlStateManager.color(1, 1, 1, 1);
                    GlStateManager.disableBlend();
                }
            }
        }
        int x = ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseX : Mouse.getX() * width / mc.displayWidth;
        int y = ServerListDrag.qaMouseX >= 0 ? ServerListDrag.qaMouseY : height - Mouse.getY() * height / mc.displayHeight - 1;
        ladsMenu.render(adapter, x, y);
    }
}
