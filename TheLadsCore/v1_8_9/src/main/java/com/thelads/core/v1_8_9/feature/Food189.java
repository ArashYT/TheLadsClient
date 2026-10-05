package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.FoodPreview;
import com.thelads.core.v1_8_9.mixin.FoodStatsAccessor;
import com.thelads.core.v1_8_9.mixin.ItemFoodFields;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemAppleGold;
import net.minecraft.item.ItemFishFood;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FoodStats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.EnumDifficulty;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * The AppleSkin module on 1.8.9, as 26.x NativeFood: through Forge's HUD events it draws what the held food would restore (hunger,
 * saturation, health, pulsing), saturation as gold outlines on the hunger icons and exhaustion as a band behind them; it adds the
 * food line to tooltips and an F3 line. GuiIngameForgeMixin reports where Forge drew each icon, so previews follow its shake.
 * Singleplayer and LAN hosts read the integrated server's own player (exact); other servers send saturation only with a health or
 * hunger change and never exhaustion, so there exhaustion is not drawn. 1.8.9 has no off hand and no images in tooltips (the
 * tooltip is the text line EnhancedTooltips uses), and heals 1 health per 3 exhaustion with no fast healing.
 */
public final class Food189 {
    private static final String MODULE = "AppleSkin";
    private static final ResourceLocation SATURATION = new ResourceLocation("theladscore", "textures/hud/food_saturation.png");
    /** QA only: a fixed preview opacity in place of the pulse; frames that drew the saturation outlines. */
    static Float qaPulse;
    static long frames;
    private static int foodRight, heartsTop, heartsRowHeight, heartsLeft, lastHeartX, lastHeartY;
    private static final int[] foodY = new int[10];
    private static int[] heartX = new int[0], heartY = new int[0];

    static boolean on() { return Options189.enabled(MODULE); }
    private static boolean option(String name) { return Options189.bool(MODULE, name, true); }

    /** The integrated server's copy of the local player (singleplayer, LAN host); null on other servers. */
    static EntityPlayerMP serverPlayer(EntityPlayer player) {
        MinecraftServer server = Minecraft.getMinecraft().getIntegratedServer();
        try { return server == null ? null : server.getConfigurationManager().getPlayerByUUID(player.getUniqueID()); }
        catch (RuntimeException joiningOrLeaving) { return null; } // the server thread changed its player list meanwhile
    }
    static float exhaustion(EntityPlayerMP own) { return own == null ? 0 : ((FoodStatsAccessor) own.getFoodStats()).ladsExhaustion(); }
    private static FoodStats food(EntityPlayer player, EntityPlayerMP own) { return own != null ? own.getFoodStats() : player.getFoodStats(); }

    /** The held food if it can be eaten now; null otherwise. */
    static ItemStack heldFood(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        return held != null && held.getItem() instanceof ItemFood && player.canEat(((ItemFoodFields) held.getItem()).ladsAlwaysEdible()) ? held : null;
    }
    /** Food with a harmful effect (rotten flesh, spider eyes, pufferfish) shows the hunger effect's green icons. */
    static boolean harmful(ItemStack stack) {
        int id = ((ItemFoodFields) stack.getItem()).ladsPotionId();
        return id > 0 && id < Potion.potionTypes.length && Potion.potionTypes[id] != null && Potion.potionTypes[id].isBadEffect()
            || stack.getItem() instanceof ItemFishFood && ItemFishFood.FishType.byItemStack(stack) == ItemFishFood.FishType.PUFFERFISH;
    }
    /** Health the food's own Regeneration gives; the enchanted golden apple's Regeneration V comes from ItemAppleGold, not its fields. */
    static float regenerationEffect(ItemStack stack) {
        if (stack.getItem() instanceof ItemAppleGold && stack.getMetadata() > 0) return FoodPreview.regenerationEffect(600, 4);
        ItemFoodFields fields = (ItemFoodFields) stack.getItem();
        return fields.ladsPotionId() == Potion.regeneration.id ? FoodPreview.regenerationEffect(fields.ladsPotionDuration() * 20, fields.ladsPotionAmplifier()) : 0;
    }

    @SubscribeEvent
    public void overlayStart(RenderGameOverlayEvent.Pre event) {
        int width = event.resolution.getScaledWidth(), height = event.resolution.getScaledHeight();
        if (event.type == ElementType.HEALTH) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            float hearts = (player.getMaxHealth() + player.getAbsorptionAmount()) / 2;
            heartsTop = height - GuiIngameForge.left_height;
            heartsRowHeight = Math.max(10 - ((int) Math.ceil(hearts / 10) - 2), 3);
            int total = (int) Math.ceil(hearts);
            heartsLeft = total > 0 && total <= 4096 ? total : 0;
            lastHeartX = lastHeartY = Integer.MIN_VALUE;
            if (heartX.length < heartsLeft) { heartX = new int[heartsLeft]; heartY = new int[heartsLeft]; }
        } else if (event.type == ElementType.FOOD) {
            foodRight = width / 2 + 91;
            int top = height - GuiIngameForge.right_height;
            Arrays.fill(foodY, top);
            EntityPlayerMP own = on() && option("Show Exhaustion") ? serverPlayer(Minecraft.getMinecraft().thePlayer) : null;
            int band = own == null ? 0 : Math.round(81 * Math.max(0, Math.min(exhaustion(own), FoodPreview.MAX_EXHAUSTION)) / FoodPreview.MAX_EXHAUSTION);
            if (band > 0) {
                // No depth write: the hunger icons Forge draws next (at the HUD's lower z) must still show through it.
                GlStateManager.depthMask(false);
                Gui.drawRect(foodRight - band, top, foodRight, top + 9, 0x50FFFFFF);
                GlStateManager.depthMask(true);
                GlStateManager.color(1, 1, 1, 1);
            }
        } else if (event.type == ElementType.TEXT && Minecraft.getMinecraft().gameSettings.showDebugInfo && on()) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            EntityPlayerMP own = serverPlayer(player);
            FoodStats stats = food(player, own);
            ((RenderGameOverlayEvent.Text) event).left.add(FoodPreview.debugLine(stats.getFoodLevel(), stats.getSaturationLevel(), exhaustion(own), own != null));
        }
    }

    /** GuiIngameForgeMixin: each heart icon renderHealth draws; a new position is the next heart, from the last index down. */
    public static void heartIcon(int x, int y) {
        if (x == lastHeartX && y == lastHeartY || heartsLeft <= 0) return;
        lastHeartX = x;
        lastHeartY = y;
        int index = --heartsLeft;
        heartX[index] = x;
        heartY[index] = option("Vanilla Animations") ? y : heartsTop - index / 10 * heartsRowHeight;
    }

    /** GuiIngameForgeMixin: each hunger icon renderFood draws, at the height it shakes to while saturation is empty. */
    public static void foodIcon(int x, int y) {
        int slot = (foodRight - 9 - x) / 8;
        if (slot >= 0 && slot < 10 && option("Vanilla Animations")) foodY[slot] = y;
    }

    @SubscribeEvent
    public void overlayEnd(RenderGameOverlayEvent.Post event) {
        if (!on() || event.type != ElementType.FOOD && event.type != ElementType.HEALTH) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        EntityPlayerMP own = serverPlayer(player);
        FoodStats stats = food(player, own);
        ItemStack held = heldFood(player);
        ItemFood food = held == null ? null : (ItemFood) held.getItem();
        int alpha = previewAlpha();
        if (event.type == ElementType.FOOD) {
            boolean outlines = option("Show Saturation");
            if (outlines) { outline(0, stats.getSaturationLevel(), 255); frames++; }
            if (food == null || !option("Show Food Values") || alpha == 0) return;
            int level = stats.getFoodLevel(), after = FoodPreview.foodAfter(level, food.getHealAmount(held));
            int u = harmful(held) ? 88 : 52; // icons.png row 27: full shank 52, half 61; the hunger effect's 88 and 97
            for (int slot = level / 2; slot < (after + 1) / 2; slot++)
                icon(Gui.icons, alpha, foodRight - slot * 8 - 9, foodY[slot], u + (slot * 2 + 1 == after ? 9 : 0), 27, 9, 256);
            float gained = food.getHealAmount(held) * food.getSaturationModifier(held) * 2;
            if (outlines && option("Show Saturation Overlay"))
                outline(stats.getSaturationLevel(), FoodPreview.saturationAfter(stats.getSaturationLevel(), gained, after), alpha);
        } else if (food != null && option("Show Health Overlay") && heartsLeft == 0 && alpha > 0 && player.worldObj.getDifficulty() != EnumDifficulty.PEACEFUL) {
            int level = FoodPreview.foodAfter(stats.getFoodLevel(), food.getHealAmount(held));
            float saturation = FoodPreview.saturationAfter(stats.getSaturationLevel(), food.getHealAmount(held) * food.getSaturationModifier(held) * 2, level);
            boolean regeneration = own == null || own.worldObj.getGameRules().getBoolean("naturalRegeneration");
            float health = player.getHealth(), max = player.getMaxHealth();
            float gain = (regeneration ? FoodPreview.regenerated(level, saturation, exhaustion(own), max - health, true) : 0) + regenerationEffect(held);
            int current = (int) Math.ceil(health), after = (int) Math.ceil(Math.min(max, health + gain));
            int hearts = Math.min((int) Math.ceil(max / 2), heartX.length);
            int v = mc.theWorld.getWorldInfo().isHardcoreModeEnabled() ? 45 : 0; // icons.png hearts: full 52, half 61; hardcore 45 lower
            for (int i = Math.max(0, current / 2); i < Math.min((after + 1) / 2, hearts); i++)
                icon(Gui.icons, alpha, heartX[i], heartY[i], i * 2 + 1 == after ? 61 : 52, v, 9, 256);
        }
    }

    @SubscribeEvent
    public void tooltip(ItemTooltipEvent event) {
        String line = event.itemStack == null ? null : Tooltips189.foodLine(event.itemStack);
        if (line == null || !on() || !option("Food Tooltips") || !option("Tooltips Always Visible") && !GuiScreen.isShiftKeyDown()) return;
        // EnhancedTooltips' food line already says it.
        if (Options189.enabled("EnhancedTooltips") && Options189.bool("EnhancedTooltips", "Show Food Values", true)) return;
        event.toolTip.add(line);
    }

    private static int previewAlpha() {
        float pulse = qaPulse != null ? qaPulse : FoodPreview.pulse(System.nanoTime() / 1_000_000L % 1200 / 50f);
        return Math.round(255 * pulse * (float) Math.max(0, Math.min(1, Options189.number(MODULE, "Overlay Opacity", 65) / 100)));
    }

    /** Gold outlines for saturation {@code from} to {@code to}; a part-filled icon shows its right side, like the half shank. */
    private static void outline(float from, float to, int alpha) {
        for (int slot = 0; slot < 10; slot++) {
            int start = Math.round(9 * FoodPreview.iconFill(from, slot)), end = Math.round(9 * FoodPreview.iconFill(to, slot));
            if (end > start) icon(SATURATION, alpha, foodRight - slot * 8 - end, foodY[slot], 9 - end, 0, end - start, 9);
        }
    }

    /** One icon part from a {@code size}-pixel square texture, then the HUD's state back: icons.png bound, white, blending as found. */
    private static void icon(ResourceLocation texture, int alpha, int x, int y, int u, int v, int width, int size) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean blend = GlState189.blend();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.color(1, 1, 1, alpha / 255f);
        mc.getTextureManager().bindTexture(texture);
        Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, width, 9, size, size);
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(Gui.icons);
        if (!blend) GlStateManager.disableBlend();
    }
}
