package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.OldAnimations189;
import com.thelads.core.v1_8_9.feature.OldAnimations189.Hook;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 1.7 Animations, No heart flashing. Forge draws the health bar in GuiIngameForge.renderHealth (OptiFine leaves it alone); its one
 * boolean local, "highlight", is the blink after damage (the lighter outline and the lost hearts). A Forge class: no remap.
 */
@Mixin(value = GuiIngameForge.class, remap = false)
public abstract class GuiIngameForgeMixin {
    @ModifyVariable(method = "renderHealth", at = @At("STORE"), ordinal = 0, require = 1, allow = 1)
    private boolean ladsHeartsBlink(boolean highlight) {
        boolean shown = OldAnimations189.MODULE.heartsBlink(OldAnimations189.PLATFORM, highlight);
        if (shown != highlight) OldAnimations189.hit(Hook.HEARTS);
        return shown;
    }
}
