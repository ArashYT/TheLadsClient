package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.client.ServerListColumns;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.navigation.ScreenDirection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In columns the server list (ServerListDragMixin1201) answers the list's final, row-only hit test, for clicks and hover alike, and arrow keys
 * move in the grid. Here rather than as an override in that mixin: Mixin does not apply the bridge methods that generic E overrides compile to.
 */
@Mixin(AbstractSelectionList.class)
abstract class ServerListRowsMixin {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "getEntryAtPosition", at = @At("HEAD"), cancellable = true)
    private void ladsColumnsHit(double x, double y, CallbackInfoReturnable cir) {
        if ((Object) this instanceof ServerListColumns.Hits rows && rows.lads$columns() > 1) cir.setReturnValue(rows.lads$rowAt(x, y));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "nextEntry(Lnet/minecraft/client/gui/navigation/ScreenDirection;Ljava/util/function/Predicate;Lnet/minecraft/client/gui/components/AbstractSelectionList$Entry;)Lnet/minecraft/client/gui/components/AbstractSelectionList$Entry;", at = @At("HEAD"), cancellable = true)
    private void ladsGridArrows(ScreenDirection direction, Predicate canSelect, @Coerce Object start, CallbackInfoReturnable cir) {
        if (start == null || !((Object) this instanceof ServerListColumns.Hits list) || list.lads$columns() == 1) return;
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
