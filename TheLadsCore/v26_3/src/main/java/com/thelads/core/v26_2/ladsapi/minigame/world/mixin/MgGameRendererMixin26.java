package com.thelads.core.v26_2.ladsapi.minigame.world.mixin;

import com.thelads.core.client.ladsapi.minigame.FxState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Minigame fx shaders ride the vanilla post-effect list (so spectator/glowing effects keep working); the held item hides with the HUD. */
@Mixin(GameRenderer.class)
public abstract class MgGameRendererMixin26 {
    private static final Identifier BLUR = Identifier.fromNamespaceAndPath("theladscore", "mg_blur");
    private static final Identifier GRAYSCALE = Identifier.fromNamespaceAndPath("theladscore", "mg_grayscale");
    private static final Identifier SEPIA = Identifier.fromNamespaceAndPath("theladscore", "mg_sepia");
    private static final Identifier INVERT = Identifier.fromNamespaceAndPath("theladscore", "mg_invert");

    @Shadow @Final private List<Identifier> requestedPostEffects;

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void lads$fxShader(DeltaTracker tracker, CallbackInfo ci) {
        FxState.Shader shader = Minigame.fx.shader(Minigame.now());
        if (shader == FxState.Shader.NONE) return;
        requestedPostEffects.add(switch (shader) {
            case BLUR -> BLUR;
            case GRAYSCALE -> GRAYSCALE;
            case SEPIA -> SEPIA;
            default -> INVERT;
        });
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void lads$noHand(CallbackInfo ci) {
        if (Minigame.camera.hidesHud(Minigame.now())) ci.cancel();
    }
}
