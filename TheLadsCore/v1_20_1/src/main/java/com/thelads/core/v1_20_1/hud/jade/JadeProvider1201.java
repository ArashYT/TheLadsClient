package com.thelads.core.v1_20_1.hud.jade;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Addon hook of the Lads Jade overlay: other Lads code adds lines for the block or entity under the crosshair.
 * Register once at client init with {@link #register}. Called on the client thread about 5 times a second while a target is
 * looked at; added lines appear above the mod name. Only client-side data is available (no server round trip).
 */
public interface JadeProvider1201 {
    List<JadeProvider1201> PROVIDERS = new CopyOnWriteArrayList<>();

    static void register(JadeProvider1201 provider) {
        PROVIDERS.add(provider);
    }

    /** {@code state} is the fluid's block state when the target is a fluid; {@code blockEntity} may be null. */
    default void appendBlock(BlockState state, BlockPos pos, BlockEntity blockEntity, List<Component> lines) {}

    default void appendEntity(Entity entity, List<Component> lines) {}
}
