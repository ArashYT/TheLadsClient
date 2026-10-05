package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.thelads.core.client.KillBannerTimeline;
import com.thelads.core.client.killbanner.KillBanners;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.KillBannerModule;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only: the kill streak in the 1.8.9 QA world, as 26.x KillStreakCapture (CoreProbe focus "killstreak", from the harness's
 * LADS_VERIFY_189_FOCUS=killstreak; no synthetic input). Five pigs finished by the player's own blows (PlayerControllerMP.attackEntity)
 * in one client tick, then one tick apart: the banner (Base, whose label reads the count) must show 1 to 5 in turn
 * (lads-qa/screenshots/ks-&lt;run&gt;-&lt;n&gt;-k&lt;count&gt;.png, every tick in lads-qa/screenshots/killstreak-trace.csv). Then server kill
 * messages through NetHandlerPlayClient.handleChat, the streak timer typed as 3 s, Unlimited and the player's death.
 */
final class Probe172KillStreak {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final List<JsonElement> WAS = new ArrayList<JsonElement>();
    private static final List<Integer> PIGS = Collections.synchronizedList(new ArrayList<Integer>());
    private static final List<Integer> SHOWN = new ArrayList<Integer>();
    private static final StringBuilder TRACE = new StringBuilder("ms,run,streak,queued,sequence,age\n");
    private static final String[][] LINES = {
        {"kill", "LadsQaBob was killed by ME."}, // Hypixel Bed Wars, SkyWars, Duels
        {"kill", "§9LadsQaBob §7was knocked into the void by §cME§7. §b§lFINAL KILL!"},
        {"kill", "LadsQaBob was spooked by ME."}, // a Bed Wars kill effect's wording
        {"kill", "[MVP+] LadsQaBob was killed by [VIP] ME."},
        {"kill", "KILL! on [120] LadsQaBob +15.00XP +10.00g"}, // The Pit
        {"kill", "LadsQaBob (4.5❤) was killed by ME (10.0❤)"}, // practice servers
        {"kill", "» LadsQaBob was slain by ME"},
        {"kill", "You killed LadsQaBob!"},
        {"kill", "LadsQaBob was shot by ME using [Bow]"}, // vanilla wording
        {"kill", "LadsQaBob walked into a cactus whilst trying to escape ME"},
        {"none", "[MVP+] LadsQaBob: gg ME"},
        {"none", "LadsQaBob was killed by Steve, assisted by ME."},
        {"none", "BED DESTRUCTION > Red Bed was destroyed by ME!"},
    };
    private static boolean enabledWas, picture;
    private static String run;
    private static int lastSequence, samples, pictures;
    private static double lastAge;
    private static long began;

    private Probe172KillStreak() {}

    static List<CoreProbe.Step> steps() {
        List<CoreProbe.Step> steps = new ArrayList<CoreProbe.Step>();
        steps.add(Probe172KillStreak::start);
        steps.add(mc -> pigs(mc, 5));
        // Five blows in one client tick: the server takes all five in its next tick, so the deaths arrive together.
        steps.add(mc -> { begin("same-tick"); for (int id : new ArrayList<Integer>(PIGS)) attack(mc, id); return true; });
        steps.add(mc -> sample(mc, 50));
        steps.add(mc -> {
            end(Arrays.asList(1, 2, 3, 4, 5), "five pigs finished in one tick show banners 1 to 5 in turn");
            check(streak() == 5, "KillStreak: and all five count (streak " + streak() + ")");
            return pigs(mc, 5);
        });
        for (int i = 0; i < 5; i++) {
            final int n = i;
            steps.add(mc -> {
                if (n == 0) { KillBanners.TIMELINE.clear(); begin("tick-apart"); }
                attack(mc, PIGS.get(n));
                observe(mc);
                return true; // the next blow on the next tick
            });
        }
        steps.add(mc -> sample(mc, 50));
        steps.add(mc -> {
            end(Arrays.asList(1, 2, 3, 4, 5), "five pigs finished one tick apart show banners 1 to 5 in turn");
            check(streak() == 5, "KillStreak: and all five count (streak " + streak() + ")");
            return chat(mc);
        });
        steps.add(mc -> {
            KillBanners.TIMELINE.clear();
            banner().streakReset.setValue("abc");
            check(banner().streakReset.get() == 45, "KillStreak: typing 'abc' as Streak Reset keeps 45");
            banner().streakReset.setValue("3");
            check(banner().streakReset.get() == 3, "KillStreak: typing 3 as Streak Reset sets 3 seconds");
            return pigs(mc, 4);
        });
        steps.add(mc -> { begin("timer"); attack(mc, PIGS.get(0)); return true; });
        steps.add(mc -> sample(mc, 20));
        steps.add(mc -> { attack(mc, PIGS.get(1)); return true; });
        steps.add(mc -> sample(mc, 10));
        steps.add(mc -> { check(streak() == 2, "KillStreak: a kill 1 s after the first: streak 2 (" + streak() + ")"); return true; });
        steps.add(mc -> sample(mc, 60));
        steps.add(mc -> { attack(mc, PIGS.get(2)); return true; });
        steps.add(mc -> sample(mc, 10));
        steps.add(mc -> {
            check(streak() == 1, "KillStreak: a kill 3.5 s later, past the 3 s Streak Reset: a new streak (" + streak() + ")");
            banner().unlimitedStreak.set(true);
            return true;
        });
        steps.add(mc -> sample(mc, 80));
        steps.add(mc -> { attack(mc, PIGS.get(3)); return true; });
        steps.add(mc -> sample(mc, 10));
        steps.add(mc -> {
            check(streak() == 2, "KillStreak: Unlimited Streak: a kill 4 s later still counts (streak " + streak() + ")");
            end(Arrays.asList(1, 2, 1, 2), "the timer run's banners");
            return pigs(mc, 1);
        });
        steps.add(mc -> { begin("death"); attack(mc, PIGS.get(0)); return true; });
        steps.add(mc -> sample(mc, 10));
        steps.add(mc -> {
            check(streak() == 1, "KillStreak: a kill before dying (streak " + streak() + ")");
            final UUID id = mc.thePlayer.getUniqueID();
            mc.getIntegratedServer().addScheduledTask(() -> mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id)
                .attackEntityFrom(DamageSource.outOfWorld, Float.MAX_VALUE));
            return after(10);
        });
        steps.add(mc -> {
            if (!(mc.currentScreen instanceof GuiGameOver)) return retry(2);
            check(streak() == 0, "KillStreak: death ends the streak, Unlimited too (streak " + streak() + ")");
            mc.thePlayer.respawnPlayer();
            mc.displayGuiScreen(null);
            return after(30);
        });
        steps.add(mc -> pigs(mc, 1));
        steps.add(mc -> { attack(mc, PIGS.get(0)); return true; });
        steps.add(mc -> sample(mc, 10));
        steps.add(mc -> {
            check(streak() == 1, "KillStreak: after respawning the next kill starts at 1 (streak " + streak() + ")");
            end(Arrays.asList(1, 1), "the death run's banners");
            return true;
        });
        steps.add(Probe172KillStreak::finish);
        return steps;
    }

    private static KillBannerModule banner() { return (KillBannerModule) ModuleManager.getInstance().getModule("KillBanner"); }

    private static int streak() { return KillBanners.TIMELINE.streak(); }

    private static boolean start(Minecraft mc) {
        KillBannerModule module = banner();
        enabledWas = module.isEnabled();
        for (Option option : module.getOptions()) WAS.add(option.save());
        for (Option option : module.getOptions()) option.reset();
        module.setEnabled(true);
        module.mobs.set(true);
        module.bannerStyle.setIndex(KillBannerModule.BASE); // its label reads the kill count
        module.duration.setValue(2);
        check(module.streakReset.get() == 45 && module.streakWindow() == 45_000_000_000L, "KillStreak: Streak Reset starts at 45 seconds");
        began = System.nanoTime();
        KillBanner189.reset();
        KillBanner189.bindCurrent();
        return after(5);
    }

    /** {@code count} new pigs (no AI, half a heart: one blow) in a ring 1.5 blocks round the player, made on the server. */
    private static boolean pigs(Minecraft mc, final int count) {
        PIGS.clear();
        final UUID id = mc.thePlayer.getUniqueID();
        mc.getIntegratedServer().addScheduledTask(() -> {
            EntityPlayerMP player = mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id);
            for (int i = 0; i < count; i++) {
                EntityPig pig = new EntityPig(player.worldObj);
                double angle = Math.PI * 2 * i / count;
                pig.setLocationAndAngles(player.posX + Math.cos(angle) * 1.5, player.posY, player.posZ + Math.sin(angle) * 1.5, 0, 0);
                pig.setNoAI(true);
                player.worldObj.spawnEntityInWorld(pig);
                pig.setHealth(.5F);
                PIGS.add(pig.getEntityId());
            }
        });
        return after(20);
    }

    /** The player's own blow, as a click sends it: the attack packet, and Forge's attack event on the client (KillBanner189). */
    private static void attack(Minecraft mc, int id) {
        Entity pig = mc.theWorld.getEntityByID(id);
        check(pig instanceof EntityPig, "KillStreak: pig " + id + " is in the client world");
        mc.playerController.attackEntity(mc.thePlayer, pig);
    }

    /** Server kill messages through NetHandlerPlayClient.handleChat (Forge's chat event), each after a new hit as in a fight. */
    private static boolean chat(Minecraft mc) {
        KillBanners.TIMELINE.clear();
        String me = mc.thePlayer.getName();
        EntityOtherPlayerMP bob = standIn(mc, "LadsQaBob", -1600);
        standIn(mc, "LadsQaArcher", -1601);
        try {
            for (String[] line : LINES) {
                MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(mc.thePlayer, bob));
                expect(mc, line[1].replace("ME", me), new ChatComponentText(line[1].replace("ME", me)), line[0].equals("kill"));
            }
            // No hit on the archer (an arrow, a rod's knock-off): a plain kill message about a player in the world still counts.
            expect(mc, "LadsQaArcher was shot by " + me + ".", new ChatComponentText("LadsQaArcher was shot by " + me + "."), true);
            MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(mc.thePlayer, bob));
            expect(mc, "vanilla death.attack.player (by its key)", new ChatComponentTranslation("death.attack.player", "LadsQaBob", me), true);
            banner().unlimitedStreak.set(true);
            int before = streak();
            send(mc, new ChatComponentText(me + " was killed by LadsQaBob."));
            check(before > 0 && streak() == 0, "KillStreak: '" + me + " was killed by LadsQaBob.' (the player's own death, a server message) ends the streak, Unlimited too");
            banner().unlimitedStreak.set(false);
        } finally {
            mc.theWorld.removeEntityFromWorld(-1600);
            mc.theWorld.removeEntityFromWorld(-1601);
        }
        return after(10);
    }

    private static void expect(Minecraft mc, String name, IChatComponent line, boolean kill) {
        int before = streak();
        send(mc, line);
        int after = streak();
        check(after == before + (kill ? 1 : 0), "KillStreak: '" + name + "' " + (kill ? "counts as a kill" : "is no kill") + " (streak " + before + " -> " + after + ")");
    }

    private static void send(Minecraft mc, IChatComponent line) {
        mc.getNetHandler().handleChat(new S02PacketChat(line, (byte) 1));
    }

    private static EntityOtherPlayerMP standIn(Minecraft mc, String name, int id) {
        EntityOtherPlayerMP player = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), name));
        player.setPosition(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ + 3);
        mc.theWorld.addEntityToWorld(id, player);
        return player;
    }

    private static void begin(String name) {
        run = name;
        SHOWN.clear();
        lastSequence = 0;
        lastAge = -1;
        picture = false;
    }

    private static void end(List<Integer> expected, String what) {
        check(SHOWN.equals(expected), "KillStreak: " + what + ": shown " + SHOWN);
        run = null;
    }

    /** Samples the banner each tick for {@code ticks} ticks (a banner lasts at least 5). */
    private static boolean sample(Minecraft mc, int ticks) {
        observe(mc);
        if (++samples < ticks) return retry(0);
        samples = 0;
        return true;
    }

    /** The banner traced, and a picture of each new banner about 0.1 s in (Base has faded in). */
    private static void observe(Minecraft mc) {
        if (run == null) return;
        KillBannerTimeline banner = KillBanners.TIMELINE;
        long now = System.nanoTime();
        double age = banner.age(now);
        int sequence = age >= 0 ? banner.sequence() : 0;
        TRACE.append(String.format(java.util.Locale.ROOT, "%.1f,%s,%d,%d,%d,%.3f%n", (now - began) / 1e6, run, banner.streak(), banner.queued(), sequence, age));
        if (sequence > 0 && (sequence != lastSequence || age < lastAge)) { SHOWN.add(sequence); picture = true; }
        lastSequence = sequence;
        lastAge = age;
        if (picture && age >= .1) {
            CoreProbe.screenshot(mc, "ks-" + run + "-" + SHOWN.size() + "-k" + sequence);
            picture = false;
            pictures++;
        }
    }

    private static boolean finish(Minecraft mc) throws Exception {
        KillBannerModule module = banner();
        for (int i = 0; i < WAS.size(); i++) module.getOptions().get(i).load(WAS.get(i));
        module.setEnabled(enabledWas);
        KillBanner189.reset();
        File trace = new File(new File(mc.mcDataDir, "lads-qa"), "screenshots/killstreak-trace.csv");
        Files.write(trace.toPath(), TRACE.toString().getBytes(StandardCharsets.UTF_8));
        LOG.info("Lads 1.8.9 kill streak: {} banners pictured, trace {}", pictures, trace);
        return after(5);
    }
}
