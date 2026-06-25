package com.thelads.core.mixin.auto.modernadvancementsscreen1901262;

import net.minecraft.client.gui.screens.advancements.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementToast.class)
public interface AdvancementToastAccessor {
    @Accessor("icon")
    void setIcon(net.minecraft.world.item.ItemStack icon);
}
