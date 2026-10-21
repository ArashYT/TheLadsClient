package com.thelads.core.v26_2.mixin;

import com.thelads.core.modules.CloudHeightModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
    @ModifyVariable(
        method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0
    )
    private float ladsCloudHeight(float cloudHeight) {
        if (Float.isNaN(cloudHeight)) return cloudHeight;
        int maxHeight = 320;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            maxHeight = mc.level.getMaxY();
        }
        return CloudHeightModule.getTargetCloudHeight(cloudHeight, maxHeight);
    }
}
