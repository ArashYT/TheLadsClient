package com.thelads.core.v26_2.feature.packs.mixin;

import com.thelads.core.perf.FastPackScreen;
import com.thelads.core.v26_2.feature.packs.PackIcons26;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pack icons decode off the render thread (see PackIcons26). Off with -Dlads.fastpackscreen=false. */
@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenIconMixin {
    @Shadow @Final private static Identifier DEFAULT_ICON;
    @Shadow @Final private Map<String, Identifier> packIcons;
    @Shadow @Final private PackSelectionModel model;

    @Unique private final PackIcons26 lads$icons = new PackIcons26();
    @Unique private boolean lads$logged;

    @Inject(method = "getPackIcon", at = @At("HEAD"), cancellable = true)
    private void lads$asyncIcon(Pack pack, CallbackInfoReturnable<Identifier> cir) {
        if (!FastPackScreen.enabled()) return;
        cir.setReturnValue(lads$icons.get(pack, packIcons, DEFAULT_ICON, Minecraft.getInstance().getTextureManager()));
    }

    @Inject(method = "reload", at = @At("HEAD"))
    private void lads$reload(CallbackInfo ci) {
        lads$icons.reset();
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void lads$close(CallbackInfo ci) {
        lads$icons.close();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void lads$log(CallbackInfo ci) {
        if (lads$logged || !FastPackScreen.enabled()) return;
        lads$logged = true;
        LoggerFactory.getLogger("TheLadsCore").info(FastPackScreen.openLine((int) (model.getSelected().count() + model.getUnselected().count())));
    }
}
