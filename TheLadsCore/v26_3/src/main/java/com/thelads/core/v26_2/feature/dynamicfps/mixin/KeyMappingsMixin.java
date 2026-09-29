// Derived from Dynamic FPS 3.11.9 (MIT); see licenses/DynamicFPS-LICENSE.txt.
package com.thelads.core.v26_2.feature.dynamicfps.mixin;
import com.thelads.core.v26_2.feature.dynamicfps.util.KeyMappingHandler;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Options.class)
public abstract class KeyMappingsMixin {
    @Shadow @Final @Mutable public KeyMapping[] keyMappings;
    @Inject(method = "load", at = @At("HEAD"))
    private void lads$backgroundKeyBindings(CallbackInfo callback) {
        var mappings = new ArrayList<>(Arrays.asList(keyMappings));
        for (var handler : KeyMappingHandler.getHandlers()) {
            if (!mappings.contains(handler.keyMapping())) mappings.add(handler.keyMapping());
        }
        keyMappings = mappings.toArray(KeyMapping[]::new);
    }
}
