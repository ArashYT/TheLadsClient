package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientLevel.class)
public interface ClientLevelDataAccessor {
    @Accessor("levelData")
    void setLevelData(net.minecraft.world.level.LevelData levelData);
}
