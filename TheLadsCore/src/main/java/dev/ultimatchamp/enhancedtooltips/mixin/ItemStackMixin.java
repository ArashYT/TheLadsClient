/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item$TooltipContext
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.TooltipFlag
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package dev.ultimatchamp.enhancedtooltips.mixin;

import dev.ultimatchamp.enhancedtooltips.tooltip.TooltipItemStackCache;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={ItemStack.class})
public abstract class ItemStackMixin {
    @Inject(method={"getTooltipLines"}, at={@At(value="RETURN", ordinal=1)})
    private void enhancedTooltips$getTooltip(Item.TooltipContext context, Player player, TooltipFlag type, CallbackInfoReturnable<List<Component>> cir) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("EnhancedTooltips").isEnabled() &&
            !com.thelads.core.config.ModuleManager.getInstance().getModule("EnhancedToolbars").isEnabled()) {
            return;
        }
        if (cir.getReturnValue() == null || ((List)cir.getReturnValue()).isEmpty()) {
            return;
        }
        TooltipItemStackCache.saveItemStack((ItemStack)(Object)this);
    }
}

