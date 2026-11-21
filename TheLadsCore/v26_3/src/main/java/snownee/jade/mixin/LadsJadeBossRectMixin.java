package snownee.jade.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.thelads.core.client.hud.ServerRulesNotice;

import snownee.jade.api.ui.Rect2f;

/** Real Jade only (see JadeMixinPlugin): vanilla bars are drawn below the Lads API notification, so Jade's "push down" rect grows by that shift. */
@Pseudo
@Mixin(targets = "snownee.jade.util.ClientProxy", remap = false)
public class LadsJadeBossRectMixin {
	@Inject(at = @At("RETURN"), method = "getBossBarRect", require = 0)
	private static void lads$shift(CallbackInfoReturnable<Rect2f> cir) {
		Rect2f r = cir.getReturnValue();
		if (r == null) return;
		int k = (int) r.getX();
		int shift = ServerRulesNotice.clearOf(k, k + 182, 12) - 12;
		if (shift > 0) r.setHeight(r.getHeight() + shift);
	}
}
