/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.component.DataComponentGetter
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.item.Item$TooltipContext
 *  net.minecraft.world.item.TooltipFlag
 *  net.minecraft.world.item.alchemy.PotionContents
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.ultimatchamp.enhancedtooltips.mixin;

import dev.ultimatchamp.enhancedtooltips.config.EnhancedTooltipsConfig;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={PotionContents.class})
public class PotionContentsMixin {
    @Inject(method={"addToTooltip"}, at={@At(value="INVOKE", target="Lnet/minecraft/world/item/alchemy/PotionContents;addPotionTooltip(Ljava/lang/Iterable;Ljava/util/function/Consumer;FF)V")}, cancellable=true)
    private void enhancedTooltips$cancelEffectTooltip(Item.TooltipContext context, Consumer<Component> textConsumer, TooltipFlag type, DataComponentGetter components, CallbackInfo ci) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("EnhancedTooltips").isEnabled() &&
            !com.thelads.core.config.ModuleManager.getInstance().getModule("EnhancedToolbars").isEnabled()) {
            return;
        }
        if (EnhancedTooltipsConfig.load().foodAndDrinks.effectsTooltip == EnhancedTooltipsConfig.EffectsTooltipMode.WITH_ICONS) {
            ci.cancel();
        }
    }
}

