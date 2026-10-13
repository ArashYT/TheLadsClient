package com.thelads.core.v1_20_1.hud.overlay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.thelads.core.client.CrosshairDesign;
import com.thelads.core.client.CrosshairDrawing;
import com.thelads.core.client.CrosshairDrawingStore;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.CrosshairModule;
import com.thelads.core.v1_20_1.hud.Hud1201;
import com.thelads.core.v1_20_1.hud.Mods1201;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix4f;

/**
 * Crosshair Tweaks on 1.20.1: the 26.3 NativeCrosshair drawn in immediate mode. Replaces Gui.renderCrosshair while the module is
 * on; reads local aiming state only (never changes attacks, inventories or hit results). Stands down for Custom Crosshair Mod.
 */
public final class Crosshair1201 {
    static final String MODULE = "Crosshair Tweaks";
    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    private static boolean external;
    private static CrosshairDrawing drawing = defaultDrawing();
    private static String geometryKey = "";
    private static List<CrosshairDesign.Rect> geometry = List.of(), outline = List.of();
    private static final Map<Integer, List<CrosshairDesign.Rect>> RINGS = new HashMap<>();

    private Crosshair1201() {}

    static void register() {
        external = Mods1201.loaded("custom-crosshair-mod", "crosshairtweaks");
        if (external) {
            ModuleSupport.registerExternal(MODULE, "Custom Crosshair Mod", "custom-crosshair-mod", true);
            return;
        }
        ModuleSupport.registerBuiltIn(MODULE);
        CrosshairModule module = module();
        if (module == null) return;
        drawing = CrosshairDrawingStore.load(defaultDrawing());
        CrosshairDrawingStore.setDrawingChangedListener(() -> { drawing = CrosshairDrawingStore.load(defaultDrawing()); geometryKey = ""; });
        module.drawingEditor.setAction(() -> Minecraft.getInstance().setScreen(new CrosshairDrawingScreen1201(Minecraft.getInstance().screen)));
    }

    public static CrosshairModule module() { return Hud1201.module(MODULE) instanceof CrosshairModule module ? module : null; }
    public static boolean active() { CrosshairModule module = module(); return !external && module != null && module.isEnabled(); }
    static boolean flag(String name) { return Hud1201.bool(MODULE, name, false); }
    static double number(String name) { return Hud1201.number(MODULE, name, 0); }
    static int color(String name) {
        ColorOption value = (ColorOption) module().getOption(name);
        return value.isUseGlobal() ? HudSettings.getInstance().getGlobalColor() : value.getColor();
    }

    static CrosshairDrawing drawing() { return drawing.copy(); }
    static void commitDrawing(CrosshairDrawing next) throws IOException { CrosshairDrawingStore.save(next); drawing = next.copy(); geometryKey = ""; }
    private static CrosshairDrawing defaultDrawing() {
        CrosshairDrawing value = new CrosshairDrawing(17, 17);
        for (CrosshairDesign.Rect r : CrosshairDesign.geometry(0, 5, 5, 2, 1))
            for (int y = r.top(); y < r.bottom(); y++) for (int x = r.left(); x < r.right(); x++) value.set(x + 8, y + 8, true);
        return value;
    }

    /** Vanilla 1.20.1 shows the 3D debug axes in place of the crosshair under these conditions (F3 without reduced debug info). */
    static boolean debugAxes() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options.renderDebug && mc.player != null && !mc.player.isReducedDebugInfo() && !mc.options.reducedDebugInfo().get();
    }

    static CrosshairDesign.Visibility context() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        ItemStack held = player.getMainHandItem(), off = player.getOffhandItem();
        return new CrosshairDesign.Visibility(!mc.options.getCameraType().isFirstPerson(), player.isSpectator(), spectatorTarget(mc),
            mc.options.hideGui, mc.options.renderDebug, mc.screen instanceof AbstractContainerScreen<?>, player.isScoping(),
            ranged(held) || ranged(off), throwable(held) || throwable(off));
    }

    private static boolean spectatorTarget(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entity) return entity.getEntity() instanceof MenuProvider;
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
            && mc.level.getBlockState(block.getBlockPos()).getMenuProvider(mc.level, block.getBlockPos()) != null;
    }

    static CrosshairDesign.Rules rules() {
        return new CrosshairDesign.Rules(flag("Show in Third Person"), flag("Show in Spectator"), flag("Visible with Hidden HUD"),
            flag("Visible with Debug"), flag("Hide in Containers"), flag("Visible Normally"), flag("Visible Using Spyglass"),
            flag("Visible Holding Ranged Weapon"), flag("Visible Holding Throwable"));
    }

    static boolean suppressDebugAxes() {
        Minecraft mc = Minecraft.getInstance();
        return active() && mc.player != null && (!flag("Keep Vanilla Debug") || flag("Disable Crosshair") || !CrosshairDesign.visible(context(), rules()));
    }

    /** Gui.renderCrosshair replacement (CrosshairGuiMixin): vanilla's own debug axes when kept, then the Lads crosshair. */
    public static void renderInHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        boolean spectatorOk = !mc.player.isSpectator() || spectatorTarget(mc);
        if (debugAxes() && !suppressDebugAxes() && mc.options.getCameraType().isFirstPerson() && spectatorOk) vanillaAxes(graphics);
        render(graphics);
    }

    /** With F1 the HUD is not drawn; "Visible with Hidden HUD" draws the crosshair after the HUD pass anyway (HiddenHudMixin). */
    public static void renderHiddenHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.hideGui || !active() || !flag("Visible with Hidden HUD")
            || mc.screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) return;
        render(graphics);
    }

    static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null || mc.level == null) return;
        CrosshairDesign.Visibility context = context();
        if (!CrosshairDesign.visible(context, rules())) return;
        double alpha = number(context.thirdPerson() ? "Opacity Third Person" : "Opacity First Person") / 100;
        boolean axes = debugAxes() && flag("Keep Vanilla Debug");
        float partial = mc.getFrameTime();
        int cx = graphics.guiWidth() / 2 + (int) number("Offset X"), cy = graphics.guiHeight() / 2 + (int) number("Offset Y");
        try {
            if (!flag("Disable Crosshair") && !axes) {
                double bow = chargeProgress(mc.player.getUseItem(), mc.player.getTicksUsingItem(), partial, mc.player.isUsingItem());
                double gap = CrosshairDesign.gap(number("Gap"), flag("Dynamic Attack Gap"), mc.player.getAttackStrengthScale(partial), flag("Dynamic Bow Gap"), bow);
                draw(graphics, cx, cy, gap, alpha, context.thirdPerson(), null);
            }
            if (!flag("Disable Attack Indicator") && (!axes || flag("Debug Attack Indicator"))) attack(graphics, cx, cy, context.thirdPerson());
            if (!flag("Disable Crosshair") && !axes) indicators(graphics, cx, cy, partial, alpha);
        } finally {
            graphics.setColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
        }
    }

    private static boolean previewing;

    /** Lads settings page: the configured crosshair as in game (first-person opacity, base colour, no dynamic gap) at cx, cy. False when another mod draws it. */
    public static boolean preview(GuiGraphics graphics, int cx, int cy) {
        if (external || module() == null) return false;
        previewing = true;
        try {
            draw(graphics, cx, cy, number("Gap"), number("Opacity First Person") / 100, false, null);
        } finally {
            previewing = false;
            RenderSystem.defaultBlendFunc();
        }
        return true;
    }

    /** The shape at (cx, cy); preview: the drawing editor's unsaved bitmap. */
    static void draw(GuiGraphics graphics, int cx, int cy, double gap, double opacity, boolean third, CrosshairDrawing preview) {
        CrosshairModule module = module();
        if (module == null) return;
        int shape = preview != null ? 8 : Hud1201.choice(MODULE, "Shape", 0), main = targetColor();
        boolean invert = (flag("Adaptive Color") || shape == 3) && !flag(third ? "Remove Blend Third Person" : "Remove Blend First Person");
        main = CrosshairDesign.tint(main, opacity, invert);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(cx, cy, 0);
            pose.mulPose(Axis.ZP.rotationDegrees((float) number("Rotation")));
            float scale = (float) module.scale.get();
            pose.scale(scale, scale, 1);
            if (shape == 3) {
                int width = (int) number("Width") * 2 + 5, height = (int) number("Height") * 2 + 5;
                sprite(graphics, invert, main, -width / 2, -height / 2, width, height, 0, 0, 15, 15);
            } else if (shape == 7) debugShape(graphics, opacity);
            else {
                int w = (int) number("Width"), h = (int) number("Height"), t = Math.max(1, (int) Math.round(module.thickness.get())), g = (int) Math.round(gap);
                String key = shape + ":" + w + ":" + h + ":" + t + ":" + g;
                List<CrosshairDesign.Rect> parts, edge;
                if (preview != null) { parts = preview.rectangles(); edge = merge(parts, 1); }
                else {
                    if (!geometryKey.equals(key)) {
                        geometry = merge(shape == 8 ? drawing.rectangles() : CrosshairDesign.geometry(shape, w, h, g, t), 0);
                        outline = merge(geometry, 1);
                        geometryKey = key;
                    }
                    parts = geometry;
                    edge = outline;
                }
                if (flag("Outline")) quads(graphics, edge, CrosshairDesign.alpha(color("Outline Color"), opacity), false);
                quads(graphics, parts, main, invert);
            }
            if (flag("Center Dot")) {
                int t = Math.max(1, (int) Math.round(module.thickness.get()));
                quads(graphics, List.of(new CrosshairDesign.Rect(-t / 2, -t / 2, t - t / 2, t - t / 2)), CrosshairDesign.alpha(color("Dot Color"), opacity), false);
            }
        } finally {
            pose.popPose();
        }
    }

    static int targetColor() {
        Minecraft mc = Minecraft.getInstance();
        int base = color("Color");
        if (flag("Rainbow")) {
            float hue = (float) ((System.nanoTime() / 1e9 * number("Rainbow Speed") / 6 + number("Rainbow Phase") / 360) % 1);
            base = (base & 0xff000000) | (Mth.hsvToRgb(hue, 1, 1) & 0xffffff);
        }
        if (previewing) return base;
        var target = mc.crosshairPickEntity;
        if (target instanceof Player && flag("Highlight Players")) return color("Player Color");
        if ((target instanceof Enemy || target instanceof NeutralMob) && flag("Highlight Hostiles")) return color("Hostile Color");
        if (target instanceof Animal && flag("Highlight Passives")) return color("Passive Color");
        return base;
    }

    private static void debugShape(GuiGraphics graphics, double opacity) {
        Player player = Minecraft.getInstance().player;
        double yaw = player == null ? 0 : Math.toRadians(player.getYRot()), pitch = player == null ? 0 : Math.toRadians(player.getXRot());
        double[][] axes = {{Math.cos(yaw), Math.sin(yaw) * Math.sin(pitch)}, {0, -Math.cos(pitch)}, {-Math.sin(yaw), Math.cos(yaw) * Math.sin(pitch)}};
        int[] colors = {0xffff5555, 0xff55ff55, 0xff5555ff};
        int thickness = Math.max(1, (int) Math.round(module().thickness.get()));
        for (int i = 0; i < 3; i++) {
            var parts = new ArrayList<CrosshairDesign.Rect>();
            CrosshairDesign.line(parts, 0, 0, (int) Math.round(axes[i][0] * (number("Width") + number("Gap"))),
                (int) Math.round(axes[i][1] * (number("Height") + number("Gap"))), thickness);
            quads(graphics, merge(parts, 0), CrosshairDesign.alpha(colors[i], opacity), false);
        }
    }

    private static void attack(GuiGraphics graphics, int cx, int cy, boolean third) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.attackIndicator().get() != AttackIndicatorStatus.CROSSHAIR) return;
        float charge = mc.player.getAttackStrengthScale(0);
        int x = cx - 8, y = cy + 9;
        boolean invert = !flag(third ? "Remove Attack Blend Third Person" : "Remove Attack Blend First Person");
        int tint = CrosshairDesign.tint(0xffffffff, number(third ? "Attack Opacity Third Person" : "Attack Opacity First Person") / 100, invert);
        if ((tint >>> 24) == 0) return;
        // 1.20.1 has no attack range component: vanilla's own "full" rule (living target, charged, slow weapon).
        boolean full = charge >= 1 && mc.crosshairPickEntity instanceof LivingEntity target && target.isAlive()
            && mc.player.getCurrentItemAttackStrengthDelay() > 5;
        if (full) sprite(graphics, invert, tint, x, y, 16, 16, 68, 94, 16, 16);
        else if (charge < 1) {
            sprite(graphics, invert, tint, x, y, 16, 4, 36, 94, 16, 4);
            int width = Math.max(0, Math.min(16, (int) (charge * 17)));
            if (width > 0) sprite(graphics, invert, tint, x, y, width, 4, 52, 94, width, 4);
        }
    }

    private static void indicators(GuiGraphics graphics, int cx, int cy, float partial, double opacity) {
        if (opacity <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        ItemStack held = mc.player.getMainHandItem();
        int x = cx + 14, y = cy + 18;
        if (flag("Item Cooldown")) cooldowns(graphics, cx, cy, partial, opacity);
        RenderSystem.defaultBlendFunc();
        if (flag("Tool Durability Indicator") && held.isDamageableItem()) {
            graphics.renderItem(held, x, y);
            graphics.drawString(mc.font, Integer.toString(Math.max(0, held.getMaxDamage() - held.getDamageValue())), x + 18, y + 4, CrosshairDesign.alpha(0xffffffff, opacity), com.thelads.core.config.HudSettings.getInstance().isTextShadow());
            y += 18;
        }
        if (flag("Projectile Indicator")) {
            ItemStack weapon = held.getItem() instanceof ProjectileWeaponItem || throwable(held) ? held : mc.player.getOffhandItem();
            Ammo ammo = projectiles(weapon);
            if (ammo != null) {
                graphics.renderItem(ammo.icon(), x, y);
                graphics.drawString(mc.font, ammo.infinite() ? "∞" : Integer.toString(ammo.count()), x + 18, y + 4, CrosshairDesign.alpha(0xffffffff, opacity), com.thelads.core.config.HudSettings.getInstance().isTextShadow());
            }
        }
    }

    record Ammo(ItemStack icon, int count, boolean infinite) {}

    static Ammo projectiles(ItemStack held) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return null;
        var inventory = player.getInventory();
        if (held.getItem() instanceof ProjectileWeaponItem weapon) {
            ItemStack projectile = player.getProjectile(held);
            int count = 0;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (weapon.getAllSupportedProjectiles().test(stack)) count += stack.getCount();
            }
            for (ItemStack loaded : chargedProjectiles(held)) {
                count += loaded.getCount();
                if (projectile.isEmpty()) projectile = loaded;
            }
            boolean infinite = player.getAbilities().instabuild
                || held.getItem() instanceof BowItem && projectile.is(Items.ARROW) && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, held) > 0;
            if (projectile.isEmpty()) projectile = new ItemStack(Items.ARROW);
            return new Ammo(projectile, count, infinite);
        }
        if (throwable(held)) {
            int count = 0;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.is(held.getItem())) count += stack.getCount();
            }
            return new Ammo(held, count, player.getAbilities().instabuild);
        }
        return null;
    }

    /** A loaded crossbow's projectiles (1.20.1 keeps them in the ChargedProjectiles tag). */
    private static List<ItemStack> chargedProjectiles(ItemStack crossbow) {
        CompoundTag tag = crossbow.getTag();
        if (tag == null || !tag.contains("ChargedProjectiles", 9)) return List.of();
        ListTag list = tag.getList("ChargedProjectiles", 10);
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) result.add(ItemStack.of(list.getCompound(i)));
        return result;
    }

    static double chargeProgress(ItemStack item, int usedTicks, float partial, boolean using) {
        if (!using) return -1;
        double ticks = usedTicks + partial;
        if (item.getItem() instanceof BowItem) return Math.min(1, ticks / 20);
        if (item.getItem() instanceof CrossbowItem) return Math.min(1, ticks / Math.max(1, CrossbowItem.getChargeDuration(item)));
        if (item.is(Items.TRIDENT)) return Math.min(1, ticks / 10);
        return -1;
    }

    private static boolean ranged(ItemStack item) { return item.getItem() instanceof ProjectileWeaponItem || item.is(Items.TRIDENT); }
    private static boolean throwable(ItemStack item) {
        return item.is(Items.SNOWBALL) || item.is(Items.EGG) || item.is(Items.ENDER_PEARL) || item.is(Items.ENDER_EYE)
            || item.is(Items.EXPERIENCE_BOTTLE) || item.is(Items.SPLASH_POTION) || item.is(Items.LINGERING_POTION);
    }

    private static void cooldowns(GuiGraphics graphics, int cx, int cy, float partial, double opacity) {
        Player player = Minecraft.getInstance().player;
        var cooldowns = player.getCooldowns();
        var seen = new HashSet<Item>();
        int ring = 0;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(cx, cy, 0);
            float scale = (float) module().scale.get();
            pose.scale(scale, scale, 1);
            for (ItemStack item : List.of(new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.CHORUS_FRUIT), player.getMainHandItem(), player.getOffhandItem())) {
                if (item.isEmpty() || !seen.add(item.getItem())) continue;
                float remaining = cooldowns.getCooldownPercent(item.getItem(), partial);
                if (remaining <= 0) continue;
                int radius = Math.min(120, (int) (Math.max(number("Width"), number("Height")) + number("Gap") + 3 + ring++ * 3));
                List<CrosshairDesign.Rect> pixels = RINGS.computeIfAbsent(radius, r -> merge(CrosshairDesign.geometry(4, Math.min(64, r), Math.min(64, r), Math.max(0, r - 64), 2), 0));
                int tint = CrosshairDesign.alpha(color("Cooldown Color"), opacity);
                double progress = 1 - remaining;
                var arc = new ArrayList<CrosshairDesign.Rect>();
                for (CrosshairDesign.Rect rect : pixels)
                    for (int x = rect.left(); x < rect.right(); x++) {
                        double angle = (Math.atan2(rect.top(), x) + Math.PI / 2 + 2 * Math.PI) % (2 * Math.PI) / (2 * Math.PI);
                        if (angle <= progress) arc.add(new CrosshairDesign.Rect(x, rect.top(), x + 1, rect.bottom()));
                    }
                quads(graphics, arc, tint, false);
            }
        } finally {
            pose.popPose();
        }
    }

    /** All rectangles in one draw; invert: vanilla's crosshair blending (inverts what is behind it). */
    private static void quads(GuiGraphics graphics, List<CrosshairDesign.Rect> rectangles, int color, boolean invert) {
        if ((color >>> 24) == 0 || rectangles.isEmpty()) return;
        Matrix4f matrix = graphics.pose().last().pose();
        float a = (color >>> 24) / 255f, r = (color >> 16 & 255) / 255f, g = (color >> 8 & 255) / 255f, b = (color & 255) / 255f;
        RenderSystem.enableBlend();
        if (invert) RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (CrosshairDesign.Rect rect : rectangles) {
            buffer.vertex(matrix, rect.left(), rect.top(), 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, rect.left(), rect.bottom(), 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, rect.right(), rect.bottom(), 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, rect.right(), rect.top(), 0).color(r, g, b, a).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.defaultBlendFunc();
    }

    /** A region of vanilla's icons.png (crosshair, attack indicator) tinted with color, inverted like vanilla when asked. */
    private static void sprite(GuiGraphics graphics, boolean invert, int color, int x, int y, int width, int height, int u, int v, int uw, int vh) {
        RenderSystem.enableBlend();
        if (invert) RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        else RenderSystem.defaultBlendFunc();
        graphics.setColor((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f);
        graphics.blit(ICONS, x, y, width, height, u, v, uw, vh, 256, 256);
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
    }

    /** Vanilla 1.20.1's own F3 crosshair axes (Gui.renderCrosshair's debug branch). */
    private static void vanillaAxes(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.mulPoseMatrix(graphics.pose().last().pose());
        poseStack.translate(graphics.guiWidth() / 2, graphics.guiHeight() / 2, 0.0F);
        poseStack.mulPose(Axis.XN.rotationDegrees(camera.getXRot()));
        poseStack.mulPose(Axis.YP.rotationDegrees(camera.getYRot()));
        poseStack.scale(-1.0F, -1.0F, -1.0F);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.renderCrosshair(10);
        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    /** Union prevents duplicate alpha blending at joined corners; outline is a one-pixel dilation. */
    static List<CrosshairDesign.Rect> merge(List<CrosshairDesign.Rect> rectangles, int border) {
        if (rectangles.isEmpty()) return List.of();
        int minX = 1000, minY = 1000, maxX = -1000, maxY = -1000;
        for (CrosshairDesign.Rect r : rectangles) {
            minX = Math.min(minX, r.left() - border); minY = Math.min(minY, r.top() - border);
            maxX = Math.max(maxX, r.right() + border); maxY = Math.max(maxY, r.bottom() + border);
        }
        boolean[][] pixels = new boolean[maxY - minY][maxX - minX];
        for (CrosshairDesign.Rect r : rectangles)
            for (int y = r.top() - border; y < r.bottom() + border; y++) for (int x = r.left() - border; x < r.right() + border; x++) pixels[y - minY][x - minX] = true;
        var result = new ArrayList<CrosshairDesign.Rect>();
        for (int y = 0; y < pixels.length; y++)
            for (int x = 0; x < pixels[y].length; ) {
                if (!pixels[y][x]) { x++; continue; }
                int start = x;
                while (x < pixels[y].length && pixels[y][x]) x++;
                result.add(new CrosshairDesign.Rect(start + minX, y + minY, x + minX, y + minY + 1));
            }
        return result;
    }
}
