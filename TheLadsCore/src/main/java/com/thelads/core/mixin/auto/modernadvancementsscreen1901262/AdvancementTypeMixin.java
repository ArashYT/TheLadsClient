package com.thelads.core.mixin.auto.modernadvancementsscreen1901262;

import java.util.Optional;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.Custom;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AdvancementType.class})
public class AdvancementTypeMixin {
   @Inject(
      method = {"createAnnouncement"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void modern$createAnnouncement(AdvancementHolder holder, ServerPlayer player, CallbackInfoReturnable<MutableComponent> cir) {
      Component nameWithClick = Advancement.name(holder)
         .copy()
         .withStyle(
            s -> s.withClickEvent(
               new Custom(Identifier.fromNamespaceAndPath("modern-advancements", "open_advancement"), Optional.of(StringTag.valueOf(holder.id().toString())))
            )
         );
      cir.setReturnValue(
         Component.translatable("chat.type.advancement." + ((AdvancementType)(Object)this).getSerializedName(), new Object[]{player.getDisplayName(), nameWithClick})
      );
   }
}
