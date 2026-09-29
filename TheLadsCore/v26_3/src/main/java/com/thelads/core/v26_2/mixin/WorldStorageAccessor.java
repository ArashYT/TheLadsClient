package com.thelads.core.v26_2.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface WorldStorageAccessor {
    @Mutable @Accessor("levelSource") void ladsSetLevelSource(LevelStorageSource source);
}
