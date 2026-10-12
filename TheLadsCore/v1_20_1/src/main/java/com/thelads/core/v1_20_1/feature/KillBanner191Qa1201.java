package com.thelads.core.v1_20_1.feature;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.killbanner.KillBanner191Qa;
import com.thelads.core.modules.KillBannerModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.slf4j.LoggerFactory;

/** QA only: the script verb {@code kb191} runs the 1.9.1 kill banner run (common KillBanner191Qa) on 1.20.1 (Zero SMP too). */
final class KillBanner191Qa1201 implements KillBanner191Qa.Driver {
    private static final int FIRST_ID = Integer.MAX_VALUE - 300;
    private static KillBanner191Qa run;
    private final AtomicInteger pig = new AtomicInteger(-1);
    private final Map<SoundSource, Double> volumes = new EnumMap<>(SoundSource.class);
    private int standIns;
    private boolean dynamicFpsBefore;

    private KillBanner191Qa1201() {}

    static boolean tick(String[] args) {
        if (run == null) run = new KillBanner191Qa(new KillBanner191Qa1201());
        if (!run.tick()) return false;
        boolean failed = run.failed();
        run = null;
        if (failed) throw new IllegalStateException("kb191: a check failed (see the 'Lads kb191' lines)");
        return true;
    }

    @Override public int settleTicks() { return 1800; }

    @Override public String me() { return Minecraft.getInstance().player.getGameProfile().getName(); }

    @Override public void standIns(List<String> names) {
        Minecraft mc = Minecraft.getInstance();
        for (String name : names) {
            RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), name));
            int id = FIRST_ID + standIns++;
            player.setId(id);
            player.setPos(mc.player.getX() + standIns, mc.player.getY(), mc.player.getZ() + 4);
            mc.level.addPlayer(id, player);
        }
    }

    @Override public void removeStandIns() {
        for (int i = 0; i < standIns; i++) Minecraft.getInstance().level.removeEntity(FIRST_ID + i, Entity.RemovalReason.DISCARDED);
        standIns = 0;
        int id = pig.getAndSet(-1);
        if (id >= 0) server(sp -> { Entity entity = sp.level().getEntity(id); if (entity != null) entity.discard(); });
    }

    @Override public void tellraw(String json) {
        server(sp -> sp.getServer().getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "tellraw @s " + json));
    }

    @Override public void spawnPig() {
        server(sp -> {
            var mob = EntityType.PIG.create(sp.level());
            mob.setPos(sp.getX(), sp.getY(), sp.getZ() + 1.5);
            mob.setNoAi(true);
            mob.setHealth(.1f);
            sp.level().addFreshEntity(mob);
            pig.set(mob.getId());
        });
    }

    @Override public boolean attackPig() {
        Minecraft mc = Minecraft.getInstance();
        Entity target = pig.get() < 0 ? null : mc.level.getEntity(pig.get());
        if (target == null) return false;
        mc.gameMode.attack(mc.player, target); // the player's own blow (KillBannerAttackMixin)
        return true;
    }

    @Override public void openSoundScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new SoundOptionsScreen(null, mc.options));
        // Ours is the row under the game's volumes: scrolled into the 944x501 QA window's view.
        for (GuiEventListener child : mc.screen.children())
            if (child instanceof OptionsList list) list.setScrollAmount(list.getMaxScroll() - 50);
    }

    @Override public boolean clickSlider(String label, double fraction) {
        var screen = Minecraft.getInstance().screen;
        List<AbstractWidget> found = new ArrayList<>();
        widgets(screen, found);
        for (AbstractWidget widget : found) {
            if (!(widget instanceof AbstractSliderButton) || !widget.getMessage().getString().startsWith(label)) continue;
            double x = widget.getX() + 4 + fraction * (widget.getWidth() - 8), y = widget.getY() + widget.getHeight() / 2.0;
            boolean clicked = screen.mouseClicked(x, y, 0);
            screen.mouseReleased(x, y, 0);
            LoggerFactory.getLogger("TheLadsCore").info("Lads kb191 slider '{}' at ({}, {}): clicked {}, now '{}'", label, x, y, clicked, widget.getMessage().getString());
            return clicked;
        }
        return false;
    }

    private static void widgets(GuiEventListener parent, List<AbstractWidget> out) {
        if (parent instanceof AbstractWidget widget) out.add(widget);
        if (parent instanceof ContainerEventHandler container) for (GuiEventListener child : container.children()) widgets(child, out);
    }

    @Override public void closeScreen() { Minecraft.getInstance().setScreen(null); }

    @Override public void shot(String name) {
        Path output = FabricLoader.getInstance().getGameDir().resolve("screenshots").resolve("kb191").resolve(name + ".png");
        try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            Files.createDirectories(output.getParent());
            image.writeToFile(output);
            LoggerFactory.getLogger("TheLadsCore").info("Lads kb191 frame {}", output);
        } catch (Exception failure) {
            LoggerFactory.getLogger("TheLadsCore").warn("Lads kb191 frame {} not saved", name, failure);
        }
    }

    @Override public List<String> references() {
        return List.of("minecraft:entity.player.attack.strong 1", "minecraft:entity.player.hurt 1", "minecraft:entity.player.levelup 0.75",
            "minecraft:block.note_block.pling 1");
    }

    @Override public void playReference(String reference) {
        Minecraft mc = Minecraft.getInstance();
        String[] parts = reference.split(" ");
        var event = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(parts[0]));
        mc.level.playLocalSound(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), event, SoundSource.PLAYERS, Float.parseFloat(parts[1]), 1f, false);
    }

    @Override public void banner(KillBannerModule.Pick pick) {
        NativeKillBanner1201.bindCurrent();
        NativeKillBanner1201.trigger(1, false, false, pick);
    }

    @Override public void mute(boolean on) {
        var options = Minecraft.getInstance().options;
        var dynamicFps = com.thelads.core.config.ModuleManager.getInstance().getModule("DynamicFPS");
        if (dynamicFps != null) {
            if (on) dynamicFpsBefore = dynamicFps.isEnabled();
            dynamicFps.setEnabled(!on && dynamicFpsBefore);
            com.thelads.core.v1_20_1.perf.dynamicfps.DynamicFPSMod.onStatusChanged(false); // its power state now, volume multipliers included
        }
        if (on) {
            for (SoundSource source : SoundSource.values()) {
                if (source == SoundSource.MASTER || source == SoundSource.PLAYERS) continue;
                volumes.put(source, options.getSoundSourceOptionInstance(source).get());
                options.getSoundSourceOptionInstance(source).set(0.0);
            }
        } else {
            volumes.forEach((source, volume) -> options.getSoundSourceOptionInstance(source).set(volume));
            volumes.clear();
        }
    }

    private static void server(Consumer<ServerPlayer> action) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) action.accept(sp);
        });
    }
}
