package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thelads.core.config.Option;
import com.thelads.core.modules.ItemPhysicsModule;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-itemphysics" from the harness's LADS_VERIFY_CAPTURE_ITEMPHYSICS): builds a stone arena
 * with a water pool, a lava pool and a cactus in open sky above the QA player (from y 150), drops real items on the integrated server and saves
 * itemphysics-*.png frames, with checks for every singleplayer rule. The arena, items, player, inventory and settings are put back.
 */
final class ItemPhysicsCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private interface Step { int run(Minecraft mc) throws Exception; }
    private static final List<Step> STEPS = new ArrayList<>();
    private static final Map<String, Object> FACTS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, BlockState> ARENA = new LinkedHashMap<>();
    private static final List<Integer> SPAWNED = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final Map<Option, JsonElement> OPTIONS = new LinkedHashMap<>();
    private static final List<ItemStack> INVENTORY = new ArrayList<>();
    private static int step = -1, wait, checks, failures, frames, sample;
    private static String pendingFrame;
    private static boolean frameBusy, enabledBefore, hideGuiBefore, flyingBefore;
    private static long modifiedBefore;
    private static double[] posBefore;
    private static float yawBefore, pitchBefore;
    private static GameType modeBefore;
    private static CameraType cameraBefore;
    private static net.minecraft.client.CloudStatus cloudsBefore;
    private static volatile BlockPos base;
    private static int[] offPixels, onPixels;
    private ItemPhysicsCapture() {}

    static boolean busy() { return step >= 0 && step < STEPS.size(); }

    static void tick(Path game, boolean ready) {
        if (busy()) { run(Minecraft.getInstance()); return; }
        if (step >= 0 || !ready) return;
        Path request = game.resolve(".lads-qa-capture-itemphysics");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try { Files.delete(request); } catch (Exception failure) { LOGGER.error("Lads item physics capture FAILED: request", failure); return; }
        STEPS.clear();
        STEPS.addAll(steps());
        LOGGER.info("Lads item physics capture BEGIN: {} steps; arena, items, player and settings restored afterwards", STEPS.size());
        step = 0;
    }

    private static void run(Minecraft mc) {
        if (pendingFrame != null || frameBusy) return;
        if (wait > 0) { wait--; return; }
        try {
            int next = STEPS.get(step).run(mc);
            if (next < 0) return;
            wait = next;
            if (++step == STEPS.size()) LOGGER.info("Lads item physics capture END: {} passed, {} failed; {} frames", checks, failures, frames);
        } catch (Throwable failure) {
            LOGGER.error("Lads item physics capture FAILED: step {}", step, failure);
            failures++;
            step = STEPS.size() - 1; // the last step puts everything back
        }
    }

    /** Each completed frame (NativeWorldVerification.renderedFrame): saves the frame a step asked for. */
    static void frame(RenderTarget target, Path game) {
        if (pendingFrame == null || frameBusy) return;
        String name = "itemphysics-" + pendingFrame;
        pendingFrame = null;
        frameBusy = true;
        try {
            Path output = game.resolve("screenshots").resolve(name + ".png");
            Files.createDirectories(output.getParent());
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try {
                    image.writeToFile(output);
                    frames++;
                    LOGGER.info("Lads item physics frame {}", output);
                    if (name.endsWith("-off-a")) offPixels = image.getPixels();
                    if (name.endsWith("-on-frozen")) onPixels = image.getPixels();
                    if (name.endsWith("-off-b")) {
                        int[] pixels = image.getPixels();
                        int changed = offPixels == null ? -1 : differing(offPixels, pixels);
                        check(changed == 0, "module off draws the frozen scene pixel for pixel as before it was on (" + changed + " pixels differ)");
                        check(onPixels != null && differing(offPixels, onPixels) > 1000, "module on changes that scene ("
                            + (onPixels == null ? -1 : differing(offPixels, onPixels)) + " pixels differ)");
                    }
                } catch (Exception failure) { failures++; LOGGER.error("Lads item physics capture FAILED: {}", name, failure); }
                finally { image.close(); frameBusy = false; }
            });
        } catch (Exception failure) { frameBusy = false; failures++; LOGGER.error("Lads item physics capture FAILED: {}", name, failure); }
    }

    private static int differing(int[] a, int[] b) {
        if (a.length != b.length) return Integer.MAX_VALUE;
        int count = 0;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) count++;
        return count;
    }

    private static void check(boolean ok, String what) {
        if (ok) { checks++; LOGGER.info("Lads item physics check PASS: {}", what); }
        else { failures++; LOGGER.error("Lads item physics check FAILED: {}", what); }
    }

    private static ItemPhysicsModule module() { return NativeItemPhysics.module(); }

    private static void onServer(Consumer<ServerPlayer> task) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> task.accept(server.getPlayerList().getPlayer(id)));
    }

    /** Server thread: a dropped item at (x, y, z) above the arena floor, tracked for clean-up. */
    private static ItemEntity spawn(ServerLevel level, Item item, int count, double x, double y, double z, double dx, double dy, double dz) {
        ItemEntity entity = new ItemEntity(level, base.getX() + x, base.getY() + y, base.getZ() + z, new ItemStack(item, count), dx, dy, dz);
        entity.setNeverPickUp();
        level.addFreshEntity(entity);
        SPAWNED.add(entity.getId());
        return entity;
    }

    /** Server thread: where each tracked item is now, as "x,y,z,alive" under its name. */
    private static void record(ServerLevel level, String name, ItemEntity entity) {
        FACTS.put(name, new double[]{entity.getX() - base.getX(), entity.getY() - base.getY(), entity.getZ() - base.getZ(), entity.isAlive() ? 1 : 0});
    }

    private static double[] fact(String name) {
        double[] value = (double[]) FACTS.get(name);
        if (value == null) throw new IllegalStateException("no record of " + name);
        return value;
    }

    private static void clearItems(ServerLevel level) {
        for (int id : SPAWNED) if (level.getEntity(id) instanceof ItemEntity item) item.discard();
        SPAWNED.clear();
    }

    /** The player, flying, at (x, y, z) above the arena floor looking with this yaw and pitch. */
    private static void view(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        double ax = base.getX() + x, ay = base.getY() + y, az = base.getZ() + z;
        onServer(player -> player.teleportTo(ax, ay, az));
        mc.player.getAbilities().flying = true;
        mc.player.onUpdateAbilities();
        mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setXRot(pitch);
        mc.player.xRotO = pitch;
    }

    /** F1: the HUD hidden (true) or shown. */
    private static void hud(Minecraft mc, boolean hidden) {
        if (mc.gui.hud.isHidden() != hidden) mc.gui.hud.toggle();
    }

    private static void frozen(boolean frozen) {
        onServer(player -> player.level().getServer().tickRateManager().setFrozen(frozen));
    }

    private static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(mc -> { // save everything, then build the arena
            ItemPhysicsModule module = module();
            enabledBefore = module.isEnabled();
            modifiedBefore = module.getLastModified();
            OPTIONS.clear();
            for (Option option : module.getOptions()) OPTIONS.put(option, option.save().deepCopy());
            module.getOptions().forEach(Option::reset);
            module.setEnabled(true);
            LOGGER.info("Lads item physics rules: this client's level {}, integrated server {}; a remote server's world is only a ClientLevel "
                + "with no integrated server, so rules() is null there and every rule stays off", NativeItemPhysics.rules(mc.level) != null ? "on" : "off",
                mc.hasSingleplayerServer() ? "running" : "absent");
            posBefore = new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()};
            yawBefore = mc.player.getYRot();
            pitchBefore = mc.player.getXRot();
            modeBefore = mc.gameMode.getPlayerMode();
            cameraBefore = mc.options.getCameraType();
            hideGuiBefore = mc.gui.hud.isHidden();
            flyingBefore = mc.player.getAbilities().flying;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.level.setRainLevel(0);
            mc.level.setThunderLevel(0);
            onServer(player -> {
                INVENTORY.clear();
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) INVENTORY.add(player.getInventory().getItem(i).copy());
                player.getInventory().clearContent();
                player.setGameMode(GameType.CREATIVE);
                ServerLevel level = player.level();
                base = null; // the first open sky from y 150 up (below the clouds when it can)
                for (int y = 150; y <= 290 && base == null; y += 20) {
                    BlockPos at = BlockPos.containing(posBefore[0], y, posBefore[2]);
                    if (room(level, at)) base = at;
                }
                if (base == null) throw new IllegalStateException("no room for the arena above " + player.blockPosition());
                level.getWeatherData().setRaining(false); // no rain moving between the compared frames
                level.getWeatherData().setThundering(false);
                level.getWeatherData().setClearWeatherTime(6000);
                level.setRainLevel(0);
                level.setThunderLevel(0);
                for (int x = -6; x <= 16; x++)
                    for (int z = -6; z <= 6; z++) set(level, x, 0, z, Blocks.STONE.defaultBlockState());
                for (int x = 1; x <= 5; x++) // water pool, two deep: x 2..4, z -5..-3
                    for (int z = -6; z <= -2; z++) {
                        boolean inside = x >= 2 && x <= 4 && z >= -5 && z <= -3;
                        set(level, x, -1, z, inside ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState());
                        if (inside) { set(level, x, -2, z, Blocks.STONE.defaultBlockState()); set(level, x, 0, z, Blocks.WATER.defaultBlockState()); }
                    }
                for (int x = -4; x <= -2; x++) // lava pool, one deep: x -4..-2, z -5..-3
                    for (int z = -5; z <= -3; z++) { set(level, x, -1, z, Blocks.STONE.defaultBlockState()); set(level, x, 0, z, Blocks.LAVA.defaultBlockState()); }
                set(level, 0, -1, 4, Blocks.STONE.defaultBlockState()); // sand falls without it
                set(level, 0, 0, 4, Blocks.SAND.defaultBlockState());
                set(level, 0, 1, 4, Blocks.CACTUS.defaultBlockState());
                set(level, 0, 2, 4, Blocks.CACTUS.defaultBlockState());
                set(level, -5, 0, 5, Blocks.OAK_PLANKS.defaultBlockState());
                set(level, -5, 0, 3, Blocks.OAK_PLANKS.defaultBlockState());
                FACTS.put("built", true);
            });
            return 10;
        });
        steps.add(mc -> {
            check(FACTS.containsKey("built"), "the arena (stone floor, water and lava pools, cactus) is built in open sky at " + base);
            view(mc, 0.5, 1, -2.5, 0, 30);
            onServer(player -> { // a mix of flat items and blocks in a row, lying still
                Item[] row = {Items.DIAMOND_SWORD, Items.APPLE, Items.STICK, Items.STONE, Items.OAK_LOG, Items.TORCH, Items.CHEST, Items.COBBLESTONE, Items.PAPER};
                int[] counts = {1, 1, 1, 1, 1, 1, 1, 64, 32};
                for (int i = 0; i < row.length; i++) spawn(player.level(), row[i], counts[i], i - 3.5, 1.3, 2.5, 0, 0, 0).setUnlimitedLifetime();
            });
            return 50;
        });
        steps.add(mc -> { pendingFrame = "rest-side"; return 2; });
        steps.add(mc -> { view(mc, 0.5, 5, 2.5, 0, 90); return 15; });
        steps.add(mc -> { pendingFrame = "rest-top"; return 2; });
        // Module off is vanilla: the frozen scene drawn off, on, and off again (HUD hidden: its clock and FPS change by themselves).
        steps.add(mc -> { // clouds move while the game is frozen: off for these frames
            view(mc, 0.5, 1, -2.5, 0, 30);
            frozen(true);
            hud(mc, true);
            cloudsBefore = mc.options.cloudStatus().get();
            mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
            return 15;
        });
        steps.add(mc -> { module().setEnabled(false); return 5; });
        steps.add(mc -> { pendingFrame = "rest-off-a"; return 2; });
        steps.add(mc -> { module().setEnabled(true); return 5; });
        steps.add(mc -> { pendingFrame = "rest-on-frozen"; return 2; });
        steps.add(mc -> { module().setEnabled(false); return 5; });
        steps.add(mc -> { pendingFrame = "rest-off-b"; return 2; });
        steps.add(mc -> { module().setEnabled(true); hud(mc, hideGuiBefore); mc.options.cloudStatus().set(cloudsBefore); frozen(false); return 5; });
        // Mid-air tumble: items tossed up, frozen in flight.
        steps.add(mc -> {
            view(mc, 0.5, 1.6, -3, 0, 5);
            onServer(player -> {
                ServerLevel level = player.level();
                spawn(level, Items.IRON_SWORD, 1, -0.5, 1.2, 0.5, -0.08, 0.42, 0.02);
                spawn(level, Items.GOLDEN_APPLE, 1, 0.5, 1.2, 0.5, 0.02, 0.45, 0.03);
                spawn(level, Items.BRICKS, 1, 1.5, 1.2, 0.5, 0.09, 0.4, 0.01);
                spawn(level, Items.FEATHER, 1, 2.5, 1.2, 0.5, 0.05, 0.38, -0.02);
            });
            return 6;
        });
        steps.add(mc -> { frozen(true); return 4; });
        steps.add(mc -> { pendingFrame = "tumble-midair"; return 2; });
        steps.add(mc -> { frozen(false); return 40; });
        steps.add(mc -> { pendingFrame = "tumble-landed"; return 2; });
        // Water: wood, sticks and wool float; stone, iron and diamonds sink.
        steps.add(mc -> {
            view(mc, 3.5, 3.2, -0.6, 180, 50);
            onServer(player -> {
                ServerLevel level = player.level();
                Item[] items = {Items.OAK_PLANKS, Items.STICK, Items.WOOL.white(), Items.STONE, Items.IRON_INGOT, Items.DIAMOND};
                for (int i = 0; i < items.length; i++) { // at the surface (0.89; the floor's top is 1, the pool's bottom -1)
                    ItemEntity entity = spawn(level, items[i], 1, 2.5 + i % 3, 0.8, -4.5 + i / 3, 0, 0, 0); // half a block from the walls
                    FACTS.put("water-id-" + i, entity.getId());
                }
            });
            return 80;
        });
        steps.add(mc -> {
            onServer(player -> {
                for (int i = 0; i < 6; i++) if (player.level().getEntity((Integer) FACTS.get("water-id-" + i)) instanceof ItemEntity item) record(player.level(), "water-" + i, item);
            });
            pendingFrame = "water";
            return 4;
        });
        steps.add(mc -> {
            String[] names = {"oak planks", "a stick", "white wool", "stone", "an iron ingot", "a diamond"};
            for (int i = 0; i < 6; i++) {
                double[] at = fact("water-" + i);
                if (i < 3) check(at[1] > 0.4, names[i] + " floats at the surface (y " + fmt(at[1]) + ")");
                else check(at[1] < -0.6, names[i] + " sinks to the bottom (y " + fmt(at[1]) + ")");
            }
            view(mc, 3.5, 4.5, -4, 0, 90);
            return 15;
        });
        steps.add(mc -> { pendingFrame = "water-top"; return 2; });
        steps.add(mc -> { // control: module off, stone rises as in vanilla
            module().setEnabled(false);
            onServer(player -> FACTS.put("water-off-id", spawn(player.level(), Items.STONE, 1, 3.5, -0.8, -4, 0, 0, 0).getId()));
            return 160;
        });
        steps.add(mc -> {
            onServer(player -> { if (player.level().getEntity((Integer) FACTS.get("water-off-id")) instanceof ItemEntity item) record(player.level(), "water-off", item); });
            return 4;
        });
        steps.add(mc -> {
            check(fact("water-off")[1] > 0.4, "control, module off: stone rises to the surface as vanilla (y " + fmt(fact("water-off")[1]) + ")");
            module().setEnabled(true);
            return 1;
        });
        // Lava: only flammable items burn; the rest float.
        steps.add(mc -> {
            view(mc, -3, 3.2, -0.6, 180, 50);
            onServer(player -> {
                ServerLevel level = player.level();
                Item[] items = {Items.OAK_PLANKS, Items.COBBLESTONE, Items.IRON_INGOT, Items.GOLD_INGOT};
                for (int i = 0; i < items.length; i++) FACTS.put("lava-id-" + i, spawn(level, items[i], 1, -3.6 + (i % 2) * 1.2, 0.9, -4.6 + (i / 2) * 1.2, 0, 0, 0).getId());
            });
            return 80;
        });
        steps.add(mc -> {
            onServer(player -> {
                for (int i = 0; i < 4; i++) {
                    Entity entity = player.level().getEntity((Integer) FACTS.get("lava-id-" + i));
                    FACTS.put("lava-" + i, entity instanceof ItemEntity item ? new double[]{0, item.getY() - base.getY(), 0, item.isAlive() ? 1 : 0} : new double[]{0, 0, 0, 0});
                }
            });
            pendingFrame = "lava";
            return 4;
        });
        steps.add(mc -> {
            check(fact("lava-0")[3] == 0, "oak planks burn up in lava");
            String[] names = {"", "cobblestone", "an iron ingot", "a gold ingot"};
            for (int i = 1; i < 4; i++) check(fact("lava-" + i)[3] == 1 && fact("lava-" + i)[1] > 0.3, names[i] + " survives the lava, floating (y " + fmt(fact("lava-" + i)[1]) + ")");
            module().setEnabled(false);
            onServer(player -> FACTS.put("lava-off-id", spawn(player.level(), Items.COBBLESTONE, 1, -3, 0.9, -4, 0, 0, 0).getId()));
            return 80;
        });
        steps.add(mc -> {
            onServer(player -> FACTS.put("lava-off-alive", player.level().getEntity((Integer) FACTS.get("lava-off-id")) instanceof ItemEntity item && item.isAlive()));
            return 4;
        });
        steps.add(mc -> {
            check(Boolean.FALSE.equals(FACTS.get("lava-off-alive")), "control, module off: cobblestone burns in lava as vanilla");
            module().setEnabled(true);
            return 1;
        });
        // Cactus spares items; with the module off it destroys them.
        steps.add(mc -> {
            view(mc, 0.5, 3.6, 1.2, 0, 35);
            onServer(player -> FACTS.put("cactus-on-id", spawn(player.level(), Items.DIRT, 1, 0.5, 3.3, 4.5, 0, 0, 0).getId()));
            return 50;
        });
        steps.add(mc -> {
            onServer(player -> { if (player.level().getEntity((Integer) FACTS.get("cactus-on-id")) instanceof ItemEntity item) record(player.level(), "cactus-on", item); });
            pendingFrame = "cactus";
            return 4;
        });
        steps.add(mc -> {
            double[] at = fact("cactus-on");
            check(at[3] == 1 && at[1] > 2.5, "an item lying on a cactus survives (y " + fmt(at[1]) + ")");
            module().setEnabled(false);
            onServer(player -> {
                if (player.level().getEntity((Integer) FACTS.get("cactus-on-id")) instanceof ItemEntity item) item.discard();
                FACTS.put("cactus-off-id", spawn(player.level(), Items.DIRT, 1, 0.5, 3.3, 4.5, 0, 0, 0).getId());
            });
            return 50;
        });
        steps.add(mc -> {
            onServer(player -> FACTS.put("cactus-off-alive", player.level().getEntity((Integer) FACTS.get("cactus-off-id")) instanceof ItemEntity item && item.isAlive()));
            return 4;
        });
        steps.add(mc -> {
            check(Boolean.FALSE.equals(FACTS.get("cactus-off-alive")), "control, module off: the cactus destroys the item as vanilla");
            module().setEnabled(true);
            return 1;
        });
        // Igniting: a burning stick on planks lights them; burning cobblestone cannot burn at all.
        steps.add(mc -> {
            view(mc, -2.5, 2.5, 4, 90, 40);
            onServer(player -> {
                ItemEntity stick = spawn(player.level(), Items.STICK, 1, -4.5, 1.05, 5.5, 0, 0, 0);
                stick.igniteForSeconds(4);
                ItemEntity stone = spawn(player.level(), Items.COBBLESTONE, 1, -4.5, 1.05, 3.5, 0, 0, 0);
                stone.igniteForSeconds(4);
                FACTS.put("stone-burning", stone.isOnFire());
            });
            return 30;
        });
        steps.add(mc -> {
            onServer(player -> {
                FACTS.put("fire-stick", player.level().getBlockState(base.offset(-5, 1, 5)).getBlock() instanceof BaseFireBlock);
                FACTS.put("fire-stone", player.level().getBlockState(base.offset(-5, 1, 3)).getBlock() instanceof BaseFireBlock);
            });
            pendingFrame = "ignite";
            return 4;
        });
        steps.add(mc -> {
            check(Boolean.TRUE.equals(FACTS.get("fire-stick")), "a burning stick lying on oak planks sets them alight");
            check(Boolean.FALSE.equals(FACTS.get("stone-burning")) && Boolean.FALSE.equals(FACTS.get("fire-stone")), "cobblestone cannot be set on fire, so it lights nothing");
            onServer(player -> { // put the fire out before it spreads
                for (int x = -6; x <= -4; x++)
                    for (int z = 2; z <= 6; z++)
                        for (int y = 0; y <= 2; y++)
                            if (player.level().getBlockState(base.offset(x, y, z)).getBlock() instanceof BaseFireBlock) set(player.level(), x, y, z, Blocks.AIR.defaultBlockState());
                clearItems(player.level());
            });
            return 5;
        });
        // Right-click pickup: no auto pickup for the host; the use key on the item picks it up. Off: vanilla auto pickup.
        steps.add(mc -> {
            module().pickup.set(true);
            view(mc, -5.5, 1, 0.5, -90, 0);
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            onServer(player -> {
                ItemEntity emerald = new ItemEntity(player.level(), player.getX() + 1.2, player.getY() + 0.2, player.getZ(), new ItemStack(Items.EMERALD), 0, 0, 0);
                player.level().addFreshEntity(emerald);
                SPAWNED.add(emerald.getId());
                FACTS.put("emerald", emerald.getId());
            });
            return 30;
        });
        steps.add(mc -> {
            onServer(player -> FACTS.put("emerald-waiting", player.level().getEntity((Integer) FACTS.get("emerald")) instanceof ItemEntity item && item.isAlive()
                && player.getInventory().countItem(Items.EMERALD) == 0));
            var item = mc.level.getEntity((Integer) FACTS.get("emerald"));
            if (item != null) { // look at it
                var eye = mc.player.getEyePosition();
                double dx = item.getX() - eye.x, dy = item.getY() + 0.1 - eye.y, dz = item.getZ() - eye.z;
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.setXRot(pitch); mc.player.xRotO = pitch;
            }
            return 3;
        });
        steps.add(mc -> {
            check(Boolean.TRUE.equals(FACTS.get("emerald-waiting")), "right-click pickup on: an emerald at the player's feet is not picked up by walking into it");
            mc.options.keyUse.setDown(true);
            return 2;
        });
        steps.add(mc -> { mc.options.keyUse.setDown(false); return 10; });
        steps.add(mc -> {
            onServer(player -> FACTS.put("emerald-picked", !(player.level().getEntity((Integer) FACTS.get("emerald")) instanceof ItemEntity item && item.isAlive())
                && player.getInventory().countItem(Items.EMERALD) == 1));
            return 4;
        });
        steps.add(mc -> {
            check(Boolean.TRUE.equals(FACTS.get("emerald-picked")), "the use key on the emerald picks it up into the inventory");
            module().pickup.set(false);
            mc.player.setXRot(0); mc.player.xRotO = 0; mc.player.setYRot(-90); mc.player.yRotO = -90;
            onServer(player -> {
                ItemEntity gold = new ItemEntity(player.level(), player.getX() + 0.3, player.getY() + 0.2, player.getZ(), new ItemStack(Items.GOLD_INGOT), 0, 0, 0);
                player.level().addFreshEntity(gold);
                SPAWNED.add(gold.getId());
                FACTS.put("gold", gold.getId());
            });
            return 30;
        });
        steps.add(mc -> {
            onServer(player -> FACTS.put("gold-picked", player.getInventory().countItem(Items.GOLD_INGOT) == 1));
            return 4;
        });
        steps.add(mc -> {
            check(Boolean.TRUE.equals(FACTS.get("gold-picked")), "right-click pickup off: walking into a gold ingot picks it up as vanilla");
            onServer(player -> { // cobblestone to throw
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
            });
            mc.player.getInventory().setSelectedSlot(0);
            return 10;
        });
        // Charged throw: a tap throws as vanilla; holding the drop key throws further.
        steps.add(mc -> { mc.options.keyDrop.setDown(true); return 1; });
        steps.add(mc -> { mc.options.keyDrop.setDown(false); return 50; });
        steps.add(mc -> { thrown("throw-tap"); return 4; });
        steps.add(mc -> { mc.options.keyDrop.setDown(true); sample = 0; return 0; });
        steps.add(mc -> {
            if (++sample == 16) pendingFrame = "throw-charging";
            if (sample < 26) return -1;
            mc.options.keyDrop.setDown(false);
            return 5;
        });
        steps.add(mc -> { frozen(true); return 4; });
        steps.add(mc -> { pendingFrame = "throw-midair"; return 2; });
        steps.add(mc -> { frozen(false); return 60; });
        steps.add(mc -> { thrown("throw-charged"); return 4; });
        steps.add(mc -> {
            double tap = fact("throw-tap")[0], charged = fact("throw-charged")[0];
            check(tap > 0.5 && charged > tap * 1.5, "a charged throw lands further than a tap (" + fmt(charged) + " vs " + fmt(tap) + " blocks)");
            return 1;
        });
        // The module's settings page.
        steps.add(mc -> {
            var menu = new com.thelads.core.v26_2.gui.LadsSettingsScreen26(null);
            mc.gui.setScreen(menu);
            menu.openModule(ItemPhysicsModule.NAME);
            return 20;
        });
        steps.add(mc -> { pendingFrame = "settings"; return 2; });
        steps.add(mc -> { mc.gui.setScreen(null); return 5; });
        // Despawn time: 1 minute and 10 minutes. The game is frozen and only these two items are ticked, an exact number of times
        // (no server sprint: the world's clock stays where it was).
        steps.add(mc -> {
            frozen(true);
            module().despawn.setValue(1);
            onServer(player -> FACTS.put("despawn-1", spawn(player.level(), Items.COAL, 1, 8.5, 1.1, 0.5, 0, 0, 0).getId()));
            return 2;
        });
        steps.add(mc -> {
            module().despawn.setValue(10);
            onServer(player -> FACTS.put("despawn-10", spawn(player.level(), Items.LAPIS_LAZULI, 1, 9.5, 1.1, 0.5, 0, 0, 0).getId()));
            return 2;
        });
        steps.add(mc -> { aged("after-1190", 1190); return 4; });
        steps.add(mc -> {
            check(Boolean.TRUE.equals(FACTS.get("after-1190-1")), "1-minute despawn: the item is still there after 1190 ticks");
            aged("after-1210", 20);
            return 4;
        });
        steps.add(mc -> {
            check(Boolean.FALSE.equals(FACTS.get("after-1210-1")), "1-minute despawn: gone after 1210 ticks");
            check(Boolean.TRUE.equals(FACTS.get("after-1210-10")), "10-minute despawn: still there");
            aged("after-6110", 4900);
            return 4;
        });
        steps.add(mc -> {
            check(Boolean.TRUE.equals(FACTS.get("after-6110-10")), "10-minute despawn: still there after 6110 ticks, past vanilla's 6000");
            frozen(false);
            return 1;
        });
        steps.add(mc -> { // everything back
            mc.options.keyDrop.setDown(false);
            mc.options.keyUse.setDown(false);
            hud(mc, hideGuiBefore);
            if (cloudsBefore != null) mc.options.cloudStatus().set(cloudsBefore);
            if (mc.gui.screen() != null) mc.gui.setScreen(null);
            ItemPhysicsModule module = module();
            OPTIONS.forEach(Option::load);
            module.setEnabled(enabledBefore);
            module.setLastModified(modifiedBefore);
            mc.player.getAbilities().flying = flyingBefore; // as found: the QA player may hover where the world probes expect it
            mc.player.onUpdateAbilities();
            mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            if (posBefore != null) {
                double[] at = posBefore;
                GameType mode = modeBefore;
                onServer(player -> {
                    player.level().getServer().tickRateManager().setFrozen(false);
                    clearItems(player.level());
                    if (base != null) for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(base).inflate(24))) item.discard();
                    List<Map.Entry<BlockPos, BlockState>> found = new ArrayList<>(ARENA.entrySet());
                    java.util.Collections.reverse(found); // cactus and pools before the floor under them: nothing drops or flows
                    for (var entry : found) player.level().setBlockAndUpdate(entry.getKey(), entry.getValue());
                    ARENA.clear();
                    player.getInventory().clearContent();
                    for (int i = 0; i < INVENTORY.size(); i++) player.getInventory().setItem(i, INVENTORY.get(i));
                    player.setGameMode(mode);
                    player.teleportTo(at[0], at[1], at[2]);
                });
                mc.player.setYRot(yawBefore);
                mc.player.setXRot(pitchBefore);
            }
            if (cameraBefore != null) mc.options.setCameraType(cameraBefore);
            return 10;
        });
        return steps;
    }

    /** Server thread: the two despawn items live this many more ticks of their own; then whether each is still there. */
    private static void aged(String name, int ticks) {
        onServer(player -> {
            Entity one = player.level().getEntity((Integer) FACTS.get("despawn-1")), ten = player.level().getEntity((Integer) FACTS.get("despawn-10"));
            for (int tick = 0; tick < ticks; tick++)
                for (Entity item : new Entity[]{one, ten}) if (item != null && item.isAlive()) item.tick();
            FACTS.put(name + "-1", one != null && one.isAlive());
            FACTS.put(name + "-10", ten != null && ten.isAlive());
        });
    }

    /** Server thread: how far east the newest thrown cobblestone landed from the thrower (x), then it goes. */
    private static void thrown(String name) {
        onServer(player -> {
            ItemEntity newest = null;
            for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(24)))
                if (item.getItem().is(Items.COBBLESTONE) && (newest == null || item.getId() > newest.getId())) newest = item;
            FACTS.put(name, newest == null ? new double[]{0, 0, 0, 0} : new double[]{newest.getX() - player.getX(), newest.getY() - player.getY(), 0, 1});
            LOGGER.info("Lads item physics {}: landed {} blocks east, on the ground {}", name, newest == null ? "none" : fmt(newest.getX() - player.getX()),
                newest != null && newest.onGround());
            if (newest != null) newest.discard();
        });
    }

    private static boolean room(ServerLevel level, BlockPos at) {
        for (int x = -6; x <= 16; x++)
            for (int z = -6; z <= 6; z++)
                for (int y = -2; y <= 3; y++)
                    if (!level.getBlockState(at.offset(x, y, z)).isAir()) return false;
        return true;
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        BlockPos at = base.offset(x, y, z);
        ARENA.putIfAbsent(at, level.getBlockState(at));
        level.setBlockAndUpdate(at, state);
    }

    private static String fmt(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
}
