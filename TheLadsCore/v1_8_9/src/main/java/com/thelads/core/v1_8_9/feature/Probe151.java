package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import com.thelads.core.modules.OldAnimationsModule.Platform;
import com.thelads.core.v1_8_9.feature.OldAnimations189.Hook;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldSettings;

/**
 * QA only: 1.7 Animations (OldAnimations189), run by CoreProbe in its QA world after Probe150. The Lads menu lists it without the
 * two options 1.8.9 lacks; then each pose through the real input path (held use, attack and sneak keys, so runTick uses the
 * items itself) with the module on and then off: every hook must change 1.8.9's result with it on and stay silent with it off.
 * Eating and the hurt red armour run in survival on the integrated server. Module, options, inventory, game mode, health, food,
 * camera and keys are put back as found. Screenshots: 151-*.png (the "-off" ones are 1.8.9's own for comparison). Last, a held
 * block and apple, and Legacy Swing's swing (as placing swings) on a block and on a torch, which 1.7 then leaves to Legacy Swing.
 * 1.7.0: the sword block, bow and food start at once while the attack key mines a block (in survival); eating never swings; and
 * the camera pitch (1.8.9's own vertical bob) sampled per tick as flying stops a fall, into lads-qa/170-fly-cancel-bob.csv.
 */
final class Probe151 {
    static final List<CoreProbe.Step> STEPS = new ArrayList<>(Arrays.<CoreProbe.Step>asList(Probe151::start, Probe151::menu,
        Probe151::menuListed, Probe151::menuOptions));
    static {
        STEPS.addAll(creative(true));
        STEPS.addAll(creative(false));
        STEPS.addAll(flyCancel());
        STEPS.add(Probe151::survival);
        STEPS.addAll(onBlock(true));
        STEPS.addAll(onBlock(false));
        STEPS.addAll(survival(true));
        STEPS.addAll(survival(false));
        STEPS.add(Probe151::restore);
        STEPS.add(Probe151::restored);
    }
    private static final OldAnimationsModule MODULE = OldAnimations189.MODULE;
    private static final boolean[] optionsWere = new boolean[Feature.values().length];
    private static boolean wasEnabled, legacyWas, focusWas;
    private static double[] posWas;
    private static final StringBuilder BOB = new StringBuilder();
    private static long legacyFrames;
    private static int slotWas, viewWas, dropWait;
    private static float pitchWas;
    private static ItemStack[] mainWas, armourWas;
    private static LadsSettingsScreen189 settings;

    private Probe151() {}

    /** A sword, bow, rod and food in the hotbar (slot 4 empty) and a chestplate, from the integrated server; every option on. */
    private static boolean start(Minecraft mc) {
        wasEnabled = MODULE.isEnabled();
        legacyWas = legacy().isEnabled();
        for (Feature feature : Feature.values()) {
            optionsWere[feature.ordinal()] = MODULE.option(feature).get();
            MODULE.option(feature).set(true);
        }
        slotWas = mc.thePlayer.inventory.currentItem;
        viewWas = mc.gameSettings.thirdPersonView;
        pitchWas = mc.thePlayer.rotationPitch;
        onServer(mc, player -> {
            mainWas = copy(player.inventory.mainInventory);
            armourWas = copy(player.inventory.armorInventory);
            player.inventory.clear();
            player.inventory.mainInventory[0] = new ItemStack(Items.diamond_sword);
            player.inventory.mainInventory[1] = new ItemStack(Items.bow);
            player.inventory.mainInventory[2] = new ItemStack(Items.fishing_rod);
            player.inventory.mainInventory[3] = new ItemStack(Items.cooked_beef, 16);
            player.inventory.mainInventory[5] = new ItemStack(net.minecraft.init.Blocks.stone);
            player.inventory.mainInventory[6] = new ItemStack(Items.apple);
            player.inventory.mainInventory[7] = new ItemStack(net.minecraft.init.Blocks.torch);
            player.inventory.mainInventory[8] = new ItemStack(Items.arrow); // a survival bow draws only with an arrow
            player.inventory.armorInventory[2] = new ItemStack(Items.iron_chestplate);
        });
        return after(10);
    }

    private static boolean menu(Minecraft mc) {
        ItemStack[] hotbar = mc.thePlayer.inventory.mainInventory;
        check(mainWas != null && holds(hotbar[0], Items.diamond_sword) && holds(hotbar[1], Items.bow) && holds(hotbar[2], Items.fishing_rod)
            && holds(hotbar[3], Items.cooked_beef) && hotbar[4] == null && holds(mc.thePlayer.inventory.armorInventory[2], Items.iron_chestplate),
            "1.7 Animations: the integrated server gave a sword, bow, rod, food and a chestplate");
        settings = new LadsSettingsScreen189(null);
        mc.displayGuiScreen(settings);
        return after(10);
    }

    private static boolean menuListed(Minecraft mc) {
        check(ModuleSupport.isBuiltIn(OldAnimationsModule.NAME) && ModuleSupport.isToggleable(OldAnimationsModule.NAME)
            && settings.ui().visibleModuleNames().contains(OldAnimationsModule.NAME), "the Lads menu lists 1.7 Animations, built in on 1.8.9");
        settings.openModule(OldAnimationsModule.NAME);
        return after(10);
    }

    private static boolean menuOptions(Minecraft mc) {
        List<String> wrong = new ArrayList<>();
        for (Feature feature : Feature.values()) {
            boolean hidden = MODULE.hidden(MODULE.option(feature));
            if (hidden == feature.appliesTo(Platform.V1_8_9) || hidden && settings.ui().controlBounds("option:" + feature.option) != null)
                wrong.add(feature.option);
        }
        check(wrong.isEmpty() && settings.ui().controlBounds("toggle:detail") != null
            && settings.ui().controlBounds("option:" + Feature.BLOCKHIT.option) != null,
            "its page lists the options 1.8.9 has and hides No attack-cooldown dip and Low Shield " + wrong);
        screenshot(mc, "151-menu");
        mc.displayGuiScreen(null);
        return after(10);
    }

    /** Sword idle, block and blockhit (first and third person), bow, cast rod, dropped item, sneak camera and fire, in creative. */
    private static List<CoreProbe.Step> creative(boolean on) {
        String tag = on ? "" : "-off", state = "1.7 Animations " + (on ? "on" : "off") + ": ";
        return Arrays.<CoreProbe.Step>asList(
            mc -> {
                MODULE.setEnabled(on);
                select(mc, 0);
                mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 35; // the ground 2.3 blocks ahead, in reach
                return after(15);
            },
            mc -> {
                OldAnimations189.resetHits();
                return after(3);
            },
            mc -> {
                check(holds(mc.thePlayer.getHeldItem(), Items.diamond_sword), state + "the sword is held");
                // 1.7 held item positions: the idle sword sits where 1.7 held it (by 1.8.9's idle hand), so poses don't resize it.
                if (on) check(OldAnimations189.hits(Hook.FP_ICON) > 0 && OldAnimations189.hits(Hook.FP_HAND) == 0,
                    state + "the idle sword is drawn where 1.7 held it " + counts());
                else check(silent(), state + "1.8.9's idle sword " + counts());
                screenshot(mc, "151-idle-sword" + tag);
                key(mc.gameSettings.keyBindUseItem, true);
                return after(6);
            },
            mc -> {
                check(mc.thePlayer.isBlocking(), state + "the held use key blocks with the sword (runTick's own item use)");
                key(mc.gameSettings.keyBindAttack, true);
                OldAnimations189.resetHits();
                return after(8);
            },
            mc -> {
                check(mc.thePlayer.isBlocking(), state + "still blocking with the attack key held on the ground");
                if (on) check(OldAnimations189.hits(Hook.SWING) > 0 && mc.thePlayer.isSwingInProgress && OldAnimations189.usedSwing > 0
                    && OldAnimations189.hits(Hook.FP_HAND) > 0 && OldAnimations189.hits(Hook.FP_ICON) > 0, state + "blockhit: the held attack "
                    + "swings the blocking sword (swing up to " + OldAnimations189.usedSwing + ") in 1.7's block pose " + counts());
                else check(silent() && !mc.thePlayer.isSwingInProgress, state + "1.8.9 swallows the attack while blocking: no swing " + counts());
                screenshot(mc, "151-blockhit" + tag);
                mc.gameSettings.thirdPersonView = 2;
                OldAnimations189.resetHits();
                return after(5);
            },
            mc -> {
                check(mc.thePlayer.isBlocking(), state + "still blocking in third person");
                hooks(on, "third-person block (held item and arm)", Hook.TP_ITEM, Hook.TP_ARM);
                screenshot(mc, "151-third-person-block" + tag);
                mc.gameSettings.thirdPersonView = 0;
                key(mc.gameSettings.keyBindAttack, false);
                return after(2);
            },
            mc -> {
                key(mc.gameSettings.keyBindUseItem, false);
                return after(4);
            },
            mc -> {
                check(!mc.thePlayer.isUsingItem(), state + "releasing the use key ends the block");
                select(mc, 1);
                return after(15);
            },
            mc -> {
                key(mc.gameSettings.keyBindUseItem, true);
                OldAnimations189.resetHits();
                return after(14);
            },
            mc -> {
                check(mc.thePlayer.isUsingItem() && holds(mc.thePlayer.getItemInUse(), Items.bow), state + "the held use key draws the bow");
                hooks(on, "drawn bow", Hook.FP_HAND, Hook.FP_ICON);
                screenshot(mc, "151-bow" + tag);
                select(mc, 4); // an empty slot ends the draw on both sides before the key is released: no arrow flies
                return after(2);
            },
            mc -> {
                key(mc.gameSettings.keyBindUseItem, false);
                return after(4);
            },
            mc -> {
                check(!mc.thePlayer.isUsingItem() && mc.theWorld.getEntitiesWithinAABB(EntityArrow.class,
                    mc.thePlayer.getEntityBoundingBox().expand(32, 16, 32)).isEmpty(), state + "the bow was put away without shooting");
                select(mc, 2);
                return after(15);
            },
            mc -> {
                OldAnimations189.resetHits();
                return after(3);
            },
            mc -> {
                check(holds(mc.thePlayer.getHeldItem(), Items.fishing_rod), state + "the fishing rod is held");
                if (on) check(OldAnimations189.hits(Hook.FP_ICON) > 0 && OldAnimations189.hits(Hook.FP_HAND) == 0,
                    state + "the rod is drawn where 1.7 held it, by 1.8.9's idle hand " + counts());
                else check(silent(), state + "1.8.9's rod " + counts());
                key(mc.gameSettings.keyBindUseItem, true); // for one tick: runTick's item use casts
                return after(1);
            },
            mc -> {
                key(mc.gameSettings.keyBindUseItem, false);
                OldAnimations189.resetHits();
                return after(15);
            },
            mc -> {
                check(mc.thePlayer.fishEntity != null, state + "the rod is cast (the integrated server's hook reached the client)");
                hooks(on, "cast rod (line from 1.7's rod tip)", Hook.LINE, Hook.FP_ICON);
                screenshot(mc, "151-rod" + tag);
                key(mc.gameSettings.keyBindUseItem, true); // reel in
                return after(1);
            },
            mc -> {
                key(mc.gameSettings.keyBindUseItem, false);
                return after(5);
            },
            mc -> {
                check(mc.thePlayer.fishEntity == null, state + "the line is reeled in");
                select(mc, 4);
                double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
                double x = mc.thePlayer.posX - Math.sin(yaw) * 2.3, y = mc.thePlayer.posY + 0.2, z = mc.thePlayer.posZ + Math.cos(yaw) * 2.3;
                onServer(mc, player -> {
                    EntityItem apple = new EntityItem(player.worldObj, x, y, z, new ItemStack(Items.apple));
                    apple.motionX = apple.motionY = apple.motionZ = 0;
                    apple.setInfinitePickupDelay();
                    player.worldObj.spawnEntityInWorld(apple);
                });
                return after(15);
            },
            mc -> {
                OldAnimations189.resetHits();
                return after(3);
            },
            mc -> {
                check(!items(mc).isEmpty(), state + "the integrated server dropped an apple in view");
                // Its render hook counts frames: a loaded PC can render none in 3 ticks (every hook at 0), so on waits up to 2 s more.
                if (on && OldAnimations189.hits(Hook.DROP) == 0 && ++dropWait < 40) return retry(1);
                dropWait = 0;
                hooks(on, "dropped apple", Hook.DROP);
                screenshot(mc, "151-dropped-2d" + tag);
                onServer(mc, player -> {
                    for (EntityItem item : player.worldObj.getEntitiesWithinAABB(EntityItem.class, player.getEntityBoundingBox().expand(8, 4, 8)))
                        item.setDead();
                });
                key(mc.gameSettings.keyBindSneak, true);
                return after(0);
            },
            // Sneak camera: OldAnimations189.tick runs before CoreProbe in the same client tick, so the reads are exact.
            mc -> {
                float target = mc.thePlayer.getEyeHeight(), camera = OldAnimations189.cameraEyeHeight(mc.thePlayer, 1);
                check(mc.thePlayer.isSneaking() && target < 1.6f, state + "the held sneak key sneaks (eye height " + target + ")");
                check(Math.abs(camera - target) < 1e-4f, state + "one tick in, the camera is at the sneak height (" + camera + ")");
                key(mc.gameSettings.keyBindSneak, false);
                return after(0);
            },
            mc -> {
                float standing = mc.thePlayer.getEyeHeight(), camera = OldAnimations189.cameraEyeHeight(mc.thePlayer, 1);
                check(!mc.thePlayer.isSneaking() && standing > 1.6f, state + "releasing it stands up (eye height " + standing + ")");
                if (on) check(camera > 1.55f && camera < standing - 1e-3f, state + "one tick after standing up the camera is still easing up (" + camera + ")");
                else check(camera == standing, state + "the camera is 1.8.9's eye height at once (" + camera + ")");
                OldAnimations189.resetHits();
                return after(10);
            },
            mc -> {
                hooks(on, "camera easing up through orientCamera", Hook.SNEAK);
                onServer(mc, player -> { survivalSafe(player); player.setFire(5); }); // creative players never burn
                return after(10);
            },
            mc -> {
                OldAnimations189.resetHits();
                return after(3);
            },
            mc -> {
                check(mc.thePlayer.isBurning(), state + "the integrated server set the player on fire");
                hooks(on, "fire overlay", Hook.FIRE);
                screenshot(mc, "151-low-fire" + tag);
                onServer(mc, player -> { player.extinguish(); player.setGameType(WorldSettings.GameType.CREATIVE); });
                select(mc, 5);
                return after(15);
            },
            mc -> {
                OldAnimations189.resetHits();
                return after(3);
            },
            mc -> {
                check(holds(mc.thePlayer.getHeldItem(), Item.getItemFromBlock(net.minecraft.init.Blocks.stone)), state + "a stone block is held");
                check(OldAnimations189.hits(Hook.FP_ICON) == 0 && OldAnimations189.hits(Hook.FP_HAND) == 0,
                    state + "a held block keeps 1.8.9's placement, which is 1.7's " + counts());
                screenshot(mc, "151-held-block" + tag);
                select(mc, 6);
                return after(15);
            },
            mc -> {
                OldAnimations189.resetHits();
                return after(3);
            },
            mc -> {
                check(holds(mc.thePlayer.getHeldItem(), Items.apple), state + "an apple is held");
                if (on) check(OldAnimations189.hits(Hook.FP_ICON) > 0, state + "the apple is drawn where 1.7 held it " + counts());
                else check(silent(), state + "1.8.9's apple " + counts());
                screenshot(mc, "151-idle-item" + tag);
                select(mc, 7);
                return after(15);
            },
            mc -> {
                OldAnimations189.resetHits();
                return after(3);
            },
            mc -> {
                check(holds(mc.thePlayer.getHeldItem(), Item.getItemFromBlock(net.minecraft.init.Blocks.torch)), state + "a torch is held");
                if (on) check(OldAnimations189.hits(Hook.FP_ICON) > 0, state + "Legacy Swing off: the torch is drawn where 1.7 held it " + counts());
                else check(silent(), state + "1.8.9's torch " + counts());
                legacy().setEnabled(true);
                select(mc, 5);
                return after(15);
            },
            mc -> legacySwing(mc),
            mc -> {
                check(LegacySwing189.frames > legacyFrames, state + "Legacy Swing swings the block (" + (LegacySwing189.frames - legacyFrames) + " frames)");
                screenshot(mc, "151-legacy-place" + tag);
                select(mc, 7);
                return after(15);
            },
            mc -> legacySwing(mc),
            mc -> {
                check(LegacySwing189.frames > legacyFrames, state + "Legacy Swing swings the torch (" + (LegacySwing189.frames - legacyFrames) + " frames)");
                check(silent(), state + "placing blocks is Legacy Swing's alone: 1.7 Animations leaves the torch to it " + counts());
                screenshot(mc, "151-legacy-torch" + tag);
                legacy().setEnabled(legacyWas);
                return after(5);
            });
    }

    /**
     * 1.8.9's own vertical bob (EntityPlayer.cameraPitch, which EntityRenderer.setupViewBobbing turns the camera by): a creative fall
     * from 24 blocks up, then flying starts as a double jump would, and the pitch is sampled each tick at four partial ticks.
     */
    private static List<CoreProbe.Step> flyCancel() {
        int[] ticks = {0};
        return Arrays.<CoreProbe.Step>asList(
            mc -> {
                posWas = new double[]{mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ};
                mc.thePlayer.capabilities.isFlying = false;
                onServer(mc, player -> player.setPositionAndUpdate(posWas[0], posWas[1] + 24, posWas[2]));
                BOB.setLength(0);
                BOB.append("tick,phase,motionY,partial0,partial25,partial50,partial75\n");
                return after(4);
            },
            mc -> {
                check(mc.thePlayer.posY > posWas[1] + 15 && !mc.thePlayer.onGround, "fly-cancel bob: the player falls from 24 blocks up");
                ticks[0] = 0;
                return after(0);
            },
            mc -> {
                if (ticks[0] == 10) {
                    mc.thePlayer.capabilities.isFlying = true; // flying stops the fall
                    mc.thePlayer.sendPlayerAbilities();
                }
                EntityPlayer p = mc.thePlayer;
                BOB.append(ticks[0]).append(ticks[0] < 10 ? ",fall," : ",flying,").append(String.format(java.util.Locale.ROOT, "%.4f", p.motionY));
                for (float partial : new float[]{0, 0.25f, 0.5f, 0.75f})
                    BOB.append(',').append(String.format(java.util.Locale.ROOT, "%.4f", p.prevCameraPitch + (p.cameraPitch - p.prevCameraPitch) * partial));
                BOB.append('\n');
                return ++ticks[0] < 26 ? retry(1) : after(0);
            },
            mc -> {
                java.io.File out = new java.io.File(mc.mcDataDir, "lads-qa/170-fly-cancel-bob.csv");
                java.nio.file.Files.write(out.toPath(), BOB.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String[] rows = BOB.toString().split("\n");
                float before = Float.parseFloat(rows[10].split(",")[6]), largest = 0, last = before;
                for (int row = 11; row < rows.length; row++)
                    for (int column = 3; column <= 6; column++) {
                        float value = Float.parseFloat(rows[row].split(",")[column]);
                        largest = Math.max(largest, Math.abs(value - last));
                        last = value;
                    }
                org.apache.logging.log4j.LogManager.getLogger("TheLadsCore").info("Lads 1.7.0 fly-cancel bob (1.8.9 cameraPitch):\n{}", BOB);
                check(before > 1 && largest < before * 0.5f && Math.abs(last) < before * 0.2f, "fly-cancel bob: 1.8.9's pitch eases back from "
                    + before + " (largest quarter-tick step " + largest + ", " + last + " after 15 ticks): no snap");
                onServer(mc, player -> {
                    player.capabilities.isFlying = false;
                    player.sendPlayerAbilities();
                    player.setPositionAndUpdate(posWas[0], posWas[1], posWas[2]);
                });
                return after(10);
            },
            mc -> {
                check(Math.abs(mc.thePlayer.posY - posWas[1]) < 0.01 && !mc.thePlayer.capabilities.isFlying, "fly-cancel bob: back on the ground");
                mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 35;
                return after(5);
            });
    }

    /**
     * Survival: the attack key held on the ground mines it (isHittingBlock), then the use key. 1.7 Animations on, the sword blocks,
     * the bow draws and the food is eaten at once (1.8.9 ignores the use key while mining); eating never swings.
     */
    private static List<CoreProbe.Step> onBlock(boolean on) {
        String tag = on ? "" : "-off", state = "1.7 Animations " + (on ? "on" : "off") + ": ";
        List<CoreProbe.Step> steps = new ArrayList<>();
        for (int slot : new int[]{0, 1, 3}) {
            String what = slot == 0 ? "block" : slot == 1 ? "bow" : "eat";
            steps.add(mc -> {
                MODULE.setEnabled(on);
                select(mc, slot);
                return after(15);
            });
            steps.add(mc -> {
                net.minecraft.util.MovingObjectPosition target = mc.objectMouseOver;
                check(target != null && target.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK, state + "looking at the ground");
                net.minecraft.block.Block block = mc.theWorld.getBlockState(target.getBlockPos()).getBlock();
                float perTick = block.getPlayerRelativeBlockHardness(mc.thePlayer, mc.theWorld, target.getBlockPos());
                check(perTick * 12 < 1, state + block.getLocalizedName() + " outlasts the 10 ticks this mines it (" + perTick + " a tick)");
                focusWas = mc.inGameHasFocus;
                mc.inGameHasFocus = true; // QA: runTick mines only with in-game focus; the synthetic keys stand in for the window's
                key(mc.gameSettings.keyBindAttack, true);
                return after(4);
            });
            steps.add(mc -> {
                check(mc.playerController.getIsHittingBlock() && !mc.thePlayer.isUsingItem(), state + "the held attack key mines the ground");
                key(mc.gameSettings.keyBindUseItem, true);
                OldAnimations189.resetHits();
                return after(slot == 1 ? 6 : 3);
            });
            steps.add(mc -> {
                boolean using = mc.thePlayer.isUsingItem();
                if (on) check(using && OldAnimations189.hits(Hook.FP_HAND) > 0, state + "the use key starts the " + what
                    + " at once while the attack key mines, as in 1.7 " + counts());
                else check(!using && mc.playerController.getIsHittingBlock(), state + "1.8.9 ignores the use key while mining: no " + what);
                if (on && slot == 3) check(OldAnimations189.hits(Hook.SWING) == 0 && OldAnimations189.usedSwing == 0,
                    state + "eating on a block never swings the food " + counts());
                key(mc.gameSettings.keyBindAttack, false); // the mining stops here: the ground never breaks
                screenshot(mc, "170-" + what + "-on-block" + tag);
                select(mc, 4); // the empty slot ends the use first: no arrow flies
                return after(2);
            });
            steps.add(mc -> {
                key(mc.gameSettings.keyBindAttack, false);
                key(mc.gameSettings.keyBindUseItem, false);
                mc.inGameHasFocus = focusWas;
                return after(6);
            });
            steps.add(mc -> {
                check(!mc.thePlayer.isUsingItem() && !mc.playerController.getIsHittingBlock(), state + "both keys up: no use, no mining");
                return after(2);
            });
        }
        return steps;
    }

    /** The swing placing a block plays (EntityPlayerSP.swingItem), shown two ticks in with Legacy Swing on. */
    private static boolean legacySwing(Minecraft mc) {
        legacyFrames = LegacySwing189.frames;
        OldAnimations189.resetHits();
        mc.thePlayer.swingItem();
        return after(2);
    }

    private static Module legacy() {
        return ModuleManager.getInstance().getModule("LegacySwing");
    }

    /** Survival and hungry, so the beef can be eaten and the server's hit hurts. */
    private static boolean survival(Minecraft mc) {
        check(!mc.thePlayer.isBurning(), "the fire is out");
        onServer(mc, player -> {
            survivalSafe(player);
            player.getFoodStats().readNBT(food(10, 0));
            player.setHealth(player.getMaxHealth());
        });
        return after(20);
    }

    /** Eating with the attack key held on the ground, then a 1-point hit seen in third person (red armour, no heart flash). */
    private static List<CoreProbe.Step> survival(boolean on) {
        String tag = on ? "" : "-off", state = "1.7 Animations " + (on ? "on" : "off") + ": ";
        return Arrays.<CoreProbe.Step>asList(
            mc -> {
                check(!mc.playerController.isInCreativeMode() && mc.thePlayer.getFoodStats().getFoodLevel() < 20,
                    state + "survival and hungry (food " + mc.thePlayer.getFoodStats().getFoodLevel() + ")");
                MODULE.setEnabled(on);
                select(mc, 3);
                return after(15);
            },
            mc -> {
                key(mc.gameSettings.keyBindUseItem, true);
                return after(4);
            },
            mc -> {
                check(mc.thePlayer.isUsingItem() && holds(mc.thePlayer.getItemInUse(), Items.cooked_beef), state + "the held use key eats");
                key(mc.gameSettings.keyBindAttack, true);
                OldAnimations189.resetHits();
                return after(8);
            },
            mc -> {
                int left = mc.thePlayer.getItemInUseCount();
                check(mc.thePlayer.isUsingItem() && left > 0 && left < 32, state + "still eating, " + left + " of 32 ticks left");
                if (on) check(OldAnimations189.hits(Hook.FP_HAND) > 0 && OldAnimations189.hits(Hook.FP_ICON) > 0 && OldAnimations189.hits(Hook.SWING) == 0
                    && OldAnimations189.usedSwing == 0 && !mc.thePlayer.isSwingInProgress, state + "1.7's eating pose and placement; the held attack "
                    + "does not swing the food " + counts());
                else check(silent() && !mc.thePlayer.isSwingInProgress, state + "1.8.9's eating, no swing " + counts());
                screenshot(mc, "151-eat" + tag);
                key(mc.gameSettings.keyBindAttack, false);
                select(mc, 4); // as with the bow: the empty slot ends the use first
                return after(2);
            },
            mc -> {
                key(mc.gameSettings.keyBindUseItem, false);
                return after(4);
            },
            mc -> {
                check(!mc.thePlayer.isUsingItem(), state + "eating stopped");
                mc.gameSettings.thirdPersonView = 2;
                OldAnimations189.resetHits();
                onServer(mc, player -> player.attackEntityFrom(DamageSource.generic, 1));
                return true;
            },
            mc -> mc.thePlayer.hurtTime > 0 && after(2),
            mc -> {
                check(mc.thePlayer.hurtTime > 0, state + "the server's 1-point hit reached the client (hurtTime " + mc.thePlayer.hurtTime + ")");
                screenshot(mc, "151-red-armour" + tag);
                return after(6); // the hearts blink from 3 to 5 ticks after the hit
            },
            mc -> {
                hooks(on, "hurt in a chestplate (armour tint, heart blink)", Hook.ARMOUR, Hook.HEARTS);
                check(new LayerBipedArmor(null).shouldCombineTextures() == on, state + "the armour layer " + (on ? "takes" : "skips") + " the hurt tint");
                mc.gameSettings.thirdPersonView = 0;
                return after(30); // past the 20-tick damage cooldown
            });
    }

    private static boolean restore(Minecraft mc) {
        key(mc.gameSettings.keyBindUseItem, false);
        key(mc.gameSettings.keyBindAttack, false);
        key(mc.gameSettings.keyBindSneak, false);
        mc.gameSettings.thirdPersonView = viewWas;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = pitchWas;
        select(mc, slotWas);
        for (Feature feature : Feature.values()) MODULE.option(feature).set(optionsWere[feature.ordinal()]);
        MODULE.setEnabled(wasEnabled);
        legacy().setEnabled(legacyWas);
        ItemStack[] main = mainWas, armour = armourWas;
        mainWas = null; // restored once, also when CoreProbe stops after a failure (stop)
        onServer(mc, player -> {
            player.extinguish();
            player.setGameType(WorldSettings.GameType.CREATIVE);
            if (spawningWas != null) player.worldObj.getGameRules().setOrCreateGameRule("doMobSpawning", spawningWas);
            spawningWas = null;
            player.getFoodStats().readNBT(food(20, 5));
            player.setHealth(player.getMaxHealth());
            System.arraycopy(main, 0, player.inventory.mainInventory, 0, main.length);
            System.arraycopy(armour, 0, player.inventory.armorInventory, 0, armour.length);
        });
        return after(20);
    }

    /** CoreProbe.finish after a failure: the player, inventory and module go back even though the steps stopped. */
    static void stop() {
        if (mainWas == null) return;
        try { restore(Minecraft.getMinecraft()); } catch (Throwable ignored) {}
    }

    private static boolean restored(Minecraft mc) {
        check(mc.playerController.isInCreativeMode() && mc.thePlayer.getHealth() == mc.thePlayer.getMaxHealth()
            && !mc.thePlayer.isUsingItem() && mc.thePlayer.fishEntity == null && MODULE.isEnabled() == wasEnabled,
            "1.7 Animations: module, options, inventory, game mode, health, food and camera are back as found (creative "
            + mc.playerController.isInCreativeMode() + ", health " + mc.thePlayer.getHealth() + ", using " + mc.thePlayer.isUsingItem()
            + ", hook " + (mc.thePlayer.fishEntity != null) + ", module " + MODULE.isEnabled() + "/" + wasEnabled + ")");
        return after(1);
    }

    /** With the module on each named hook changed 1.8.9's result since the last reset; off, none did. */
    private static void hooks(boolean on, String what, Hook... expected) {
        boolean ok = on || silent();
        if (on) for (Hook hook : expected) ok &= OldAnimations189.hits(hook) > 0;
        check(ok, "1.7 Animations " + (on ? "on" : "off") + ", " + what + ": " + (on ? "hooks ran " + Arrays.toString(expected) : "no hook ran")
            + " " + counts());
    }

    private static boolean silent() {
        for (Hook hook : Hook.values()) if (OldAnimations189.hits(hook) != 0) return false;
        return true;
    }

    private static String counts() {
        StringBuilder counts = new StringBuilder("(");
        for (Hook hook : Hook.values()) counts.append(hook).append('=').append(OldAnimations189.hits(hook)).append(hook.ordinal() < Hook.values().length - 1 ? " " : ")");
        return counts.toString();
    }

    private static List<EntityItem> items(Minecraft mc) {
        return mc.theWorld.getEntitiesWithinAABB(EntityItem.class, mc.thePlayer.getEntityBoundingBox().expand(8, 4, 8));
    }

    private static boolean holds(ItemStack stack, Item item) {
        return stack != null && stack.getItem() == item;
    }

    /** Hotbar slot; PlayerControllerMP sends the change on the next tick. */
    private static void select(Minecraft mc, int slot) {
        mc.thePlayer.inventory.currentItem = slot;
    }

    /** Held or released as if by the player: runTick and the movement input read the key binding's state. */
    private static void key(KeyBinding key, boolean down) {
        KeyBinding.setKeyBindState(key.getKeyCode(), down);
    }

    private static ItemStack[] copy(ItemStack[] stacks) {
        ItemStack[] copy = new ItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) copy[i] = ItemStack.copyItemStack(stacks[i]);
        return copy;
    }

    private static NBTTagCompound food(int level, float saturation) {
        NBTTagCompound food = new NBTTagCompound();
        food.setInteger("foodLevel", level);
        food.setInteger("foodTickTimer", 0);
        food.setFloat("foodSaturationLevel", saturation);
        food.setFloat("foodExhaustionLevel", 0);
        return food;
    }

    private static String spawningWas;

    /** Survival without being killed meanwhile: superflat spawns slimes, so hostile mobs go and stop spawning until restore. */
    private static void survivalSafe(EntityPlayerMP player) {
        net.minecraft.world.GameRules rules = player.worldObj.getGameRules();
        if (spawningWas == null) spawningWas = rules.getString("doMobSpawning");
        rules.setOrCreateGameRule("doMobSpawning", "false");
        for (Object entity : player.worldObj.loadedEntityList)
            if (entity instanceof net.minecraft.entity.monster.IMob) ((net.minecraft.entity.Entity) entity).setDead();
        player.setGameType(WorldSettings.GameType.SURVIVAL);
    }

    private interface ServerTask { void run(EntityPlayerMP player); }

    private static void onServer(Minecraft mc, ServerTask task) {
        MinecraftServer server = mc.getIntegratedServer();
        java.util.UUID id = mc.thePlayer.getUniqueID();
        server.addScheduledTask(() -> task.run(server.getConfigurationManager().getPlayerByUUID(id)));
    }
}
