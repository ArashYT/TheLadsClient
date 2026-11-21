package snownee.jade.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.ServerRulesNotice;

import snownee.jade.api.ui.Rect2f;
import snownee.jade.api.ui.TooltipAnimation;

/**
 * Applies only while the real Jade is installed (see JadeMixinPlugin); the embedded port has the same two rules written
 * into its own BoxElementImpl. Keeps Jade's tooltip below the Lads API notification and the Lads Boss Bar HUD.
 */
@Pseudo
@Mixin(targets = "snownee.jade.impl.ui.BoxElementImpl", remap = false)
public class LadsJadeClearMixin {
	@Inject(at = @At("RETURN"), method = "updateExpectedRect", require = 0)
	private void lads$clear(TooltipAnimation animation, CallbackInfo ci) {
		Rect2f r = animation.expectedRect;
		int toastY = ServerRulesNotice.clearOf((int) r.getX(), (int) (r.getX() + r.getWidth()), (int) r.getY());
		if (toastY > r.getY()) r.setY(toastY);
		int[] bars = HudManager.bossBarBox();
		if (bars != null && r.getX() < bars[2] && r.getX() + r.getWidth() > bars[0]
				&& r.getY() < bars[3] + 2 && r.getY() + r.getHeight() > bars[1]) {
			r.setY(bars[3] + 2);
		}
	}
}
