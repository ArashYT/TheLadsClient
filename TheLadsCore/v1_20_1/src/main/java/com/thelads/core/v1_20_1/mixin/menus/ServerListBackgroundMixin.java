package com.thelads.core.v1_20_1.mixin.menus;

import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes the dirt texture (and the dirt bars above and below it) behind the server entries on the multiplayer screen. */
@Mixin(ServerSelectionList.class)
public abstract class ServerListBackgroundMixin {
    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void ladsNoServerListBackground(CallbackInfo ci) {
        AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
        list.setRenderBackground(false);
        list.setRenderTopAndBottom(false);
    }
}
