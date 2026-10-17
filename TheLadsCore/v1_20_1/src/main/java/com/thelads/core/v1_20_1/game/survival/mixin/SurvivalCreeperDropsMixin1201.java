package com.thelads.core.v1_20_1.game.survival.mixin;

import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v1_20_1.game.survival.NativeSurvivalTweaks1201;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** SurvivalTweaks: 100% creeper block drops. The creeper blast becomes explosion-interaction TNT, so every destroyed block drops. */
@Mixin(Creeper.class)
public abstract class SurvivalCreeperDropsMixin1201 {
    @ModifyArg(
            method = "explodeCreeper",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"
            ),
            index = 5
    )
    private Level.ExplosionInteraction lads$creeperExplosionDropsAll(Level.ExplosionInteraction original) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (NativeSurvivalTweaks1201.active() && mod != null && mod.isCreeperFullDropsEnabled()
                && original == Level.ExplosionInteraction.MOB) {
            return Level.ExplosionInteraction.TNT;
        }
        return original;
    }
}
