package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.google.gson.JsonElement;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.SprintTrace;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.v1_8_9.gui.ControlsScreen189;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.input.Keyboard;

/**
 * QA only: Toggle Sprint &amp; Sneak (Toggles189), run by CoreProbe in its QA world (superflat; survival with mob spawning off,
 * on a stone corridor QA builds and takes away). The Sprint key is tapped through runTick's own input loop, W held, then a wall,
 * three zombie hits, hunger 6, eating, blindness, water, sneaking, flying, a death and respawn, and a separate Toggle Sprint key
 * (H). Every tick's sprint state and the state last sent to the server go to lads-qa/screenshots/170-sprint-trace.csv
 * (SprintTrace: no start-stop flicker). Screenshots: 170-controls-sprint-bound, 170-controls-sprint, 170-controls-sneak,
 * 170-module, 170-hud. Module, options, keys, game mode, food, effects, game rule and blocks are put back.
 */
final class Probe170 {
    private static final int SPRINT = Keyboard.KEY_LCONTROL;
    private static final Map<Option, JsonElement> optionsWere = new LinkedHashMap<>();
    private static final Map<BlockPos, IBlockState> blocksWere = new LinkedHashMap<>();
    private static SprintTrace trace;
    private static boolean wasEnabled, started;
    private static long modifiedWas;
    private static String spawningWas;
    private static BlockPos origin;
    private static EntityPlayerSP before;
    private static ControlsScreen189 controls;

    private interface ServerTask { void run(EntityPlayerMP player); }

    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe170::start,
        mc -> {
            reposition(mc);
            check(!toggles().isSprintToggled(), "Sprint: the QA run starts untoggled");
            trace.phase("toggle");
            CoreProbe.tap(SPRINT, (char) 0);
            return after(3);
        },
        mc -> {
            check(toggles().isSprintToggled(), "Sprint: a tap of the Sprint key (Toggle Sprint unbound) toggles sprint on");
            String config = new String(Files.readAllBytes(ClientPaths.getConfigFile().toPath()), StandardCharsets.UTF_8);
            check(config.contains("\"Sprint toggled\": true"), "Sprint: the toggle is saved in thelads_config.json at once");
            trace.phase("run");
            hold(mc.gameSettings.keyBindForward, true);
            return after(30);
        },
        mc -> {
            check(mc.thePlayer.isSprinting() && trace.sent(), "Sprint: walking forward with the toggle sprints, and the server was told");
            trace.phase("wall");
            final int wallZ = MathHelper.floor_double(mc.thePlayer.posZ) + 4;
            onServer(mc, player -> { for (int x = -2; x <= 2; x++) for (int y = 0; y <= 2; y++) set(player.worldObj, new BlockPos(origin.getX() + x, origin.getY() + y, wallZ), Blocks.stone.getDefaultState()); });
            return after(40);
        },
        mc -> {
            stopped(mc, "running into a wall");
            clearCorridor(mc);
            reposition(mc);
            trace.phase("after-wall");
            return after(30);
        },
        mc -> { resumed(mc, "the wall gone"); trace.phase("hit"); return after(1); },
        Probe170::hit, Probe170::hit, Probe170::hit,
        mc -> {
            check(mc.thePlayer.isSprinting() && trace.sent(), "Sprint: three zombie hits (knockback) leave the toggled sprint going ("
                + trace.phaseChanges() + " sprint packets)");
            reposition(mc);
            trace.phase("hunger");
            onServer(mc, player -> player.getFoodStats().setFoodLevel(6));
            return after(30);
        },
        mc -> {
            stopped(mc, "food 6");
            onServer(mc, player -> player.getFoodStats().setFoodLevel(20));
            trace.phase("fed");
            return after(30);
        },
        mc -> {
            resumed(mc, "food 20 again");
            reposition(mc);
            onServer(mc, player -> {
                player.getFoodStats().setFoodLevel(10);
                player.inventory.setInventorySlotContents(player.inventory.currentItem, new ItemStack(Items.bread, 4));
            });
            return after(10);
        },
        mc -> { trace.phase("eating"); hold(mc.gameSettings.keyBindUseItem, true); return after(25); },
        mc -> {
            check(mc.thePlayer.isUsingItem() && !mc.thePlayer.isSprinting() && !trace.sent() && trace.phaseChanges() <= 1,
                "Sprint: eating while walking stops the sprint once (" + trace.phaseChanges() + " sprint packets)");
            hold(mc.gameSettings.keyBindUseItem, false);
            onServer(mc, player -> {
                player.getFoodStats().setFoodLevel(20);
                player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            });
            trace.phase("after-eating");
            return after(30);
        },
        mc -> {
            resumed(mc, "eating ended");
            reposition(mc);
            trace.phase("blindness");
            onServer(mc, player -> player.addPotionEffect(new PotionEffect(Potion.blindness.id, 400)));
            return after(30);
        },
        mc -> {
            // 1.8.9's own rule: blindness keeps a sprint from starting and never stops one.
            check(trace.phaseChanges() <= 1, "Sprint: blindness, no flicker (" + trace.phaseChanges() + " sprint packets; sprinting " + mc.thePlayer.isSprinting() + ")");
            onServer(mc, player -> player.removePotionEffect(Potion.blindness.id));
            reposition(mc);
            trace.phase("sight");
            return after(30);
        },
        mc -> {
            resumed(mc, "blindness gone");
            reposition(mc);
            onServer(mc, player -> { for (int x = -2; x <= 2; x++) for (int z = 3; z <= 40; z++) set(player.worldObj, origin.add(x, 0, z), Blocks.water.getDefaultState()); });
            trace.phase("water");
            return after(40);
        },
        mc -> {
            check(trace.phaseChanges() <= 2, "Sprint: water, no flicker (" + trace.phaseChanges() + " sprint packets; 1.8.9 sprints in water: " + mc.thePlayer.isSprinting() + ")");
            clearCorridor(mc);
            reposition(mc);
            trace.phase("dry");
            return after(30);
        },
        mc -> {
            resumed(mc, "out of the water");
            trace.phase("sneaking");
            hold(mc.gameSettings.keyBindSneak, true);
            return after(25);
        },
        mc -> {
            stopped(mc, "holding Sneak");
            check(toggles().status().contains("[Sneaking (Key Held)]") && toggles().isSprintToggled(), "Sprint: the HUD line shows the held sneak: " + toggles().status());
            hold(mc.gameSettings.keyBindSneak, false);
            reposition(mc);
            trace.phase("unsneak");
            return after(30);
        },
        mc -> {
            resumed(mc, "Sneak released");
            trace.phase("flying");
            onServer(mc, player -> { player.setGameType(WorldSettings.GameType.CREATIVE); player.capabilities.isFlying = true; player.sendPlayerAbilities(); });
            return after(5);
        },
        mc -> { mc.thePlayer.capabilities.isFlying = true; reposition(mc); return after(30); },
        mc -> {
            check(trace.phaseChanges() <= 2, "Sprint: flying, no flicker (" + trace.phaseChanges() + " sprint packets)");
            onServer(mc, player -> { player.capabilities.isFlying = false; player.setGameType(WorldSettings.GameType.SURVIVAL); player.sendPlayerAbilities(); });
            mc.thePlayer.capabilities.isFlying = false;
            reposition(mc);
            trace.phase("landed");
            return after(30);
        },
        mc -> {
            resumed(mc, "flying ended");
            trace.phase("death");
            hold(mc.gameSettings.keyBindForward, false);
            before = mc.thePlayer;
            onServer(mc, player -> player.attackEntityFrom(DamageSource.outOfWorld, 1000));
            return after(20);
        },
        mc -> {
            check(mc.currentScreen instanceof GuiGameOver, "Sprint: QA died (death screen)");
            mc.thePlayer.respawnPlayer();
            return after(20);
        },
        mc -> {
            if (mc.currentScreen instanceof GuiGameOver) mc.displayGuiScreen(null);
            check(mc.thePlayer != null && mc.thePlayer != before && mc.thePlayer.isEntityAlive(), "Sprint: QA respawned as a new player");
            check(toggles().isSprintToggled(), "Sprint: the toggle survived death and respawn (a new player, as a world, server or dimension change)");
            reposition(mc);
            trace.phase("respawned");
            hold(mc.gameSettings.keyBindForward, true);
            return after(30);
        },
        mc -> {
            resumed(mc, "respawned");
            hold(mc.gameSettings.keyBindForward, false);
            trace.phase("separate-key");
            Toggles189.TOGGLE_SPRINT.setKeyCode(Keyboard.KEY_H);
            KeyBinding.resetKeyBindingArrayAndHash();
            CoreProbe.tap(SPRINT, (char) 0);
            return after(3);
        },
        mc -> {
            check(toggles().isSprintToggled(), "Sprint: with Toggle Sprint on H, a Sprint tap no longer toggles");
            CoreProbe.tap(Keyboard.KEY_H, 'h');
            return after(3);
        },
        mc -> {
            check(!toggles().isSprintToggled(), "Sprint: H toggles sprint off");
            reposition(mc);
            hold(mc.gameSettings.keyBindSprint, true);
            hold(mc.gameSettings.keyBindForward, true);
            return after(20);
        },
        mc -> {
            check(mc.thePlayer.isSprinting() && !toggles().isSprintToggled(), "Sprint: with the toggle off, holding Sprint (Left Control) sprints as in vanilla");
            hold(mc.gameSettings.keyBindSprint, false);
            hold(mc.gameSettings.keyBindForward, false);
            CoreProbe.tap(Keyboard.KEY_H, 'h');
            return after(3);
        },
        mc -> {
            check(toggles().isSprintToggled(), "Sprint: H toggles sprint on again");
            return openControls(mc);
        },
        mc -> {
            screenshot(mc, "170-controls-sprint-bound");
            Toggles189.TOGGLE_SPRINT.setKeyCode(0);
            KeyBinding.resetKeyBindingArrayAndHash();
            return openControls(mc);
        },
        mc -> {
            check(controls.shownKeys().contains(Toggles189.TOGGLE_SPRINT) && controls.shownKeys().contains(mc.gameSettings.keyBindSprint),
                "Controls: 'sprint' lists Sprint and Toggle Sprint");
            screenshot(mc, "170-controls-sprint");
            for (int i = 0; i < 6; i++) CoreProbe.tap(Keyboard.KEY_BACK, '\b');
            type("sneak");
            return after(10);
        },
        mc -> {
            check(controls.shownKeys().contains(Toggles189.TOGGLE_SNEAK) && controls.shownKeys().contains(mc.gameSettings.keyBindSneak),
                "Controls: 'sneak' lists Sneak and Toggle Sneak");
            screenshot(mc, "170-controls-sneak");
            LadsSettingsScreen189 menu = new LadsSettingsScreen189(null);
            mc.displayGuiScreen(menu);
            menu.openModule(ToggleSprintModule.NAME);
            return after(20);
        },
        mc -> {
            screenshot(mc, "170-module");
            mc.displayGuiScreen(null);
            reposition(mc);
            hold(mc.gameSettings.keyBindForward, true);
            trace.phase("hud");
            return after(30);
        },
        mc -> {
            check("[Sprinting (Toggled)]".equals(toggles().status()), "Sprint: the HUD line reads " + toggles().status());
            screenshot(mc, "170-hud");
            hold(mc.gameSettings.keyBindForward, false);
            trace.finish();
            File csv = new File(mc.mcDataDir, "lads-qa/screenshots/170-sprint-trace.csv");
            Files.write(csv.toPath(), trace.csv().getBytes(StandardCharsets.UTF_8));
            LogManager.getLogger("TheLadsCore").info("Lads 1.8.9 sprint trace: {} ({})", trace.summary(), csv);
            check(trace.failures().isEmpty(), "Sprint: no start-stop flicker in any phase: " + (trace.failures().isEmpty() ? trace.summary() : trace.failures()));
            stop();
            return after(2);
        });

    private Probe170() {}

    private static ToggleSprintModule toggles() { return Toggles189.toggles(); }

    private static boolean start(Minecraft mc) {
        ToggleSprintModule toggles = toggles();
        wasEnabled = toggles.isEnabled();
        modifiedWas = toggles.getLastModified();
        for (Option option : toggles.getOptions()) optionsWere.put(option, option.save());
        LogManager.getLogger("TheLadsCore").info("Lads 1.8.9 sprint probe: at world load Toggle Sprint & Sneak is {}, sprint toggled={}, sneak toggled={} (thelads_config.json)",
            wasEnabled ? "on" : "off", toggles.isSprintToggled(), toggles.isSneakToggled());
        toggles.getOptions().forEach(Option::reset); // Sprint Toggle, Sneak Vanilla, untoggled
        toggles.setEnabled(true);
        started = true;
        trace = new SprintTrace();
        Toggles189.trace = trace;
        origin = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minY),
            MathHelper.floor_double(mc.thePlayer.posZ));
        onServer(mc, player -> {
            net.minecraft.world.GameRules rules = player.worldObj.getGameRules();
            if (spawningWas == null) spawningWas = rules.getString("doMobSpawning");
            rules.setOrCreateGameRule("doMobSpawning", "false");
            for (Object entity : player.worldObj.loadedEntityList)
                if (entity instanceof net.minecraft.entity.monster.IMob) ((net.minecraft.entity.Entity) entity).setDead();
            player.setGameType(WorldSettings.GameType.SURVIVAL);
            player.getFoodStats().setFoodLevel(20);
            player.setHealth(player.getMaxHealth());
            for (int x = -2; x <= 2; x++) for (int z = -1; z <= 40; z++) for (int y = -1; y <= 3; y++)
                set(player.worldObj, origin.add(x, y, z), y < 0 ? Blocks.stone.getDefaultState() : Blocks.air.getDefaultState());
        });
        return after(20);
    }

    /** A zombie's blow from in front: the real damage and knockback path. */
    private static boolean hit(Minecraft mc) {
        onServer(mc, player -> {
            EntityZombie zombie = new EntityZombie(player.worldObj);
            zombie.setPosition(player.posX, player.posY, player.posZ + 1.5);
            player.attackEntityFrom(DamageSource.causeMobDamage(zombie), 1);
            player.setHealth(player.getMaxHealth());
        });
        return after(15);
    }

    private static void stopped(Minecraft mc, String why) {
        check(!mc.thePlayer.isSprinting() && !trace.sent() && trace.phaseChanges() <= 1, "Sprint: " + why + " stops the sprint once ("
            + trace.phaseChanges() + " sprint packets)");
    }

    private static void resumed(Minecraft mc, String why) {
        check(mc.thePlayer.isSprinting() && trace.sent() && trace.phaseChanges() <= 1, "Sprint: " + why + ", the toggled sprint starts again by itself ("
            + trace.phaseChanges() + " sprint packets)");
    }

    private static boolean openControls(Minecraft mc) throws Exception {
        mc.displayGuiScreen(new GuiControls(null, mc.gameSettings));
        check(mc.currentScreen instanceof ControlsScreen189, "Controls: the Lads Controls screen is open");
        controls = (ControlsScreen189) mc.currentScreen;
        type("sprint");
        return after(10);
    }

    private static void type(String text) throws Exception {
        for (char c : text.toCharArray()) CoreProbe.tap(Keyboard.getKeyIndex(String.valueOf(Character.toUpperCase(c))), c);
    }

    private static void hold(KeyBinding key, boolean down) {
        KeyBinding.setKeyBindState(key.getKeyCode(), down);
    }

    /** Back to the corridor's start, facing along it (+Z, yaw 0), still: the server moves the player. */
    private static void reposition(Minecraft mc) {
        mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0;
        onServer(mc, player -> player.playerNetServerHandler.setPlayerLocation(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0));
    }

    /** Server thread: one block, remembering what was there first (flag 2: no neighbour updates, so water stays put). */
    private static void set(World world, BlockPos pos, IBlockState state) {
        synchronized (blocksWere) { if (!blocksWere.containsKey(pos)) blocksWere.put(pos, world.getBlockState(pos)); }
        world.setBlockState(pos, state, 2);
    }

    /** QA's wall and water go back to air; the floor stays until stop(). */
    private static void clearCorridor(Minecraft mc) {
        onServer(mc, player -> {
            synchronized (blocksWere) {
                for (BlockPos pos : blocksWere.keySet()) if (pos.getY() >= origin.getY()) player.worldObj.setBlockState(pos, Blocks.air.getDefaultState(), 2);
            }
        });
    }

    private static void onServer(Minecraft mc, ServerTask task) {
        MinecraftServer server = mc.getIntegratedServer();
        java.util.UUID id = mc.thePlayer.getUniqueID();
        server.addScheduledTask(() -> {
            EntityPlayerMP player = server.getConfigurationManager().getPlayerByUUID(id);
            if (player != null) task.run(player);
        });
    }

    /** Also CoreProbe.finish after a failure: keys up, module, options, game mode, food, effects and blocks back. */
    static void stop() {
        if (!started) return;
        started = false;
        Minecraft mc = Minecraft.getMinecraft();
        Toggles189.trace = null;
        for (KeyBinding key : new KeyBinding[] {mc.gameSettings.keyBindForward, mc.gameSettings.keyBindSneak, mc.gameSettings.keyBindUseItem,
            mc.gameSettings.keyBindSprint}) hold(key, false);
        Toggles189.TOGGLE_SPRINT.setKeyCode(0);
        KeyBinding.resetKeyBindingArrayAndHash();
        if (mc.currentScreen != null && mc.theWorld != null) mc.displayGuiScreen(null);
        if (mc.thePlayer != null && mc.getIntegratedServer() != null) onServer(mc, player -> {
            player.removePotionEffect(Potion.blindness.id);
            player.getFoodStats().setFoodLevel(20);
            player.setHealth(player.getMaxHealth());
            player.capabilities.isFlying = false;
            player.setGameType(WorldSettings.GameType.CREATIVE);
            if (spawningWas != null) player.worldObj.getGameRules().setOrCreateGameRule("doMobSpawning", spawningWas);
            synchronized (blocksWere) {
                for (Map.Entry<BlockPos, IBlockState> entry : blocksWere.entrySet()) player.worldObj.setBlockState(entry.getKey(), entry.getValue(), 2);
                blocksWere.clear();
            }
        });
        ToggleSprintModule toggles = toggles();
        optionsWere.forEach(Option::load);
        toggles.setEnabled(wasEnabled);
        toggles.setLastModified(modifiedWas);
        ConfigManager.save();
    }
}
