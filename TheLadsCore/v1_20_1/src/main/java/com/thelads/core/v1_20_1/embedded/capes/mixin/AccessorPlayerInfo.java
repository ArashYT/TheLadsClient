// The Lads: accessor used by the QA probe (a PlayerInfo made up for it must not ask the session server for skins).
package com.thelads.core.v1_20_1.embedded.capes.mixin;

import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerInfo.class)
public interface AccessorPlayerInfo {
    @Accessor("pendingTextures")
    void capes$setPendingTextures(boolean pending);
}
