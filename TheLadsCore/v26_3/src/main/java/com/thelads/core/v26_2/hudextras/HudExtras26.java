package com.thelads.core.v26_2.hudextras;

import com.thelads.core.client.hud.DamageNumbers;
import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.client.hud.JumpReset;
import com.thelads.core.client.hud.MediaSession;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.RaisedModule;
import com.thelads.core.modules.TitleTweakerModule;
import com.thelads.core.v26_2.hudextras.mixin.GameModeAccessor26;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

/**
 * The HUD widgets and vanilla HUD tweaks of parity 1.9.19 on 26.3 (common HudExtras): once a client tick it fills what the switched-on
 * widgets show; Damage Indicator's floating numbers are text gizmos emitted with the debug renderer's (DamageNumbersMixin26). The vanilla
 * HUD hooks are the mixins in theladscore.v26_3.hudextras.mixins.json (package hudextras.mixin).
 */
public final class HudExtras26 {
    static final String[] MODULES = {HudExtras.DAMAGE_INDICATOR, HudExtras.JUMP_RESET, HudExtras.MEDIA, HudExtras.HORSE, HudExtras.PLAYERS,
        HudExtras.TOTEMS, HudExtras.BLOCK_INFO, HudExtras.CUSTOM_TEXT, HudExtras.CUSTOM_SHAPE, HudExtras.CUSTOM_IMAGE, HudExtras.HEAD,
        HudExtras.ABOVE, HudExtras.ENTITIES, HudExtras.SHADER, HudExtras.LORE, com.thelads.core.modules.SubtitlesModule.NAME,
        TitleTweakerModule.NAME, com.thelads.core.modules.ToastControlModule.NAME, com.thelads.core.modules.VanillaHudModule.NAME};
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static int lastHurt, targetEntity = -1, shaderTicks;
    private static BlockState blockState, aboveState;
    private static ItemStack loreStack = ItemStack.EMPTY;
    private static boolean numbersShown;
    private static Method irisInstance, irisInUse, irisConfig, packName;
    private static boolean irisLooked;

    private HudExtras26() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(MODULES);
        if (!MediaSession.SUPPORTED) ModuleSupport.registerUnavailable(HudExtras.MEDIA, "Reads Windows' media controls; this computer is not on Windows.");
        ClientTickEvents.END_CLIENT_TICK.register(HudExtras26::tick);
    }

    private static void tick(Minecraft mc) {
        HudExtras.tick();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            HudExtras.targetId = -1;
            HudExtras.horseShown = HudExtras.blockShown = false;
            JumpReset.reset();
            DamageNumbers.clear();
            return;
        }
        long now = System.currentTimeMillis();
        int hurt = player.hurtTime;
        if (HudExtras.jumpReset) JumpReset.tick(hurt > lastHurt, player.onGround(), now);
        lastHurt = hurt;
        if (HudExtras.target) target(mc, level, now);
        if (HudExtras.numbers) {
            numbersShown = true;
            for (Entity entity : level.entitiesForRendering()) {
                if (entity == player || !(entity instanceof LivingEntity living) || entity.distanceToSqr(player) > 32 * 32) continue;
                DamageNumbers.observe(entity.getId(), living.getHealth(), entity.getX(), entity.getY() + entity.getBbHeight() + 0.3, entity.getZ(), now);
            }
            DamageNumbers.endTick();
        } else if (numbersShown) {
            numbersShown = false;
            DamageNumbers.clear();
        }
        if (HudExtras.horse) horse(mc, player);
        if (HudExtras.players) {
            var connection = mc.getConnection();
            HudExtras.playersTotal = connection == null ? 1 : connection.getOnlinePlayers().size();
            HudExtras.playersNearby = Math.max(0, level.players().size() - 1);
        }
        if (HudExtras.block) block(mc, level, player);
        if (HudExtras.above) above(level, player);
        if (HudExtras.entities) HudExtras.entityCount = level.getEntityCount();
        if (HudExtras.shader && shaderTicks-- <= 0) {
            shaderTicks = 20;
            HudExtras.shaderName = shaderPack();
        }
        if (HudExtras.lore) lore(player.getMainHandItem());
    }

    /** Damage Indicator: the living entity under the crosshair, kept 3 s after it leaves it while it is still loaded. */
    private static void target(Minecraft mc, ClientLevel level, long now) {
        if (mc.crosshairPickEntity instanceof LivingEntity living && living.isAlive()) {
            if (living.getId() != targetEntity) {
                targetEntity = living.getId();
                HudExtras.targetName = living.getName().getString();
            }
            HudExtras.targetId = targetEntity;
            HudExtras.targetUntil = now + 3000;
        }
        if (HudExtras.targetId == -1) return;
        if (level.getEntity(HudExtras.targetId) instanceof LivingEntity living) {
            HudExtras.targetHealth = living.getHealth();
            HudExtras.targetMax = living.getMaxHealth();
        } else {
            HudExtras.targetId = targetEntity = -1;
        }
    }

    private static void horse(Minecraft mc, LocalPlayer player) {
        AbstractHorse horse = player.getVehicle() instanceof AbstractHorse ridden ? ridden
            : mc.crosshairPickEntity instanceof AbstractHorse looked ? looked : null;
        HudExtras.horseShown = horse != null;
        if (horse == null) return;
        HudExtras.horseJump = HudExtras.jumpHeight(horse.getAttributeValue(Attributes.JUMP_STRENGTH));
        HudExtras.horseSpeed = HudExtras.blocksPerSecond(horse.getAttributeValue(Attributes.MOVEMENT_SPEED));
        HudExtras.horseHealth = horse.getHealth();
        HudExtras.horseMax = horse.getMaxHealth();
    }

    private static void block(Minecraft mc, ClientLevel level, LocalPlayer player) {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult target) || hit.getType() != HitResult.Type.BLOCK) {
            HudExtras.blockShown = false;
            return;
        }
        BlockPos pos = target.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state != blockState) { // a name and tool once per block state, not per tick
            blockState = state;
            HudExtras.blockName = state.getBlock().getName().getString();
            String tool = state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? "pickaxe" : state.is(BlockTags.MINEABLE_WITH_AXE) ? "axe"
                : state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? "shovel" : state.is(BlockTags.MINEABLE_WITH_HOE) ? "hoe" : null;
            int tier = state.is(BlockTags.NEEDS_DIAMOND_TOOL) ? 3 : state.is(BlockTags.NEEDS_IRON_TOOL) ? 2 : state.is(BlockTags.NEEDS_STONE_TOOL) ? 1 : 0;
            HudExtras.blockTool = HudExtras.toolName(tool, tier);
        }
        HudExtras.blockHarvest = !state.requiresCorrectToolForDrops() ? -1 : player.hasCorrectToolForDrops(state) ? 1 : 0;
        CURSOR.setWithOffset(pos, target.getDirection()); // the light on the face you look at
        HudExtras.blockLight = level.getBrightness(LightLayer.BLOCK, CURSOR);
        HudExtras.skyLight = level.getBrightness(LightLayer.SKY, CURSOR);
        var mode = mc.gameMode;
        HudExtras.blockProgress = mode != null && mode.isDestroying() && pos.equals(((GameModeAccessor26) mode).lads$destroyPos()) ? mode.destroyProgress : 0;
        HudExtras.blockShown = true;
    }

    private static void above(ClientLevel level, LocalPlayer player) {
        int x = player.getBlockX(), z = player.getBlockZ(), from = (int) Math.floor(player.getY() + player.getBbHeight());
        for (int gap = 0; gap < 32; gap++) {
            BlockState state = level.getBlockState(CURSOR.set(x, from + gap, z));
            if (state.isAir()) continue;
            if (state != aboveState) {
                aboveState = state;
                HudExtras.aboveName = state.getBlock().getName().getString();
            }
            HudExtras.aboveGap = gap;
            return;
        }
        aboveState = null;
        HudExtras.aboveName = "";
    }

    /** Iris through its API by reflection, so the Core runs without it; "" when off or absent. */
    private static String shaderPack() {
        try {
            if (!irisLooked) {
                irisLooked = true;
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisInstance = api.getMethod("getInstance");
                irisInUse = api.getMethod("isShaderPackInUse");
                irisConfig = Class.forName("net.irisshaders.iris.Iris").getMethod("getIrisConfig");
                packName = irisConfig.getReturnType().getMethod("getShaderPackName");
            }
            if (irisInUse == null || !(Boolean) irisInUse.invoke(irisInstance.invoke(null))) return "";
            Object name = packName.invoke(irisConfig.invoke(null));
            String pack = name instanceof java.util.Optional<?> optional ? (optional.isPresent() ? String.valueOf(optional.get()) : "") : String.valueOf(name);
            return pack.endsWith(".zip") ? pack.substring(0, pack.length() - 4) : pack;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) {
            irisInUse = null;
            return "";
        }
    }

    /** Held Item Lore: enchantments then lore lines, rebuilt only when the held item changes. */
    private static void lore(ItemStack stack) {
        if (ItemStack.isSameItemSameComponents(stack, loreStack)) return;
        loreStack = stack.copy();
        StringBuilder out = new StringBuilder();
        for (Object2IntMap.Entry<Holder<Enchantment>> enchantment : stack.getEnchantments().entrySet()) {
            if (out.length() > 0) out.append('\n');
            out.append(Enchantment.getFullname(enchantment.getKey(), enchantment.getIntValue()).getString());
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                if (out.length() > 0) out.append('\n');
                out.append(line.getString());
            }
        }
        HudExtras.loreText = out.toString();
    }

    /** With the debug renderer's gizmos (each frame, F3 or not): Damage Indicator's floating numbers, hidden behind blocks. */
    public static void numbers() {
        if (!numbersShown) return;
        long now = System.currentTimeMillis();
        int life = HudExtras.numberMillis;
        for (int slot = 0; slot < DamageNumbers.slots(); slot++) {
            String text = DamageNumbers.text(slot, now, life);
            if (text == null) continue;
            int color = DamageNumbers.alpha(slot, now, life) << 24 | 0xFF5555;
            Gizmos.billboardText(text, new Vec3(DamageNumbers.x(slot), DamageNumbers.y(slot, now, life), DamageNumbers.z(slot)),
                TextGizmo.Style.forColorAndCentered(color).withScale(0.4f));
        }
    }

    /** The title's draw scale now: Title Tweaker's, fitted to the screen width (widths at 1x). */
    public static float titleScale(int titleWidth, int subtitleWidth, int screenWidth) {
        TitleTweakerModule titles = HudExtras.TITLES;
        return TitleTweakerModule.drawScale(titles.size, titles.fits, titleWidth, subtitleWidth, screenWidth);
    }

    /** Raised's own move and scale of one vanilla part (OneClient's VanillaHUD), around the part's pivot on a w x h GUI. */
    public static boolean move(Matrix3x2fStack pose, int piece, int w, int h) {
        RaisedModule raised = HudExtras.RAISED;
        if (raised == null || !raised.moved[piece]) return false;
        float px = RaisedModule.pivotX(piece, w), py = RaisedModule.pivotY(piece, h), scale = raised.scale[piece];
        pose.pushMatrix();
        pose.translate(px + raised.dx[piece], py + raised.dy[piece]);
        pose.scale(scale, scale);
        pose.translate(-px, -py);
        return true;
    }

    /** Totem Pop Counter: an entity event the server sent (TotemMixin26). */
    public static void entityEvent(Entity entity, byte id) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player)) return;
        if (id == 35) HudExtras.totemPopped(entity.getId(), entity.getName().getString());
        else if (id == 3) HudExtras.died(entity.getId());
    }
}
