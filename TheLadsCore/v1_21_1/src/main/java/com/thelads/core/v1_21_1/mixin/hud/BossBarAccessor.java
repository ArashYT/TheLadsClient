package com.thelads.core.v1_21_1.mixin.hud;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BossHealthOverlay.class)
public interface BossBarAccessor {
    @Accessor("events") Map<UUID, LerpingBossEvent> ladsEvents();
    @Invoker("drawBar") void ladsDrawBar(GuiGraphics g, int x, int y, BossEvent event);
}
