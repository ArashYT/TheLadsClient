package com.thelads.core.v1_20_1.feature;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v1_20_1.game.survival.NativeStackRefill1201;
import com.thelads.core.v1_20_1.game.survival.NativeSurvivalTweaks1201;
import com.thelads.core.v1_20_1.game.survival.mixin.ChatHistoryQaAccessor1201;
import com.thelads.core.v1_20_1.game.survival.mixin.CreeperQaInvoker1201;
import com.thelads.core.v1_20_1.game.survival.mixin.SoulSpeedQaInvoker1201;
import com.thelads.core.v1_20_1.game.survival.mixin.SurvivalFireBlockInvoker1201;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ThornsEnchantment;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (LADS_QA_PARITY=1), as 26.3's other probes: SurvivalTweaks and StackRefill in the QA world. Checks that every mixin is merged
 * into its game class, then runs each tweak against the real game, switched on and off where that tells the two apart: block drops,
 * berry bush, boat view, Thorns and Soul Speed wear, obsidian speed, cobweb, Fast IP Ping, Longer Chat History, animals eating a
 * dropped wheat, and StackRefill (stone placed until the hand stack runs out). Log: "Lads parity probe PASS/FAIL ...", then
 * "Lads parity probe END: N passed, M failed".
 */
public final class ParityProbe1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static int inWorld, step = -1, wait, passed, failed, serverTests, sub;
    private static boolean done, sent;
    private static volatile String serverResult;
    private static java.util.UUID cow, wheat;
    private static BlockPos stoneAt;

    private ParityProbe1201() {}

    public static void register() {
        if (System.getenv("LADS_QA_PARITY") != null) ClientTickEvents.END_CLIENT_TICK.register(ParityProbe1201::tick);
    }

    private static void record(String name, boolean ok, String detail) {
        if (ok) passed++; else failed++;
        LOGGER.info("Lads parity probe {} {}: {}", ok ? "PASS" : "FAIL", name, detail);
    }

    private static void recordResult(String name, String result) {
        record(name, result != null && result.startsWith("PASS"), result);
    }

    private static void tick(Minecraft mc) {
        if (done || mc.player == null || mc.level == null) return;
        if (step < 0) {
            if (mc.screen != null || ++inWorld < 100 || mc.getSingleplayerServer() == null) return; // after the QA world's own capture
            step = 0;
            LOGGER.info("Lads parity probe START (game mode {}, survival tweaks {}, stack refill {})",
                mc.gameMode.getPlayerMode(), SurvivalTweaksModule.get() != null, com.thelads.core.modules.StackRefillModule.get() != null);
        }
        if (wait > 0) { wait--; return; }
        try {
            run(mc);
        } catch (Throwable failure) {
            done = true;
            LOGGER.error("Lads parity probe END: failed", failure);
        }
    }

    private static final List<String> SERVER_NAMES = List.of("Stable block drops", "Berry bush protection", "Boat 360 View",
        "No Thorns damage", "No Soul Speed damage");

    private static void run(Minecraft mc) {
        switch (step) {
            case 0 -> { applied(); clientChecks(mc); step = 2; } // StackRefill first: the boat test below moves the player
            case 1 -> { // server-side tweaks, one task each
                if (serverTests >= SERVER_NAMES.size()) { step = 9; return; }
                if (!sent) {
                    int index = serverTests;
                    serverTask(mc, (sp, level) -> switch (index) {
                        case 0 -> dropsTest(sp, level);
                        case 1 -> berryTest(sp, level);
                        case 2 -> boatTest(sp, level);
                        case 3 -> thornsTest(sp);
                        default -> soulSpeedTest(sp, level);
                    });
                    sent = true;
                } else if (serverResult != null) {
                    recordResult(SERVER_NAMES.get(serverTests++), serverResult);
                    sent = false;
                }
            }
            case 2 -> { serverTask(mc, (sp, level) -> refillSetup(sp)); step = 3; }
            case 3 -> { if (serverResult != null) { recordResult("StackRefill setup", serverResult); wait = 5; step = 4; } }
            case 4 -> { placeStone(mc, 0); wait = 3; step = 5; }
            case 5 -> { placeStone(mc, 1); wait = 1; step = 6; sub = 0; }
            case 6 -> refillResult(mc);
            case 7 -> { serverTask(mc, (sp, level) -> refillCleanup(sp, level)); step = 8; }
            case 8 -> { if (serverResult != null) { LOGGER.info("Lads parity probe cleanup: {}", serverResult); step = 1; } }
            case 9 -> { serverTask(mc, (sp, level) -> animalSetup(sp, level)); step = 10; }
            case 10 -> { if (serverResult != null) { recordResult("Animals eat floor food setup", serverResult); sub = 0; wait = 10; step = 11; } }
            case 11 -> { serverTask(mc, (sp, level) -> animalPoll(level)); step = 12; }
            case 12 -> animalWait();
            case 13 -> { if (serverResult != null) { serverTask(mc, (sp, level) -> elytraSetup(sp, level, true)); step = 14; } }
            case 14 -> { if (serverResult != null) { wait = 6; step = 15; } }
            case 15 -> { serverTask(mc, (sp, level) -> elytraCheck(sp)); step = 16; }
            case 16 -> { if (serverResult != null) { elytraOn = serverResult; serverTask(mc, (sp, level) -> elytraSetup(sp, level, false)); step = 17; } }
            case 17 -> { if (serverResult != null) { wait = 6; step = 18; } }
            case 18 -> { serverTask(mc, (sp, level) -> elytraCheck(sp)); step = 19; }
            case 19 -> {
                if (serverResult != null) {
                    String off = serverResult;
                    record("Elytra water swim", elytraOn.contains("gliding=false") && elytraOn.contains("inWater=true"),
                        "gliding player dropped into water: " + elytraOn + " (option off: " + off + ")");
                    serverTask(mc, (sp, level) -> elytraCleanup(sp, level));
                    step = 20;
                }
            }
            case 20 -> { if (serverResult != null) { serverTask(mc, (sp, level) -> creeperTest(sp, level)); step = 21; } }
            case 21 -> { if (serverResult != null) { recordResult("100% creeper block drops", serverResult); step = 22; } }
            default -> finish();
        }
    }

    private static void finish() {
        done = true;
        LOGGER.info("Lads parity probe info: survival gates asked {} times, stack refills {}", NativeSurvivalTweaks1201.GATE_CALLS.get(), NativeStackRefill1201.refills);
        LOGGER.info("Lads parity probe END: {} passed, {} failed", passed, failed);
    }

    // ---------------------------------------------------------------- server plumbing

    private interface ServerBody { String run(ServerPlayer player, ServerLevel level); }

    private static void serverTask(Minecraft mc, ServerBody body) {
        serverResult = null;
        var server = mc.getSingleplayerServer();
        var player = mc.player;
        server.execute(() -> {
            try {
                ServerPlayer sp = server.getPlayerList().getPlayer(player.getUUID());
                serverResult = sp == null ? "FAIL no server player" : body.run(sp, sp.serverLevel());
            } catch (Throwable failure) {
                serverResult = "FAIL threw " + failure;
            }
        });
    }

    // ---------------------------------------------------------------- mixins merged into their classes

    private record Merge(String tweak, Class<?> target, String handler) {}

    private static void applied() {
        List<Merge> merges = List.of(
            new Merge("Animals eat floor food", Animal.class, "lads$addSearchForFoodGoal"),
            new Merge("Animals eat floor food", net.minecraft.world.entity.Mob.class, "lads$allowAnimalFoodPickup"),
            new Merge("Animals eat floor food", net.minecraft.world.entity.Mob.class, "lads$animalConsumeFloorFood"),
            new Merge("Animals eat floor food", net.minecraft.world.entity.Mob.class, "lads$preventAnimalFoodEquipmentReplace"),
            new Merge("Berry bush protection", SweetBerryBushBlock.class, "lads$reduceBerryBushSlowWhenArmored"),
            new Merge("Berry bush protection", SweetBerryBushBlock.class, "lads$preventBerryBushDamage"),
            new Merge("Boat 360 View", Boat.class, "lads$boat360Clamp"),
            new Merge("100% creeper block drops", Creeper.class, "lads$creeperExplosionDropsAll"),
            new Merge("Elytra water swim", Player.class, "lads$cancelElytraFlyingInLiquid"),
            new Merge("No Thorns damage", ThornsEnchantment.class, "lads$preventThornsDamage"),
            new Merge("No Soul Speed damage", LivingEntity.class, "lads$preventSoulSpeedDamage"),
            new Merge("Faster obsidian mining", DiggerItem.class, "lads$boostObsidianMiningSpeed"),
            new Merge("Stable block drops", Block.class, "lads$stabilizeBlockDropSpawnOffset"),
            new Merge("Stable block drops", ItemEntity.class, "lads$stabilizeDroppedItemMovement"),

            new Merge("Dimension brightness", LightTexture.class, "lads$modifyDimensionBrightness"),
            new Merge("Flammable cobwebs", FireBlock.class, "lads$invokeSetFlammable"),
            new Merge("Longer Chat History", ChatComponent.class, "lads$historyCap"),
            new Merge("Fast IP Ping", ServerNameResolver.class, "setHostnameToIpAddressToAvoidReversedDnsLookupOnGetHostname"));
        if (!com.thelads.core.v1_20_1.compat.ModPresence1201.onForge()) { // the Keep Bars mixin is Fabric-only (FabricOnlyMixinPlugin1201)
            merges = new java.util.ArrayList<>(merges);
            merges.add(new Merge("Keep Bars While Riding", Gui.class, "lads$keepXpBarWhileRiding"));
            merges.add(new Merge("Keep Bars While Riding", Gui.class, "lads$keepFoodBarWhileRiding"));
        }
        for (Merge merge : merges) {
            boolean found = false;
            for (Method method : merge.target().getDeclaredMethods()) {
                if (method.getName().contains(merge.handler())) { found = true; break; }
            }
            record("mixin merged: " + merge.tweak(), found, merge.handler() + " in " + merge.target().getSimpleName());
        }
    }

    // ---------------------------------------------------------------- client-side checks

    private static void clientChecks(Minecraft mc) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (mod == null) { record("SurvivalTweaks module", false, "not registered"); return; }

        // Faster obsidian mining: a diamond pickaxe mines obsidian 1.6x as fast; stone and a block it cannot mine stay as they were.
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        float on = pick.getDestroySpeed(obsidian), stone = pick.getDestroySpeed(Blocks.STONE.defaultBlockState()), dirt = pick.getDestroySpeed(Blocks.DIRT.defaultBlockState());
        mod.fasterObsidianMining.set(false);
        float off = pick.getDestroySpeed(obsidian);
        mod.fasterObsidianMining.set(true);
        record("Faster obsidian mining", Math.abs(on - off * 1.6f) < 0.01f && off > 1f && stone == off && dirt == 1f,
            "obsidian " + on + " (option off " + off + "), stone " + stone + ", dirt " + dirt);

        // Flammable cobwebs: fire can catch on a cobweb (igniteOdds was 0 in vanilla).
        int odds = ((SurvivalFireBlockInvoker1201) Blocks.FIRE).lads$igniteOdds(Blocks.COBWEB.defaultBlockState());
        record("Flammable cobwebs", odds > 0, "ignite odds " + odds + " (vanilla 0)");

        // Fast IP Ping: the resolved address of an ip keeps the ip as its host name (no reverse lookup), unless the option is off.
        String ipOn = hostOf("127.0.0.1");
        mod.fastIpPing.set(false);
        String ipOff = hostOf("127.0.0.1");
        mod.fastIpPing.set(true);
        record("Fast IP Ping", "127.0.0.1".equals(ipOn) && !ipOn.equals(ipOff), "host name " + ipOn + " (option off: " + ipOff + ")");

        longerChat(mc, mod);
    }

    private static String hostOf(String ip) {
        Optional<ResolvedServerAddress> resolved = ServerNameResolver.DEFAULT.resolveAddress(new ServerAddress(ip, 25565));
        return resolved.map(ResolvedServerAddress::getHostName).orElse("-");
    }

    /** Longer Chat History: with Infinite History off, 150 messages are all kept (vanilla keeps 100). */
    private static void longerChat(Minecraft mc, SurvivalTweaksModule mod) {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        if (!(chat != null && chat.getOption("Infinite History") instanceof BoolOption infinite)) {
            record("Longer Chat History", false, "Chat module has no Infinite History option");
            return;
        }
        boolean infiniteWas = infinite.get(), longerWas = mod.longerChatHistory.get();
        ChatComponent component = mc.gui.getChat();
        ChatHistoryQaAccessor1201 access = (ChatHistoryQaAccessor1201) component;
        infinite.set(false);
        mod.longerChatHistory.set(true);
        component.clearMessages(false);
        for (int i = 0; i < 150; i++) component.addMessage(Component.literal("parity probe line " + i));
        int kept = access.lads$allMessages().size(), lines = access.lads$trimmedMessages().size();
        mod.longerChatHistory.set(false);
        component.clearMessages(false);
        for (int i = 0; i < 150; i++) component.addMessage(Component.literal("parity probe line " + i));
        int vanilla = access.lads$allMessages().size();
        component.clearMessages(false);
        infinite.set(infiniteWas);
        mod.longerChatHistory.set(longerWas);
        record("Longer Chat History", kept >= 150 && lines >= 150 && vanilla == 100, "150 added: kept " + kept + " messages / " + lines + " lines (option off: " + vanilla + ")");
    }

    // ---------------------------------------------------------------- server-side checks (each runs on the integrated server thread)

    private static String dropsTest(ServerPlayer sp, ServerLevel level) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        double stableOffset = drops(sp, level), stableMotion = lastMotion;
        mod.stableBlockDrops.set(false);
        double randomOffset = drops(sp, level);
        mod.stableBlockDrops.set(true);
        boolean ok = stableOffset < 1e-6 && stableMotion == 0 && randomOffset > 0.01;
        return (ok ? "PASS" : "FAIL") + " 8 dirt drops: spawn offset from the block centre " + stableOffset + ", sideways motion " + stableMotion
            + " (option off: offset " + randomOffset + ")";
    }

    private static double lastMotion;

    /** The biggest distance from the block centre a freshly popped item spawned at, over 8 drops. */
    private static double drops(ServerPlayer sp, ServerLevel level) {
        BlockPos base = sp.blockPosition().relative(sp.getDirection().getOpposite(), 4);
        double worst = 0;
        lastMotion = 0;
        for (int i = 0; i < 8; i++) {
            BlockPos pos = base.above(i % 2);
            Block.popResource(level, pos, new ItemStack(Items.DIRT));
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.0), e -> e.getItem().is(Items.DIRT) && e.tickCount == 0)) {
                worst = Math.max(worst, Math.max(Math.abs(item.getX() - (pos.getX() + 0.5)), Math.abs(item.getZ() - (pos.getZ() + 0.5))));
                lastMotion = Math.max(lastMotion, Math.max(Math.abs(item.getDeltaMovement().x), Math.abs(item.getDeltaMovement().z)));
                item.discard();
            }
        }
        return worst;
    }

    private static String berryTest(ServerPlayer sp, ServerLevel level) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        BlockPos pos = sp.blockPosition().relative(sp.getDirection().getOpposite(), 4);
        BlockState oldBlock = level.getBlockState(pos);
        BlockState bush = Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3);
        level.setBlock(pos, bush, 3);
        ItemStack oldLegs = sp.getItemBySlot(EquipmentSlot.LEGS);
        float health = sp.getHealth();
        try {
            sp.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            boolean legsSafe = !pricked(sp, level, pos, bush, health);
            sp.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            sp.setShiftKeyDown(true);
            boolean sneakSafe = !pricked(sp, level, pos, bush, health);
            sp.setShiftKeyDown(false);
            boolean barePricked = pricked(sp, level, pos, bush, health);
            mod.berryBushProtection.set(false);
            sp.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            boolean offPricked = pricked(sp, level, pos, bush, health);
            mod.berryBushProtection.set(true);
            boolean ok = legsSafe && sneakSafe && barePricked && offPricked;
            return (ok ? "PASS" : "FAIL") + " thorns on 1 health: leggings safe " + legsSafe + ", sneaking safe " + sneakSafe
                + ", bare legs pricked " + barePricked + ", option off + leggings pricked " + offPricked;
        } finally {
            mod.berryBushProtection.set(true);
            sp.setShiftKeyDown(false);
            sp.setItemSlot(EquipmentSlot.LEGS, oldLegs);
            sp.setHealth(health);
            sp.invulnerableTime = 0;
            level.setBlock(pos, oldBlock, 3);
        }
    }

    /** Walks the player through the bush once (vanilla only hurts an entity that moved): true when it lost health. */
    private static boolean pricked(ServerPlayer sp, ServerLevel level, BlockPos pos, BlockState bush, float health) {
        sp.setHealth(health);
        sp.invulnerableTime = 0;
        sp.xOld = sp.getX() - 1;
        sp.zOld = sp.getZ() - 1;
        bush.entityInside(level, pos, sp);
        boolean hurt = sp.getHealth() < health;
        sp.setHealth(health);
        sp.invulnerableTime = 0;
        return hurt;
    }

    private static String boatTest(ServerPlayer sp, ServerLevel level) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        Vec3 home = sp.position();
        Boat boat = new Boat(level, sp.getX(), sp.getY(), sp.getZ());
        level.addFreshEntity(boat);
        try {
            sp.startRiding(boat, true);
            float on = boatYaw(sp, boat);
            mod.boat360View.set(false);
            float off = boatYaw(sp, boat);
            mod.boat360View.set(true);
            boolean ok = Math.abs(on) > 150f && Math.abs(off) < 110f;
            return (ok ? "PASS" : "FAIL") + " passenger turned 170 degrees from the boat: stays at " + on + " (option off: clamped to " + off + ")";
        } finally {
            mod.boat360View.set(true);
            sp.stopRiding();
            boat.discard();
            sp.teleportTo(home.x, home.y, home.z); // dismounting can leave the player in the ground
        }
    }

    private static float boatYaw(ServerPlayer sp, Boat boat) {
        sp.setYRot(boat.getYRot() + 170f);
        boat.onPassengerTurned(sp);
        return Mth.wrapDegrees(sp.getYRot() - boat.getYRot());
    }

    private static String thornsTest(ServerPlayer sp) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        ItemStack oldChest = sp.getItemBySlot(EquipmentSlot.CHEST);
        try {
            int on = thornsWear(sp);
            mod.noEnchantDurabilityLoss.set(false);
            int off = thornsWear(sp);
            mod.noEnchantDurabilityLoss.set(true);
            return (on == 0 && off > 0 ? "PASS" : "FAIL") + " Thorns III chestplate, 100 reflected hits: wear " + on + " (option off: " + off + ")";
        } finally {
            mod.noEnchantDurabilityLoss.set(true);
            sp.setItemSlot(EquipmentSlot.CHEST, oldChest);
        }
    }

    private static int thornsWear(ServerPlayer sp) {
        ItemStack chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
        chest.enchant(Enchantments.THORNS, 3);
        sp.setItemSlot(EquipmentSlot.CHEST, chest);
        ThornsEnchantment thorns = (ThornsEnchantment) Enchantments.THORNS;
        for (int i = 0; i < 100; i++) thorns.doPostHurt(sp, null, 3);
        return chest.getDamageValue();
    }

    private static String soulSpeedTest(ServerPlayer sp, ServerLevel level) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        BlockPos below = BlockPos.containing(sp.getX(), sp.getY() - 0.500001, sp.getZ());
        BlockState oldBlock = level.getBlockState(below);
        ItemStack oldBoots = sp.getItemBySlot(EquipmentSlot.FEET);
        level.setBlock(below, Blocks.SOUL_SAND.defaultBlockState(), 3);
        try {
            int on = soulSpeedWear(sp);
            mod.noEnchantDurabilityLoss.set(false);
            int off = soulSpeedWear(sp);
            mod.noEnchantDurabilityLoss.set(true);
            return (on == 0 && off > 0 ? "PASS" : "FAIL") + " Soul Speed III boots on soul sand, 300 block changes: wear " + on + " (option off: " + off + ")";
        } finally {
            mod.noEnchantDurabilityLoss.set(true);
            sp.setItemSlot(EquipmentSlot.FEET, oldBoots);
            level.setBlock(below, oldBlock, 3);
        }
    }

    private static int soulSpeedWear(ServerPlayer sp) {
        ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);
        boots.enchant(Enchantments.SOUL_SPEED, 3);
        sp.setItemSlot(EquipmentSlot.FEET, boots);
        SoulSpeedQaInvoker1201 steps = (SoulSpeedQaInvoker1201) sp;
        for (int i = 0; i < 300; i++) {
            steps.lads$removeSoulSpeed();
            steps.lads$tryAddSoulSpeed();
        }
        steps.lads$removeSoulSpeed();
        return boots.getDamageValue();
    }

    // ---------------------------------------------------------------- StackRefill

    private static String refillSetup(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int slot = 31; slot <= 35; slot++) inv.setItem(slot, ItemStack.EMPTY);
        inv.setItem(inv.selected, new ItemStack(Items.STONE, 2));
        inv.setItem(20, new ItemStack(Items.STONE, 64));
        inv.setItem(30, new ItemStack(Items.STONE, 32));
        sp.inventoryMenu.broadcastChanges();
        return "PASS selected slot " + inv.selected + " holds 2 stone, slots 20 (64) and 30 (32) hold more";
    }

    /** Places one stone on the ground two blocks ahead (0), then on top of it (1), as a player right-clicking would. */
    private static void placeStone(Minecraft mc, int which) {
        LocalPlayer player = mc.player;
        BlockPos ground = player.blockPosition().below().relative(player.getDirection(), 2);
        if (which == 0) stoneAt = ground;
        BlockPos target = which == 0 ? stoneAt : stoneAt.above();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target).add(0, 0.5, 0), Direction.UP, target, false);
        BlockPos into = target.above();
        var blockers = mc.level.getEntities(player, new AABB(into));
        var result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        LOGGER.info("Lads parity probe: stone {} placed on {} ({}) into {} ({}) player at {}, entities in the way {} -> {} (hand now {} x{})", which + 1,
            target, mc.level.getBlockState(target).getBlock(), into, mc.level.getBlockState(into).getBlock(), player.position(), blockers, result,
            player.getMainHandItem().getItem(), player.getMainHandItem().getCount());
    }

    private static void refillResult(Minecraft mc) {
        LocalPlayer player = mc.player;
        ItemStack hand = player.getMainHandItem();
        boolean refilled = hand.is(Items.STONE) && hand.getCount() == 32;
        if (!refilled && ++sub < 40) return;
        boolean placed = mc.level.getBlockState(stoneAt.above()).is(Blocks.STONE) && mc.level.getBlockState(stoneAt.above(2)).is(Blocks.STONE);
        record("StackRefill", refilled && placed && NativeStackRefill1201.refills >= 1,
            "after the last of 2 stone was placed (" + placed + ") the hand holds " + hand.getItem() + " x" + hand.getCount() + " (the 32 of slot 30, the highest matching slot), refills " + NativeStackRefill1201.refills);
        step = 7;
    }

    private static String refillCleanup(ServerPlayer sp, ServerLevel level) {
        var inv = sp.getInventory();
        for (int slot = 0; slot < 36; slot++) if (inv.getItem(slot).is(Items.STONE)) inv.setItem(slot, ItemStack.EMPTY);
        level.setBlock(stoneAt.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(stoneAt.above(2), Blocks.AIR.defaultBlockState(), 3);
        sp.inventoryMenu.broadcastChanges();
        return "stone blocks and stacks removed";
    }

    // ---------------------------------------------------------------- Elytra water swim, creeper drops

    private static String elytraOn = "";
    private static Vec3 elytraHome;
    private static ItemStack elytraChest;
    private static BlockPos elytraBase;

    private static String elytraSetup(ServerPlayer sp, ServerLevel level, boolean on) {
        SurvivalTweaksModule.get().elytraWaterSwim.set(on);
        if (elytraHome == null) {
            elytraHome = sp.position();
            elytraChest = sp.getItemBySlot(EquipmentSlot.CHEST);
            elytraBase = surface(level, sp.blockPosition().relative(sp.getDirection(), 6));
            for (int y = 0; y < 4; y++) level.setBlock(elytraBase.above(y), Blocks.WATER.defaultBlockState(), 3);
        }
        sp.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        sp.teleportTo(elytraBase.getX() + 0.5, elytraBase.getY() + 3.0, elytraBase.getZ() + 0.5); // top block of a 4-high water column
        sp.startFallFlying();
        return "PASS elytra worn and gliding, option " + on + ", player at the top of a 4-high water column";
    }

    private static String elytraCheck(ServerPlayer sp) {
        return "gliding=" + sp.isFallFlying() + " inWater=" + sp.isInWater() + " swimming=" + sp.isSwimming();
    }

    private static String elytraCleanup(ServerPlayer sp, ServerLevel level) {
        SurvivalTweaksModule.get().elytraWaterSwim.set(true);
        sp.setItemSlot(EquipmentSlot.CHEST, elytraChest);
        sp.stopFallFlying();
        for (int y = 0; y < 4; y++) level.setBlock(elytraBase.above(y), Blocks.AIR.defaultBlockState(), 3);
        sp.teleportTo(elytraHome.x, elytraHome.y, elytraHome.z);
        return "water and elytra removed";
    }

    /** A creeper blows up inside a 9x9x9 dirt cube: the dirt that drops, with the option on and off (the decay rule keeps a third). */
    private static String creeperTest(ServerPlayer sp, ServerLevel level) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        BlockPos centre = surface(level, sp.blockPosition().relative(sp.getDirection().getClockWise(), 16));
        try {
            int on = creeperDrops(level, centre);
            mod.creeperFullDrops.set(false);
            int off = creeperDrops(level, centre);
            mod.creeperFullDrops.set(true);
            return (on > 50 && on * 2 > off * 3 ? "PASS" : "FAIL") + " creeper blast in a 9x9x9 dirt cube drops " + on + " dirt (option off: " + off + ")";
        } finally {
            mod.creeperFullDrops.set(true);
        }
    }

    private static int creeperDrops(ServerLevel level, BlockPos centre) {
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-4, 0, -4), centre.offset(4, 8, 4))) level.setBlock(p, Blocks.DIRT.defaultBlockState(), 2);
        BlockPos middle = centre.offset(0, 4, 0);
        level.setBlock(middle, Blocks.AIR.defaultBlockState(), 2);
        var creeper = EntityType.CREEPER.create(level);
        creeper.moveTo(middle.getX() + 0.5, middle.getY(), middle.getZ() + 0.5, 0f, 0f);
        ((CreeperQaInvoker1201) creeper).lads$explodeCreeper();
        int dirt = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(centre).inflate(14.0), e -> e.getItem().is(Items.DIRT))) {
            dirt += item.getItem().getCount();
            item.discard();
        }
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-4, 0, -4), centre.offset(4, 8, 4))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        return dirt;
    }

    // ---------------------------------------------------------------- Animals eat floor food

    private static String animalSetup(ServerPlayer sp, ServerLevel level) {
        Direction away = sp.getDirection().getOpposite();
        BlockPos spot = surface(level, sp.blockPosition().relative(away, 8));
        var animal = EntityType.COW.create(level);
        animal.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
        animal.setPersistenceRequired();
        level.addFreshEntity(animal);
        BlockPos food = surface(level, sp.blockPosition().relative(away, 5));
        ItemEntity item = new ItemEntity(level, food.getX() + 0.5, food.getY() + 0.5, food.getZ() + 0.5, new ItemStack(Items.WHEAT, 2));
        item.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(item);
        cow = animal.getUUID();
        wheat = item.getUUID();
        return "PASS cow at " + spot + ", 2 wheat on the ground at " + food + " (3 blocks apart)";
    }

    /** The first free block above the ground at that column. */
    private static BlockPos surface(ServerLevel level, BlockPos column) {
        return new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), column.getZ());
    }

    private static String animalPoll(ServerLevel level) {
        var animal = level.getEntity(cow);
        var item = level.getEntity(wheat) instanceof ItemEntity i ? i : null;
        int left = item == null || !item.isAlive() ? 0 : item.getItem().getCount();
        boolean love = animal instanceof Animal a && a.isInLove();
        return (left < 2 ? "DONE" : "WAIT") + " wheat left " + left + ", cow in love " + love;
    }

    private static void animalWait() {
        if (serverResult == null) return;
        sub += 10;
        if (serverResult.startsWith("DONE") && serverResult.endsWith("true")) {
            record("Animals eat floor food", true, serverResult + " after ~" + sub + " ticks");
            step = 13;
        } else if (sub >= 400) {
            record("Animals eat floor food", false, serverResult + " after ~" + sub + " ticks");
            step = 13;
        } else {
            wait = 10;
            step = 11;
        }
        if (step == 13) {
            Minecraft mc = Minecraft.getInstance();
            serverTask(mc, (sp, level) -> {
                var animal = level.getEntity(cow);
                if (animal != null) animal.discard();
                if (level.getEntity(wheat) != null) level.getEntity(wheat).discard();
                return "cow and wheat removed";
            });
        }
    }
}
