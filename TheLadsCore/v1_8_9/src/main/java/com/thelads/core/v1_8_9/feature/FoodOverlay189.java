// Adapted from AppleSkin 3.0.10 (Unlicense), commit 62513191f6a3497447595c2215d466ad1d2bdb92, as 26.x feature/food.
// Built into The Lads Core; see assets/theladscore/licenses/AppleSkin-Unlicense.txt.
package com.thelads.core.v1_8_9.feature;

import com.thelads.core.v1_8_9.mixin.ItemFoodAccessor;
import java.text.DecimalFormat;
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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FoodStats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.EnumDifficulty;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

/**
 * AppleSkin on 1.8.9 through Forge's HUD events: saturation over the hunger bar, the exhaustion underlay, and the hunger,
 * saturation and health a held food restores, flashing. 26.x syncs saturation and exhaustion with AppleSkin packets; 1.8.9
 * sends saturation only with a hunger change and never exhaustion, so in singleplayer and hosted LAN worlds both are read
 * from the integrated server (this JVM), and a remote server's worlds show what 1.8.9 sends (no exhaustion), as 26.x does
 * there. 1.8.9 has no off-hand ("Offhand Food") and no images in tooltips: the food tooltip is the text line
 * EnhancedTooltips uses. Health estimates follow 1.8.9's regeneration (1 health per 3 exhaustion at 18 hunger or more).
 */
public final class FoodOverlay189 {
    private static final ResourceLocation MOD_ICONS = new ResourceLocation("theladscore", "textures/food/icons.png");
    private static final float MAX_EXHAUSTION = 4.0f, REGEN_EXHAUSTION = 3.0f;
    private static final DecimalFormat SATURATION = new DecimalFormat("#.##"), EXHAUSTION = new DecimalFormat("0.00");
    /** The host's food data, written on the integrated server thread (26.x: SyncHandler) and read on the client thread. */
    private static volatile float hostSaturation, hostExhaustion;
    private static volatile boolean hostRegeneration = true, hostSynced, enabled;
    /** Client copies: exhaustion is 0 and regeneration on where the server sends neither (26.x: ClientSyncHandler defaults). */
    static float exhaustion;
    static boolean synced, naturalRegeneration = true;
    /** QA only: frames that drew the saturation overlay. */
    static long frames;
    private float unclampedFlashAlpha, flashAlpha;
    private byte alphaDir = 1;
    private int foodTop, healthTop, offsetsTick = Integer.MIN_VALUE;
    private final int[][] foodOffsets = new int[10][2];
    private int[][] healthOffsets = new int[0][2];
    private final java.util.Random random = new java.util.Random();

    @SubscribeEvent
    public void serverTick(TickEvent.PlayerTickEvent event) {
        if (!enabled || event.side != Side.SERVER || event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || !event.player.getName().equals(server.getServerOwner())) return;
        NBTTagCompound food = new NBTTagCompound();
        event.player.getFoodStats().writeNBT(food); // the one public read of FoodStats' exhaustion
        hostSaturation = food.getFloat("foodSaturationLevel");
        hostExhaustion = food.getFloat("foodExhaustionLevel");
        hostRegeneration = event.player.worldObj.getGameRules().getBoolean("naturalRegeneration");
        hostSynced = true;
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        enabled = Options189.enabled("AppleSkin");
        if (!mc.isSingleplayer()) hostSynced = false;
        synced = hostSynced && mc.thePlayer != null;
        if (synced) {
            if (valid(hostSaturation, 20)) mc.thePlayer.getFoodStats().setFoodSaturationLevel(hostSaturation);
            exhaustion = valid(hostExhaustion, 40) ? hostExhaustion : 0;
            naturalRegeneration = hostRegeneration;
        } else {
            exhaustion = 0;
            naturalRegeneration = true;
        }
        unclampedFlashAlpha += alphaDir * 0.125f;
        if (unclampedFlashAlpha >= 1.5f) alphaDir = -1;
        else if (unclampedFlashAlpha <= -0.5f) alphaDir = 1;
        flashAlpha = clamp01(unclampedFlashAlpha) * clamp01((float) Options189.number("AppleSkin", "Overlay Opacity", 65) / 100);
    }

    @SubscribeEvent
    public void overlayStart(RenderGameOverlayEvent.Pre event) {
        if (!enabled) return;
        int width = event.resolution.getScaledWidth(), height = event.resolution.getScaledHeight();
        if (event.type == RenderGameOverlayEvent.ElementType.HEALTH) healthTop = height - GuiIngameForge.left_height;
        else if (event.type == RenderGameOverlayEvent.ElementType.FOOD) {
            foodTop = height - GuiIngameForge.right_height;
            if (option("Show Exhaustion")) {
                int bar = (int) (clamp01(exhaustion / MAX_EXHAUSTION) * 81);
                draw(MOD_ICONS, 0.75f, width / 2 + 91 - bar, foodTop, 81 - bar, 18, bar);
            }
        } else if (event.type == RenderGameOverlayEvent.ElementType.TEXT && Minecraft.getMinecraft().gameSettings.showDebugInfo) {
            FoodStats stats = Minecraft.getMinecraft().thePlayer.getFoodStats();
            ((RenderGameOverlayEvent.Text) event).left.add("hunger: " + stats.getFoodLevel() + ", sat: " + (synced ? "" : "~")
                + SATURATION.format(stats.getSaturationLevel()) + ", exh: " + (synced ? "" : "~") + EXHAUSTION.format(exhaustion) + "/4");
        }
    }

    @SubscribeEvent
    public void overlayEnd(RenderGameOverlayEvent.Post event) {
        if (!enabled || !(event.type == RenderGameOverlayEvent.ElementType.FOOD || event.type == RenderGameOverlayEvent.ElementType.HEALTH)) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        boolean saturation = option("Show Saturation"), values = option("Show Food Values"), health = option("Show Health Overlay");
        if (player == null || !(saturation || values || health)) return;
        offsets(player, mc.ingameGUI.getUpdateCounter());
        FoodStats stats = player.getFoodStats();
        ItemStack held = player.getHeldItem();
        ItemFood food = held != null && held.getItem() instanceof ItemFood ? (ItemFood) held.getItem() : null;
        if (food != null && !player.canEat(((ItemFoodAccessor) food).ladsAlwaysEdible())) food = null;
        if (food == null) resetFlash();
        int center = event.resolution.getScaledWidth() / 2;
        if (event.type == RenderGameOverlayEvent.ElementType.FOOD) {
            if (saturation) { drawSaturation(0, stats.getSaturationLevel(), center + 91, 1); frames++; }
            if (food == null || !values) return;
            int hunger = food.getHealAmount(held);
            drawHunger(hunger, stats.getFoodLevel(), center + 91, rotten(food, held));
            int newFood = stats.getFoodLevel() + hunger;
            float gain = hunger * food.getSaturationModifier(held) * 2, newSaturation = stats.getSaturationLevel() + gain;
            if (saturation && option("Show Saturation Overlay"))
                drawSaturation(newSaturation > newFood ? newFood - stats.getSaturationLevel() : gain, stats.getSaturationLevel(), center + 91, flashAlpha);
        } else if (food != null && health && showEstimatedHealth(player, stats)) {
            float current = player.getHealth(), modified = Math.min(current + healthIncrement(player, held, food), player.getMaxHealth());
            if (current < modified) drawHealth(current, modified, center - 91);
        }
    }

    @SubscribeEvent
    public void tooltip(ItemTooltipEvent event) {
        String line = event.itemStack == null ? null : Tooltips189.foodLine(event.itemStack);
        if (line == null || !Options189.enabled("AppleSkin") || !option("Food Tooltips")) return;
        if (!option("Tooltips Always Visible") && !GuiScreen.isShiftKeyDown()) return;
        // EnhancedTooltips' food line already says it.
        if (Options189.enabled("EnhancedTooltips") && Options189.bool("EnhancedTooltips", "Show Food Values", true)) return;
        event.toolTip.add(line);
    }

    private void drawSaturation(float gained, float level, int right, float alpha) {
        if (level + gained < 0) return;
        float modified = Math.max(0, Math.min(level + gained, 20));
        // Gained saturation starts at the current saturation's last icon.
        int start = gained != 0 ? (int) Math.max(level / 2, 0) : 0, end = (int) Math.ceil(modified / 2);
        for (int i = start; i < end && i < 10; i++) {
            float bar = modified / 2 - i;
            draw(MOD_ICONS, alpha, right + foodOffsets[i][0], foodTop + foodOffsets[i][1], bar >= 1 ? 27 : bar > .5f ? 18 : bar > .25f ? 9 : 0, 0, 9);
        }
    }

    /** 1.8.9's icons.png food row (y 27): background 16, full 52, half 61; the hunger effect's green set at 133, 88 and 97. */
    private void drawHunger(int restored, int level, int right, boolean rotten) {
        if (restored <= 0) return;
        int modified = Math.max(0, Math.min(20, level + restored));
        for (int i = Math.max(0, level / 2); i < (int) Math.ceil(modified / 2.0f) && i < 10; i++) {
            int x = right + foodOffsets[i][0], y = foodTop + foodOffsets[i][1];
            draw(Gui.icons, flashAlpha * 0.25f, x, y, rotten ? 133 : 16, 27, 9);
            draw(Gui.icons, flashAlpha, x, y, (rotten ? 88 : 52) + (i * 2 + 1 == modified ? 9 : 0), 27, 9);
        }
    }

    /** 1.8.9's icons.png hearts: container 16, full 52, half 61; the hardcore row is 45 below. */
    private void drawHealth(float health, float modified, int left) {
        int ceil = (int) Math.ceil(modified), v = Minecraft.getMinecraft().theWorld.getWorldInfo().isHardcoreModeEnabled() ? 45 : 0;
        for (int i = (int) Math.max(0, Math.ceil(health) / 2.0f); i < (int) Math.max(0, Math.ceil(modified / 2.0f)) && i < healthOffsets.length; i++) {
            int x = left + healthOffsets[i][0], y = healthTop + healthOffsets[i][1];
            draw(Gui.icons, flashAlpha * 0.25f, x, y, 16, v, 9);
            draw(Gui.icons, flashAlpha, x, y, i * 2 + 1 == ceil ? 61 : 52, v, 9);
        }
    }

    /** One icon (or the exhaustion bar, width wide), then 1.8.9's HUD state back: the icons texture bound, white, blending as found. */
    private static void draw(ResourceLocation texture, float alpha, int x, int y, int u, int v, int width) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);
        mc.getTextureManager().bindTexture(texture);
        mc.ingameGUI.drawTexturedModalRect(x, y, u, v, width, 9);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(Gui.icons);
        if (!blend) GlStateManager.disableBlend();
    }

    /**
     * Where 1.8.9 draws each heart and hunger icon relative to the bars' origin, shaking as GuiIngameForge does: hearts and hunger
     * share one random sequence seeded by the HUD tick, so both are generated together ("Vanilla Animations" off: no shaking).
     */
    private void offsets(EntityPlayer player, int ticks) {
        if (ticks == offsetsTick) return;
        offsetsTick = ticks;
        boolean animate = option("Vanilla Animations");
        int hearts = (int) Math.ceil((player.getMaxHealth() + player.getAbsorptionAmount()) / 2.0f);
        if (hearts < 0 || hearts > 1000) hearts = 0; // runaway health: no estimate, as 26.x
        if (healthOffsets.length != hearts) healthOffsets = new int[hearts][2];
        int rowHeight = Math.max(10 - ((int) Math.ceil(hearts / 10.0f) - 2), 3);
        random.setSeed((long) (ticks * 312871));
        boolean shakeHealth = animate && Math.ceil(player.getHealth()) <= 4;
        for (int i = hearts - 1; i >= 0; i--) {
            healthOffsets[i][0] = i % 10 * 8;
            healthOffsets[i][1] = -((int) Math.ceil((i + 1) / 10.0f) - 1) * rowHeight + (shakeHealth ? random.nextInt(2) : 0);
        }
        FoodStats stats = player.getFoodStats();
        boolean shakeFood = animate && stats.getSaturationLevel() <= 0 && ticks % (stats.getFoodLevel() * 3 + 1) == 0;
        for (int i = 0; i < 10; i++) {
            foodOffsets[i][0] = -(i * 8) - 9;
            foodOffsets[i][1] = shakeFood ? random.nextInt(3) - 1 : 0;
        }
    }

    private boolean showEstimatedHealth(EntityPlayer player, FoodStats stats) {
        return healthOffsets.length > 0 && player.worldObj.getDifficulty() != EnumDifficulty.PEACEFUL && stats.getFoodLevel() < 18
            && !player.isPotionActive(Potion.poison) && !player.isPotionActive(Potion.wither) && !player.isPotionActive(Potion.regeneration);
    }

    /** Health a food restores: natural regeneration at its hunger and saturation, and a regeneration effect it gives (golden apples). */
    static float healthIncrement(EntityPlayer player, ItemStack stack, ItemFood food) {
        if (!player.shouldHeal()) return 0;
        FoodStats stats = player.getFoodStats();
        int hunger = food.getHealAmount(stack), level = Math.min(stats.getFoodLevel() + hunger, 20);
        float health = 0;
        if (level >= 18 && naturalRegeneration)
            health = regeneration(level, Math.min(stats.getSaturationLevel() + hunger * food.getSaturationModifier(stack) * 2, level), exhaustion);
        ItemFoodAccessor effect = (ItemFoodAccessor) food;
        int id = effect.ladsPotionId(), duration = effect.ladsPotionDuration() * 20, amplifier = effect.ladsPotionAmplifier();
        // ItemAppleGold.onFoodEaten: the enchanted apple gives Regeneration V for 30 s instead of its item's effect.
        if (food instanceof ItemAppleGold && stack.getMetadata() > 0) { id = Potion.regeneration.id; duration = 600; amplifier = 4; }
        if (id == Potion.regeneration.id) health += (float) Math.floor(duration / Math.max(50 >> amplifier, 1));
        return health;
    }

    /** FoodStats.onUpdate's healing until hunger drops below 18: exhaustion over 4 costs saturation, then hunger. */
    static float regeneration(int level, float saturation, float exhaustion) {
        if (!Float.isFinite(saturation) || !Float.isFinite(exhaustion)) return 0;
        level = Math.max(0, Math.min(20, level));
        saturation = Math.max(0, Math.min(20, saturation));
        exhaustion = Math.max(0, Math.min(40, exhaustion));
        float health = 0;
        while (level >= 18) {
            while (exhaustion > MAX_EXHAUSTION) {
                exhaustion -= MAX_EXHAUSTION;
                if (saturation > 0) saturation = Math.max(saturation - 1, 0);
                else level--;
            }
            if (level >= 18) {
                health += 1;
                exhaustion += REGEN_EXHAUSTION;
            }
        }
        return health;
    }

    private static boolean rotten(ItemFood food, ItemStack stack) {
        int id = ((ItemFoodAccessor) food).ladsPotionId();
        return id > 0 && id < Potion.potionTypes.length && Potion.potionTypes[id] != null && Potion.potionTypes[id].isBadEffect()
            || food instanceof ItemFishFood && ItemFishFood.FishType.byItemStack(stack) == ItemFishFood.FishType.PUFFERFISH;
    }

    private void resetFlash() {
        unclampedFlashAlpha = flashAlpha = 0;
        alphaDir = 1;
    }

    private static boolean option(String name) { return Options189.bool("AppleSkin", name, true); }
    private static boolean valid(float value, float maximum) { return Float.isFinite(value) && value >= 0 && value <= maximum; }
    private static float clamp01(float value) { return Math.max(0, Math.min(1, value)); }
}
