package com.thelads.core.v26_2.embedded.emf.mixin.mixins.accessor;

import net.minecraft.client.DeltaTracker;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {
    @Accessor
    DeltaTracker.Timer getDeltaTracker();
}
