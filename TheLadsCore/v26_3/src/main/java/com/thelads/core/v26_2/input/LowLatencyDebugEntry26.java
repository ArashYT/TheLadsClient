package com.thelads.core.v26_2.input;

import com.thelads.core.modules.ExtraModules;
import java.util.List;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/** The Low Latency module's debug overlay: a section of the F3 screen (cpu, gpu and wait times), empty while the module's overlay is off. */
public class LowLatencyDebugEntry26 implements DebugScreenEntry {
    public static final Identifier ENTRY_ID = Identifier.fromNamespaceAndPath("theladscore", "low_latency");

    @Override
    public void display(final DebugScreenDisplayer displayer, final Level level, final LevelChunk clientChunk, final LevelChunk serverChunk) {
        List<String> lines = ExtraModules.LOW_LATENCY.overlayLines();
        if (!lines.isEmpty()) displayer.addToGroup(ENTRY_ID, lines);
    }

    @Override
    public boolean isAllowed(final boolean reducedDebugInfo) {
        return true;
    }
}
