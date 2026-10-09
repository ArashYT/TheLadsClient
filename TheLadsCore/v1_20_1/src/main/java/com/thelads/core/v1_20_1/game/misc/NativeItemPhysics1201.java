package com.thelads.core.v1_20_1.game.misc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.ItemPhysics;
import com.thelads.core.client.OldAnimations;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ItemPhysicsModule;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;

/**
 * Item Physics on 1.20.1 (the 26.3 module ported to the classic renderer). The look is client-side and works on any server. The
 * rules run only where this client owns the game: the integrated server's levels (singleplayer, or a world opened to LAN) and,
 * for the same physics on both ends, this client's own levels while that server runs. A remote server's world exists here only as a
 * ClientLevel with no integrated server, so every rule is inert there, and there are no packets of our own.
 */
public final class NativeItemPhysics1201 {
    static final int BURNS = 1, FLOATS = 2;
    private static final Map<Item, Integer> KINDS = new ConcurrentHashMap<>();
    private static final ItemPhysics.Charge CHARGE = new ItemPhysics.Charge();
    /** A charged throw on its way to the integrated server: the thrower and the speed multiple. */
    private static volatile UUID thrower;
    private static volatile float power;
    /** Server thread: our right-click pickup is touching the item, so the no-auto-pickup rule lets it through. */
    private static boolean picking;
    /** Model bounds {minX, minY, minZ, maxX, maxY, maxZ} in the frame an item is drawn in (ground transform applied), per baked model. */
    private static final Map<BakedModel, float[]> BOUNDS = new IdentityHashMap<>();
    /** QA: dropped items drawn by Item Physics since start, and the last tumble seen (read by the QA probe). */
    public static volatile long drawn;
    public static volatile String lastDrawn = "";
    /** QA: HUD callbacks seen, and those that drew the charge bar. */
    public static volatile long barCalls, barDrawn;

    private NativeItemPhysics1201() {}

    public static void initialize() {
        ClientTickEvents.START_CLIENT_TICK.register(NativeItemPhysics1201::throwTick);
        HudRenderCallback.EVENT.register((graphics, delta) -> chargeBar(graphics));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            BOUNDS.clear();
            if (module() != null && module().isEnabled())
                LoggerFactory.getLogger("TheLadsCore").info("Lads Item Physics: {}", mc.hasSingleplayerServer()
                    ? "singleplayer rules on (this client's integrated server)" : "look only; the remote server keeps its own item rules");
        });
    }

    public static ItemPhysicsModule module() {
        return ModuleManager.getInstance().getModule(ItemPhysicsModule.NAME) instanceof ItemPhysicsModule module ? module : null;
    }

    /** Dropped items lie flat and tumble (any server: it only changes how this client draws them). Public for 1.7 Animations' dropped-item icon to stand aside. */
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

    public static boolean burns(ItemEntity entity) { return (kind(entity.getItem()) & BURNS) != 0; }

    public static boolean floats(ItemEntity entity) { return (kind(entity.getItem()) & FLOATS) != 0; }

    /** BURNS and FLOATS for this item: fuel, a flammable block, or one of ItemPhysics' named materials. */
    private static int kind(ItemStack stack) {
        return KINDS.computeIfAbsent(stack.getItem(), item -> {
            boolean fuel = AbstractFurnaceBlockEntity.isFuel(stack);
            boolean block = item instanceof BlockItem placed && placed.getBlock().defaultBlockState().ignitedByLava();
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            return (ItemPhysics.burns(id, fuel, block) ? BURNS : 0) | (ItemPhysics.floats(id, fuel, block) ? FLOATS : 0);
        });
    }

    // ---- look ----

    /**
     * ItemEntityRenderer.render, in place of vanilla's bob and spin: every copy of the stack placed by ItemPhysics at this frame's
     * yaw and tumble. False lets vanilla draw (module or animation off, or nothing to draw).
     */
    public static boolean render(ItemEntity entity, float partial, PoseStack pose, MultiBufferSource buffers, int light, ItemRenderer renderer) {
        if (!renders()) return false;
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) return false;
        BakedModel model = renderer.getModel(stack, entity.level(), null, entity.getId());
        float[] box = bounds(model);
        ItemPhysics.Tumble tumble = ((ItemPhysics.Holder) entity).lads$tumble();
        float sizeZ = box[5] - box[2];
        tumble.rest = sizeZ <= ItemPhysics.FLAT ? 180 : 90;
        float yaw = tumble.yaw(partial), pitch = tumble.pitch(partial), raise = tumble.raise(partial);
        int copies = copies(stack), seed = Item.getId(stack.getItem()) + stack.getDamageValue();
        float cx = (box[0] + box[3]) / 2, cy = (box[1] + box[4]) / 2, cz = (box[2] + box[5]) / 2;
        OldAnimations.Sink out = sink(pose);
        for (int copy = 0; copy < copies; copy++) {
            pose.pushPose();
            ItemPhysics.place(out, yaw, pitch, raise, cx, cy, cz, (box[4] - box[1]) / 2, sizeZ / 2, copy, copies, seed);
            renderer.render(stack, ItemDisplayContext.GROUND, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
            pose.popPose();
        }
        drawn++;
        lastDrawn = String.format("yaw=%.1f pitch=%.1f raise=%.3f copies=%d flat=%b", yaw, pitch, raise, copies, sizeZ <= ItemPhysics.FLAT);
        return true;
    }

    /** How many copies of the stack vanilla draws: 1 to 5 by count. */
    static int copies(ItemStack stack) {
        int count = stack.getCount();
        return count > 48 ? 5 : count > 32 ? 4 : count > 16 ? 3 : count > 1 ? 2 : 1;
    }

    /** The model's extent after its ground transform and vanilla's centring: {minX, minY, minZ, maxX, maxY, maxZ}. */
    static float[] bounds(BakedModel model) {
        float[] known = BOUNDS.get(model);
        if (known != null) return known;
        float[] box = measure(model);
        if (BOUNDS.size() > 4096) BOUNDS.clear();
        BOUNDS.put(model, box);
        return box;
    }

    private static float[] measure(BakedModel model) {
        PoseStack frame = new PoseStack();
        model.getTransforms().getTransform(ItemDisplayContext.GROUND).apply(false, frame);
        frame.translate(-0.5f, -0.5f, -0.5f);
        Matrix4f matrix = frame.last().pose();
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        Vector3f corner = new Vector3f();
        boolean any = false;
        if (!model.isCustomRenderer()) {
            RandomSource random = RandomSource.create();
            Direction[] sides = Direction.values();
            for (int side = 0; side <= sides.length; side++) {
                random.setSeed(42L);
                for (BakedQuad quad : model.getQuads(null, side == sides.length ? null : sides[side], random)) {
                    int[] data = quad.getVertices();
                    int stride = data.length / 4;
                    for (int vertex = 0; vertex + 2 < data.length; vertex += stride) {
                        corner.set(Float.intBitsToFloat(data[vertex]), Float.intBitsToFloat(data[vertex + 1]), Float.intBitsToFloat(data[vertex + 2]));
                        include(box, matrix, corner);
                        any = true;
                    }
                }
            }
        }
        if (!any) { // a chest, a shield, a banner: drawn by an entity renderer inside the unit cube
            for (int i = 0; i < 8; i++) include(box, matrix, corner.set(i & 1, i >> 1 & 1, i >> 2 & 1));
        }
        return box;
    }

    private static void include(float[] box, Matrix4f matrix, Vector3f corner) {
        matrix.transformPosition(corner);
        box[0] = Math.min(box[0], corner.x); box[1] = Math.min(box[1], corner.y); box[2] = Math.min(box[2], corner.z);
        box[3] = Math.max(box[3], corner.x); box[4] = Math.max(box[4], corner.y); box[5] = Math.max(box[5], corner.z);
    }

    /** ItemEntity client tick: the tumble follows how far the item moved, whether it landed and whether it floats. */
    public static void clientTick(ItemEntity entity) {
        if (!renders()) return;
        double dx = entity.getX() - entity.xo, dy = entity.getY() - entity.yo, dz = entity.getZ() - entity.zo;
        ((ItemPhysics.Holder) entity).lads$tumble().tick(Math.sqrt(dx * dx + dy * dy + dz * dz), entity.onGround(),
            Math.max(entity.getFluidHeight(FluidTags.WATER), entity.getFluidHeight(FluidTags.LAVA)));
    }

    // ---- singleplayer rules ----

    /** A burning flammable item lying on a flammable block sets it alight where fire may spread (the fire-tick game rule). */
    public static void ignite(ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % 10 != 0 || !entity.isOnFire() || !entity.onGround()) return;
        ItemPhysicsModule module = rules(level);
        if (module == null || !module.ignite.get() || !burns(entity) || !level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) return;
        BlockPos at = entity.blockPosition();
        if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).ignitedByLava()) {
            BlockState fire = BaseFireBlock.getState(level, at);
            if (fire.canSurvive(level, at)) level.setBlockAndUpdate(at, fire);
        }
    }

    /** Auto-pickup stays off for the host while right-click pickup is on (LAN guests cannot right-click, so they keep it). */
    public static boolean noAutoPickup(ItemEntity entity, Player player) {
        if (picking || !(entity.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer host)) return false;
        ItemPhysicsModule module = rules(level);
        return module != null && module.pickup.get() && level.getServer().isSingleplayerOwner(host.getGameProfile());
    }

    /** Minecraft.startUseItem: right-click on a dropped item in reach picks it up; true when one was picked. */
    public static boolean pickUp(Minecraft mc) {
        ItemPhysicsModule module = module();
        var server = mc.getSingleplayerServer();
        Player player = mc.player;
        if (module == null || !module.isEnabled() || !module.pickup.get() || server == null || player == null || mc.level == null
            || mc.gameMode == null || player.isSpectator()) return false;
        double reach = mc.gameMode.getPickRange();
        Vec3 eye = player.getEyePosition(), end = eye.add(player.getViewVector(1).scale(reach));
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
        player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /** Start of each client tick: the drop key charges while held and throws on release (the integrated server only). */
    private static void throwTick(Minecraft mc) {
        ItemPhysicsModule module = module();
        if (module == null || !module.isEnabled() || !module.charged.get() || !mc.hasSingleplayerServer() || mc.player == null
            || mc.screen != null || mc.player.isSpectator()) {
            CHARGE.reset();
            return;
        }
        boolean clicked = false;
        while (mc.options.keyDrop.consumeClick()) clicked = true;
        float throwPower = CHARGE.tick(mc.options.keyDrop.isDown(), clicked);
        if (throwPower <= 0 || mc.player.getMainHandItem().isEmpty()) return;
        thrower = mc.player.getUUID();
        power = throwPower;
        drop(mc, Screen.hasControlDown());
    }

    /** What vanilla does for one press of the drop key (Minecraft.handleKeybinds). */
    static void drop(Minecraft mc, boolean all) {
        if (mc.player.drop(all)) mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /** Player.drop on the server: the host's charged throw leaves the hand faster, before the item enters the level. */
    public static void thrown(Entity dropper, ItemEntity item) {
        if (item == null || !(dropper instanceof ServerPlayer player) || !player.getUUID().equals(thrower)) return;
        thrower = null;
        item.setDeltaMovement(item.getDeltaMovement().scale(power));
    }

    /** The charge, as a thin bar under the crosshair. */
    private static void chargeBar(GuiGraphics graphics) {
        barCalls++;
        float shown = CHARGE.shown();
        if (shown <= 0) return;
        barDrawn++;
        int x = graphics.guiWidth() / 2 - 8, y = graphics.guiHeight() / 2 + 9;
        graphics.fill(x, y, x + 16, y + 2, 0x80000000);
        graphics.fill(x, y, x + Math.round(16 * shown), y + 2, 0xE0FFFFFF);
    }

    /** QA: the charge bar's fill, 0 to 1. */
    public static float chargeShown() { return CHARGE.shown(); }

    static OldAnimations.Sink sink(PoseStack pose) {
        return new OldAnimations.Sink() {
            @Override public void translate(float x, float y, float z) { pose.translate(x, y, z); }
            @Override public void rotate(float degrees, float x, float y, float z) {
                pose.mulPose(new Quaternionf().rotationAxis(degrees * Mth.DEG_TO_RAD, x, y, z));
            }
            @Override public void scale(float x, float y, float z) { pose.scale(x, y, z); }
        };
    }
}
