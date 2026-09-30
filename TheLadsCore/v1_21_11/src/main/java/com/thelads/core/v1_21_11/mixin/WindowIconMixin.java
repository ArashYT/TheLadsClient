package com.thelads.core.v1_21_11.mixin;

import com.mojang.blaze3d.platform.IconSet;
import com.thelads.core.client.WindowIcons;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The game window and taskbar show The Lads artwork instead of the grass block; macOS keeps vanilla's .icns. */
@Mixin(IconSet.class)
public class WindowIconMixin {
    @Inject(method = "getStandardIcons", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsWindowIcons(PackResources resources, CallbackInfoReturnable<List<IoSupplier<InputStream>>> cir) {
        if (!WindowIcons.available()) return;
        cir.setReturnValue(Arrays.stream(WindowIcons.SIZES).<IoSupplier<InputStream>>mapToObj(size -> () -> WindowIcons.open(size)).toList());
        WindowIcons.logApplied();
    }
}
