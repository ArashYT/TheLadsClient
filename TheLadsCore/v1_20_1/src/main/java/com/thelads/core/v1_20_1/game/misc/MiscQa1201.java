package com.thelads.core.v1_20_1.game.misc;

import com.thelads.core.client.DynamicLights;
import com.thelads.core.client.ItemPhysics;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.v1_20_1.game.Gameplay1201;
import com.thelads.core.v1_20_1.game.ModPresence1201;
import com.thelads.core.v1_20_1.game.misc.mixin.ContainerAccess1201;
import com.thelads.core.v1_20_1.game.misc.mixin.MouseAccess1201;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only, inert unless the verification harness drives this game (Gameplay1201.qaActive: the harness flags and a game folder under
 * artifacts/verification). The probe script of the harness has no step plug-in, so these steps arrive as chat commands:
 * {@code cmd ladsqa <verb> ...} is swallowed on the client (never sent) and answered with "Lads misc qa ..." log lines.
 * Verbs: items, lookat, drop, light, gaps, farbe, renderdistance, fov, mt (open, slots, cursor, press, drag, release, scroll, shift, clicks, close).
 */
final class MiscQa1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");

    private MiscQa1201() {}

    static void register() {
        if (!Gameplay1201.qaActive()) return;
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (!command.startsWith("ladsqa")) return true;
            try {
                run(Minecraft.getInstance(), command.substring(6).strip().split("\\s+"));
            } catch (Exception e) {
                LOGGER.error("Lads misc qa FAILED: {}: {}", command, e.toString(), e);
            }
            return false;
        });
        LOGGER.info("Lads misc qa: step commands armed (cmd ladsqa <verb>)");
    }

    private static void run(Minecraft mc, String[] s) {
        switch (s[0]) {
            case "items" -> items(mc);
            case "sitems" -> serverItems(mc);
            case "count" -> count(mc, s[1]);
            case "lookat" -> lookAtItem(mc);
            case "drop" -> drop(mc, s[1]);
            case "light" -> light(mc, Integer.parseInt(s[1]), Integer.parseInt(s[2]), Integer.parseInt(s[3]));
            case "gaps" -> gaps(mc, s.length > 1 ? s[1] : "minecraft:stick");
            case "farbe" -> farBlockEntities();
            case "renderdistance" -> { mc.options.renderDistance().set(Integer.parseInt(s[1])); LOGGER.info("Lads misc qa: render distance {}", s[1]); }
            case "fov" -> { mc.options.fov().set(Integer.parseInt(s[1])); LOGGER.info("Lads misc qa: fov {}", s[1]); }
            case "mt" -> mouseTweaks(mc, s);
            default -> throw new IllegalArgumentException("unknown ladsqa verb " + s[0]);
        }
    }

    // ---- Item Physics ----

    private static List<ItemEntity> nearbyItems(Minecraft mc) {
        List<ItemEntity> found = new ArrayList<>();
        for (var entity : mc.level.entitiesForRendering())
            if (entity instanceof ItemEntity item && item.distanceToSqr(mc.player) < 24 * 24) found.add(item);
        found.sort((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player)));
        return found;
    }

    private static void items(Minecraft mc) {
        LOGGER.info("Lads misc qa items: module renders={} rules={} drawn={} last=[{}] charge={}", NativeItemPhysics1201.renders(),
            NativeItemPhysics1201.rules(mc.level) != null, NativeItemPhysics1201.drawn, NativeItemPhysics1201.lastDrawn, NativeItemPhysics1201.chargeShown());
        for (ItemEntity item : nearbyItems(mc)) {
            ItemPhysics.Tumble tumble = ((ItemPhysics.Holder) item).lads$tumble();
            LOGGER.info(String.format(Locale.ROOT, "Lads misc qa item: %s x%d at %.2f,%.2f,%.2f onGround=%b speed=%.3f yaw=%.1f pitch=%.1f raise=%.3f age=%d",
                BuiltInRegistries.ITEM.getKey(item.getItem().getItem()), item.getItem().getCount(), item.getX(), item.getY(), item.getZ(),
                item.onGround(), item.getDeltaMovement().length(), tumble.yaw(1), tumble.pitch(1), tumble.raise(1), item.getAge()));
        }
    }

    /** The integrated server's own dropped items (their age is what the despawn rule changes), logged from the server thread. */
    private static void serverItems(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("no integrated server");
        server.execute(() -> {
            int n = 0;
            for (var level : server.getAllLevels())
                for (var entity : level.getAllEntities())
                    if (entity instanceof ItemEntity item) {
                        n++;
                        LOGGER.info(String.format(Locale.ROOT, "Lads misc qa server item: %s x%d at %.1f,%.1f,%.1f age=%d onFire=%b fireImmune=%b speed=%.3f",
                            BuiltInRegistries.ITEM.getKey(item.getItem().getItem()), item.getItem().getCount(), item.getX(), item.getY(), item.getZ(),
                            item.getAge(), item.isOnFire(), item.fireImmune(), item.getDeltaMovement().length()));
                    }
            LOGGER.info("Lads misc qa server items: {}", n);
        });
    }

    /** How many of an item the player carries (inventory and cursor). */
    private static void count(Minecraft mc, String id) {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        int total = 0;
        for (ItemStack stack : mc.player.getInventory().items) if (stack.getItem() == item) total += stack.getCount();
        LOGGER.info("Lads misc qa count: {} x{} in the inventory", id, total);
    }

    private static void lookAtItem(Minecraft mc) {
        List<ItemEntity> items = nearbyItems(mc);
        if (items.isEmpty()) throw new IllegalStateException("no dropped item near");
        Vec3 to = items.get(0).getBoundingBox().getCenter().subtract(mc.player.getEyePosition());
        mc.player.setYRot((float) (Math.toDegrees(Math.atan2(-to.x, to.z))));
        mc.player.setXRot((float) (-Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)))));
        LOGGER.info("Lads misc qa: looking at {} yaw={} pitch={}", items.get(0).getItem(), mc.player.getYRot(), mc.player.getXRot());
    }

    /** The drop key held or released as a player would; the throw itself is NativeItemPhysics1201's tick. */
    private static void drop(Minecraft mc, String mode) {
        switch (mode) {
            case "hold" -> mc.options.keyDrop.setDown(true);
            case "release" -> mc.options.keyDrop.setDown(false);
            case "state" -> { }
            default -> throw new IllegalArgumentException("drop hold|release|state");
        }
        LOGGER.info("Lads misc qa drop: key down={} charge bar={} (hud callbacks {}, bar drawn {})", mc.options.keyDrop.isDown(), NativeItemPhysics1201.chargeShown(),
            NativeItemPhysics1201.barCalls, NativeItemPhysics1201.barDrawn);
    }

    // ---- Dynamic Lights ----

    private static void light(Minecraft mc, int dx, int dy, int dz) {
        BlockPos at = mc.player.blockPosition().offset(dx, dy, dz);
        int packed = LevelRenderer.getLightColor(mc.level, at);
        boolean sodium = ModPresence1201.loaded("sodium"), embeddium = ModPresence1201.loaded("embeddium") || ModPresence1201.loaded("rubidium");
        LOGGER.info(String.format(Locale.ROOT,
            "Lads misc qa light at %d,%d,%d (player %+d,%+d,%+d): world block=%d sky=%d | dynamic=%d | vanilla getLightColor block=%d | module on=%b active=%b | sodium=%b embeddium=%b light-cache hook=%s | hits vanilla=%d entity=%d sodium=%d | ticks=%d rebuilds=%d | chunkCompiled=%b | dayTime=%d skyDarken=%.2f gamma=%.2f fullbright=%b",
            at.getX(), at.getY(), at.getZ(), dx, dy, dz, mc.level.getBrightness(LightLayer.BLOCK, at), mc.level.getBrightness(LightLayer.SKY, at),
            DynamicLights.WORLD.at(at.getX(), at.getY(), at.getZ()), LightTexture.block(packed),
            ModuleManager.getInstance().getModule("DynamicLights").isEnabled(), NativeDynamicLights1201.active(), sodium, embeddium, NativeDynamicLights1201.sodiumMixinState(),
            NativeDynamicLights1201.vanillaHits, NativeDynamicLights1201.entityHits, NativeDynamicLights1201.sodiumHits,
            NativeDynamicLights1201.ticks, NativeDynamicLights1201.rebuilds, mc.levelRenderer.isChunkCompiled(at), mc.level.getDayTime(),
            mc.level.getSkyDarken(1f), mc.options.gamma().get(), ModuleManager.getInstance().getModule("Fullbright").isEnabled()));
    }

    // ---- Item Model Gaps ----

    /** Baked side quads of a flat item: how many sit off the 1/16 grid (the overlap) against how many sit on it (front and back). */
    private static void gaps(Minecraft mc, String id) {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        BakedModel model = mc.getItemRenderer().getItemModelShaper().getItemModel(item);
        RandomSource random = RandomSource.create();
        int onGrid = 0, offGrid = 0;
        float worst = 0;
        for (int side = 0; side <= Direction.values().length; side++) {
            random.setSeed(42L);
            for (BakedQuad quad : model.getQuads(null, side == Direction.values().length ? null : Direction.values()[side], random)) {
                int[] data = quad.getVertices();
                boolean aligned = true;
                for (int vertex = 0; vertex + 2 < data.length; vertex += data.length / 4)
                    for (int axis = 0; axis < 3; axis++) {
                        float v = Float.intBitsToFloat(data[vertex + axis]) * 16, off = Math.abs(v - Math.round(v * 2) / 2f);
                        if (off > 1e-4f) { aligned = false; worst = Math.max(worst, off); }
                    }
                if (aligned) onGrid++; else offGrid++;
            }
        }
        LOGGER.info("Lads misc qa gaps: {} quads on the grid (faces), {} off it by up to {} model units (edge quads with the overlap)", onGrid, offGrid, worst);
    }

    // ---- Far Block Entities ----

    private static void farBlockEntities() {
        var module = ModuleManager.getInstance().getModule(FarBlockDistance1201.NAME);
        LOGGER.info("Lads misc qa farbe: enabled={} Distance={} last resolved view distance={}", module.isEnabled(), module.getOption("Distance").save(),
            FarBlockDistance1201.lastResolved);
    }

    // ---- Mouse Tweaks ----

    private static AbstractContainerScreen<?> container(Minecraft mc) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) throw new IllegalStateException("no container screen open: " + mc.screen);
        return screen;
    }

    private static double[] centre(AbstractContainerScreen<?> screen, int slotIndex) {
        Slot slot = screen.getMenu().slots.get(slotIndex);
        var access = (ContainerAccess1201) screen;
        return new double[] {access.lads$leftPos() + slot.x + 8, access.lads$topPos() + slot.y + 8};
    }

    private static void mouseTweaks(Minecraft mc, String[] s) {
        switch (s[1]) {
            case "status" -> LOGGER.info("Lads misc qa mt: mouse tweaks mod loaded={} module on={}", NativeMouseTweaks1201.externalMod(),
                ModuleManager.getInstance().getModule("MouseTweaks").isEnabled());
            case "open" -> {
                BlockPos pos = mc.player.blockPosition().offset(Integer.parseInt(s[2]), Integer.parseInt(s[3]), Integer.parseInt(s[4]));
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                LOGGER.info("Lads misc qa mt: right-clicked {}", pos);
            }
            case "close" -> mc.player.closeContainer();
            case "screen" -> LOGGER.info("Lads misc qa mt: screen {}", mc.screen == null ? "none" : mc.screen.getClass().getName());
            case "slots" -> {
                StringBuilder out = new StringBuilder();
                for (Slot slot : container(mc).getMenu().slots)
                    if (slot.hasItem()) out.append(slot.index).append('=').append(BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).getPath()).append('x').append(slot.getItem().getCount()).append(' ');
                LOGGER.info("Lads misc qa mt slots: {}| cursor {}", out, cursor(container(mc)));
            }
            case "cursor" -> LOGGER.info("Lads misc qa mt cursor: {}", cursor(container(mc)));
            case "shift" -> { NativeMouseTweaks1201.shiftOverride = s[2].equals("auto") ? null : s[2].equals("on"); LOGGER.info("Lads misc qa mt: shift {}", s[2]); }
            case "clicks" -> {
                synchronized (NativeMouseTweaks1201.CLICKS) {
                    LOGGER.info("Lads misc qa mt clicks ({}): {}", NativeMouseTweaks1201.CLICKS.size(), String.join(" ", NativeMouseTweaks1201.CLICKS));
                    NativeMouseTweaks1201.CLICKS.clear();
                }
            }
            case "press" -> { double[] c = centre(container(mc), Integer.parseInt(s[2])); container(mc).mouseClicked(c[0], c[1], Integer.parseInt(s[3])); }
            case "shiftpress" -> { // what shift held at the press does: a quick move of the first slot, then the tweak's drag
                AbstractContainerScreen<?> screen = container(mc);
                double[] c = centre(screen, Integer.parseInt(s[2]));
                NativeMouseTweaks1201.press(screen, c[0], c[1], 0);
                ((com.thelads.core.v1_20_1.game.misc.NativeMouseTweaks1201.Screen1201) screen).lads$click(screen.getMenu().slots.get(Integer.parseInt(s[2])), 0, ClickType.QUICK_MOVE);
            }
            case "drag" -> { // drag <button> <slot>...
                AbstractContainerScreen<?> screen = container(mc);
                for (int i = 3; i < s.length; i++) { double[] c = centre(screen, Integer.parseInt(s[i])); screen.mouseDragged(c[0], c[1], Integer.parseInt(s[2]), 0, 0); }
            }
            case "release" -> { double[] c = centre(container(mc), Integer.parseInt(s[2])); container(mc).mouseReleased(c[0], c[1], Integer.parseInt(s[3])); }
            case "scroll" -> { // scroll <slot> up|down, through the real MouseHandler.onScroll
                AbstractContainerScreen<?> screen = container(mc);
                double[] c = centre(screen, Integer.parseInt(s[2]));
                var window = mc.getWindow();
                var mouse = (MouseAccess1201) mc.mouseHandler;
                mouse.lads$xpos(c[0] * window.getScreenWidth() / window.getGuiScaledWidth());
                mouse.lads$ypos(c[1] * window.getScreenHeight() / window.getGuiScaledHeight());
                mouse.lads$onScroll(window.getWindow(), 0, s[3].equals("up") ? 1 : -1);
            }
            default -> throw new IllegalArgumentException("mt verb " + s[1]);
        }
    }

    private static String cursor(AbstractContainerScreen<?> screen) {
        ItemStack carried = screen.getMenu().getCarried();
        return carried.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(carried.getItem()).getPath() + "x" + carried.getCount();
    }
}
