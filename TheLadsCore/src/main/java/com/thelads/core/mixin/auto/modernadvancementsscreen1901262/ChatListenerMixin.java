package com.thelads.core.mixin.auto.modernadvancementsscreen1901262;

import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent.Custom;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin({ChatListener.class})
public class ChatListenerMixin {
   @ModifyVariable(
      method = {"handleSystemMessage"},
      at = @At("HEAD"),
      argsOnly = true,
      name = {"message"}
   )
   private Component modern$modifySystemMessage(Component message) {
      if (message.getContents() instanceof TranslatableContents translatable) {
         if (!translatable.getKey().startsWith("chat.type.advancement.")) {
            return message;
         } else {
            Object[] args = translatable.getArgs();
            if (!(args.length >= 2 && args[1] instanceof Component advName)) {
               return message;
            } else if (advName.getStyle().getClickEvent() != null) {
               return message;
            } else {
               Minecraft mc = Minecraft.getInstance();
               if (mc.getConnection() == null) {
                  return message;
               } else {
                  String advNameText = advName.getString();

                  for (AdvancementNode node : mc.getConnection().getAdvancements().getTree().nodes()) {
                     AdvancementHolder holder = node.holder();
                     if (Advancement.name(holder).getString().equals(advNameText)) {
                        Component nameWithClick = advName.copy()
                           .withStyle(
                              s -> s.withClickEvent(
                                 new Custom(
                                    Identifier.fromNamespaceAndPath("modern-advancements", "open_advancement"),
                                    Optional.of(StringTag.valueOf(holder.id().toString()))
                                 )
                              )
                           );
                        Object[] newArgs = (Object[])args.clone();
                        newArgs[1] = nameWithClick;
                        return Component.translatable(translatable.getKey(), newArgs).withStyle(message.getStyle());
                     }
                  }

                  return message;
               }
            }
         }
      } else {
         return message;
      }
   }
}
