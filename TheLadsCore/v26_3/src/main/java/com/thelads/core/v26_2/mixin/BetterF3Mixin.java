package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeBetterF3;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Better F3: each finished column of debug text goes to NativeBetterF3 instead of the game's plain grey lines (with the chunks the
 * game's own entries read), and each frame with the debug screen closed lets the columns it showed slide out.
 */
@Mixin(DebugScreenOverlay.class)
public class BetterF3Mixin {
    @Shadow @Final private Font font;

    @Shadow private Level getLevel() { throw new AssertionError(); }
    @Shadow private LevelChunk getClientChunk() { throw new AssertionError(); }
    @Shadow private LevelChunk getServerChunk() { throw new AssertionError(); }

    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 1)
    private void lads$betterF3Closed(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        NativeBetterF3.closed(graphics, font);
    }

    @Inject(method = "extractLines", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$betterF3(GuiGraphicsExtractor graphics, List<String> lines, boolean left, int width, CallbackInfo ci) {
        int mark = com.thelads.core.client.hud.HudCover.mark();
        if (NativeBetterF3.enabled()) {
            NativeBetterF3.draw(graphics, font, lines, left, width, getLevel(), getClientChunk(), getServerChunk());
            ci.cancel();
        }
        // The game draws the plain text: the Lads HUD widgets under its lines step aside (HudCover).
        else com.thelads.core.client.hud.HudCover.debugColumn(lines, left, width, font::width);
        // Its lines are laid out at the debug GUI scale (Options.debugGuiScale): their boxes in GUI units.
        var pose = graphics.pose();
        com.thelads.core.client.hud.HudCover.scaleSince(mark, pose.m00(), pose.m11(), pose.m20(), pose.m21());
    }
}
