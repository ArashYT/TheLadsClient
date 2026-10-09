package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.v1_20_1.gui.ForgeOverlayBridge1201;
import com.thelads.core.v1_20_1.gui.LoadingLogo1201;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge (Zero SMP): ForgeLoadingOverlay replaces vanilla's whole render, drawing the early-display window's picture. The Lads
 * loading screen takes its place with Forge's own timing: the same reload progress, finish callback, fade and hand-over, so the
 * modpack starts exactly as before, only looking like the Lads Client. Only linked where Forge is installed.
 * The method name is Forge's SRG name for Overlay.render(GuiGraphics, int, int, float).
 */
@Pseudo
@Mixin(targets = "net.minecraftforge.client.loading.ForgeLoadingOverlay", remap = false)
public abstract class ForgeLoadingOverlayMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private Consumer<Optional<Throwable>> onFinish;
    @Shadow private long fadeOutStart;
    @Unique private float ladsProgress;

    @Inject(method = "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void ladsRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ForgeOverlayBridge1201.ready(this)) return; // keep Forge's own screen
        long now = Util.getMillis();
        float out = fadeOutStart > -1L ? (now - fadeOutStart) / 1000.0f : -1.0f;
        float actual = reload.getActualProgress();
        ladsProgress = Mth.clamp(ladsProgress * 0.95f + actual * 0.050000012f, 0.0f, 1.0f);
        ForgeOverlayBridge1201.progress(this, actual);
        if (out >= 1.0f && minecraft.screen != null) minecraft.screen.render(graphics, 0, 0, partialTick);
        float alpha = out >= 1.0f ? 1.0f - Mth.clamp(out - 1.0f, 0.0f, 1.0f) : 1.0f;
        LoadingLogo1201.draw(graphics, ladsProgress, alpha, 1.0f - Mth.clamp(out, 0.0f, 1.0f));
        if (out >= 2.0f) {
            minecraft.setOverlay(null);
            ForgeOverlayBridge1201.closeWindow(this);
        }
        if (fadeOutStart == -1L && reload.isDone()) {
            ForgeOverlayBridge1201.finishProgress(this);
            fadeOutStart = Util.getMillis();
            try {
                reload.checkExceptions();
                onFinish.accept(Optional.empty());
            } catch (Throwable failure) {
                onFinish.accept(Optional.of(failure));
            }
            if (minecraft.screen != null) {
                minecraft.screen.init(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
            }
        }
        ci.cancel();
    }
}
