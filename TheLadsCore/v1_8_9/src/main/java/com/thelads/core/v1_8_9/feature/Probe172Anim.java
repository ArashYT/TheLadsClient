package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;
import static com.thelads.core.v1_8_9.feature.Probe151.food;
import static com.thelads.core.v1_8_9.feature.Probe151.holds;
import static com.thelads.core.v1_8_9.feature.Probe151.key;
import static com.thelads.core.v1_8_9.feature.Probe151.onServer;
import static com.thelads.core.v1_8_9.feature.Probe151.select;

import com.google.gson.JsonElement;
import com.thelads.core.client.DamageTilt;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.BoolOption;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.DamageSource;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * QA only (LADS_VERIFY_189_ONLY=anim): the 1.7.2 animation lane in the QA world, in survival on the integrated server.
 * Swing while using items: eating, a bow draw and a sword block, each clicked once through LWJGL's mouse queue (runTick's own input
 * path) with 1.7 Animations on and then off (1.8.9's own, for comparison): frames 172-&lt;use&gt;-click-N(-off).png, every tick's use
 * count and swing in lads-qa/172-swing.csv, and the packets the client sent around the click. The use must go on, the food be eaten
 * on time, the bow shoot when released, and on and off must send the same packets (no swing, attack or use packet).
 * Damage tilt (the OldDamageTilt module): a zombie hits the player from the left, right, front and back, arrows from the left and
 * right, at 100, 50 and 0% and with Directional off, fall damage, and the module off: the camera's hurt rotation is read from the
 * GL model-view matrix each frame (EntityRendererMixin), its largest roll and nod per hit go to lads-qa/172-tilt.csv, one frame
 * per hit to 172-tilt-*.png. Everything is put back.
 */
public final class Probe172Anim {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    /** EntityRendererMixin samples the hurt rotation while true. */
    public static boolean recording;
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static float roll, nod;
    private static final OldAnimationsModule ANIMATIONS = OldAnimations189.MODULE;
    private static final Module TILT = ModuleManager.getInstance().getModule(DamageTilt.MODULE);
    private static final Map<Option, JsonElement> optionsWere = new LinkedHashMap<Option, JsonElement>();
    private static final Map<Module, Boolean> enabledWere = new LinkedHashMap<Module, Boolean>();
    private static final StringBuilder SWING = new StringBuilder("use,animations,sample,tick,using,useCount,swing,swingInProgress\n");
    private static final StringBuilder TILT_CSV = new StringBuilder("hit,source,module,directional,intensity,yawUsed,roll,nod,expected\n");
    private static final List<String> SENT = Collections.synchronizedList(new ArrayList<String>());
    private static final Map<String, String> PACKETS = new TreeMap<String, String>();
    private static final Map<String, Integer> EATEN = new TreeMap<String, Integer>();
    private static volatile boolean counting;
    private static Channel channel;
    private static ItemStack[] mainWas;
    private static double[] posWas;
    private static float yawWas, pitchWas;
    private static int slotWas, viewWas, difficultyWas, sample, tick, useStart, foodWas, arrowsWas, arrowEntities;
    private static String spawningWas;
    private static LadsSettingsScreen189 settings;

    private static boolean focusWas;
    /** Packets that every tick may or may not send (movement, keep-alive): left out when the click's packets are compared. */
    private static final List<String> ROUTINE = Arrays.asList("C03PacketPlayer", "C04PacketPlayerPosition", "C05PacketPlayerLook",
        "C06PacketPlayerPosLook", "C00PacketKeepAlive");

    static final List<CoreProbe.Step> STEPS = new ArrayList<CoreProbe.Step>(Arrays.<CoreProbe.Step>asList(Probe172Anim::setup,
        Probe172Anim::menu, Probe172Anim::menuOpen, Probe172Anim::menuShown));
    static {
        for (String use : new String[]{"eat", "bow", "block"})
            for (boolean on : new boolean[]{true, false}) STEPS.addAll(swing(use, on));
        STEPS.add(Probe172Anim::swingCompared);
        STEPS.add(Probe172Anim::tiltSetup);
        //        hit           source   module directional intensity expected (roll L/R or nod F/B; "fixed": the old left roll)
        tilt("left", "zombie", true, true, 100, "roll-");
        tilt("right", "zombie", true, true, 100, "roll+");
        tilt("front", "zombie", true, true, 100, "nod+");
        tilt("back", "zombie", true, true, 100, "nod-");
        tilt("left", "arrow", true, true, 100, "roll-");
        tilt("right", "arrow", true, true, 100, "roll+");
        tilt("right", "zombie", true, true, 50, "roll+");
        tilt("right", "zombie", true, true, 0, "none");
        tilt("right", "zombie", true, false, 100, "roll-");
        tilt("none", "fall", true, true, 100, "roll-");
        tilt("right", "zombie", false, true, 100, "roll-"); // 1.8.9's own tilt never turns
        STEPS.add(Probe172Anim::restore);
        STEPS.add(Probe172Anim::restored);
    }

    private Probe172Anim() {}

    /** EntityRendererMixin, at hurtCameraEffect's end: the model-view matrix is the hurt rotation alone (it starts from identity). */
    public static void tiltFrame() {
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        float r = degrees(MATRIX.get(1)), n = degrees(-MATRIX.get(9)); // the image of X's y (roll), of -Z's y (the view tipping down)
        if (Math.abs(r) > Math.abs(roll)) roll = r;
        if (Math.abs(n) > Math.abs(nod)) nod = n;
    }

    private static float degrees(float sine) {
        return (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, sine))));
    }

    /** A sword, bow, beef and arrows, survival and hungry; 1.7 Animations on with every option, Legacy Swing off; packets counted. */
    private static boolean setup(Minecraft mc) throws Exception {
        for (Module module : new Module[]{ANIMATIONS, TILT, ModuleManager.getInstance().getModule("LegacySwing")}) {
            enabledWere.put(module, module.isEnabled());
            for (Option option : module.getOptions()) optionsWere.put(option, option.save());
            module.getOptions().forEach(Option::reset);
        }
        ANIMATIONS.setEnabled(true);
        ModuleManager.getInstance().getModule("LegacySwing").setEnabled(false);
        TILT.setEnabled(false);
        slotWas = mc.thePlayer.inventory.currentItem;
        viewWas = mc.gameSettings.thirdPersonView;
        yawWas = mc.thePlayer.rotationYaw;
        pitchWas = mc.thePlayer.rotationPitch;
        posWas = new double[]{mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ};
        difficultyWas = mc.theWorld.getDifficulty().getDifficultyId();
        mc.gameSettings.thirdPersonView = 0;
        look(mc, 0, 0);
        onServer(mc, player -> {
            mainWas = new ItemStack[player.inventory.mainInventory.length];
            for (int i = 0; i < mainWas.length; i++) mainWas[i] = ItemStack.copyItemStack(player.inventory.mainInventory[i]);
            player.inventory.clear();
            player.inventory.mainInventory[0] = new ItemStack(Items.diamond_sword);
            player.inventory.mainInventory[1] = new ItemStack(Items.bow);
            player.inventory.mainInventory[2] = new ItemStack(Items.cooked_beef, 16);
            player.inventory.mainInventory[8] = new ItemStack(Items.arrow, 32);
            net.minecraft.world.GameRules rules = player.worldObj.getGameRules();
            spawningWas = rules.getString("doMobSpawning");
            rules.setOrCreateGameRule("doMobSpawning", "false");
            for (Object entity : player.worldObj.loadedEntityList)
                if (entity instanceof net.minecraft.entity.monster.IMob) ((Entity) entity).setDead();
            player.setGameType(WorldSettings.GameType.SURVIVAL);
            player.getFoodStats().readNBT(food(10, 0));
            player.setHealth(player.getMaxHealth());
        });
        for (Field field : NetworkManager.class.getDeclaredFields())
            if (field.getType() == Channel.class) {
                field.setAccessible(true);
                channel = (Channel) field.get(mc.getNetHandler().getNetworkManager());
            }
        channel.pipeline().addLast("lads-qa-sent", new ChannelOutboundHandlerAdapter() {
            @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                if (counting) SENT.add(message.getClass().getSimpleName());
                super.write(context, message, promise);
            }
        });
        return after(20);
    }

    /** The damage tilt settings in the Lads menu. */
    private static boolean menu(Minecraft mc) {
        check(!mc.playerController.isInCreativeMode() && holds(mc.thePlayer.inventory.mainInventory[2], Items.cooked_beef),
            "1.7.2 animations: survival with a sword, bow, beef and arrows from the integrated server");
        settings = new LadsSettingsScreen189(null);
        mc.displayGuiScreen(settings);
        return after(10);
    }

    private static boolean menuOpen(Minecraft mc) {
        settings.openModule(DamageTilt.MODULE);
        return after(10);
    }

    private static boolean menuShown(Minecraft mc) {
        check(settings.ui().controlBounds("option:" + DamageTilt.DIRECTIONAL) != null && settings.ui().controlBounds("option:" + DamageTilt.INTENSITY) != null,
            "the damage tilt page lists Directional and Intensity");
        screenshot(mc, "172-damage-tilt-menu");
        mc.displayGuiScreen(null);
        return after(10);
    }

    /** One use clicked once, with 1.7 Animations on or off. */
    private static List<CoreProbe.Step> swing(String use, boolean on) {
        int slot = use.equals("eat") ? 2 : use.equals("bow") ? 1 : 0;
        String tag = on ? "" : "-off", state = "1.7 Animations " + (on ? "on" : "off") + ", " + use + ": ";
        return Arrays.<CoreProbe.Step>asList(
            mc -> {
                ANIMATIONS.setEnabled(on);
                select(mc, slot);
                look(mc, 0, 0); // at the sky: the click hits nothing
                onServer(mc, player -> player.getFoodStats().readNBT(food(10, 0)));
                return after(15);
            },
            mc -> {
                key(mc.gameSettings.keyBindUseItem, true);
                useStart = mc.thePlayer.ticksExisted;
                foodWas = count(mc, 2);
                arrowsWas = count(mc, 8);
                return after(6);
            },
            mc -> {
                check(mc.thePlayer.isUsingItem() && !mc.thePlayer.isSwingInProgress, state + "the held use key " + use + "s, no swing yet");
                OldAnimations189.resetHits();
                SENT.clear();
                counting = true;
                focusWas = mc.inGameHasFocus;
                mc.inGameHasFocus = true; // QA: the click must not grab the mouse of a window the player is not in
                CoreProbe.mouse(0, true, 0, 0); // a left click as the mouse sends it: runTick reads it next tick
                CoreProbe.mouse(0, false, 0, 0);
                sample = 0;
                return after(0);
            },
            mc -> {
                int count = mc.thePlayer.getItemInUseCount();
                SWING.append(use).append(on ? ",on," : ",off,").append(sample).append(',').append(mc.thePlayer.ticksExisted - useStart).append(',')
                    .append(mc.thePlayer.isUsingItem()).append(',').append(count).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", mc.thePlayer.getSwingProgress(1))).append(',').append(mc.thePlayer.isSwingInProgress).append('\n');
                if (sample >= 1 && sample <= 4) screenshot(mc, "172-" + use + "-click-" + sample + tag);
                if (sample == 1) { // the click was read this tick
                    if (on) check(OldAnimations189.hits(OldAnimations189.Hook.SWING) == 1 && mc.thePlayer.isSwingInProgress,
                        state + "the click swings the arm, on this client only");
                    else check(!mc.thePlayer.isSwingInProgress, state + "1.8.9 drops the click: no swing");
                }
                if (++sample <= 10) return retry(0);
                counting = false;
                mc.inGameHasFocus = focusWas;
                List<String> sent = new ArrayList<String>(SENT);
                PACKETS.put(use + tag, types(sent));
                LOG.info("Lads 1.7.2 swing while using: {} packets around the click: {}", use + tag, sent);
                check(!sent.contains("C0APacketAnimation") && !sent.contains("C02PacketUseEntity") && !sent.contains("C07PacketPlayerDigging")
                    && !sent.contains("C08PacketPlayerBlockPlacement"), state + "no swing, attack, dig or use packet around the click " + types(sent));
                check(mc.thePlayer.isUsingItem(), state + "still in use 10 ticks after the click (use count " + mc.thePlayer.getItemInUseCount() + ")");
                if (on && !use.equals("block")) check(OldAnimations189.usedSwing > 0.3f, state + "the swing is drawn over the use pose (up to "
                    + OldAnimations189.usedSwing + ")");
                if (on && use.equals("block")) check(OldAnimations189.usedSwing > 0.3f, state + "the swing is drawn on the blocking sword (up to "
                    + OldAnimations189.usedSwing + ")");
                if (!on) check(OldAnimations189.usedSwing == 0, state + "1.8.9 draws no swing while using");
                return after(0);
            },
            mc -> {
                // Eating: held until the beef is eaten; the bow: drawn 25 ticks, then released; the block: released.
                int held = mc.thePlayer.ticksExisted - useStart;
                if (use.equals("eat")) {
                    if (count(mc, 2) == foodWas && held < 60) return retry(0);
                    key(mc.gameSettings.keyBindUseItem, false);
                    check(count(mc, 2) == foodWas - 1 && held >= 32 && held <= 38, state + "one beef eaten after " + held + " ticks (32 to eat, then the server's word)");
                    EATEN.put(tag, held);
                    LOG.info("Lads 1.7.2 swing while using: {} eaten after {} ticks", use + tag, held);
                    return after(10);
                }
                if (use.equals("bow") && held < 25) return retry(0);
                arrowEntities = mc.theWorld.getEntitiesWithinAABB(EntityArrow.class, mc.thePlayer.getEntityBoundingBox().expand(40, 20, 40)).size();
                key(mc.gameSettings.keyBindUseItem, false);
                return after(8);
            },
            mc -> {
                check(!mc.thePlayer.isUsingItem(), state + "the use ended when the key came up");
                if (use.equals("bow")) {
                    List<EntityArrow> arrows = mc.theWorld.getEntitiesWithinAABB(EntityArrow.class, mc.thePlayer.getEntityBoundingBox().expand(40, 20, 40));
                    double speed = 0; // the new arrow is the one in flight
                    for (EntityArrow arrow : arrows) speed = Math.max(speed, Math.sqrt(arrow.motionX * arrow.motionX + arrow.motionY * arrow.motionY
                        + arrow.motionZ * arrow.motionZ));
                    check(arrows.size() == arrowEntities + 1 && count(mc, 8) == arrowsWas - 1, state + "the released bow shot one arrow (speed " + speed
                        + ", arrows " + arrowsWas + " -> " + count(mc, 8) + ")");
                    onServer(mc, player -> {
                        for (EntityArrow arrow : player.worldObj.getEntitiesWithinAABB(EntityArrow.class, player.getEntityBoundingBox().expand(80, 40, 80)))
                            arrow.setDead();
                    });
                }
                select(mc, 4);
                return after(10);
            });
    }

    /** On and off sent the same kinds of packets around the click: the swing is drawn only. */
    private static boolean swingCompared(Minecraft mc) throws Exception {
        for (String use : new String[]{"eat", "bow", "block"})
            check(PACKETS.get(use).equals(PACKETS.get(use + "-off")), use + ": 1.7 Animations on and off sent the same packet kinds around the click "
                + PACKETS.get(use) + " / " + PACKETS.get(use + "-off"));
        check(EATEN.size() == 2 && Math.abs(EATEN.get("") - EATEN.get("-off")) <= 1, "the beef took as long with 1.7 Animations on as off " + EATEN);
        Files.write(new File(mc.mcDataDir, "lads-qa/172-swing.csv").toPath(), SWING.toString().getBytes(StandardCharsets.UTF_8));
        LOG.info("Lads 1.7.2 swing while using samples:\n{}", SWING);
        return after(0);
    }

    private static boolean tiltSetup(Minecraft mc) {
        TILT.setEnabled(true);
        onServer(mc, player -> player.mcServer.setDifficultyForAllWorlds(EnumDifficulty.NORMAL)); // mobs hurt players on Normal
        return after(10);
    }

    private static void tilt(String hit, String source, boolean module, boolean directional, int intensity, String expected) {
        String name = source + "-" + hit + (module ? "" : "-module-off") + (directional ? "" : "-fixed") + (intensity == 100 ? "" : "-" + intensity);
        int[] waited = {0};
        STEPS.add(mc -> {
            TILT.setEnabled(module);
            ((BoolOption) TILT.getOption(DamageTilt.DIRECTIONAL)).set(directional);
            ((SliderOption) TILT.getOption(DamageTilt.INTENSITY)).setValue(intensity);
            look(mc, 0, 0);
            onServer(mc, player -> {
                player.setHealth(player.getMaxHealth());
                player.hurtResistantTime = 0;
                player.motionX = player.motionY = player.motionZ = 0;
                player.setPositionAndUpdate(posWas[0], posWas[1], posWas[2]);
            });
            return after(10);
        });
        STEPS.add(mc -> {
            check(Math.abs(mc.thePlayer.rotationYaw) < 1e-3 && mc.thePlayer.hurtTime == 0, name + ": facing south, not hurt");
            roll = nod = 0;
            recording = true;
            waited[0] = 0;
            // facing south (yaw 0): +X is the player's left, +Z ahead
            double dx = hit.equals("left") ? 1 : hit.equals("right") ? -1 : 0, dz = hit.equals("front") ? 1 : hit.equals("back") ? -1 : 0;
            onServer(mc, player -> {
                if (source.equals("fall")) player.attackEntityFrom(DamageSource.fall, 2);
                else if (source.equals("zombie")) {
                    EntityZombie zombie = new EntityZombie(player.worldObj);
                    zombie.setPosition(player.posX + dx * 1.5, player.posY, player.posZ + dz * 1.5);
                    player.worldObj.spawnEntityInWorld(zombie);
                    zombie.attackEntityAsMob(player);
                    zombie.setDead();
                } else {
                    EntityArrow arrow = new EntityArrow(player.worldObj, player.posX + dx * 3, player.posY + 1.2, player.posZ + dz * 3);
                    arrow.setThrowableHeading(-dx, 0, -dz, 1.5F, 0);
                    player.worldObj.spawnEntityInWorld(arrow);
                }
            });
            return after(0);
        });
        STEPS.add(mc -> {
            if (mc.thePlayer.hurtTime == 0) {
                if (++waited[0] > 60) throw new IllegalStateException(name + ": the hit never reached the client");
                return retry(0);
            }
            mc.thePlayer.motionX = mc.thePlayer.motionZ = 0; // the knockback would carry the player off
            if (mc.thePlayer.hurtTime == 8) screenshot(mc, "172-tilt-" + name); // the last frame drew hurtTime 9: the tilt's peak
            if (mc.thePlayer.hurtTime > 1) return retry(0);
            recording = false;
            float yaw = DamageTilt.CLIENT.yaw(System.currentTimeMillis());
            TILT_CSV.append(hit).append(',').append(source).append(',').append(module).append(',').append(directional).append(',').append(intensity)
                .append(',').append(Float.isNaN(yaw) ? "unknown" : String.format(Locale.ROOT, "%.1f", yaw)).append(',')
                .append(String.format(Locale.ROOT, "%.2f,%.2f", roll, nod)).append(',').append(expected).append('\n');
            String measured = String.format(Locale.ROOT, "roll %.2f, nod %.2f, hit yaw %s", roll, nod, Float.isNaN(yaw) ? "unknown" : String.format(Locale.ROOT, "%.1f", yaw));
            float full = 14f * intensity / 100, main = expected.startsWith("roll") ? roll : nod, other = expected.startsWith("roll") ? nod : roll;
            if (expected.equals("none")) check(Math.abs(roll) < 0.01f && Math.abs(nod) < 0.01f, name + ": Intensity 0 does not tilt (" + measured + ")");
            else check(Math.signum(main) == (expected.endsWith("+") ? 1 : -1) && Math.abs(main) > full * 0.75f && Math.abs(main) < full * 1.02f
                && Math.abs(other) < full * 0.25f, name + ": the camera's " + expected + " of up to " + full + " degrees (" + measured + ")");
            onServer(mc, player -> { player.setHealth(player.getMaxHealth()); player.setPositionAndUpdate(posWas[0], posWas[1], posWas[2]); });
            return after(25); // past the hurt cooldown
        });
    }

    private static boolean restore(Minecraft mc) throws Exception {
        recording = counting = false;
        if (channel != null && channel.pipeline().get("lads-qa-sent") != null) channel.pipeline().remove("lads-qa-sent");
        key(mc.gameSettings.keyBindUseItem, false);
        key(mc.gameSettings.keyBindAttack, false);
        for (Map.Entry<Option, JsonElement> option : optionsWere.entrySet()) option.getKey().load(option.getValue());
        for (Map.Entry<Module, Boolean> module : enabledWere.entrySet()) module.getKey().setEnabled(module.getValue());
        mc.gameSettings.thirdPersonView = viewWas;
        select(mc, slotWas);
        look(mc, yawWas, pitchWas);
        final ItemStack[] main = mainWas;
        mainWas = null;
        final int difficulty = difficultyWas;
        final String spawning = spawningWas;
        onServer(mc, player -> {
            player.mcServer.setDifficultyForAllWorlds(EnumDifficulty.getDifficultyEnum(difficulty));
            if (spawning != null) player.worldObj.getGameRules().setOrCreateGameRule("doMobSpawning", spawning);
            player.setGameType(WorldSettings.GameType.CREATIVE);
            player.getFoodStats().readNBT(food(20, 5));
            player.setHealth(player.getMaxHealth());
            if (main != null) System.arraycopy(main, 0, player.inventory.mainInventory, 0, main.length);
            player.setPositionAndUpdate(posWas[0], posWas[1], posWas[2]);
        });
        Files.write(new File(mc.mcDataDir, "lads-qa/172-tilt.csv").toPath(), TILT_CSV.toString().getBytes(StandardCharsets.UTF_8));
        LOG.info("Lads 1.7.2 damage tilt per hit:\n{}", TILT_CSV);
        return after(20);
    }

    /** CoreProbe.finish after a failure: everything goes back even though the steps stopped. */
    static void stop() {
        if (enabledWere.isEmpty()) return;
        try { restore(Minecraft.getMinecraft()); } catch (Throwable ignored) {}
        enabledWere.clear();
    }

    private static boolean restored(Minecraft mc) {
        check(mc.playerController.isInCreativeMode() && !mc.thePlayer.isUsingItem() && ANIMATIONS.isEnabled() == enabledWere.get(ANIMATIONS)
            && TILT.isEnabled() == enabledWere.get(TILT), "1.7.2 animations: modules, inventory, game mode, difficulty and camera are back as found");
        enabledWere.clear();
        return after(1);
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = yaw;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = pitch;
    }

    private static int count(Minecraft mc, int slot) {
        ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
        return stack == null ? 0 : stack.stackSize;
    }

    /** The packet kinds sent, each once, without the routine ones. */
    private static String types(List<String> sent) {
        TreeSet<String> kinds = new TreeSet<String>(sent);
        kinds.removeAll(ROUTINE);
        return kinds.toString();
    }
}
