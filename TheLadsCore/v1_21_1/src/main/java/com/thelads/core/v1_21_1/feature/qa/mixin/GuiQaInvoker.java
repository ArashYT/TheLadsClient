package com.thelads.core.v1_21_1.feature.qa.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The real HUD layers, with every mod's transformations, drawn through a QA GuiGraphics (U3 HUD probe). */
@Mixin(Gui.class)
public interface GuiQaInvoker {
    @Invoker("renderHotbarAndDecorations") void ladsQaHotbar(GuiGraphics graphics, DeltaTracker delta);
    @Invoker("renderItemHotbar") void ladsQaItemHotbar(GuiGraphics graphics, DeltaTracker delta);
    @Invoker("displayScoreboardSidebar") void ladsQaSidebar(GuiGraphics graphics, Objective objective);
}
