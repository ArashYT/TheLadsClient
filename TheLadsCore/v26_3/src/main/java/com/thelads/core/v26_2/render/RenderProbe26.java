package com.thelads.core.v26_2.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.HudModule;
import com.thelads.core.perf.BuiltInRender;
import com.thelads.core.perf.RenderQa;
import com.thelads.core.perf.RenderQaPack;
import com.thelads.core.v26_2.feature.NativeWorldVerification;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (auto-world, ".lads-qa-capture-render185" from the harness's LADS_VERIFY_CAPTURE_RENDER185): the 1.8.5 built-in render
 * remakes off and on in a scene built in the sky above the QA player (item frames, dropped items, armed mobs, a hotbar and inventory
 * of items, a wall of animated blocks). Per scene: frame CPU time (extract + render) in alternating off/on windows; frames off, on and
 * off again of the frozen scene (world frozen, animations held, Lads HUD modules and chat hidden) compared pixel for pixel; the block
 * atlas after Kerria's copies and after the game's own draws of the same frames, compared texel for texel on every mip level, before
 * and after F3+T and with mipmaps off. Results in lads-qa/render185/results.json, frames in screenshots/render185-*.png. Everything
 * is put back.
 */
public final class RenderProbe26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final int MEASURE_TICKS = 60, SETTLE_TICKS = 10, ROUNDS = 3, MAGENTA = 0xFFFF00FF;
    private interface Step { int run(Minecraft mc) throws Exception; }

    /** Read by TextureAtlasAnimationMixin: atlas animations held still. Read by GameRendererProbeMixin: GUI item atlas to redraw. */
    public static boolean animationsHeld, invalidateGui;

    private static final List<Step> STEPS = new ArrayList<>();
    private static final Map<BlockPos, BlockState> CHANGED = new LinkedHashMap<>();
    private static final List<Integer> SPAWNED = new CopyOnWriteArrayList<>();
    private static final List<NativeImage> POISON = new ArrayList<>();
    private static final Map<String, int[]> SHOTS = new LinkedHashMap<>();
    private static final List<Module> HIDDEN = new ArrayList<>();
    private static int step = -1, wait, shotsInFlight;
    private static Path game, out;
    private static volatile BlockPos base;
    private static String pendingShot;
    private static double[] posBefore;
    private static float yawBefore, pitchBefore;
    private static GameType modeBefore;
    private static CameraType cameraBefore;
    private static CloudStatus cloudsBefore;
    private static ParticleStatus particlesBefore;
    private static boolean hudHiddenBefore, flyingBefore;
    private static int mipmapsBefore;
    private static final List<ItemStack> INVENTORY = new ArrayList<>();
    private static CompletableFuture<Void> reload;

    private RenderProbe26() {}

    /** END_CLIENT_TICK, registered by Render26 in QA auto-world runs only. */
    public static void tick(Minecraft mc) {
        if (step >= 0 && step < STEPS.size()) { run(mc); return; }
        if (step >= 0 || !NativeWorldVerification.worldReady()) return;
        game = mc.gameDirectory.toPath();
        Path request = game.resolve(".lads-qa-capture-render185");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            Files.delete(request);
            out = game.resolve("lads-qa").resolve("render185");
            Files.createDirectories(out);
        } catch (Exception failure) { LOGGER.error("Lads render 1.8.5 capture FAILED: request", failure); return; }
        RenderQa.reset();
        STEPS.clear();
        STEPS.addAll(steps());
        LOGGER.info("Lads render 1.8.5 capture BEGIN: {} steps; Flerovium {}, Kerria {}", STEPS.size(),
            BuiltInRender.items ? "on" : "off", BuiltInRender.animations ? "on" : "off");
        step = 0;
    }

    private static void run(Minecraft mc) {
        if (pendingShot != null || shotsInFlight > 0) return;
        if (wait > 0) { wait--; return; }
        try {
            int next = STEPS.get(step).run(mc);
            if (next < 0) return; // waits for something; runs again next tick
            wait = next;
            if (++step == STEPS.size()) RenderQa.finish(out);
        } catch (Throwable failure) {
            LOGGER.error("Lads render 1.8.5 capture FAILED: step {}", step, failure);
            RenderQa.failures++;
            RenderQa.stop();
            step = STEPS.size() - 1; // the last step puts everything back
        }
    }

    // ---- frames (GameRendererProbeMixin) ----

    public static void phaseStart() { RenderQa.phaseStart(); }

    public static void phaseEnd(boolean render, RenderTarget target) {
        RenderQa.phaseEnd(render);
        if (!render || pendingShot == null || target == null) return;
        String name = pendingShot;
        pendingShot = null;
        shotsInFlight++;
        try {
            Path file = game.resolve("screenshots").resolve("render185-" + name + ".png");
            Files.createDirectories(file.getParent());
            net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
                try {
                    image.writeToFile(file);
                    SHOTS.put(name, image.getPixels());
                    LOGGER.info("Lads render 1.8.5 frame {}", file);
                } catch (Exception failure) { RenderQa.failures++; LOGGER.error("Lads render 1.8.5 capture FAILED: frame {}", name, failure); }
                finally { image.close(); shotsInFlight--; }
            });
        } catch (Exception failure) { shotsInFlight--; RenderQa.failures++; LOGGER.error("Lads render 1.8.5 capture FAILED: frame {}", name, failure); }
    }

    // ---- block atlas parity (Kerria) ----

    private static TextureAtlas blocks(Minecraft mc) { return (TextureAtlas) mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS); }

    /** Every animated sprite rectangle on every mip level painted magenta, so a redraw that misses one shows. */
    private static void poison(TextureAtlas atlas) {
        AtlasProbeLink26 link = (AtlasProbeLink26) atlas;
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        for (TextureAtlasSprite sprite : link.lads$animatedSprites())
            for (int level = 0; level <= link.lads$maxMipLevel(); level++) {
                int w = sprite.contents().width() >> level, h = sprite.contents().height() >> level;
                if (w <= 0 || h <= 0) continue;
                NativeImage image = new NativeImage(w, h, false);
                image.fillRect(0, 0, w, h, MAGENTA);
                encoder.writeToTexture(atlas.getTexture(), image, level, 0, sprite.getX() >> level, sprite.getY() >> level);
                POISON.add(image);
            }
    }

    /** Poison, redraw every animation with Kerria's copies on or off, dump all mip levels to lads-qa/render185/atlas-{tag}. */
    private static void redrawAndDump(TextureAtlas atlas, String tag, boolean copies) throws Exception {
        Path dir = out.resolve("atlas-" + tag);
        Files.createDirectories(dir);
        try (var files = Files.list(dir)) { for (Path f : files.toList()) Files.delete(f); }
        poison(atlas);
        boolean before = BuiltInRender.animations;
        BuiltInRender.animations = copies;
        boolean visibleOnly = sodiumVisibleOnly(false);
        try { ((AtlasProbeLink26) atlas).lads$redrawAll(); }
        finally { BuiltInRender.animations = before; sodiumVisibleOnly(visibleOnly); }
        TextureUtil.writeAsPNG(dir, "blocks", atlas.getTexture(), ((AtlasProbeLink26) atlas).lads$maxMipLevel(), argb -> argb);
    }

    /** -1 until both dumps are written (26.3 reads textures back asynchronously); then compares them texel for texel. */
    private static int compareAtlas(String a, String b, int maxMip) throws Exception {
        for (int level = 0; level <= maxMip; level++)
            for (String tag : new String[]{a, b})
                if (!Files.isRegularFile(out.resolve("atlas-" + tag).resolve("blocks_" + level + ".png"))) return -1;
        Thread.sleep(300); // the last file may still be being written
        int[][] first = new int[maxMip + 1][], second = new int[maxMip + 1][];
        for (int level = 0; level <= maxMip; level++) {
            try (NativeImage x = NativeImage.read(Files.newInputStream(out.resolve("atlas-" + a).resolve("blocks_" + level + ".png")));
                 NativeImage y = NativeImage.read(Files.newInputStream(out.resolve("atlas-" + b).resolve("blocks_" + level + ".png")))) {
                first[level] = x.getPixels();
                second[level] = y.getPixels();
            }
        }
        for (NativeImage image : POISON) image.close();
        POISON.clear();
        RenderQa.compareAtlas(a + " vs " + b + ": Kerria's texture copies vs the game's draws", first, second, MAGENTA);
        return 0;
    }

    /** Sodium's "animate only visible textures": off while every animation is redrawn; returns the old value. */
    private static boolean sodiumVisibleOnly(boolean value) {
        try {
            Object options = Class.forName("net.caffeinemc.mods.sodium.client.SodiumClientMod").getMethod("options").invoke(null);
            Object performance = options.getClass().getField("performance").get(options);
            var field = performance.getClass().getField("animateOnlyVisibleTextures");
            boolean old = field.getBoolean(performance);
            field.setBoolean(performance, value);
            return old;
        } catch (Throwable absent) { return false; }
    }

    private static void atlasParity(List<Step> steps, String tag) {
        steps.add(mc -> { animationsHeld = true; return 3; });
        steps.add(mc -> {
            TextureAtlas atlas = blocks(mc);
            redrawAndDump(atlas, tag + "-copies", true);
            redrawAndDump(atlas, tag + "-game", false);
            RenderQa.RESULTS.put(tag + "AnimatedSprites", ((AtlasProbeLink26) atlas).lads$animatedSprites().size());
            return 2;
        });
        steps.add(mc -> compareAtlas(tag + "-copies", tag + "-game", ((AtlasProbeLink26) blocks(mc)).lads$maxMipLevel()));
        steps.add(mc -> { animationsHeld = false; return 0; });
    }

    // ---- scene ----

    private static void onServer(Consumer<ServerPlayer> task) {
        Minecraft mc = Minecraft.getInstance();
        var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)));
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        CHANGED.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static <T extends Entity> void add(ServerLevel level, T entity) {
        level.addFreshEntity(entity);
        SPAWNED.add(entity.getId());
    }

    private static final Item[] ITEMS = {Items.DIAMOND_SWORD, Items.IRON_PICKAXE, Items.APPLE, Items.BREAD, Items.GOLDEN_APPLE, Items.DIAMOND,
        Items.EMERALD, Items.STICK, Items.BOW, Items.FISHING_ROD, Items.ARROW, Items.REDSTONE, Items.COOKED_BEEF, Items.FEATHER, Items.BONE,
        Items.ENDER_PEARL, Items.BLAZE_ROD, Items.GOLD_INGOT, Items.IRON_INGOT, Items.COAL, Items.TORCH, Items.STONE, Items.GRASS_BLOCK,
        Items.OAK_LOG, Items.GLASS, Items.OAK_LEAVES, Items.CRAFTING_TABLE, Items.FURNACE, Items.TNT, Items.BOOKSHELF, Items.IRON_CHESTPLATE,
        Items.GOLDEN_HELMET, Items.SHEARS, Items.FLINT_AND_STEEL, Items.WHEAT, Items.CARROT, Items.POTATO, Items.MELON_SLICE, Items.SUGAR_CANE,
        Items.PAPER, Items.BOOK, Items.SLIME_BALL, Items.GUNPOWDER, Items.STRING, Items.LEATHER, Items.GLASS_PANE, Items.COBWEB,
        Items.CACTUS, Items.PUMPKIN, Items.REDSTONE_TORCH, Items.LADDER, Items.OAK_DOOR, Items.IRON_BARS};

    private static ItemStack item(int i, boolean glint) {
        ItemStack stack = new ItemStack(ITEMS[Math.floorMod(i, ITEMS.length)]);
        if (glint) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    /** Server thread: the scene around base (the player stands at base looking south, +z); same layout as 1.20.1's RenderProbe1201. */
    private static void build(ServerLevel level) {
        BlockPos b = base;
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int x = -24; x <= 76; x++) for (int z = -40; z <= 20; z++) set(level, b.offset(x, -1, z), stone);
        for (int x = -10; x <= 10; x++) for (int y = 0; y <= 7; y++) set(level, b.offset(x, y, 16), stone);
        int n = 0;
        for (int x = -9; x <= 9; x++) for (int y = 1; y <= 6; y++) {
            ItemFrame frame = new ItemFrame(level, b.offset(x, y, 15), Direction.NORTH);
            frame.setItem(item(n++, false), false);
            add(level, frame);
        }
        for (int x = -8; x <= 8; x++) for (int z = 3; z <= 11; z += 2) for (int k = 0; k < 2; k++) {
            ItemEntity entity = new ItemEntity(level, b.getX() + x + 0.5 + k * 0.3, b.getY(), b.getZ() + z + 0.5, item(n++, false), 0, 0, 0);
            entity.setNeverPickUp();
            entity.setUnlimitedLifetime();
            entity.setNoGravity(true);
            add(level, entity);
        }
        for (int i = 0; i < 12; i++) {
            EntityType<? extends Mob> type = i % 2 == 0 ? net.minecraft.world.entity.EntityTypes.ZOMBIE : net.minecraft.world.entity.EntityTypes.SKELETON;
            Mob mob = type.create(level, EntitySpawnReason.COMMAND);
            if (mob == null) continue;
            mob.snapTo(b.getX() + (i < 6 ? -11.5 : 11.5), b.getY(), b.getZ() + 4.5 + (i % 6) * 1.8, i < 6 ? -90 : 90, 0);
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.setItemSlot(EquipmentSlot.MAINHAND, item(i, false));
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(i % 3 == 0 ? Items.IRON_HELMET : Items.GOLDEN_HELMET));
            mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            add(level, mob);
        }
        BlockPos a = b.offset(60, 0, 8);
        for (int x = -7; x <= 7; x++) for (int y = 0; y <= 5; y++) set(level, a.offset(x, y, 1), stone);
        BlockState[] row = {Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(), Blocks.PRISMARINE.defaultBlockState(),
            Blocks.COMMAND_BLOCK.defaultBlockState(), Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState(), Blocks.REPEATING_COMMAND_BLOCK.defaultBlockState(),
            Blocks.WARPED_STEM.defaultBlockState(), Blocks.CRIMSON_STEM.defaultBlockState(), Blocks.SCULK.defaultBlockState(),
            Blocks.STONECUTTER.defaultBlockState(), Blocks.CAMPFIRE.defaultBlockState(), Blocks.SOUL_CAMPFIRE.defaultBlockState(),
            Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT, true), Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.LIT, true),
            Blocks.SEA_LANTERN.defaultBlockState()};
        for (int i = 0; i < row.length; i++) set(level, a.offset(i - 7, 2, 0), row[i]);
        for (int x = -7; x <= 7; x++) set(level, a.offset(x, 3, 0), Blocks.NETHER_PORTAL.defaultBlockState());
        for (int x = -7; x <= 7; x++) {
            set(level, a.offset(x, 0, -1), stone);
            set(level, a.offset(x, 0, 0), x < 0 ? Blocks.WATER.defaultBlockState() : Blocks.LAVA.defaultBlockState());
        }
        for (int x = -7; x <= 7; x += 2) {
            set(level, a.offset(x, 1, -1), x < 0 ? Blocks.NETHERRACK.defaultBlockState() : Blocks.SOUL_SAND.defaultBlockState());
            set(level, a.offset(x, 2, -1), x < 0 ? Blocks.FIRE.defaultBlockState() : Blocks.SOUL_FIRE.defaultBlockState());
        }
    }

    private static void look(Minecraft mc, double dx, double dz, float yaw, float pitch) {
        double x = base.getX() + 0.5 + dx, y = base.getY(), z = base.getZ() + 0.5 + dz;
        onServer(player -> player.teleportTo(x, y, z));
        mc.player.setYRot(yaw); mc.player.yRotO = yaw;
        mc.player.setXRot(pitch); mc.player.xRotO = pitch;
    }

    private static void frozen(boolean frozen) {
        onServer(player -> player.level().getServer().tickRateManager().setFrozen(frozen));
    }

    private static void hud(Minecraft mc, boolean hidden) { if (mc.gui.hud.isHidden() != hidden) mc.gui.hud.toggle(); }

    private static void fillInventory(boolean glint) {
        onServer(player -> {
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, item(i * 7 + 3, glint));
            player.getInventory().setSelectedSlot(4);
        });
    }

    /** Lads HUD modules (FPS, clock, memory...) change by themselves between frames: off for the still frames. */
    private static void hudModules(boolean hide) {
        if (hide) {
            HIDDEN.clear();
            for (Module module : ModuleManager.getInstance().getModules())
                if (module instanceof HudModule && module.isEnabled()) { HIDDEN.add(module); module.setEnabled(false); }
        } else {
            for (Module module : HIDDEN) module.setEnabled(true);
            HIDDEN.clear();
        }
    }

    private static final RenderQaPack.Sprite[] HD = {
        new RenderQaPack.Sprite("block/water_still", 32, 2, false), new RenderQaPack.Sprite("block/lava_still", 20, 2, false),
        new RenderQaPack.Sprite("block/sea_lantern", 5, 5, false), new RenderQaPack.Sprite("block/nether_portal", 32, 1, false),
        new RenderQaPack.Sprite("block/fire_0", 32, 1, false), new RenderQaPack.Sprite("block/magma", 3, 8, true),
        new RenderQaPack.Sprite("block/prismarine", 4, 20, true)};

    /** Adds or removes the generated HD pack from the selected resource packs (the reload is the caller's). */
    private static void pack(Minecraft mc, boolean on) {
        var repository = mc.getResourcePackRepository();
        repository.reload();
        List<String> ids = new ArrayList<>(repository.getSelectedIds());
        ids.remove("file/" + RenderQaPack.NAME);
        if (on) ids.add("file/" + RenderQaPack.NAME);
        repository.setSelected(ids);
    }

    private static void measure(List<Step> steps, String scene) {
        for (int round = 0; round < ROUNDS; round++) for (boolean on : new boolean[]{false, true}) {
            String mode = on ? "on" : "off";
            steps.add(mc -> { BuiltInRender.items = on; BuiltInRender.animations = on; return SETTLE_TICKS; });
            steps.add(mc -> { RenderQa.startWindow(); return MEASURE_TICKS; });
            steps.add(mc -> { RenderQa.endWindow(scene, mode); return 0; });
        }
        steps.add(mc -> { BuiltInRender.items = true; BuiltInRender.animations = true; return 0; });
    }

    /** Off, on, off again of the frozen, still scene; the GUI item atlas is redrawn after each switch. */
    private static void shots(List<Step> steps, String scene, boolean lod) {
        steps.add(mc -> {
            animationsHeld = true;
            hudModules(true);
            mc.gui.hud.getChat().clearMessages(false);
            mc.particleEngine.clearParticles();
            BuiltInRender.items = false; BuiltInRender.animations = false; invalidateGui = true;
            return 6;
        });
        steps.add(mc -> { pendingShot = scene + "-off"; return 2; });
        steps.add(mc -> { BuiltInRender.items = true; BuiltInRender.animations = true; invalidateGui = true; return 6; });
        steps.add(mc -> { pendingShot = scene + "-on"; return 2; });
        steps.add(mc -> { BuiltInRender.items = false; BuiltInRender.animations = false; invalidateGui = true; return 6; });
        steps.add(mc -> { pendingShot = scene + "-off2"; return 2; });
        steps.add(mc -> {
            RenderQa.compareShots(scene, SHOTS.get(scene + "-off"), SHOTS.get(scene + "-on"), SHOTS.get(scene + "-off2"), lod);
            BuiltInRender.items = true; BuiltInRender.animations = true; invalidateGui = true;
            hudModules(false);
            animationsHeld = false;
            return 2;
        });
    }

    private static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(mc -> {
            posBefore = new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()};
            yawBefore = mc.player.getYRot(); pitchBefore = mc.player.getXRot();
            modeBefore = mc.gameMode.getPlayerMode();
            cameraBefore = mc.options.getCameraType();
            cloudsBefore = mc.options.cloudStatus().get();
            particlesBefore = mc.options.particles().get();
            mipmapsBefore = mc.options.mipmapLevels().get();
            hudHiddenBefore = mc.gui.hud.isHidden();
            flyingBefore = mc.player.getAbilities().flying;
            INVENTORY.clear();
            for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) INVENTORY.add(mc.player.getInventory().getItem(i).copy());
            base = BlockPos.containing(mc.player.getX(), 230, mc.player.getZ());
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.cloudStatus().set(CloudStatus.OFF);
            mc.options.particles().set(ParticleStatus.MINIMAL);
            hud(mc, false);
            RenderQa.RESULTS.put("version", "26.3");
            RenderQa.RESULTS.put("backend", RenderSystem.getBackendDescription());
            RenderQa.RESULTS.put("flerovium", BuiltInRender.standDownReason(BuiltInRender.FLEROVIUM) == null ? "on" : BuiltInRender.standDownReason(BuiltInRender.FLEROVIUM));
            RenderQa.RESULTS.put("kerria", BuiltInRender.standDownReason(BuiltInRender.KERRIA) == null ? "on" : BuiltInRender.standDownReason(BuiltInRender.KERRIA));
            onServer(player -> { player.setGameMode(GameType.CREATIVE); build((ServerLevel) player.level()); });
            return 10;
        });
        steps.add(mc -> { mc.player.getAbilities().flying = true; look(mc, 0, 0, 0, 12); fillInventory(true); return 80; });
        steps.add(mc -> { frozen(true); return 10; });
        // 1. Items in frames, on the floor, in mobs' hands, a glinting hotbar: costs; then the still scene with plain items
        measure(steps, "items");
        steps.add(mc -> { fillInventory(false); return 10; });
        shots(steps, "items", false);
        // 2. Far: the same scene from 45 blocks back, where LOD drops sub-pixel quads
        steps.add(mc -> { look(mc, 0, -30, 0, 4); fillInventory(true); return 40; });
        measure(steps, "far");
        steps.add(mc -> { fillInventory(false); return 10; });
        shots(steps, "far", true);
        // 3. The inventory screen full of glinting items (redrawn every frame), then of plain ones for the still frames
        steps.add(mc -> { look(mc, 0, 0, 0, 12); fillInventory(true); mc.gui.setScreen(new InventoryScreen(mc.player)); return 20; });
        measure(steps, "inventory");
        steps.add(mc -> { fillInventory(false); return 10; });
        shots(steps, "inventory", false);
        steps.add(mc -> { mc.gui.setScreen(null); return 5; });
        // 4. Animated blocks: atlas animation cost per tick and frame cost, animations running (world frozen)
        steps.add(mc -> { look(mc, 60, 0, 0, 10); return 60; });
        measure(steps, "animations");
        atlasParity(steps, "start");
        // 5. An HD pack of animated textures (256 px frames), where frame updates are heavy: costs and parity, then the pack is removed
        steps.add(mc -> {
            RenderQaPack.write(game.resolve("resourcepacks"), "\"min_format\":97,\"max_format\":97,\"pack_format\":97", 256, HD);
            pack(mc, true);
            reload = mc.reloadResourcePacks();
            return 0;
        });
        steps.add(mc -> reload.isDone() ? 60 : -1);
        steps.add(mc -> {
            RenderQa.check(mc.getResourcePackRepository().getSelectedIds().contains("file/" + RenderQaPack.NAME), "the HD animated pack is active");
            return 0;
        });
        measure(steps, "hd");
        atlasParity(steps, "hd");
        steps.add(mc -> { pack(mc, false); reload = mc.reloadResourcePacks(); return 0; });
        steps.add(mc -> reload.isDone() ? 20 : -1);
        // 5. F3+T: textures reloaded, frame textures rebuilt from the new sprites, copies still exact
        steps.add(mc -> { reload = mc.reloadResourcePacks(); return 0; });
        steps.add(mc -> reload.isDone() ? 40 : -1);
        steps.add(mc -> {
            RenderQa.check(!reload.isCompletedExceptionally(), "F3+T resource reload completed");
            RenderQa.startWindow();
            return 100;
        });
        steps.add(mc -> { RenderQa.endWindow("afterReload", "on"); return 0; });
        atlasParity(steps, "reload");
        // 6. Mipmaps off: the atlas is rebuilt with one level
        steps.add(mc -> { mc.options.mipmapLevels().set(0); mc.updateMaxMipLevel(0); reload = mc.reloadResourcePacks(); return 0; });
        steps.add(mc -> reload.isDone() ? 40 : -1);
        atlasParity(steps, "nomips");
        steps.add(mc -> { mc.options.mipmapLevels().set(mipmapsBefore); mc.updateMaxMipLevel(mipmapsBefore); reload = mc.reloadResourcePacks(); return 0; });
        steps.add(mc -> reload.isDone() ? 20 : -1);
        steps.add(mc -> {
            RenderQa.stop();
            animationsHeld = false;
            hudModules(false);
            BuiltInRender.items = BuiltInRender.enabled(BuiltInRender.FLEROVIUM);
            BuiltInRender.animations = BuiltInRender.enabled(BuiltInRender.KERRIA);
            if (mc.gui.screen() != null) mc.gui.setScreen(null);
            frozen(false);
            List<Integer> ids = List.copyOf(SPAWNED);
            Map<BlockPos, BlockState> blocks = new LinkedHashMap<>(CHANGED);
            onServer(player -> {
                ServerLevel level = (ServerLevel) player.level();
                for (int id : ids) { Entity e = level.getEntity(id); if (e != null) e.discard(); }
                blocks.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
                for (int i = 0; i < INVENTORY.size(); i++) player.getInventory().setItem(i, INVENTORY.get(i));
                if (modeBefore != null) player.setGameMode(modeBefore);
                if (posBefore != null) player.teleportTo(posBefore[0], posBefore[1], posBefore[2]);
            });
            SPAWNED.clear(); CHANGED.clear();
            mc.player.setYRot(yawBefore); mc.player.setXRot(pitchBefore);
            mc.player.getAbilities().flying = flyingBefore;
            if (cameraBefore != null) mc.options.setCameraType(cameraBefore);
            if (cloudsBefore != null) mc.options.cloudStatus().set(cloudsBefore);
            if (particlesBefore != null) mc.options.particles().set(particlesBefore);
            hud(mc, hudHiddenBefore);
            return 5;
        });
        return steps;
    }
}
