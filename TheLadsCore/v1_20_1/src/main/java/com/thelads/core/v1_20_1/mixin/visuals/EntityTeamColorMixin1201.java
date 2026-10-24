package com.thelads.core.v1_20_1.mixin.visuals;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thelads.core.modules.MobOverlayModule;
import com.thelads.core.v1_20_1.feature.PvpVisuals1201;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob Overlay: the glowing outline takes its color from getTeamColor; an outlined mob or bullet with no team color gets its kind's color. */
@Mixin(Entity.class)
public abstract class EntityTeamColorMixin1201 {
    @ModifyReturnValue(method = "getTeamColor()I", at = @At("RETURN"))
    private int ladsMobOverlayColor(int color) {
        if (color != 0xFFFFFF || !MobOverlayModule.active()) return color;
        int kind = PvpVisuals1201.kind((Entity) (Object) this);
        return MobOverlayModule.outlined(kind) ? MobOverlayModule.color(kind) : color;
    }
}
