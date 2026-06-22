package com.thelads.core.mixin.client;

import com.thelads.core.config.ModuleManager;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import net.minecraft.world.phys.AABB;

@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin extends net.minecraft.world.entity.Entity {

    public ExperienceOrbMixin(net.minecraft.world.entity.EntityType<?> entityType, net.minecraft.world.level.Level level) {
        super(entityType, level);
    }

    @Shadow public int value;
    @Shadow public int age;
    @Shadow private int count;

    @Inject(method = "tick", at = @At("TAIL"))
    private void ladsClumpOrbs(CallbackInfo ci) {
        if (!this.level().isClientSide()) return;
        com.thelads.core.config.Module m = ModuleManager.getInstance().getModule("Clumps");
        if (m == null || !m.isEnabled()) return;

        if (this.isRemoved()) return;

        AABB box = this.getBoundingBox().inflate(0.5);
        List<ExperienceOrb> others = this.level().getEntitiesOfClass(ExperienceOrb.class, box);
        for (ExperienceOrb other : others) {
            if (other != (Object)this && !other.isRemoved() && ((ExperienceOrbMixin)(Object)other).value == this.value) {
                this.count += ((ExperienceOrbMixin)(Object)other).count;
                other.discard();
            }
        }
    }
}

