package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Toggle Sprint &amp; Sneak (Toggles189) and QA (Probe170Sprint): the sprint state the client last sent the server (its START/STOP_SPRINTING packets). */
@Mixin(EntityPlayerSP.class)
public interface EntityPlayerSPAccessor {
    @Accessor("serverSprintState") boolean ladsSentSprint();
}
