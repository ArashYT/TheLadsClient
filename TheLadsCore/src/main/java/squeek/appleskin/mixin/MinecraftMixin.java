/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package squeek.appleskin.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import squeek.appleskin.client.HUDOverlayHandler;

@Mixin(value={Minecraft.class})
public class MinecraftMixin {
    @Inject(at={@At(value="HEAD")}, method={"tick"})
    void onTick(CallbackInfo info) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("AppleSkin").isEnabled()) {
            return;
        }
        if (HUDOverlayHandler.INSTANCE != null) {
            HUDOverlayHandler.INSTANCE.onClientTick();
        }
    }
}

