package com.thelads.core.v1_20_1.game.pvp;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * RenderType.lines() with its own line width (vanilla's follows the window size) and, for Hitboxes' Through Walls, no depth test.
 * Built from the public RenderType constructor with the same states lines() has (the shards are protected: a subclass reads them).
 */
final class LadsLines1201 extends RenderType {
    private LadsLines1201(RenderStateShard[] states) {
        super("lads_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false,
            () -> { for (RenderStateShard state : states) state.setupRenderState(); },
            () -> { for (RenderStateShard state : states) state.clearRenderState(); });
    }

    /** A new type: callers keep it while width and walls stay (each instance is its own batch). */
    static RenderType of(float width, boolean throughWalls) {
        return new LadsLines1201(new RenderStateShard[] {RENDERTYPE_LINES_SHADER, TRANSLUCENT_TRANSPARENCY,
            throughWalls ? NO_DEPTH_TEST : LEQUAL_DEPTH_TEST, NO_CULL, NO_LIGHTMAP, NO_OVERLAY, VIEW_OFFSET_Z_LAYERING, ITEM_ENTITY_TARGET,
            throughWalls ? COLOR_WRITE : COLOR_DEPTH_WRITE, new LineStateShard(OptionalDouble.of(width)), NO_COLOR_LOGIC});
    }
}
