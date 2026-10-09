package com.thelads.core.v1_20_1.game.mixin;

import net.minecraft.world.level.LevelSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelSettings.class)
public interface LevelSettingsAccessor {
    /** The saved switch itself, not what allowCommands() answers. */
    @Accessor("allowCommands")
    boolean ladsAllowCommands();
}
