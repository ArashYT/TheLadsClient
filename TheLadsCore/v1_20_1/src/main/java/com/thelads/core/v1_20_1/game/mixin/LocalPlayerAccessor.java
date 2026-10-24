package com.thelads.core.v1_20_1.game.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
    /** The sprint state the client last told the server (a change is one START_SPRINTING or STOP_SPRINTING packet). */
    @Accessor("wasSprinting")
    boolean ladsSentSprint();

    @Accessor("sprintTriggerTime")
    void ladsSetSprintTriggerTime(int time);
}
