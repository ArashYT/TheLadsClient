package com.thelads.core.v1_20_1.pvp.mixin;

import java.util.List;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** ScreenEffects1201 sets its chains' uniforms pass by pass. */
@Mixin(PostChain.class)
public interface PostChainAccessor1201 {
    @Accessor("passes")
    List<PostPass> lads$passes();
}
