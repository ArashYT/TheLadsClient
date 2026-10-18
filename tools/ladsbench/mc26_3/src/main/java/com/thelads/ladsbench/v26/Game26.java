package com.thelads.ladsbench.v26;

import com.thelads.ladsbench.Bench;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.UUID;

/** Minecraft 26.3 calls for {@link Bench}. */
public final class Game26 implements Bench.Game {
    public static final Game26 INSTANCE = new Game26();
    private ServerBossEvent bar;

    private static Minecraft mc() { return Minecraft.getInstance(); }

    @Override public boolean loading() { return mc().gui.overlay() != null; }
    @Override public boolean screenOpen() { return mc().gui.screen() != null; }
    @Override public String screenName() { var s = mc().gui.screen(); return s == null ? "none" : s.getClass().getName() + " '" + s.getTitle().getString() + "'"; }
    @Override public boolean focused() { return mc().isWindowActive(); }
    @Override public boolean inWorld() { return mc().level != null && mc().player != null; }
    @Override public boolean worldExists(String name) { return mc().getLevelSource().levelExists(name); }
    @Override public void openWorld(String name) { mc().createWorldOpenFlows().openWorld(name, () -> { }); }

    @Override public void createWorld(String name, long seed) {
        var settings = new LevelSettings(name, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.EASY, false, false), true,
            WorldDataConfiguration.DEFAULT);
        mc().createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(seed, true, false),
            r -> r.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(), mc().gui.screen());
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

    @Override public void hud(boolean hidden) { if (mc().gui.hud.isHidden() != hidden) mc().gui.hud.toggle(); }

    @Override public void bar(String text, float progress, boolean red) {
        MinecraftServer s = mc().getSingleplayerServer();
        if (s == null) return;
        s.execute(() -> {
            if (bar == null) bar = new ServerBossEvent(UUID.fromString("1ad5a0a0-0000-4000-8000-00000000be4c"), Component.literal(text),
                BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
            for (ServerPlayer p : s.getPlayerList().getPlayers()) if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
            bar.setName(Component.literal(text));
            bar.setProgress(progress);
            bar.setColor(red ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.GREEN);
        });
    }

    @Override public void screenshot(String name) {
        net.minecraft.client.Screenshot.grab(mc(), false); // 26.3: timestamp name only; the log line names the scene
    }

    @Override public Path gameDir() { return FabricLoader.getInstance().getGameDir(); }

    @Override public String info() {
        String backend;
        try { backend = String.valueOf(com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().backendName()); }
        catch (RuntimeException e) { backend = "?"; }
        return "mc " + SharedConstants.getCurrentVersion().name() + ", java " + Runtime.version() + ", mods " + FabricLoader.getInstance().getAllMods().size()
            + ", backend " + backend;
    }
}
