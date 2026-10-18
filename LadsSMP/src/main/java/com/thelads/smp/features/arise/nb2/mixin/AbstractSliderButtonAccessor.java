package com.thelads.smp.features.arise.nb2.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor for AbstractSliderButton protected fields and methods.
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractSliderButton.class)
public interface AbstractSliderButtonAccessor {
    @Accessor("value")
    double getValue();

    @Invoker("setValue")
    void invokeSetValue(double value);

    @Invoker("applyValue")
    void invokeApplyValue();

    @Invoker("updateMessage")
    void invokeUpdateMessage();
}
