package com.thelads.core.v26_2.qol.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

/**
 * JEI (31.14) starts itself when a container screen opens before the server sent its recipe-update packet, which our PicoLimbo
 * lobby never does. It then builds its fuel registry from the client's registries, which have no enchantments there
 * ("Missing element ... silk_touch"), and the throw out of Screen.init crashed the game (4 crash reports). Skip JEI on that
 * level instead; the next level gets a fresh try. No-op without JEI.
 */
@Pseudo
@Mixin(targets = "mezz.jei.fabric.startup.ClientLifecycleHandler", remap = false)
abstract class JeiStartGuardMixin26 {
    @Unique private static final Logger LADS_LOG = LoggerFactory.getLogger("Lads JEI guard");
    @Unique private static Object ladsFailedLevel;

    @WrapMethod(method = "startJei", require = 0)
    private void lads$guardStart(Operation<Void> original) {
        Object level = Minecraft.getInstance().level;
        if (level != null && level == ladsFailedLevel) return;
        try {
            original.call();
        } catch (RuntimeException e) {
            ladsFailedLevel = level;
            LADS_LOG.warn("JEI could not start on this server (no recipe sync); running without it here", e);
        }
    }
}
