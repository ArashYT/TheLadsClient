package com.thelads.core.v1_20_1.game.mixin;

import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PrimaryLevelData.class)
public interface PrimaryLevelDataAccessor {
    /** The world's own settings: getAllowCommands answers from them, unless another mod (Essential) answers differently. */
    @Accessor("settings")
    LevelSettings ladsSettings();

    @Accessor("settings")
    @Mutable
    void ladsSettings(LevelSettings settings);
}
