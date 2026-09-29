package com.thelads.core.v26_2.mixin;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;
import java.util.*;
@Mixin(BossHealthOverlay.class)
public interface BossBarAccessor {
    @Accessor("events") Map<UUID,LerpingBossEvent> ladsEvents();
    @Invoker("extractBar") void ladsDrawBar(GuiGraphicsExtractor g,int x,int y,BossEvent event);
}
