package com.thelads.core.v1_20_1.hudextras;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.hud.DamageNumbers;
import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.client.hud.JumpReset;
import com.thelads.core.client.hud.MediaSession;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.TitleTweakerModule;
import com.thelads.core.v1_20_1.hud.HudParts1201;
import com.thelads.core.v1_20_1.hudextras.mixin.GameModeAccessor1201;
import com.thelads.core.v1_20_1.hudextras.mixin.GuiTitleAccessor1201;
import java.lang.reflect.Method;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The HUD widgets and vanilla HUD tweaks of parity 1.9.19 on 1.20.1 (common HudExtras): once a client tick it fills what the switched-on
 * widgets show; after the world it draws Damage Indicator's floating numbers. The vanilla HUD hooks are the mixins in
 * theladscore.v1_20_1.hudextras.mixins.json (package hudextras.mixin), with Raised's part moves in HudParts1201/ForgeOverlays1201.
 */
public final class HudExtras1201 {
    /** The modules this version builds in (Totem Pop Counter and Subtitles too: 1.20.1 has both). */
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

    private HudExtras1201() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(MODULES);
        if (!MediaSession.SUPPORTED) ModuleSupport.registerUnavailable(HudExtras.MEDIA, "Reads Windows' media controls; this computer is not on Windows.");
        ClientTickEvents.END_CLIENT_TICK.register(HudExtras1201::tick);
        WorldRenderEvents.LAST.register(HudExtras1201::numbers);
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
        HudExtras.horseJump = HudExtras.jumpHeight(horse.getCustomJump());
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
        GameModeAccessor1201 mode = (GameModeAccessor1201) mc.gameMode;
        HudExtras.blockProgress = mc.gameMode != null && mc.gameMode.isDestroying() && pos.equals(mode.lads$destroyPos()) ? mode.lads$destroyProgress() : 0;
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

    /** Iris (or Oculus) through its API by reflection, so the Core runs without it; "" when off or absent. */
    private static String shaderPack() {
        try {
            if (!irisLooked) {
                irisLooked = true;
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisInstance = api.getMethod("getInstance");
                irisInUse = api.getMethod("isShaderPackInUse");
                Class<?> iris;
                try {
                    iris = Class.forName("net.irisshaders.iris.Iris");
                } catch (ClassNotFoundException older) {
                    iris = Class.forName("net.coderbot.iris.Iris");
                }
                irisConfig = iris.getMethod("getIrisConfig");
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
        if (ItemStack.isSameItemSameTags(stack, loreStack)) return;
        loreStack = stack.copy();
        StringBuilder out = new StringBuilder();
        for (Map.Entry<Enchantment, Integer> enchantment : EnchantmentHelper.getEnchantments(stack).entrySet()) {
            if (out.length() > 0) out.append('\n');
            out.append(enchantment.getKey().getFullname(enchantment.getValue()).getString());
        }
        CompoundTag display = stack.getTagElement("display");
        ListTag lines = display == null ? null : display.getList("Lore", Tag.TAG_STRING);
        for (int i = 0; lines != null && i < lines.size(); i++) {
            Component line = Component.Serializer.fromJson(lines.getString(i));
            if (line == null) continue;
            if (out.length() > 0) out.append('\n');
            out.append(line.getString());
        }
        HudExtras.loreText = out.toString();
    }

    /** After the world: Damage Indicator's floating numbers, billboards like name tags, hidden behind blocks as name tags are. */
    private static void numbers(WorldRenderContext context) {
        if (!numbersShown) return;
        Minecraft mc = Minecraft.getInstance();
        Camera camera = context.camera();
        Vec3 eye = camera.getPosition();
        PoseStack pose = context.matrixStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        long now = System.currentTimeMillis();
        int life = HudExtras.numberMillis;
        boolean any = false;
        for (int slot = 0; slot < DamageNumbers.slots(); slot++) {
            String text = DamageNumbers.text(slot, now, life);
            if (text == null) continue;
            any = true;
            pose.pushPose();
            pose.translate(DamageNumbers.x(slot) - eye.x, DamageNumbers.y(slot, now, life) - eye.y, DamageNumbers.z(slot) - eye.z);
            pose.mulPose(camera.rotation());
            pose.scale(-0.025f, -0.025f, 0.025f);
            int color = DamageNumbers.alpha(slot, now, life) << 24 | 0xFF5555;
            font.drawInBatch(text, -font.width(text) / 2f, 0, color, true, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        if (any) buffers.endBatch();
    }

    /** Title Tweaker on Forge's title overlay (ForgeOverlays1201): its move and scale around the screen centre, where the title is. */
    public static HudParts1201.Scope titleScope(GuiGraphics graphics) {
        TitleTweakerModule titles = HudExtras.TITLES;
        if (!titles.on) return HudParts1201.Scope.NOOP;
        float scale = titleScale(graphics.guiWidth());
        return HudParts1201.moved(graphics, graphics.guiWidth() / 2f, graphics.guiHeight() / 2f, titles.dx, titles.dy, scale);
    }

    /** The title's draw scale now: Title Tweaker's, fitted to the screen width. */
    public static float titleScale(int screenWidth) {
        TitleTweakerModule titles = HudExtras.TITLES;
        Minecraft mc = Minecraft.getInstance();
        GuiTitleAccessor1201 gui = (GuiTitleAccessor1201) mc.gui;
        Component title = gui.lads$title(), subtitle = gui.lads$subtitle();
        return TitleTweakerModule.drawScale(titles.size, titles.fits, title == null ? 0 : mc.font.width(title),
            subtitle == null ? 0 : mc.font.width(subtitle), screenWidth);
    }

    /** Totem Pop Counter: an entity event the server sent (ClientPacketListenerTotemMixin1201). */
    public static void entityEvent(Entity entity, byte id) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player)) return;
        if (id == 35) HudExtras.totemPopped(entity.getId(), entity.getName().getString());
        else if (id == 3) HudExtras.died(entity.getId());
    }
}
