package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.accessor;

import net.minecraft.client.DeltaTracker;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {
    @Accessor
    DeltaTracker.Timer getTimer();
}
