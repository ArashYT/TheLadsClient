package com.thelads.smp.mixin;

import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Per-tick mod code that asks "is the player inside a structure" (Bits and Balance's biome titles, advancement style checks) reads the structure starts of the
 * player's chunk with a blocking chunk read. Right after a teleport into a dimension that is not generated yet that chunk does not exist, so the server thread
 * stood still until it was generated: 12-33 s of frozen world on a slow CPU. On the server thread of a game client's own server, a chunk that is not loaded
 * simply has no structure starts yet; answer "none" instead of waiting. A dedicated server and worker threads (world generation) are untouched.
 */
@Mixin(StructureManager.class)
public abstract class MixinStructureManagerUnloadedChunk {
    @Shadow @Final private LevelAccessor level;

    private boolean ladssmp$unloadedOnServerThread(int chunkX, int chunkZ) {
        return level instanceof ServerLevel sl && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
            && sl.getServer().isSameThread() && sl.getChunkSource().getChunkNow(chunkX, chunkZ) == null;
    }

    @Inject(method = "startsForStructure(IILjava/util/function/Predicate;)Ljava/util/List;", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladssmp$startsLoadedOnly(int chunkX, int chunkZ, Predicate<Structure> test, CallbackInfoReturnable<List<StructureStart>> cir) {
        if (ladssmp$unloadedOnServerThread(chunkX, chunkZ)) cir.setReturnValue(List.of());
    }

    @Inject(method = "startsForStructure(IILnet/minecraft/world/level/levelgen/structure/Structure;)Ljava/util/List;", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladssmp$startsLoadedOnly2(int chunkX, int chunkZ, Structure structure, CallbackInfoReturnable<List<StructureStart>> cir) {
        if (ladssmp$unloadedOnServerThread(chunkX, chunkZ)) cir.setReturnValue(List.of());
    }
}
