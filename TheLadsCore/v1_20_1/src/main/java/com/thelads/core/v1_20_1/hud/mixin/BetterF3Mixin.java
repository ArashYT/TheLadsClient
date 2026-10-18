package com.thelads.core.v1_20_1.hud.mixin;

import com.thelads.core.v1_20_1.hud.NativeBetterF3_1201;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Better F3: each finished column of debug text goes to NativeBetterF3_1201 (with the chunks the game read it from) instead of the game's plain grey lines. */
@Mixin(DebugScreenOverlay.class)
public abstract class BetterF3Mixin {
    @Shadow @Final private Font font;

    @Shadow private Level getLevel() { throw new AssertionError(); }
    @Shadow private LevelChunk getClientChunk() { throw new AssertionError(); }
    @Shadow private LevelChunk getServerChunk() { throw new AssertionError(); }

    @Inject(method = "renderLines(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;Z)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$betterF3(GuiGraphics graphics, List<String> lines, boolean left, CallbackInfo ci) {
        if (NativeBetterF3_1201.enabled()) {
            NativeBetterF3_1201.draw(graphics, font, lines, left, getLevel(), getClientChunk(), getServerChunk());
            ci.cancel();
        }
        // The game draws the plain text: the Lads HUD widgets under its lines step aside (HudCover).
        else com.thelads.core.client.hud.HudCover.debugColumn(lines, left, graphics.guiWidth(), font::width);
    }
}
