package com.thelads.core.mixin.auto.modernadvancementsscreen1901262;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({AdvancementToast.class})
public interface AdvancementToastAccessor {
   @Accessor("advancement")
   AdvancementHolder getAdvancement();
}
