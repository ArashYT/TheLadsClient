package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;

import com.thelads.core.client.WorldCheats;
import com.thelads.core.client.killbanner.KillBannerStyle;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiShareToLan;
import net.minecraft.init.Items;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only: the 1.7.0 checks, run by CoreProbe as a focused self-test (-Dthelads.verify189Focus, from the harness's
 * LADS_VERIFY_189_FOCUS) with Essential loaded as players have it.
 * <ul>
 * <li>cheats-set: Open to LAN with Allow Cheats in the QA world (the real screen and its Start button).</li>
 * <li>cheats-check: after a restart, the QA world still has cheats and the LAN screen starts on Allow Cheats.</li>
 * <li>cheats-create: a new world made with Allow Cheats keeps them once Essential has the world, and after it is reopened.</li>
 * <li>cheats-reset: the QA world back to no cheats, the new world deleted.</li>
 * <li>gaps: a sword, stick and bow held against the sky (1.7 Animations off), then the three dropped on the ground close
 * up: lads-qa/screenshots/170-gap-*.png, for item model gaps.</li>
 * <li>inventory: the inventories with and without potion effects, centred (ProbeInventory).</li>
 * <li>killbanner: Rogue's opening for 1 to 5 kills, captured about 150, 250 and 350 ms after each kill
 * (lads-qa/screenshots/170-rogue-k*-*ms.png).</li>
 * <li>killbanners: every skin's sounds registered with their samples; Base, Reaver, Rogue and one skin of each Kingdom
 * Archives kind for 1 to 5 kills, headshots and variants, then the settings picker
 * (lads-qa/screenshots/170-kb-*.png).</li>
 * <li>loading: three rounds of opening the QA world, the Nether and back, a respawn and leaving, each timed from the
 * action to the player in the world with no screen (client ticks, 50 ms apart; opening and leaving block the game, so
 * their own part is exact). The times go to the log ("Lads 1.8.9 load timing").</li>
 * </ul>
 */
final class Probe170Misc {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final String QA = "Client QA 1_8_9", MADE = "Lads QA cheats 1_8_9";
    private static long since;

    private Probe170Misc() {}

    static List<CoreProbe.Step> steps(String focus) {
        List<CoreProbe.Step> steps = new ArrayList<>();
        switch (focus) {
            case "cheats-set":
                steps.addAll(open(QA));
                steps.addAll(Arrays.<CoreProbe.Step>asList(Probe170Misc::lanScreen, Probe170Misc::lanCheatsOn, Probe170Misc::lanStarted));
                steps.add(Probe170Misc::leave);
                break;
            case "cheats-create":
                steps.add(Probe170Misc::create);
                steps.addAll(ready());
                steps.add(mc -> cheatsKept(mc, "a new world made with Allow Cheats keeps them once Essential has the world"));
                steps.add(Probe170Misc::leave);
                steps.addAll(open(MADE));
                steps.add(mc -> cheatsKept(mc, "the world made with Allow Cheats still has them when reopened"));
                steps.add(Probe170Misc::leave);
                steps.add(Probe170Misc::deleteMade);
                break;
            case "cheats-check":
                steps.addAll(open(QA));
                steps.add(mc -> cheatsKept(mc, "Open to LAN's Allow Cheats stayed with the QA world after a restart"));
                steps.addAll(Arrays.<CoreProbe.Step>asList(Probe170Misc::lanScreen, Probe170Misc::lanRemembered));
                steps.add(Probe170Misc::leave);
                break;
            case "cheats-reset":
                steps.addAll(open(QA));
                steps.add(Probe170Misc::reset);
                steps.add(Probe170Misc::leave);
                steps.add(Probe170Misc::deleteMade);
                break;
            case "gaps":
                steps.addAll(open(QA));
                steps.add(Probe170Misc::gapStart);
                for (net.minecraft.item.Item item : new net.minecraft.item.Item[] {Items.diamond_sword, Items.stick, Items.bow}) {
                    steps.add(mc -> gapHold(mc, item));
                    steps.add(mc -> { CoreProbe.screenshot(mc, "170-gap-" + item.getUnlocalizedName().replace("item.", "")); return after(5); });
                }
                steps.add(Probe170Misc::gapDropped);
                steps.add(mc -> { CoreProbe.screenshot(mc, "170-gap-dropped"); return after(5); });
                steps.add(Probe170Misc::gapEnd);
                steps.add(Probe170Misc::leave);
                break;
            case "inventory":
                steps.addAll(open(QA));
                steps.addAll(ProbeInventory.steps());
                steps.add(Probe170Misc::leave);
                break;
            case "killbanner":
                steps.addAll(open(QA));
                steps.add(Probe170Misc::bannerStart);
                for (int kills = 1; kills <= 5; kills++) {
                    final int k = kills;
                    steps.add(mc -> { KillBanner189.reset(); KillBanner189.trigger(k, true); return after(3); });
                    for (int ms = 150; ms <= 350; ms += 100) {
                        final int at = ms;
                        steps.add(mc -> { CoreProbe.screenshot(mc, "170-rogue-k" + k + "-" + at + "ms"); return after(2); });
                    }
                    steps.add(mc -> { KillBanner189.reset(); return after(10); });
                }
                steps.add(Probe170Misc::bannerEnd);
                steps.add(Probe170Misc::leave);
                break;
            case "killbanners":
                steps.addAll(open(QA));
                steps.add(mc -> { focusSound = true; return true; });
                steps.add(Probe170Misc::bannerStart);
                steps.add(Probe170Misc::bannerSounds);
                for (KillBannerStyle skin : new KillBannerStyle[] {KillBannerStyle.DEFAULT, KillBannerStyle.REAVER, KillBannerStyle.ROGUE,
                        KillBannerStyle.AEMONDIR, KillBannerStyle.CHAMPIONS2024, KillBannerStyle.PHASEGUARD})
                    for (int kills = 1; kills <= 5; kills++) bannerShot(steps, skin, 0, kills, false, skin.isAnimated() && kills == 5 ? 3700 : 1000);
                bannerShot(steps, KillBannerStyle.DEFAULT, 0, 1, true, 600);
                bannerShot(steps, KillBannerStyle.AEMONDIR, 0, 1, true, 600);
                for (int variant = 1; variant <= 3; variant++) {
                    bannerShot(steps, KillBannerStyle.AEMONDIR, variant, 3, false, 1000);
                    bannerShot(steps, KillBannerStyle.PHASEGUARD, variant, 3, false, 1000);
                }
                for (String picker : new String[] {"base", "variants", "search"}) {
                    steps.add(mc -> {
                        com.thelads.core.modules.KillBannerModule module = banner();
                        module.bannerStyle.setIndex(picker.equals("base") ? com.thelads.core.modules.KillBannerModule.BASE
                            : com.thelads.core.modules.KillBannerModule.styleIndexOf(KillBannerStyle.AEMONDIR));
                        module.setVariant(KillBannerStyle.AEMONDIR, 2);
                        com.thelads.core.v1_8_9.gui.LadsSettingsScreen189 settings = new com.thelads.core.v1_8_9.gui.LadsSettingsScreen189(null);
                        mc.displayGuiScreen(settings);
                        settings.openModule("KillBanner");
                        settings.searchKillBanners(picker.equals("search") ? "phase" : "");
                        return after(20);
                    });
                    steps.add(mc -> { CoreProbe.screenshot(mc, "170-kb-picker-" + picker); mc.displayGuiScreen(null); return after(5); });
                }
                steps.add(Probe170Misc::bannerEnd);
                steps.add(Probe170Misc::leave);
                break;
            case "loading":
                steps.addAll(open(QA)); // a first open, untimed: a QA player left dead respawns, and the game has loaded a world once
                steps.add(Probe170Misc::leave);
                for (int round = 1; round <= 3; round++) {
                    steps.add(Probe170Misc::timeOpen);
                    steps.add(Probe170Misc::arrived);
                    steps.add(mc -> travel(mc, -1));
                    steps.add(Probe170Misc::arrived);
                    steps.add(mc -> travel(mc, 0));
                    steps.add(Probe170Misc::arrived);
                    steps.add(Probe170Misc::die);
                    steps.add(Probe170Misc::respawn);
                    steps.add(Probe170Misc::arrived);
                    steps.add(Probe170Misc::timeLeave);
                }
                steps.add(Probe170Misc::timings);
                break;
            default:
                throw new IllegalArgumentException("Unknown 1.8.9 QA focus " + focus);
        }
        return steps;
    }

    private static List<CoreProbe.Step> open(String world) {
        List<CoreProbe.Step> steps = new ArrayList<>();
        steps.add(mc -> {
            LOG.info("Lads 1.8.9 core probe: opening '{}'", world);
            mc.launchIntegratedServer(world, world, null);
            return true;
        });
        steps.addAll(ready());
        return steps;
    }

    /** In the world, unpaused, and 6 s for Essential to put its world settings on it. */
    private static List<CoreProbe.Step> ready() {
        return Arrays.<CoreProbe.Step>asList(mc -> {
            if (respawned(mc) || mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return retry(5);
            since = System.nanoTime();
            return true;
        }, mc -> System.nanoTime() - since > 6_000_000_000L || retry(5));
    }

    private static boolean animationsWere;
    private static float pitchWas;
    private static net.minecraft.item.ItemStack heldWas;
    private static long timeWas;

    private static void onServer(Minecraft mc, java.util.function.Consumer<net.minecraft.entity.player.EntityPlayerMP> task) {
        java.util.UUID id = mc.thePlayer.getUniqueID();
        mc.getIntegratedServer().addScheduledTask(() -> task.accept(mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id)));
    }

    private static boolean gapStart(Minecraft mc) {
        animationsWere = OldAnimations189.MODULE.isEnabled();
        OldAnimations189.MODULE.setEnabled(false); // the item models as Minecraft holds them
        pitchWas = mc.thePlayer.rotationPitch;
        heldWas = mc.thePlayer.getHeldItem() == null ? null : mc.thePlayer.getHeldItem().copy();
        timeWas = mc.getIntegratedServer().worldServers[0].getWorldTime();
        onServer(mc, player -> player.worldObj.setWorldTime(6000)); // noon: a bright sky behind the items
        return after(30);
    }

    private static boolean gapHold(Minecraft mc, net.minecraft.item.Item item) {
        int slot = mc.thePlayer.inventory.currentItem;
        onServer(mc, player -> player.inventory.mainInventory[slot] = new net.minecraft.item.ItemStack(item));
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = -60; // the sky behind the held item
        return after(25);
    }

    /** The three on the ground 1.3 blocks ahead, looked down at. */
    private static boolean gapDropped(Minecraft mc) {
        int slot = mc.thePlayer.inventory.currentItem;
        double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
        double ahead = 1.3, x = mc.thePlayer.posX - Math.sin(yaw) * ahead, z = mc.thePlayer.posZ + Math.cos(yaw) * ahead;
        double sideX = Math.cos(yaw) * 0.45, sideZ = Math.sin(yaw) * 0.45;
        net.minecraft.item.Item[] items = {Items.diamond_sword, Items.stick, Items.bow};
        double y = mc.thePlayer.posY + 0.1;
        onServer(mc, player -> {
            player.inventory.mainInventory[slot] = null;
            for (int i = 0; i < items.length; i++) {
                net.minecraft.entity.item.EntityItem dropped = new net.minecraft.entity.item.EntityItem(player.worldObj,
                    x + sideX * (i - 1), y, z + sideZ * (i - 1), new net.minecraft.item.ItemStack(items[i]));
                dropped.motionX = dropped.motionY = dropped.motionZ = 0;
                dropped.setInfinitePickupDelay();
                player.worldObj.spawnEntityInWorld(dropped);
            }
        });
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 55;
        return after(30);
    }

    private static boolean gapEnd(Minecraft mc) {
        int slot = mc.thePlayer.inventory.currentItem;
        net.minecraft.item.ItemStack held = heldWas;
        onServer(mc, player -> {
            player.inventory.mainInventory[slot] = held;
            for (net.minecraft.entity.item.EntityItem item : player.worldObj.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,
                player.getEntityBoundingBox().expand(6, 4, 6))) item.setDead();
        });
        long time = timeWas;
        onServer(mc, player -> player.worldObj.setWorldTime(time));
        OldAnimations189.MODULE.setEnabled(animationsWere);
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = pitchWas;
        return after(10);
    }

    private static final List<com.google.gson.JsonElement> BANNER_WAS = new ArrayList<>();
    private static boolean bannerWas;

    private static com.thelads.core.modules.KillBannerModule banner() {
        return (com.thelads.core.modules.KillBannerModule) com.thelads.core.config.ModuleManager.getInstance().getModule("KillBanner");
    }

    /** killbanners: sound on, through the real play path (the QA game is muted), so an unknown sound event is logged. */
    private static boolean focusSound;

    private static boolean bannerStart(Minecraft mc) {
        com.thelads.core.modules.KillBannerModule module = banner();
        bannerWas = module.isEnabled();
        for (com.thelads.core.config.Option option : module.getOptions()) BANNER_WAS.add(option.save());
        module.setEnabled(true);
        module.bannerStyle.setIndex(com.thelads.core.modules.KillBannerModule.ROGUE);
        module.rogueVariant.setIndex(0);
        module.randomize.setIndex(com.thelads.core.modules.KillBannerModule.RANDOM_OFF);
        module.sound.set(focusSound);
        module.duration.setValue(4);
        return after(10);
    }

    /** One banner: a fresh streak of {@code kills} kills of a skin and variant, captured {@code ms} after the kill (50 ms ticks). */
    private static void bannerShot(List<CoreProbe.Step> steps, KillBannerStyle skin, int variant, int kills, boolean headshot, int ms) {
        steps.add(mc -> {
            com.thelads.core.modules.KillBannerModule module = banner();
            module.bannerStyle.setIndex(com.thelads.core.modules.KillBannerModule.styleIndexOf(skin));
            module.setVariant(skin, variant);
            module.duration.setValue(6);
            module.headshotText.set(true);
            KillBanner189.reset();
            KillBanner189.trigger(kills, false, headshot);
            return after(ms / 50);
        });
        steps.add(mc -> {
            CoreProbe.screenshot(mc, "170-kb-" + skin.id + "-v" + variant + "-k" + kills + (headshot ? "-hs" : "") + "-" + ms + "ms");
            KillBanner189.reset();
            return after(4);
        });
    }

    /** Every skin's sound for 1 to 5 kills is registered with its sample (a missing file leaves the event with no weight). */
    private static boolean bannerSounds(Minecraft mc) {
        int events = 0;
        for (KillBannerStyle skin : KillBannerStyle.values())
            for (int kills = 1; kills <= 5; kills++) {
                net.minecraft.util.ResourceLocation id = new net.minecraft.util.ResourceLocation("theladscore", skin.id + "_kill_" + Math.min(kills, skin.soundCount));
                net.minecraft.client.audio.SoundEventAccessorComposite event = mc.getSoundHandler().getSound(id);
                if (event == null || event.getWeight() <= 0) check(false, "KillBanner: " + id + " is registered with its sample");
                events++;
            }
        check(true, "KillBanner: " + events + " sounds (" + KillBannerStyle.values().length + " skins, 1 to 5 kills) registered with their samples");
        return after(2);
    }

    private static boolean bannerEnd(Minecraft mc) {
        com.thelads.core.modules.KillBannerModule module = banner();
        for (int i = 0; i < BANNER_WAS.size(); i++) module.getOptions().get(i).load(BANNER_WAS.get(i));
        module.setEnabled(bannerWas);
        KillBanner189.reset();
        return after(5);
    }

    private static String timing = "";
    private static final java.util.Map<String, List<Long>> TIMES = new java.util.LinkedHashMap<>();

    private static void timed(String what) {
        timing = what;
        since = System.nanoTime();
    }

    private static boolean timeOpen(Minecraft mc) {
        timed("open the QA world");
        mc.launchIntegratedServer(QA, QA, null); // blocks until the integrated server runs
        return true;
    }

    /** In the world (the dimension the timed step went to), alive, no screen: the loading screens are gone. */
    private static boolean arrived(Minecraft mc) {
        int dimension = timing.startsWith("go to the Nether") ? -1 : 0;
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null || mc.thePlayer.isDead
            || mc.thePlayer.dimension != dimension || mc.theWorld.provider.getDimensionId() != dimension) return retry(0);
        long ms = (System.nanoTime() - since) / 1_000_000L;
        TIMES.computeIfAbsent(timing, k -> new ArrayList<>()).add(ms);
        LOG.info("Lads 1.8.9 load timing: {} took {} ms", timing, ms);
        return after(60); // 3 s in the world between switches
    }

    private static boolean travel(Minecraft mc, int dimension) {
        timed(dimension == -1 ? "go to the Nether" : "go back to the Overworld");
        java.util.UUID id = mc.thePlayer.getUniqueID();
        net.minecraft.server.MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> {
            net.minecraft.entity.player.EntityPlayerMP player = server.getConfigurationManager().getPlayerByUUID(id);
            // No portal: the player lands at the same x and z (scaled), on top (the Nether's roof).
            server.getConfigurationManager().transferPlayerToDimension(player, dimension,
                new net.minecraft.world.Teleporter(server.worldServerForDimension(dimension)) {
                    @Override public void placeInPortal(net.minecraft.entity.Entity entity, float yaw) {
                        net.minecraft.util.BlockPos top = entity.worldObj.getTopSolidOrLiquidBlock(new net.minecraft.util.BlockPos(entity));
                        entity.setLocationAndAngles(entity.posX, dimension == -1 ? 129 : top.getY() + 1, entity.posZ, entity.rotationYaw, 0);
                    }
                });
        });
        return true;
    }

    private static boolean die(Minecraft mc) {
        java.util.UUID id = mc.thePlayer.getUniqueID();
        mc.getIntegratedServer().addScheduledTask(() -> mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id)
            .attackEntityFrom(net.minecraft.util.DamageSource.outOfWorld, Float.MAX_VALUE));
        return after(10);
    }

    private static boolean respawn(Minecraft mc) {
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.GuiGameOver)) return retry(2);
        timed("respawn");
        mc.thePlayer.respawnPlayer();
        mc.displayGuiScreen(null);
        return true;
    }

    private static boolean timeLeave(Minecraft mc) {
        long start = System.nanoTime();
        mc.theWorld.sendQuittingDisconnectingPacket();
        mc.loadWorld(null); // blocks while the integrated server saves and stops
        mc.displayGuiScreen(new GuiMainMenu());
        long ms = (System.nanoTime() - start) / 1_000_000L;
        TIMES.computeIfAbsent("leave the world", k -> new ArrayList<>()).add(ms);
        LOG.info("Lads 1.8.9 load timing: leave the world took {} ms", ms);
        return after(40);
    }

    private static boolean timings(Minecraft mc) {
        StringBuilder line = new StringBuilder();
        boolean rounds = true;
        for (java.util.Map.Entry<String, List<Long>> entry : TIMES.entrySet()) {
            long sum = 0;
            for (long ms : entry.getValue()) sum += ms;
            rounds &= entry.getValue().size() == 3;
            line.append(line.length() == 0 ? "" : "; ").append(entry.getKey()).append(' ').append(entry.getValue()).append(" ms, mean ")
                .append(sum / entry.getValue().size());
        }
        LOG.info("Lads 1.8.9 load timing summary: {}", line);
        check(TIMES.size() == 5 && rounds, "every world switch was timed three times"); // open, Nether, Overworld, respawn, leave
        return true;
    }

    /** A run that ended with the QA player dead (or lost in the void) opens on the death screen: respawn at the world's spawn. */
    private static boolean respawned(Minecraft mc) {
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.GuiGameOver) || mc.thePlayer == null) return false;
        mc.thePlayer.respawnPlayer();
        mc.displayGuiScreen(null);
        return true;
    }

    private static boolean cheatsKept(Minecraft mc, String what) {
        IntegratedServer server = mc.getIntegratedServer();
        boolean world = server.worldServers[0].getWorldInfo().areCommandsAllowed();
        boolean host = server.getConfigurationManager().canSendCommands(mc.thePlayer.getGameProfile());
        Boolean essential = WorldCheats.essential();
        LOG.info("Lads 1.8.9 core probe: '{}': world Allow Cheats {}, host may use commands {}, Essential's switch {}",
            server.getFolderName(), world, host, essential);
        check(world && host && (essential == null || essential), what);
        return after(5);
    }

    private static boolean lanScreen(Minecraft mc) {
        mc.displayGuiScreen(new GuiShareToLan(null));
        return after(10);
    }

    private static boolean lanCheatsOn(Minecraft mc) throws Exception {
        GuiShareToLan lan = (GuiShareToLan) mc.currentScreen;
        if (!allowCheats(lan)) press(lan, 103); // Allow Cheats: ON
        check(allowCheats(lan), "the LAN screen's Allow Cheats is on");
        LOG.info("Lads 1.8.9 core probe: QA world before Start LAN World: Allow Cheats {}",
            mc.getIntegratedServer().worldServers[0].getWorldInfo().areCommandsAllowed());
        press(lan, 101); // Start LAN World
        return after(20);
    }

    private static boolean lanStarted(Minecraft mc) {
        IntegratedServer server = mc.getIntegratedServer();
        check(server.getPublic() && server.getConfigurationManager().canSendCommands(mc.thePlayer.getGameProfile()),
            "Start LAN World opened the world to LAN with cheats");
        check(Boolean.TRUE.equals(Cheats189.lanChoice(mc)), "the host's Allow Cheats is kept with the world for joined players");
        return cheatsKept(mc, "Allow Cheats on the LAN screen turned the world's own Allow Cheats on");
    }

    private static boolean lanRemembered(Minecraft mc) throws Exception {
        GuiShareToLan lan = (GuiShareToLan) mc.currentScreen;
        check(allowCheats(lan), "the LAN screen starts on the host's last Allow Cheats");
        CoreProbe.screenshot(mc, "170-lan-allow-cheats-remembered");
        mc.displayGuiScreen(null);
        return after(5);
    }

    private static boolean create(Minecraft mc) throws Exception {
        CoreProbe.deleteQaWorld(mc, MADE);
        LOG.info("Lads 1.8.9 core probe: creating '{}' with Allow Cheats", MADE);
        mc.launchIntegratedServer(MADE, MADE, new WorldSettings(0L, WorldSettings.GameType.SURVIVAL, false, false, WorldType.FLAT).enableCommands());
        return true;
    }

    private static boolean reset(Minecraft mc) {
        IntegratedServer server = mc.getIntegratedServer();
        server.worldServers[0].getWorldInfo().setAllowCommands(false);
        WorldCheats.essential(false);
        WorldCheats.guests(new File(WorldBackup189.savesDir(mc), QA).toPath(), false);
        check(!server.getConfigurationManager().canSendCommands(mc.thePlayer.getGameProfile()), "the QA world is back to no cheats");
        return after(5);
    }

    private static boolean deleteMade(Minecraft mc) throws Exception {
        CoreProbe.deleteQaWorld(mc, MADE);
        return true;
    }

    private static boolean leave(Minecraft mc) {
        mc.theWorld.sendQuittingDisconnectingPacket();
        mc.loadWorld(null);
        mc.displayGuiScreen(new GuiMainMenu());
        return after(20);
    }

    // GuiShareToLan's own button handler and Allow Cheats field, found by shape (their names differ between dev and game).
    private static void press(GuiShareToLan lan, int id) throws Exception {
        for (Method method : GuiShareToLan.class.getDeclaredMethods())
            if (Arrays.equals(method.getParameterTypes(), new Class<?>[] {GuiButton.class})) {
                method.setAccessible(true);
                method.invoke(lan, new GuiButton(id, 0, 0, ""));
                return;
            }
        throw new IllegalStateException("GuiShareToLan has no button handler");
    }

    private static boolean allowCheats(GuiShareToLan lan) throws Exception {
        for (Field field : GuiShareToLan.class.getDeclaredFields())
            if (field.getType() == boolean.class && !Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                return field.getBoolean(lan);
            }
        throw new IllegalStateException("GuiShareToLan has no Allow Cheats field");
    }
}
