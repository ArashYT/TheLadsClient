package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.ItemPhysics;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.ItemPhysicsModule;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/**
 * Item Physics on 26.x. The look is client-side and works on any server. The rules run only where this client owns the game: the
 * integrated server's levels (singleplayer, or a world opened to LAN) and, for the same physics on both ends, this client's own
 * levels while that server runs. A remote server's world exists here only as a ClientLevel with no integrated server, so every
 * rule is inert there, and there are no packets of our own.
 */
public final class NativeItemPhysics {
    static final int BURNS = 1, FLOATS = 2;
    private static final Map<net.minecraft.world.item.Item, Integer> KINDS = new ConcurrentHashMap<>();
    private static final ItemPhysics.Charge CHARGE = new ItemPhysics.Charge();
    /** A charged throw on its way to the integrated server: the thrower and the speed multiple. */
    private static volatile UUID thrower;
    private static volatile float power;
    /** Server thread: our right-click pickup is touching the item, so the no-auto-pickup rule lets it through. */
    private static boolean picking;

    private NativeItemPhysics() {}

    /** Render state fields: the item's yaw and tumble at extraction; a NaN tumble leaves the item to vanilla. */
    public interface Posed {
        float lads$yaw();
        float lads$pitch();
        float lads$raise();
        void lads$posed(float yaw, float pitch, float raise);
    }

    public static void initialize() {
        ModuleSupport.registerBuiltIn(ItemPhysicsModule.NAME);
        ClientTickEvents.START_CLIENT_TICK.register(NativeItemPhysics::throwTick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("theladscore", "item_physics_throw"), (graphics, delta) -> chargeBar(graphics));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            if (module() != null && module().isEnabled())
                LoggerFactory.getLogger("TheLadsCore").info("Lads Item Physics: {}", mc.hasSingleplayerServer()
                    ? "singleplayer rules on (this client's integrated server)" : "look only; the remote server keeps its own item rules");
        });
    }

    public static ItemPhysicsModule module() {
        return NativeQualityOfLife.module(ItemPhysicsModule.NAME) instanceof ItemPhysicsModule module ? module : null;
    }

    /** Dropped items lie flat and tumble (any server: it only changes how this client draws them). */
    public static boolean renders() {
        ItemPhysicsModule module = module();
        return module != null && module.isEnabled() && module.animation.get();
    }

    /** The singleplayer rules apply in this level: the module is on and the level belongs to this client's integrated server. */
    public static ItemPhysicsModule rules(Level level) {
        ItemPhysicsModule module = module();
        if (module == null || !module.isEnabled()) return null;
        boolean owned = level instanceof ServerLevel server ? !server.getServer().isDedicatedServer()
            : level.isClientSide() && Minecraft.getInstance().hasSingleplayerServer();
        return owned ? module : null;
    }

    public static boolean burns(ItemEntity entity) { return (kind(entity.level(), entity.getItem()) & BURNS) != 0; }

    public static boolean floats(ItemEntity entity) { return (kind(entity.level(), entity.getItem()) & FLOATS) != 0; }

    /** BURNS and FLOATS for this item: fuel, a flammable block, or one of ItemPhysics' named materials. */
    private static int kind(Level level, ItemStack stack) {
        return KINDS.computeIfAbsent(stack.getItem(), item -> {
            boolean fuel = stack.has(net.minecraft.core.component.DataComponents.COOKING_FUEL);
            boolean block = item instanceof BlockItem placed && placed.getBlock().defaultBlockState().ignitedByLava();
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            return (ItemPhysics.burns(id, fuel, block) ? BURNS : 0) | (ItemPhysics.floats(id, fuel, block) ? FLOATS : 0);
        });
    }

    // ---- look ----

    /** ItemEntityRenderer extraction: this frame's yaw and tumble, or NaN to leave the item to vanilla (and 1.7 Animations). */
    public static void extract(ItemEntity entity, ItemEntityRenderState state, float partial) {
        if (!renders() || state.item.isEmpty()) { ((Posed) state).lads$posed(0, Float.NaN, 0); return; }
        ItemPhysics.Tumble tumble = ((ItemPhysics.Holder) entity).lads$tumble();
        tumble.rest = state.item.getModelBoundingBox().getZsize() <= ItemPhysics.FLAT ? 180 : 90;
        ((Posed) state).lads$posed(tumble.yaw(partial), tumble.pitch(partial), tumble.raise(partial));
    }

    /** ItemEntityRenderer submit, at vanilla's draw: every copy of the stack placed by ItemPhysics; false lets vanilla draw. */
    public static boolean submit(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, int light) {
        Posed posed = (Posed) state;
        if (Float.isNaN(posed.lads$pitch())) return false;
        pose.popPose(); // back to the entity origin (submit's own push), dropping vanilla's bob and spin
        pose.pushPose();
        AABB box = state.item.getModelBoundingBox();
        var out = NativeOldAnimations.sink(pose);
        for (int copy = 0; copy < state.count; copy++) {
            pose.pushPose();
            ItemPhysics.place(out, posed.lads$yaw(), posed.lads$pitch(), posed.lads$raise(), (float) (box.minX + box.maxX) / 2, (float) (box.minY + box.maxY) / 2,
                (float) (box.minZ + box.maxZ) / 2, (float) box.getYsize() / 2, (float) box.getZsize() / 2, copy, state.count, state.seed);
            state.item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            pose.popPose();
        }
        return true;
    }

    /** ItemEntity client tick: the tumble follows how far the item moved, whether it landed and whether it floats. */
    public static void clientTick(ItemEntity entity) {
        if (!renders()) return;
        double dx = entity.getX() - entity.xo, dy = entity.getY() - entity.yo, dz = entity.getZ() - entity.zo;
        ((ItemPhysics.Holder) entity).lads$tumble().tick(Math.sqrt(dx * dx + dy * dy + dz * dz), entity.onGround(),
            Math.max(entity.getFluidHeight(FluidTags.WATER), entity.getFluidHeight(FluidTags.LAVA)));
    }

    // ---- singleplayer rules ----

    /** A burning flammable item lying on a flammable block sets it alight where fire may spread (the fire-spread game rule). */
    public static void ignite(ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % 10 != 0 || !entity.isOnFire() || !entity.onGround()) return;
        ItemPhysicsModule module = rules(level);
        if (module == null || !module.ignite.get() || !burns(entity)) return;
        BlockPos at = entity.blockPosition();
        if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).ignitedByLava() && level.canSpreadFireAround(at))
            level.setBlockAndUpdate(at, BaseFireBlock.getState(level, at));
    }

    /** Auto-pickup stays off for the host while right-click pickup is on (LAN guests cannot right-click, so they keep it). */
    public static boolean noAutoPickup(ItemEntity entity, Player player) {
        if (picking || !(entity.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer host)) return false;
        ItemPhysicsModule module = rules(level);
        return module != null && module.pickup.get() && level.getServer().isSingleplayerOwner(host.nameAndId());
    }

    /** Minecraft.startUseItem: right-click on a dropped item in reach picks it up; true when one was picked. */
    public static boolean pickUp(Minecraft mc) {
        ItemPhysicsModule module = module();
        var server = mc.getSingleplayerServer();
        Player player = mc.player;
        if (module == null || !module.isEnabled() || !module.pickup.get() || server == null || player == null || player.isSpectator()) return false;
        Vec3 eye = player.getEyePosition(), end = eye.add(player.getViewVector(1).scale(player.blockInteractionRange()));
        double nearest = mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS ? eye.distanceToSqr(mc.hitResult.getLocation()) : Double.MAX_VALUE;
        ItemEntity target = null;
        for (ItemEntity item : mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(eye, end).inflate(0.5), Entity::isAlive)) {
            var hit = item.getBoundingBox().inflate(0.15).clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < nearest) {
                nearest = eye.distanceToSqr(hit.get());
                target = item;
            }
        }
        if (target == null) return false;
        int id = target.getId();
        UUID uuid = player.getUUID();
        server.execute(() -> {
            ServerPlayer host = server.getPlayerList().getPlayer(uuid);
            if (host == null || !(host.level().getEntity(id) instanceof ItemEntity item)) return;
            picking = true;
            try { item.playerTouch(host); } finally { picking = false; }
        });
        player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        return true;
    }

    /** Start of each client tick: the drop key charges while held and throws on release (the integrated server only). */
    private static void throwTick(Minecraft mc) {
        ItemPhysicsModule module = module();
        if (module == null || !module.isEnabled() || !module.charged.get() || !mc.hasSingleplayerServer() || mc.player == null
            || mc.gui.screen() != null || mc.player.isSpectator()) {
            CHARGE.reset();
            return;
        }
        boolean clicked = false;
        while (mc.options.keyDrop.consumeClick()) clicked = true;
        float throwPower = CHARGE.tick(mc.options.keyDrop.isDown(), clicked);
        if (throwPower <= 0 || mc.player.getMainHandItem().isEmpty()) return;
        thrower = mc.player.getUUID();
        power = throwPower;
        drop(mc, mc.hasControlDown());
    }

    /** What vanilla does for one press of the drop key. */
    static void drop(Minecraft mc, boolean all) {
        mc.gameMode.dropItem(mc.player, all);
    }

    /** LivingEntity.createItemStackToDrop on the server: the host's charged throw leaves the hand faster. */
    public static void thrown(Entity dropper, ItemEntity item) {
        if (item == null || !(dropper instanceof ServerPlayer player) || !player.getUUID().equals(thrower)) return;
        thrower = null;
        item.setDeltaMovement(item.getDeltaMovement().scale(power));
    }

    /** The charge, as a thin bar under the crosshair. */
    private static void chargeBar(GuiGraphicsExtractor graphics) {
        float shown = CHARGE.shown();
        if (shown <= 0) return;
        int x = graphics.guiWidth() / 2 - 8, y = graphics.guiHeight() / 2 + 9;
        graphics.fill(x, y, x + 16, y + 2, 0x80000000);
        graphics.fill(x, y, x + Math.round(16 * shown), y + 2, 0xE0FFFFFF);
    }
}
