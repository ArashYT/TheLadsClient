package com.thelads.core.v1_20_1.game.misc.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The creative screen's selected tab (a private static field; Mouse Tweaks acts on the inventory tab only). */
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeTabAccessor1201 {
    @Accessor("selectedTab")
    static CreativeModeTab lads$selectedTab() { throw new AssertionError(); }
}
