package com.thelads.core.v1_8_9.mixin;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.thelads.core.v1_8_9.feature.Options189;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** EnhancedToolbars, Show Item Attributes off: the tooltip sees no attribute modifiers (the item keeps them), as 26.x ItemTooltipMixin. */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
    @Redirect(method = "getTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getAttributeModifiers()Lcom/google/common/collect/Multimap;"), require = 1)
    private Multimap<String, AttributeModifier> ladsTooltipAttributes(ItemStack stack) {
        return Options189.enabled("EnhancedToolbars") && !Options189.bool("EnhancedToolbars", "Show Item Attributes", true)
            ? ImmutableMultimap.<String, AttributeModifier>of() : stack.getAttributeModifiers();
    }
}
