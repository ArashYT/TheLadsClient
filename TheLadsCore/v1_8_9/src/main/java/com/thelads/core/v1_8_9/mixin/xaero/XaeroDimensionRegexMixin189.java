package com.thelads.core.v1_8_9.mixin.xaero;

import java.util.regex.Pattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Xaero's in-world waypoint renderer asks for the dimension's directory number every frame, and the answer compiled the regex
 * "-{0,1}[0-9]+" each time (String.matches): ~2.6% of the game thread with Ionium (1.9.12 profile). Same check, compiled once.
 */
@Pseudo
@Mixin(targets = "xaero.common.minimap.waypoints.WaypointsManager", remap = false)
public abstract class XaeroDimensionRegexMixin189 {
    private static final Pattern LADS_NUMBER = Pattern.compile("-{0,1}[0-9]+");

    @Redirect(method = "getDimensionForDirectoryName", at = @At(value = "INVOKE", target = "Ljava/lang/String;matches(Ljava/lang/String;)Z"),
        require = 0, remap = false)
    private boolean ladsPrecompiled(String text, String regex) {
        return "-{0,1}[0-9]+".equals(regex) ? LADS_NUMBER.matcher(text).matches() : text.matches(regex);
    }
}
