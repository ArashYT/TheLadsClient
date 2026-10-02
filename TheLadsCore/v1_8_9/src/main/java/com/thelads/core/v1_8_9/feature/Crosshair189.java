package com.thelads.core.v1_8_9.feature;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.CrosshairDesign;
import com.thelads.core.client.CrosshairDrawing;
import com.thelads.core.modules.CrosshairModule;
import com.thelads.core.v1_8_9.gui.CrosshairDrawingScreen189;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11;

/**
 * Crosshair Tweaks on 1.8.9, as 26.x NativeCrosshair: drawn in place of vanilla's through Forge's crosshair overlay event (and
 * after the frame while F1 hides the HUD); EntityRendererMixin hides F3's 3D crosshair by the same rules. It reads local aiming
 * state only. 1.8.9 has no attack cooldown, attack indicator, item cooldowns, offhand or spyglass.
 */
public final class Crosshair189 {
    private static final String MODULE = "Crosshair Tweaks";
    private static CrosshairDrawing drawing = defaultDrawing();
    private static String geometryKey = "";
    private static List<CrosshairDesign.Rect> geometry = Collections.emptyList(), outline = Collections.emptyList();
    /** QA only (Probe150): crosshairs drawn. */
    public static long frames;

    public static CrosshairModule module() {
        return Options189.module(MODULE) instanceof CrosshairModule ? (CrosshairModule) Options189.module(MODULE) : null;
    }
    public static boolean active() { return Options189.enabled(MODULE); }
    public static boolean flag(String name) { return Options189.bool(MODULE, name, false); }
    public static double number(String name) { return Options189.number(MODULE, name, 0); }
    private static int color(String name) { return Options189.color(MODULE, name, -1); }

    public static void register() {
        CrosshairModule module = module();
        if (module == null) return;
        drawing = load(defaultDrawing());
        module.drawingEditor.setAction(() -> Minecraft.getMinecraft().displayGuiScreen(new CrosshairDrawingScreen189(Minecraft.getMinecraft().currentScreen)));
    }

    public static CrosshairDrawing drawing() { return drawing.copy(); }
    public static void commitDrawing(CrosshairDrawing next) throws IOException { save(next); drawing = next.copy(); geometryKey = ""; }

    private static CrosshairDrawing defaultDrawing() {
        CrosshairDrawing value = new CrosshairDrawing(17, 17);
        for (CrosshairDesign.Rect r : CrosshairDesign.geometry(0, 5, 5, 2, 1))
            for (int y = r.top(); y < r.bottom(); y++) for (int x = r.left(); x < r.right(); x++) value.set(x + 8, y + 8, true);
        return value;
    }

    /** As vanilla's crosshair: F3 without reduced debug info shows the 3D one instead. */
    private static boolean debug(Minecraft mc) {
        return mc.gameSettings.showDebugInfo && !mc.thePlayer.hasReducedDebug() && !mc.gameSettings.reducedDebugInfo;
    }

    public static CrosshairDesign.Visibility context(Minecraft mc) {
        ItemStack held = mc.thePlayer.getHeldItem();
        MovingObjectPosition hit = mc.objectMouseOver;
        boolean spectatorTarget = mc.pointedEntity != null || hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && mc.theWorld.getTileEntity(hit.getBlockPos()) instanceof IInventory;
        return new CrosshairDesign.Visibility(mc.gameSettings.thirdPersonView != 0, mc.playerController.isSpectator(), spectatorTarget,
            mc.gameSettings.hideGUI, debug(mc), mc.currentScreen instanceof GuiContainer, false, held != null && held.getItem() instanceof ItemBow, throwable(held));
    }

    public static CrosshairDesign.Rules rules() {
        return new CrosshairDesign.Rules(flag("Show in Third Person"), flag("Show in Spectator"), flag("Visible with Hidden HUD"), flag("Visible with Debug"),
            flag("Hide in Containers"), flag("Visible Normally"), flag("Visible Using Spyglass"), flag("Visible Holding Ranged Weapon"), flag("Visible Holding Throwable"));
    }

    /** EntityRendererMixin: F3's world axes go when the module hides them, or hides the crosshair altogether. */
    public static boolean suppressDebugAxes() {
        Minecraft mc = Minecraft.getMinecraft();
        return active() && mc.thePlayer != null && (!flag("Keep Vanilla Debug") || flag("Disable Crosshair") || !CrosshairDesign.visible(context(mc), rules()));
    }

    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS || !active()) return;
        event.setCanceled(true);
        render(event.resolution.getScaledWidth(), event.resolution.getScaledHeight(), event.partialTicks);
        GlStateManager.disableBlend(); // as vanilla's crosshair leaves it
    }

    /** F1 skips the whole overlay; Visible with Hidden HUD draws the crosshair after the world instead. */
    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || !mc.gameSettings.hideGUI || mc.currentScreen != null || mc.theWorld == null || !active()) return;
        mc.entityRenderer.setupOverlayRendering();
        ScaledResolution resolution = new ScaledResolution(mc);
        render(resolution.getScaledWidth(), resolution.getScaledHeight(), event.renderTickTime);
    }

    private static void render(int width, int height, float partial) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        CrosshairDesign.Visibility context = context(mc);
        if (!CrosshairDesign.visible(context, rules())) return;
        double alpha = number(context.thirdPerson() ? "Opacity Third Person" : "Opacity First Person") / 100;
        boolean axes = context.debug() && !mc.gameSettings.hideGUI && flag("Keep Vanilla Debug");
        if (flag("Disable Crosshair") || axes) return;
        int cx = width / 2 + (int) number("Offset X"), cy = height / 2 + (int) number("Offset Y");
        double gap = CrosshairDesign.gap(number("Gap"), flag("Dynamic Attack Gap"), 1, flag("Dynamic Bow Gap"), chargeProgress(mc.thePlayer, partial));
        draw(cx, cy, gap, alpha, context.thirdPerson(), null);
        indicators(mc, cx, cy, alpha);
        frames++;
    }

    public static void draw(int cx, int cy, double gap, double opacity, boolean third, CrosshairDrawing preview) {
        CrosshairModule module = module();
        if (module == null) return;
        int shape = preview != null ? 8 : Options189.choice(MODULE, "Shape", 0), main = targetColor();
        boolean invert = (flag("Adaptive Color") || shape == 3) && !flag(third ? "Remove Blend Third Person" : "Remove Blend First Person");
        main = CrosshairDesign.tint(main, opacity, invert);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(cx, cy, 0.0F);
            GlStateManager.rotate((float) number("Rotation"), 0.0F, 0.0F, 1.0F);
            GlStateManager.scale((float) module.scale.get(), (float) module.scale.get(), 1.0F);
            if (shape == 3) {
                int width = (int) number("Width") * 2 + 5, height = (int) number("Height") * 2 + 5;
                Minecraft.getMinecraft().getTextureManager().bindTexture(Gui.icons);
                blend(invert);
                color(main);
                Gui.drawScaledCustomSizeModalRect(-width / 2, -height / 2, 0, 0, 15, 15, width, height, 256, 256);
                done();
            } else if (shape == 7) debugShape(opacity);
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
                if (flag("Outline")) fill(edge, CrosshairDesign.alpha(color("Outline Color"), opacity), false);
                fill(parts, main, invert);
            }
            if (flag("Center Dot")) {
                int t = Math.max(1, (int) Math.round(module.thickness.get()));
                fill(Collections.singletonList(new CrosshairDesign.Rect(-t / 2, -t / 2, t - t / 2, t - t / 2)), CrosshairDesign.alpha(color("Dot Color"), opacity), false);
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    static int targetColor() {
        int base = color("Color");
        if (flag("Rainbow")) {
            float hue = (float) ((System.nanoTime() / 1e9 * number("Rainbow Speed") / 6 + number("Rainbow Phase") / 360) % 1);
            base = (base & 0xff000000) | (Color.HSBtoRGB(hue, 1, 1) & 0xffffff);
        }
        Entity target = Minecraft.getMinecraft().pointedEntity;
        if (target instanceof EntityPlayer && flag("Highlight Players")) return color("Player Color");
        if (target instanceof IMob && flag("Highlight Hostiles")) return color("Hostile Color");
        if (target instanceof EntityAnimal && flag("Highlight Passives")) return color("Passive Color");
        return base;
    }

    private static void debugShape(double opacity) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        double yaw = player == null ? 0 : Math.toRadians(player.rotationYaw), pitch = player == null ? 0 : Math.toRadians(player.rotationPitch);
        double[][] axes = {{Math.cos(yaw), Math.sin(yaw) * Math.sin(pitch)}, {0, -Math.cos(pitch)}, {-Math.sin(yaw), Math.cos(yaw) * Math.sin(pitch)}};
        int[] colors = {0xffff5555, 0xff55ff55, 0xff5555ff};
        for (int i = 0; i < 3; i++) {
            List<CrosshairDesign.Rect> parts = new ArrayList<>();
            CrosshairDesign.line(parts, 0, 0, (int) Math.round(axes[i][0] * (number("Width") + number("Gap"))), (int) Math.round(axes[i][1] * (number("Height") + number("Gap"))),
                Math.max(1, (int) Math.round(module().thickness.get())));
            fill(merge(parts, 0), CrosshairDesign.alpha(colors[i], opacity), false);
        }
    }

    private static void indicators(Minecraft mc, int cx, int cy, double opacity) {
        if (opacity <= 0) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        int x = cx + 14, y = cy + 18;
        if (flag("Tool Durability Indicator") && held != null && held.isItemStackDamageable()) {
            item(held, x, y);
            mc.fontRendererObj.drawStringWithShadow(Integer.toString(Math.max(0, held.getMaxDamage() - held.getItemDamage())), x + 18, y + 4, CrosshairDesign.alpha(0xffffffff, opacity));
            y += 18;
        }
        Ammo ammo = flag("Projectile Indicator") ? projectiles(mc.thePlayer, held) : null;
        if (ammo != null) {
            item(ammo.icon, x, y);
            mc.fontRendererObj.drawStringWithShadow(ammo.infinite ? "∞" : Integer.toString(ammo.count), x + 18, y + 4, CrosshairDesign.alpha(0xffffffff, opacity));
        }
    }

    public static final class Ammo {
        public final ItemStack icon;
        public final int count;
        public final boolean infinite;
        Ammo(ItemStack icon, int count, boolean infinite) { this.icon = icon; this.count = count; this.infinite = infinite; }
    }

    /** A bow's arrows (Infinity or creative: endless), or a throwable's own stack count. */
    public static Ammo projectiles(EntityPlayer player, ItemStack held) {
        if (player == null || held == null) return null;
        if (held.getItem() instanceof ItemBow) {
            int count = 0;
            for (ItemStack stack : player.inventory.mainInventory) if (stack != null && stack.getItem() == Items.arrow) count += stack.stackSize;
            boolean infinite = player.capabilities.isCreativeMode || EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, held) > 0;
            return new Ammo(new ItemStack(Items.arrow), count, infinite);
        }
        if (!throwable(held)) return null;
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory)
            if (stack != null && stack.getItem() == held.getItem() && (stack.getItem() != Items.potionitem || ItemPotion.isSplash(stack.getMetadata()))) count += stack.stackSize;
        return new Ammo(held, count, player.capabilities.isCreativeMode);
    }

    private static double chargeProgress(EntityPlayer player, float partial) {
        ItemStack using = player.getItemInUse();
        return player.isUsingItem() && using != null && using.getItem() instanceof ItemBow ? Math.min(1, (player.getItemInUseDuration() + partial) / 20) : -1;
    }

    private static boolean throwable(ItemStack item) {
        return item != null && (item.getItem() == Items.snowball || item.getItem() == Items.egg || item.getItem() == Items.ender_pearl
            || item.getItem() == Items.ender_eye || item.getItem() == Items.experience_bottle || item.getItem() == Items.potionitem && ItemPotion.isSplash(item.getMetadata()));
    }

    private static void item(ItemStack stack, int x, int y) {
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        if (!depth) GlStateManager.disableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Vanilla's crosshair blend (inverting what is behind it), or plain alpha blending. */
    private static void blend(boolean invert) {
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        if (invert) GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, 1, 0);
        else GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
    }

    private static void color(int argb) {
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    /** Back to the overlay's state: alpha blending, alpha test on, white. */
    private static void done() {
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void fill(List<CrosshairDesign.Rect> rectangles, int argb, boolean invert) {
        if ((argb >>> 24) == 0 || rectangles.isEmpty()) return;
        blend(invert);
        GlStateManager.disableTexture2D();
        color(argb);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        for (CrosshairDesign.Rect r : rectangles) {
            buffer.pos(r.left(), r.bottom(), 0).endVertex();
            buffer.pos(r.right(), r.bottom(), 0).endVertex();
            buffer.pos(r.right(), r.top(), 0).endVertex();
            buffer.pos(r.left(), r.top(), 0).endVertex();
        }
        tessellator.draw();
        done();
    }

    /** Union prevents duplicate alpha blending at joined corners; outline is a one-pixel dilation. */
    private static List<CrosshairDesign.Rect> merge(List<CrosshairDesign.Rect> rectangles, int border) {
        if (rectangles.isEmpty()) return Collections.emptyList();
        int minX = 1000, minY = 1000, maxX = -1000, maxY = -1000;
        for (CrosshairDesign.Rect r : rectangles) {
            minX = Math.min(minX, r.left() - border); minY = Math.min(minY, r.top() - border);
            maxX = Math.max(maxX, r.right() + border); maxY = Math.max(maxY, r.bottom() + border);
        }
        boolean[][] pixels = new boolean[maxY - minY][maxX - minX];
        for (CrosshairDesign.Rect r : rectangles)
            for (int y = r.top() - border; y < r.bottom() + border; y++) for (int x = r.left() - border; x < r.right() + border; x++) pixels[y - minY][x - minX] = true;
        List<CrosshairDesign.Rect> result = new ArrayList<>();
        for (int y = 0; y < pixels.length; y++)
            for (int x = 0; x < pixels[y].length;) {
                if (!pixels[y][x]) { x++; continue; }
                int start = x;
                while (x < pixels[y].length && pixels[y][x]) x++;
                result.add(new CrosshairDesign.Rect(start + minX, y + minY, x + minX, y + minY + 1));
            }
        return result;
    }

    private static File file() { return new File(Loader.instance().getConfigDir(), "thelads/crosshair-drawing.json"); }

    /** The drawing as 26.x CrosshairDrawingStore keeps it: {width, height, rows ("#" set, "." clear)}. */
    private static CrosshairDrawing load(CrosshairDrawing fallback) {
        try {
            File file = file();
            if (!file.isFile() || file.length() > 16384) return fallback;
            JsonObject data = new JsonParser().parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> rows = new ArrayList<>();
            for (JsonElement row : data.getAsJsonArray("rows")) rows.add(row.getAsString());
            return CrosshairDrawing.fromRows(data.get("width").getAsInt(), data.get("height").getAsInt(), rows);
        } catch (Exception failure) {
            LogManager.getLogger("TheLadsCore").warn("Could not read crosshair drawing; using default", failure);
            return fallback;
        }
    }

    private static void save(CrosshairDrawing drawing) throws IOException {
        File file = file();
        Files.createDirectories(file.getParentFile().toPath());
        JsonObject json = new JsonObject();
        json.addProperty("width", drawing.width());
        json.addProperty("height", drawing.height());
        JsonArray rows = new JsonArray();
        for (String row : drawing.rows()) rows.add(new com.google.gson.JsonPrimitive(row));
        json.add("rows", rows);
        java.nio.file.Path temp = Files.createTempFile(file.getParentFile().toPath(), "crosshair-", ".tmp");
        try {
            Files.write(temp, new Gson().toJson(json).getBytes(StandardCharsets.UTF_8));
            try { Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
