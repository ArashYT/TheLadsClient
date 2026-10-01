package com.thelads.core.v26_2.embedded.etf.mixin.mixins.accessor;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Tooltip.class)
public interface TooltipAccessor {
    @Accessor
    void setCachedTooltip(List<FormattedCharSequence> lines);
}
