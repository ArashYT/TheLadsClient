package com.thelads.core.v1_20_1.game.misc.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** QA only (MiscQa1201): where a container screen's slots are on the screen. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerAccess1201 {
    @Accessor("leftPos") int lads$leftPos();
    @Accessor("topPos") int lads$topPos();
}
