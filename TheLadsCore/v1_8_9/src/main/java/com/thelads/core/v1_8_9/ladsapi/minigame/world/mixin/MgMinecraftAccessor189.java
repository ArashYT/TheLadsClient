package com.thelads.core.v1_8_9.ladsapi.minigame.world.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The view entity swapped for the camera shot without Minecraft.setRenderViewEntity, which would reload the entity shader every frame. */
@Mixin(Minecraft.class)
public interface MgMinecraftAccessor189 {
    @Accessor("renderViewEntity") Entity ladsMgView();
    @Accessor("renderViewEntity") void ladsMgSetView(Entity view);
}
