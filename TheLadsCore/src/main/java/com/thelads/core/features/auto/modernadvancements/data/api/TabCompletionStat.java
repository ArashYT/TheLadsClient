package com.thelads.core.features.auto.modernadvancements.data.api;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record TabCompletionStat(Component title, ItemStack icon, int completed, int total) {
   public boolean isDone() {
      return this.total > 0 && this.completed >= this.total;
   }

   public String countLabel() {
      return this.completed + " / " + this.total;
   }
}
