package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.CycleOption;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.resources.Identifier;

/**
 * Customizes the F3 debug screen. 26.1.2 rebuilt the debug overlay around a
 * {@link DebugScreenEntries} registry (each line is a {@link Identifier} with a
 * {@link DebugScreenEntryStatus}), replacing the old {@code getGameInformation()}
 * List-filtering approach, so this toggles entries via {@code mc.debugEntries.setStatus(...)}.
 *
 * NOTE (untested): the line toggles use the new entry API; the Text Shadow / Background
 * options are wired as settings but their visual rendering still needs a render hook into
 * the new {@code DebugScreenOverlay.extractRenderState} path.
 */
public class BetterF3Module extends Module {
    public final CycleOption fpsUpdateRate   = new CycleOption("FPS Update", 1, "Instant", "Fast", "Normal", "Slow");
    public final BoolOption  showXYZ         = new BoolOption("Show XYZ", true);
    public final BoolOption  showJava        = new BoolOption("Show Java/Version", false);
    public final BoolOption  showMemory      = new BoolOption("Show Memory", true);
    public final BoolOption  showTargetBlock = new BoolOption("Show Target Block", true);
    public final BoolOption  textShadow      = new BoolOption("Text Shadow", true);
    public final ColorOption background      = new ColorOption("Background", true, 0x80000000);
    public final CycleOption position        = new CycleOption("Position", 0, "Top", "Bottom");

    public BetterF3Module() {
        super("BetterF3", "Customize the F3 debug screen (toggle lines, shadow, background).");
        addOption(fpsUpdateRate);
        addOption(showXYZ);
        addOption(showJava);
        addOption(showMemory);
        addOption(showTargetBlock);
        addOption(textShadow);
        addOption(background);
        addOption(position);
    }

    /** Called every client tick: keep the vanilla debug entries in sync with the options. */
    public void tick(Minecraft mc) {
        if (mc == null || mc.debugEntries == null) return;
        boolean on = isEnabled();
        // When off, everything is shown (overlay default) — matches the old "no filtering" behaviour.
        apply(mc, DebugScreenEntries.PLAYER_POSITION,         !on || showXYZ.get());
        apply(mc, DebugScreenEntries.PLAYER_SECTION_POSITION, !on || showXYZ.get());
        apply(mc, DebugScreenEntries.HEIGHTMAP,               !on || showXYZ.get());
        apply(mc, DebugScreenEntries.MEMORY,                  !on || showMemory.get());
        apply(mc, DebugScreenEntries.DETAILED_MEMORY,         !on || showMemory.get());
        apply(mc, DebugScreenEntries.LOOKING_AT_BLOCK_STATE,  !on || showTargetBlock.get());
        apply(mc, DebugScreenEntries.LOOKING_AT_BLOCK_TAGS,   !on || showTargetBlock.get());
        apply(mc, DebugScreenEntries.LOOKING_AT_FLUID_STATE,  !on || showTargetBlock.get());
        apply(mc, DebugScreenEntries.LOOKING_AT_FLUID_TAGS,   !on || showTargetBlock.get());
        apply(mc, DebugScreenEntries.GAME_VERSION,            !on || showJava.get());
        apply(mc, DebugScreenEntries.SYSTEM_SPECS,            !on || showJava.get());
    }

    private void apply(Minecraft mc, Identifier id, boolean show) {
        try {
            mc.debugEntries.setStatus(id, show ? DebugScreenEntryStatus.IN_OVERLAY : DebugScreenEntryStatus.NEVER);
        } catch (Throwable ignored) {
            // tolerate an entry id changing between MC versions
        }
    }

    public boolean isTextShadow()       { return textShadow.get(); }
    public int     getBackgroundColor() { return background.getColor(); }
}
