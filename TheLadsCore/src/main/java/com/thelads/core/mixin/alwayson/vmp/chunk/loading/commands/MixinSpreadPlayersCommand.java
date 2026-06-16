/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.Maps
 *  com.mojang.brigadier.Message
 *  com.mojang.brigadier.exceptions.CommandSyntaxException
 *  com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType
 *  com.mojang.brigadier.exceptions.Dynamic4CommandExceptionType
 *  net.minecraft.commands.CommandSource
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.core.BlockPos
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.ComponentUtils
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.TickTask
 *  net.minecraft.server.commands.SpreadPlayersCommand
 *  net.minecraft.server.commands.SpreadPlayersCommand$Position
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.rcon.RconConsoleSource
 *  net.minecraft.util.RandomSource
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.phys.Vec2
 *  net.minecraft.world.scores.PlayerTeam
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.thelads.core.mixin.alwayson.vmp.chunk.loading.commands;

import com.google.common.collect.Maps;
import com.thelads.core.features.alwayson.vmp.common.chunk.loading.async_chunks_on_player_login.AsyncChunkLoadUtil;
import com.thelads.core.mixin.alwayson.vmp.access.IServerCommandSource;
import com.thelads.core.mixin.alwayson.vmp.access.ISpreadPlayersCommandPile;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic4CommandExceptionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.commands.SpreadPlayersCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.rcon.RconConsoleSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={SpreadPlayersCommand.class})
public abstract class MixinSpreadPlayersCommand {
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(1, 1, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(8));
    @Shadow
    @Final
    private static Dynamic2CommandExceptionType ERROR_INVALID_MAX_HEIGHT;
    @Shadow
    @Final
    private static Dynamic4CommandExceptionType ERROR_FAILED_TO_SPREAD_TEAMS;
    @Shadow
    @Final
    private static Dynamic4CommandExceptionType ERROR_FAILED_TO_SPREAD_ENTITIES;

    @Shadow
    protected static int getNumberOfTeams(Collection<? extends Entity> entities) {
        throw new AbstractMethodError();
    }

    @Shadow
    protected static SpreadPlayersCommand.Position[] createInitialPositions(RandomSource random, int count, double minX, double minZ, double maxX, double maxZ) {
        throw new AbstractMethodError();
    }

    @Inject(method={"spreadPlayers"}, at={@At(value="HEAD")}, cancellable=true)
    private static void execute(CommandSourceStack source, Vec2 center, float spreadDistance, float maxRange, int maxY, boolean respectTeams, Collection<? extends Entity> players, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        CommandSource output = ((IServerCommandSource)source).getOutput();
        if (!(output instanceof Player || output instanceof MinecraftServer || output instanceof RconConsoleSource)) {
            return;
        }
        cir.cancel();
        cir.setReturnValue(0);
        ServerLevel serverWorld = source.getLevel();
        int i = serverWorld.getMinY();
        if (maxY < i) {
            throw ERROR_INVALID_MAX_HEIGHT.create((Object)maxY, (Object)i);
        }
        RandomSource random = RandomSource.create();
        double d = center.x - maxRange;
        double e = center.y - maxRange;
        double f = center.x + maxRange;
        double g = center.y + maxRange;
        SpreadPlayersCommand.Position[] piles = MixinSpreadPlayersCommand.createInitialPositions(random, respectTeams ? MixinSpreadPlayersCommand.getNumberOfTeams(players) : players.size(), d, e, f, g);
        EXECUTOR.execute(() -> {
            try {
                MixinSpreadPlayersCommand.vmp$spread(center, spreadDistance, serverWorld, random, d, e, f, g, maxY, piles, respectTeams);
            }
            catch (CommandSyntaxException ex) {
                source.getServer().schedule(new TickTask(0, () -> source.sendFailure(ComponentUtils.fromMessage((Message)ex.getRawMessage()))));
            }
            catch (Throwable t) {
                source.getServer().execute(() -> {
                    source.sendFailure((Component)Component.literal((String)"An error occurred while spreading players, check console for details"));
                    t.printStackTrace();
                });
            }
            double h = MixinSpreadPlayersCommand.vmp$getMinDistance(players, serverWorld, piles, maxY, respectTeams);
            source.getServer().execute(() -> source.sendSuccess(() -> Component.translatable((String)("commands.spreadplayers.success." + (respectTeams ? "teams" : "entities")), (Object[])new Object[]{piles.length, Float.valueOf(center.x), Float.valueOf(center.y), String.format(Locale.ROOT, "%.2f", h)}), true));
        });
        cir.setReturnValue(piles.length);
    }

    @Unique
    private static void vmp$spread(Vec2 center, double spreadDistance, ServerLevel world, RandomSource random, double minX, double minZ, double maxX, double maxZ, int maxY, SpreadPlayersCommand.Position[] piles, boolean respectTeams) throws CommandSyntaxException {
        int i;
        boolean bl = true;
        double d = 3.4028234663852886E38;
        for (i = 0; i < 10000 && bl; ++i) {
            bl = false;
            d = 3.4028234663852886E38;
            for (int j = 0; j < piles.length; ++j) {
                SpreadPlayersCommand.Position pile = piles[j];
                int k = 0;
                SpreadPlayersCommand.Position pile2 = new SpreadPlayersCommand.Position();
                for (int l = 0; l < piles.length; ++l) {
                    if (j == l) continue;
                    SpreadPlayersCommand.Position pile3 = piles[l];
                    double e = ((ISpreadPlayersCommandPile)pile).invokeGetDistance(pile3);
                    d = Math.min(e, d);
                    if (!(e < spreadDistance)) continue;
                    ++k;
                    ((ISpreadPlayersCommandPile)pile2).setX(((ISpreadPlayersCommandPile)pile2).getX() + (((ISpreadPlayersCommandPile)pile3).getX() - ((ISpreadPlayersCommandPile)pile).getX()));
                    ((ISpreadPlayersCommandPile)pile2).setZ(((ISpreadPlayersCommandPile)pile2).getZ() + (((ISpreadPlayersCommandPile)pile3).getZ() - ((ISpreadPlayersCommandPile)pile).getZ()));
                }
                if (k > 0) {
                    ((ISpreadPlayersCommandPile)pile2).setX(((ISpreadPlayersCommandPile)pile2).getX() / (double)k);
                    ((ISpreadPlayersCommandPile)pile2).setZ(((ISpreadPlayersCommandPile)pile2).getZ() / (double)k);
                    double f = ((ISpreadPlayersCommandPile)pile2).invokeAbsolute();
                    if (f > 0.0) {
                        ((ISpreadPlayersCommandPile)pile2).invokeNormalize();
                        pile.moveAway(pile2);
                    } else {
                        pile.randomize(random, minX, minZ, maxX, maxZ);
                    }
                    bl = true;
                }
                if (!pile.clamp(minX, minZ, maxX, maxZ)) continue;
                bl = true;
            }
            if (bl) continue;
            ArrayList<CompletionStage> futures = new ArrayList<CompletionStage>(piles.length);
            AtomicBoolean result = new AtomicBoolean(false);
            for (SpreadPlayersCommand.Position pile2 : piles) {
                ChunkPos pos = ChunkPos.containing((BlockPos)BlockPos.containing((double)((ISpreadPlayersCommandPile)pile2).getX(), (double)0.0, (double)((ISpreadPlayersCommandPile)pile2).getZ()));
                CompletionStage future = ((CompletableFuture)((CompletableFuture)((CompletableFuture)CompletableFuture.supplyAsync(() -> AsyncChunkLoadUtil.scheduleChunkLoad(world, pos), (Executor)world.getServer()).thenCompose(Function.identity())).whenCompleteAsync((unused, throwable) -> {
                    if (!pile2.isSafe((BlockGetter)world, maxY)) {
                        pile2.randomize(random, minX, minZ, maxX, maxZ);
                        result.set(true);
                    }
                }, (Executor)world.getServer())).exceptionally(throwable -> null)).thenRun(() -> {});
                futures.add(future);
            }
            CompletableFuture.allOf((CompletableFuture[])futures.toArray(CompletableFuture[]::new)).join();
            bl = result.get();
        }
        if (d == 3.4028234663852886E38) {
            d = 0.0;
        }
        if (i >= 10000) {
            if (respectTeams) {
                throw ERROR_FAILED_TO_SPREAD_TEAMS.create((Object)piles.length, (Object)Float.valueOf(center.x), (Object)Float.valueOf(center.y), (Object)String.format(Locale.ROOT, "%.2f", d));
            }
            throw ERROR_FAILED_TO_SPREAD_ENTITIES.create((Object)piles.length, (Object)Float.valueOf(center.x), (Object)Float.valueOf(center.y), (Object)String.format(Locale.ROOT, "%.2f", d));
        }
    }

    @Unique
    private static double vmp$getMinDistance(Collection<? extends Entity> entities, ServerLevel world, SpreadPlayersCommand.Position[] piles, int maxY, boolean respectTeams) {
        double d = 0.0;
        int i = 0;
        HashMap map = Maps.newHashMap();
        ArrayList<CompletionStage> futures = new ArrayList<CompletionStage>(piles.length);
        for (Entity entity : entities) {
            SpreadPlayersCommand.Position pile;
            if (!entity.isAlive()) continue;
            if (respectTeams) {
                PlayerTeam abstractTeam;
                PlayerTeam playerTeam = abstractTeam = entity instanceof Player ? entity.getTeam() : null;
                if (!map.containsKey(abstractTeam)) {
                    map.put(abstractTeam, piles[i++]);
                }
                pile = (SpreadPlayersCommand.Position)map.get(abstractTeam);
            } else {
                pile = piles[i++];
            }
            ChunkPos pos = ChunkPos.containing((BlockPos)BlockPos.containing((double)((ISpreadPlayersCommandPile)pile).getX(), (double)0.0, (double)((ISpreadPlayersCommandPile)pile).getZ()));
            CompletionStage future = ((CompletableFuture)((CompletableFuture)((CompletableFuture)CompletableFuture.supplyAsync(() -> AsyncChunkLoadUtil.scheduleChunkLoad(world, pos), (Executor)world.getServer()).thenCompose(Function.identity())).whenCompleteAsync((unused, throwable) -> entity.teleportTo(world, Math.floor(((ISpreadPlayersCommandPile)pile).getX()) + 0.5, (double)pile.getSpawnY((BlockGetter)world, maxY), Math.floor(((ISpreadPlayersCommandPile)pile).getZ()) + 0.5, Set.of(), entity.getYRot(), entity.getXRot(), true), (Executor)world.getServer())).exceptionally(throwable -> null)).thenRun(() -> {});
            futures.add(future);
            double e = Double.MAX_VALUE;
            for (SpreadPlayersCommand.Position pile2 : piles) {
                if (pile == pile2) continue;
                double f = ((ISpreadPlayersCommandPile)pile).invokeGetDistance(pile2);
                e = Math.min(f, e);
            }
            d += e;
        }
        CompletableFuture.allOf((CompletableFuture[])futures.toArray(CompletableFuture[]::new)).join();
        return entities.size() < 2 ? 0.0 : d / (double)entities.size();
    }
}

