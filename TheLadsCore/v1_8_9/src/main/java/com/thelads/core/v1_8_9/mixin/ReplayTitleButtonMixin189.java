package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ReplayMod's title button is a jGui widget drawn over the screen, outside its button list, so LadsTitleScreen189 cannot lay it out or
 * hide it: it stays beside where vanilla's buttons were (and clickable under the panel). The Lads title has its own Replays button.
 */
@Pseudo
@Mixin(targets = "com.replaymod.replay.handler.GuiHandler", remap = false)
public abstract class ReplayTitleButtonMixin189 {
    @Inject(method = "injectIntoMainMenu", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ladsTitleHasReplays(CallbackInfo ci) {
        Module title = ModuleManager.getInstance().getModule("TitleScreen");
        if (title != null && title.isEnabled()) ci.cancel();
    }
}
