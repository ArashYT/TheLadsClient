package com.thelads.ladsbench.v1201;

import com.thelads.ladsbench.Bench;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;

/** Minecraft 1.20.1 calls for {@link Bench}. */
public final class Game1201 implements Bench.Game {
    public static final Game1201 INSTANCE = new Game1201();
    private ServerBossEvent bar;

    private static Minecraft mc() { return Minecraft.getInstance(); }

    @Override public boolean loading() { return mc().getOverlay() != null; }
    @Override public boolean screenOpen() { return mc().screen != null; }
    @Override public boolean inWorld() { return mc().level != null && mc().player != null; }
    @Override public boolean worldExists(String name) { return mc().getLevelSource().levelExists(name); }
    @Override public void openWorld(String name) { mc().createWorldOpenFlows().loadLevel(new TitleScreen(), name); }

    @Override public void createWorld(String name, long seed) {
        var settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.EASY, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        mc().createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(seed, true, false),
            r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions());
    }

    @Override public void command(String command) {
        MinecraftServer s = mc().getSingleplayerServer();
        if (s != null) s.execute(() -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), command));
    }

    @Override public void pose(double x, double y, double z, float yaw, float pitch) {
        var p = mc().player;
        if (p == null) return;
        p.getAbilities().flying = true;
        p.setDeltaMovement(Vec3.ZERO);
        p.setPos(x, y, z);
        p.setYRot(yaw); p.setXRot(pitch); p.setYHeadRot(yaw);
    }

    @Override public double[] pos() { var p = mc().player; return new double[]{p.getX(), p.getY(), p.getZ()}; }

    @Override public void hud(boolean hidden) { mc().options.hideGui = hidden; }

    @Override public void bar(String text, float progress, boolean red) {
        MinecraftServer s = mc().getSingleplayerServer();
        if (s == null) return;
        s.execute(() -> {
            if (bar == null) bar = new ServerBossEvent(Component.literal(text), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
            for (ServerPlayer p : s.getPlayerList().getPlayers()) if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
            bar.setName(Component.literal(text));
            bar.setProgress(progress);
            bar.setColor(red ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.GREEN);
        });
    }

    @Override public void screenshot(String name) {
        net.minecraft.client.Screenshot.grab(mc().gameDirectory, "ladsbench-" + name + ".png", mc().getMainRenderTarget(), c -> { });
    }

    @Override public Path gameDir() { return FabricLoader.getInstance().getGameDir(); }

    @Override public String info() {
        return "mc " + SharedConstants.getCurrentVersion().getName() + ", java " + Runtime.version() + ", mods " + FabricLoader.getInstance().getAllMods().size();
    }
}
