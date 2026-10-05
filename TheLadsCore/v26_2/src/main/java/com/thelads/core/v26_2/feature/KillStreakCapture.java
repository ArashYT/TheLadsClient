package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.thelads.core.client.KillBannerTimeline;
import com.thelads.core.config.Option;
import com.thelads.core.modules.KillBannerModule;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-killstreak" from the harness's LADS_VERIFY_CAPTURE_KILLSTREAK): the kill streak in the
 * QA world. Five pigs killed by the player in one server tick, then five killed by the player's own attacks one client tick
 * apart: the banner must show 1, 2, 3, 4, 5 in turn, none dropped (the banner state every client tick in
 * screenshots/killstreak/killstreak-trace.csv: QA games run minimized, where 26.x renders no frames to photograph; a banner
 * lasts at least 5 ticks, so each one is seen). Then server kill messages in
 * many servers' formats through the real chat packet handler (each counts, the decoys do not), the streak timer typed as 3 s
 * (a kill 3.5 s later starts again at 1), Unlimited (4 s later still counts) and the player's death (the next kill starts at 1).
 * Module options, pigs and stand-ins are put back.
 */
final class KillStreakCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final int FIRST_ID = Integer.MAX_VALUE - 96, LAST = 22;
    private static final List<String> FAILURES = new ArrayList<>();
    private static final List<JsonElement> OPTIONS = new ArrayList<>();
    private static final List<Integer> PIGS = Collections.synchronizedList(new ArrayList<>());
    private static final List<Integer> SHOWN = new ArrayList<>();
    private static final StringBuilder TRACE = new StringBuilder("ms,run,streak,queued,sequence,age\n");
    private static int step = -1, wait, passed, lastSequence;
    private static double lastAge = -1;
    private static boolean enabledBefore;
    private static long modifiedBefore, began;
    private static String run;
    private KillStreakCapture() {}

    static boolean busy() { return step >= 0 && step <= LAST; }

    /** Each client tick of the auto-world run: starts once the world is ready and the request exists, then one step. */
    static void tick(Path game, boolean ready) {
        if (step < 0) {
            Path request = game.resolve(".lads-qa-capture-killstreak");
            if (!ready || !Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads kill streak capture FAILED: request", failure); return; }
            step = 0;
        }
        if (step > LAST) return;
        observe();
        if (--wait > 0) return;
        try {
            run(Minecraft.getInstance(), game, step++);
        } catch (Exception | Error failure) {
            fail(failure.toString());
            LOGGER.error("Lads kill streak capture step {} failed", step - 1, failure);
            step = LAST;
            wait = 1;
        }
    }

    private static void run(Minecraft mc, Path game, int at) throws Exception {
        KillBannerModule module = module();
        KillBannerTimeline banner = NativeKillBanner.timeline();
        switch (at) {
            case 0 -> {
                enabledBefore = module.isEnabled();
                modifiedBefore = module.getLastModified();
                for (Option option : module.getOptions()) OPTIONS.add(option.save().deepCopy());
                LOGGER.info("Lads kill streak capture BEGIN: rapid kills, server kill messages, the streak timer, Unlimited and death; no kill statistic changed");
                // The detection probe (synthetic packets about client-only stand-ins) also runs here: requested-features runs skip it.
                int detection = NativeKillBannerProbe.run();
                check(detection > 0, "the kill banner detection probe passed (" + detection + " checks)");
                module.getOptions().forEach(Option::reset);
                module.setEnabled(true);
                module.mobs.set(true);
                module.bannerStyle.setIndex(KillBannerModule.BASE); // its label reads the kill count
                module.duration.setValue(2);
                check(module.streakReset.get() == 45 && module.streakWindow() == 45_000_000_000L, "Streak Reset starts at 45 seconds");
                began = System.nanoTime();
                NativeKillBanner.reset();
                NativeKillBanner.bindCurrent();
                pigs(5);
                wait = 20;
            }
            case 1 -> {
                start("same-tick");
                server(sp -> { for (int id : PIGS) killAsPlayer(sp, id); }); // one server task: five deaths in one server tick
                wait = 50;
            }
            case 2 -> {
                end(List.of(1, 2, 3, 4, 5), "five pigs killed in one server tick show banners 1 to 5 in turn");
                check(banner.streak() == 5, "and all five count (streak " + banner.streak() + ")");
                pigs(5);
                server(sp -> { for (int id : PIGS) if (sp.level().getEntity(id) instanceof LivingEntity pig) pig.setHealth(.1f); });
                wait = 20;
            }
            case 3, 4, 5, 6, 7 -> {
                if (at == 3) { banner.clear(); start("tick-apart"); }
                Entity pig = mc.level.getEntity(PIGS.get(at - 3));
                check(pig != null, "pig " + (at - 2) + " is in the client world");
                mc.gameMode.attack(mc.player, pig); // the player's own blow (KillBannerAttackMixin), one each client tick
                wait = at == 7 ? 50 : 1;
            }
            case 8 -> {
                end(List.of(1, 2, 3, 4, 5), "five pigs killed by the player's blows one tick apart show banners 1 to 5 in turn");
                check(banner.streak() == 5, "and all five count (streak " + banner.streak() + ")");
                chat(mc, module, banner);
                wait = 10;
            }
            case 9 -> {
                banner.clear();
                module.streakReset.setValue("abc");
                check(module.streakReset.get() == 45, "typing 'abc' as Streak Reset keeps 45");
                module.streakReset.setValue("3");
                check(module.streakReset.get() == 3, "typing 3 as Streak Reset sets 3 seconds");
                pigs(4);
                wait = 20;
            }
            case 10 -> { start("timer"); server(sp -> killAsPlayer(sp, PIGS.get(0))); wait = 20; }
            case 11 -> { server(sp -> killAsPlayer(sp, PIGS.get(1))); wait = 70; }
            case 12 -> {
                check(banner.streak() == 2, "a kill 1 s after the first: streak 2 (" + banner.streak() + ")");
                server(sp -> killAsPlayer(sp, PIGS.get(2)));
                wait = 10;
            }
            case 13 -> {
                check(banner.streak() == 1, "a kill 3.5 s later, past the 3 s Streak Reset: a new streak (" + banner.streak() + ")");
                module.unlimitedStreak.set(true);
                wait = 80;
            }
            case 14 -> { server(sp -> killAsPlayer(sp, PIGS.get(3))); wait = 10; }
            case 15 -> {
                check(banner.streak() == 2, "Unlimited Streak: a kill 4 s later still counts (streak " + banner.streak() + ")");
                end(List.of(1, 2, 1, 2), "the timer run's banners");
                pigs(1);
                wait = 20;
            }
            case 16 -> { start("death"); server(sp -> killAsPlayer(sp, PIGS.get(0))); wait = 10; }
            case 17 -> {
                check(banner.streak() == 1, "a kill before dying (streak " + banner.streak() + ")");
                server(sp -> sp.kill(sp.level()));
                wait = 20;
            }
            case 18 -> {
                check(mc.gui.screen() instanceof DeathScreen, "QA died (death screen)");
                check(banner.streak() == 0, "death ends the streak, Unlimited too (streak " + banner.streak() + ")");
                mc.player.respawn();
                wait = 30;
            }
            case 19 -> {
                if (mc.gui.screen() instanceof DeathScreen) mc.gui.setScreen(null);
                check(mc.player != null && mc.player.isAlive(), "QA respawned");
                pigs(1);
                wait = 20;
            }
            case 20 -> { server(sp -> killAsPlayer(sp, PIGS.get(0))); wait = 10; }
            case 21 -> {
                check(banner.streak() == 1, "after respawning the next kill starts at 1 (streak " + banner.streak() + ")");
                end(List.of(1, 1), "the death run's banners");
                wait = 5;
            }
            default -> finish(game);
        }
    }

    /** Server kill messages through ClientPacketListener.handleSystemChat, each after a new hit as in a fight. */
    private static void chat(Minecraft mc, KillBannerModule module, KillBannerTimeline banner) {
        banner.clear();
        String me = mc.player.getGameProfile().name();
        RemotePlayer bob = standIn(mc, "LadsQaBob", FIRST_ID), archer = standIn(mc, "LadsQaArcher", FIRST_ID + 1);
        try {
            String[][] lines = {
                {"kill", "LadsQaBob was killed by ME."}, // Hypixel Bed Wars, SkyWars, Duels
                {"kill", "§9LadsQaBob §7was knocked into the void by §cME§7. §b§lFINAL KILL!"},
                {"kill", "LadsQaBob was spooked by ME."}, // a Bed Wars kill effect's wording
                {"kill", "[MVP+] LadsQaBob was killed by [VIP] ME."},
                {"kill", "KILL! on [120] LadsQaBob +15.00XP +10.00g"}, // The Pit
                {"kill", "LadsQaBob (4.5❤) was killed by ME (10.0❤)"}, // practice servers
                {"kill", "» LadsQaBob was slain by ME"},
                {"kill", "You killed LadsQaBob!"},
                {"kill", "LadsQaBob was shot by ME using [Bow]"}, // vanilla wording
                {"kill", "LadsQaBob didn't want to live in the same world as ME"},
                {"none", "[MVP+] LadsQaBob: gg ME"},
                {"none", "LadsQaBob was killed by Steve, assisted by ME."},
                {"none", "BED DESTRUCTION > Red Bed was destroyed by ME!"},
            };
            for (String[] line : lines) {
                NativeKillBanner.attacked(bob);
                expect(line[1].replace("ME", me), Component.literal(line[1].replace("ME", me)), line[0].equals("kill"), banner);
            }
            // No hit on the archer (an arrow, a rod's knock-off): a plain kill message about a player in the world still counts.
            expect("LadsQaArcher was shot by " + me + ".", Component.literal("LadsQaArcher was shot by " + me + "."), true, banner);
            NativeKillBanner.attacked(bob);
            expect("vanilla death.attack.player (by its key)", Component.translatable("death.attack.player", Component.literal("LadsQaBob"), Component.literal(me)), true, banner);
            module.unlimitedStreak.set(true);
            int before = banner.streak();
            send(Component.literal(me + " was killed by LadsQaBob."));
            check(before > 0 && banner.streak() == 0, "'" + me + " was killed by LadsQaBob.' (the player's own death, a server message) ends the streak, Unlimited too");
            module.unlimitedStreak.set(false);
        } finally {
            mc.level.removeEntity(FIRST_ID, Entity.RemovalReason.DISCARDED);
            mc.level.removeEntity(FIRST_ID + 1, Entity.RemovalReason.DISCARDED);
        }
    }

    private static void expect(String name, Component line, boolean kill, KillBannerTimeline banner) {
        int before = banner.streak();
        send(line);
        int after = banner.streak();
        check(after == before + (kill ? 1 : 0), "'" + name + "' " + (kill ? "counts as a kill" : "is no kill") + " (streak " + before + " -> " + after + ")");
    }

    private static void send(Component line) {
        Minecraft.getInstance().getConnection().handleSystemChat(new ClientboundSystemChatPacket(line, false));
    }

    private static RemotePlayer standIn(Minecraft mc, String name, int id) {
        RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), name));
        player.setId(id);
        player.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ() + 3);
        mc.level.addEntity(player);
        return player;
    }

    /** {@code count} new pigs (no AI) in a ring 1.5 blocks round the player, their ids in PIGS once the server made them. */
    private static void pigs(int count) {
        PIGS.clear();
        server(sp -> {
            for (int i = 0; i < count; i++) {
                var pig = EntityTypes.PIG.create(sp.level(), EntitySpawnReason.COMMAND);
                double angle = Math.PI * 2 * i / count;
                pig.setPos(sp.getX() + Math.cos(angle) * 1.5, sp.getY(), sp.getZ() + Math.sin(angle) * 1.5);
                pig.setNoAi(true);
                sp.level().addFreshEntity(pig);
                PIGS.add(pig.getId());
            }
        });
    }

    /** Server thread: the pig dies of the player's attack (a damage event naming the player, then its death event). */
    private static void killAsPlayer(ServerPlayer sp, int id) {
        if (sp.level().getEntity(id) instanceof LivingEntity pig) pig.hurtServer(sp.level(), sp.damageSources().playerAttack(sp), 1000f);
        else fail("pig " + id + " missing on the server");
    }

    private static void start(String name) {
        run = name;
        SHOWN.clear();
        lastSequence = 0;
        lastAge = -1;
    }

    private static void end(List<Integer> expected, String what) {
        check(SHOWN.equals(expected), what + ": shown " + SHOWN);
        run = null;
    }

    /** Each client tick of a run: the banner traced, and each new banner noted (a new count, or the same count started again). */
    private static void observe() {
        if (run == null) return;
        KillBannerTimeline banner = NativeKillBanner.timeline();
        long now = System.nanoTime();
        double age = banner.age(now);
        int sequence = age >= 0 ? banner.sequence() : 0;
        TRACE.append(String.format(java.util.Locale.ROOT, "%.1f,%s,%d,%d,%d,%.3f%n", (now - began) / 1e6, run, banner.streak(), banner.queued(), sequence, age));
        if (sequence > 0 && (sequence != lastSequence || age < lastAge)) {
            SHOWN.add(sequence);
            LOGGER.info("Lads kill streak banner: {} #{} shows {} (streak {}, {} waiting)", run, SHOWN.size(), sequence, banner.streak(), banner.queued());
        }
        lastSequence = sequence;
        lastAge = age;
    }

    private static void finish(Path game) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof DeathScreen) { mc.player.respawn(); mc.gui.setScreen(null); }
        run = null;
        KillBannerModule module = module();
        for (int i = 0; i < OPTIONS.size(); i++) module.getOptions().get(i).load(OPTIONS.get(i));
        module.setEnabled(enabledBefore);
        module.setLastModified(modifiedBefore);
        NativeKillBanner.timeline().clear();
        try {
            Path trace = game.resolve("screenshots").resolve("killstreak").resolve("killstreak-trace.csv");
            Files.createDirectories(trace.getParent());
            Files.writeString(trace, TRACE, StandardCharsets.UTF_8);
        }
        catch (Exception failure) { fail("killstreak-trace.csv: " + failure); }
        step = LAST + 1;
        if (FAILURES.isEmpty()) LOGGER.info("Lads kill streak capture END: {} checks passed, 0 failed", passed);
        else LOGGER.error("Lads kill streak capture FAILED: {} | {} checks passed", String.join(" | ", FAILURES), passed);
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

    private static KillBannerModule module() { return (KillBannerModule) NativeQualityOfLife.module("KillBanner"); }

    private static void check(boolean result, String description) {
        if (result) { passed++; LOGGER.info("Lads kill streak capture PASS: {}", description); }
        else fail(description);
    }

    private static void fail(String failure) {
        FAILURES.add(failure);
        LOGGER.error("Lads kill streak capture check failed: {}", failure);
    }
}
