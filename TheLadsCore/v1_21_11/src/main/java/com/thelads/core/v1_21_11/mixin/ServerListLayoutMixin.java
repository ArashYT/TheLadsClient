package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.client.ServerListColumns;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.navigation.ScreenDirection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What the server list (ServerListColumns.Layout) changes in the shared list: vanilla stacks a list's rows on every scroll and resize (a
 * private method) and the server list then puts its own in their slots; the dragged row is not drawn in its gap; arrow keys move in the grid.
 * Here rather than as overrides in the server list's mixin, because these take the row as the generic E and Mixin does not apply the bridge
 * methods such overrides compile to.
 */
@Mixin(AbstractSelectionList.class)
abstract class ServerListLayoutMixin {
    @Inject(method = "repositionEntries", at = @At("TAIL"))
    private void ladsServerListSlots(CallbackInfo ci) {
        if ((Object) this instanceof ServerListColumns.Layout list) list.lads$layout();
    }

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void ladsHideDragged(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, @Coerce Object entry, CallbackInfo ci) {
        if ((Object) this instanceof ServerListColumns.Layout list && list.lads$hidden(entry)) ci.cancel();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "nextEntry(Lnet/minecraft/client/gui/navigation/ScreenDirection;Ljava/util/function/Predicate;Lnet/minecraft/client/gui/components/AbstractSelectionList$Entry;)Lnet/minecraft/client/gui/components/AbstractSelectionList$Entry;", at = @At("HEAD"), cancellable = true)
    private void ladsGridArrows(ScreenDirection direction, Predicate canSelect, @Coerce Object start, CallbackInfoReturnable cir) {
        if (start == null || !((Object) this instanceof ServerListColumns.Layout list) || list.lads$columns() == 1) return;
        int dx = direction == ScreenDirection.LEFT ? -1 : direction == ScreenDirection.RIGHT ? 1 : 0;
        int dy = direction == ScreenDirection.UP ? -1 : direction == ScreenDirection.DOWN ? 1 : 0;
        List rows = ((AbstractSelectionList) (Object) this).children();
        Object found = null;
        for (int i = rows.indexOf(start); (i = ServerListColumns.next(i, rows.size(), list.lads$columns(), dx, dy)) >= 0 && found == null; ) {
            if (canSelect.test(rows.get(i))) found = rows.get(i);
        }
        cir.setReturnValue(found);
    }
}
