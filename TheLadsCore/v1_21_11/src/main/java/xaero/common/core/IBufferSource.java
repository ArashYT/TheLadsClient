// Compile-time stub of Xaero's Minimap API (not shipped: excluded from the jar). The real interface comes from the pack.
package xaero.common.core;

import net.minecraft.client.renderer.rendertype.RenderType;

public interface IBufferSource {
    RenderType getXaero_lastRenderType();

    void setXaero_lastRenderType(RenderType lastRenderType);
}
