package com.thelads.core.v26_2.mixin;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Toggle Sprint & Sneak: the sprint state the client last sent the server (its START/STOP_SPRINTING packets). */
@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor { @Accessor("wasSprinting") boolean ladsSentSprint(); }
