package com.thelads.core.v1_20_1.game.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftTargetAccessor {
    /** Better Resolution: the world pass renders into its own target; the game's main target is put back before the HUD. */
    @Accessor("mainRenderTarget")
    @Mutable
    void ladsSetMainTarget(RenderTarget target);
}
