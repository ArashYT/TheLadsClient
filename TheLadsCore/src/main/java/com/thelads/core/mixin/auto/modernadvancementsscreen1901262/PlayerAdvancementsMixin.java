package com.thelads.core.mixin.auto.modernadvancementsscreen1901262;

import com.thelads.core.features.auto.modernadvancements.server.ServerPlayerAdvancementTracker;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({PlayerAdvancements.class})
public class PlayerAdvancementsMixin {
   @Shadow
   private ServerPlayer player;

   @Inject(
      method = {"award"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/Set;add(Ljava/lang/Object;)Z"
      )}
   )
   private void modern$award(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
      ServerPlayerAdvancementTracker.getInstance().onAdvancementGranted(this.player, holder);
   }
}
