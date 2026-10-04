package com.thelads.core.v26_2.feature.food;

import com.thelads.core.client.FoodPreview;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.food.mixin.FoodDataAccess;
import java.util.Arrays;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The AppleSkin module, written for Lads: what a held food would restore (hunger, saturation and health, pulsing), the current
 * saturation as gold outlines on the hunger icons, exhaustion as a band behind them, food values in tooltips and an F3 line.
 * Singleplayer and LAN hosts read the integrated server's own player, so every value is exact. Other servers send hunger, and
 * saturation only with a health or hunger change, never exhaustion: there saturation is the last value received, exhaustion
 * is not drawn and health estimates assume none. It never changes food, health or packets. A loaded AppleSkin jar keeps the job.
 */
public final class NativeFood {
    static final String MODULE = "AppleSkin";
    private static final Identifier GUI_ATLAS = Identifier.withDefaultNamespace("gui");
    private static final Identifier SATURATION = Identifier.fromNamespaceAndPath("theladscore", "hud/food_saturation");
    private static final Identifier FULL = Identifier.withDefaultNamespace("hud/food_full"), HALF = Identifier.withDefaultNamespace("hud/food_half");
    private static final Identifier ROTTEN_FULL = Identifier.withDefaultNamespace("hud/food_full_hunger");
    private static final Identifier ROTTEN_HALF = Identifier.withDefaultNamespace("hud/food_half_hunger");
    private static boolean active;
    /** QA only: a fixed preview opacity in place of the pulse. */
    public static Float qaPulse;
    // Where vanilla drew this frame's hunger icons (y by slot) and heart containers (by heart index): previews sit exactly on them.
    private static int foodRight, heartsTop, heartsRowHeight, heartsLeft;
    private static final int[] foodY = new int[10];
    private static int[] heartX = new int[0], heartY = new int[0];
    private NativeFood() {}

    public static void initialize() {
        if (active || FabricLoader.getInstance().isModLoaded("appleskin")) return;
        active = true;
        ModuleSupport.registerBuiltIn(MODULE);
        DebugScreenEntries.register(Identifier.fromNamespaceAndPath("theladscore", "food"), (lines, level, client, server) -> {
            var player = Minecraft.getInstance().player;
            if (!on() || player == null) return;
            ServerPlayer own = serverPlayer(player);
            FoodData data = own != null ? own.getFoodData() : player.getFoodData();
            lines.addLine(FoodPreview.debugLine(data.getFoodLevel(), data.getSaturationLevel(), exhaustion(own), own != null));
        });
    }

    public static boolean on() { return active && NativeQualityOfLife.enabled(MODULE); }
    static boolean option(String name) { return NativeQualityOfLife.bool(MODULE, name, true); }

    /** The integrated server's copy of the local player (singleplayer, LAN host); null on other servers. */
    static ServerPlayer serverPlayer(Player player) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? null : server.getPlayerList().getPlayer(player.getUUID());
    }
    static float exhaustion(ServerPlayer own) { return own == null ? 0 : ((FoodDataAccess) own.getFoodData()).lads$exhaustion(); }

    /** The food eating would use now: the main hand, else the off hand ("Offhand Food"); null when neither can be eaten. */
    static ItemStack heldFood(Player player) {
        if (edible(player, player.getMainHandItem())) return player.getMainHandItem();
        return option("Offhand Food") && edible(player, player.getOffhandItem()) ? player.getOffhandItem() : null;
    }
    private static boolean edible(Player player, ItemStack stack) {
        var food = stack.get(DataComponents.FOOD);
        return food != null && stack.has(DataComponents.CONSUMABLE) && player.canEat(food.canAlwaysEat());
    }

    /** Food that gives a harmful effect (rotten flesh, spider eyes) shows the hunger effect's green icons. */
    static boolean harmful(ItemStack stack) {
        var use = stack.get(DataComponents.CONSUMABLE);
        if (use != null) for (var effect : use.onConsumeEffects())
            if (effect instanceof ApplyStatusEffectsConsumeEffect apply)
                for (var instance : apply.effects()) if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) return true;
        return false;
    }
    /** Health a food's own Regeneration gives (golden apples), weighted by its chance. */
    static float regenerationEffect(ItemStack stack) {
        var use = stack.get(DataComponents.CONSUMABLE);
        float health = 0;
        if (use != null) for (var effect : use.onConsumeEffects())
            if (effect instanceof ApplyStatusEffectsConsumeEffect apply)
                for (var instance : apply.effects())
                    if (instance.is(MobEffects.REGENERATION)) health += FoodPreview.regenerationEffect(instance.getDuration(), instance.getAmplifier()) * apply.probability();
        return health;
    }

    private static int previewColor() {
        float pulse = qaPulse != null ? qaPulse : FoodPreview.pulse(System.nanoTime() / 1_000_000L % 1200 / 50f);
        return Math.round(255 * pulse * Mth.clamp((float) NativeQualityOfLife.number(MODULE, "Overlay Opacity", 65) / 100, 0, 1)) << 24 | 0xFFFFFF;
    }

    /** Hud.extractFood HEAD: the exhaustion band behind the hunger icons, where exhaustion is known. */
    public static void beforeFood(GuiGraphicsExtractor graphics, Player player, int top, int right) {
        foodRight = right;
        Arrays.fill(foodY, top);
        if (!on() || !option("Show Exhaustion")) return;
        ServerPlayer own = serverPlayer(player);
        if (own == null) return;
        int width = Math.round(81 * Mth.clamp(exhaustion(own), 0, FoodPreview.MAX_EXHAUSTION) / FoodPreview.MAX_EXHAUSTION);
        if (width > 0) graphics.fill(right - width, top, right, top + 9, 0x50FFFFFF);
    }

    /** Every hunger icon vanilla blits: its height, which shakes while saturation is empty ("Vanilla Animations" off: steady). */
    public static void foodIcon(int x, int y) {
        int slot = (foodRight - 9 - x) / 8;
        if (slot >= 0 && slot < 10 && option("Vanilla Animations")) foodY[slot] = y;
    }

    /** Hud.extractFood RETURN: saturation outlines, then what the held food would add. */
    public static void afterFood(GuiGraphicsExtractor graphics, Player player, int top, int right) {
        if (!on()) return;
        ServerPlayer own = serverPlayer(player);
        FoodData data = own != null ? own.getFoodData() : player.getFoodData();
        int food = data.getFoodLevel();
        float saturation = data.getSaturationLevel();
        boolean outlines = option("Show Saturation");
        if (outlines) outline(graphics, right, 0, saturation, 0xFFFFFFFF);
        ItemStack held = heldFood(player);
        if (held == null || !option("Show Food Values")) return;
        var values = held.get(DataComponents.FOOD);
        int after = FoodPreview.foodAfter(food, values.nutrition()), color = previewColor();
        if (color >>> 24 == 0) return;
        boolean rotten = harmful(held);
        for (int slot = food / 2; slot < (after + 1) / 2; slot++) {
            boolean half = slot * 2 + 1 == after;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, rotten ? (half ? ROTTEN_HALF : ROTTEN_FULL) : half ? HALF : FULL,
                right - slot * 8 - 9, foodY[slot], 9, 9, color);
        }
        if (outlines && option("Show Saturation Overlay"))
            outline(graphics, right, saturation, FoodPreview.saturationAfter(saturation, values.saturation(), after), color);
    }

    /** Gold outlines for saturation {@code from} to {@code to}; a part-filled icon shows its right side, like vanilla's half icon. */
    private static void outline(GuiGraphicsExtractor graphics, int right, float from, float to, int color) {
        for (int slot = 0; slot < 10; slot++) {
            int start = Math.round(9 * FoodPreview.iconFill(from, slot)), end = Math.round(9 * FoodPreview.iconFill(to, slot));
            if (end > start) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SATURATION, 9, 9, 9 - end, 0,
                right - slot * 8 - end, foodY[slot], end - start, 9, color);
        }
    }

    /** Hud.extractHearts HEAD: hearts come from the last index down, each container first. */
    public static void beforeHearts(int top, int rowHeight, float maxHealth, int absorption) {
        int total = Mth.ceil(maxHealth / 2) + Mth.ceil(absorption / 2.0);
        heartsTop = top;
        heartsRowHeight = rowHeight;
        heartsLeft = total > 0 && total <= 4096 ? total : 0;
        if (heartX.length < heartsLeft) { heartX = new int[heartsLeft]; heartY = new int[heartsLeft]; }
    }

    public static void heart(Hud.HeartType type, int x, int y) {
        if (type != Hud.HeartType.CONTAINER || heartsLeft <= 0) return;
        int index = --heartsLeft;
        heartX[index] = x;
        heartY[index] = option("Vanilla Animations") ? y : heartsTop - index / 10 * heartsRowHeight;
    }

    /** Hud.extractHearts RETURN: the health the held food would give back, over the hearts it would fill. */
    public static void afterHearts(GuiGraphicsExtractor graphics, Player player, float maxHealth, int health) {
        if (!on() || !option("Show Health Overlay") || heartsLeft != 0 || player.level().getDifficulty() == Difficulty.PEACEFUL) return;
        ItemStack held = heldFood(player);
        if (held == null) return;
        var values = held.get(DataComponents.FOOD);
        ServerPlayer own = serverPlayer(player);
        FoodData data = own != null ? own.getFoodData() : player.getFoodData();
        int food = FoodPreview.foodAfter(data.getFoodLevel(), values.nutrition());
        float saturation = FoodPreview.saturationAfter(data.getSaturationLevel(), values.saturation(), food);
        boolean regeneration = own == null || own.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
        float gain = (regeneration ? FoodPreview.regenerated(food, saturation, exhaustion(own), maxHealth - player.getHealth(), false) : 0)
            + regenerationEffect(held);
        int after = Mth.ceil(Math.min(maxHealth, player.getHealth() + gain)), hearts = Math.min(Mth.ceil(maxHealth / 2), heartX.length);
        int color = previewColor();
        if (color >>> 24 == 0) return;
        boolean hardcore = player.level().getLevelData().isHardcore();
        for (int i = Math.max(0, health / 2); i < Math.min((after + 1) / 2, hearts); i++)
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.HeartType.NORMAL.getSprite(hardcore, i * 2 + 1 == after, false), heartX[i], heartY[i], 9, 9, color);
    }

    /** The tooltip line of a food: hunger icons and points, then saturation outlines and points. Null when it shows none. */
    public static Component tooltipLine(ItemStack stack, TooltipDisplay display) {
        var food = stack.get(DataComponents.FOOD);
        if (food == null || !stack.has(DataComponents.CONSUMABLE) || !on() || !option("Food Tooltips") || !display.shows(DataComponents.FOOD)
            || !option("Tooltips Always Visible") && !Minecraft.getInstance().hasShiftDown()) return null;
        boolean rotten = harmful(stack);
        MutableComponent line = Component.empty();
        for (int point = 0; point < Math.min(food.nutrition(), 20); point += 2)
            line.append(icon(rotten ? (point + 1 == food.nutrition() ? ROTTEN_HALF : ROTTEN_FULL) : point + 1 == food.nutrition() ? HALF : FULL));
        line.append(Component.literal(" " + food.nutrition() + "  ").withStyle(ChatFormatting.GRAY));
        for (int icon = 0; icon < Math.min(Mth.ceil(food.saturation() / 2), 10); icon++) line.append(icon(SATURATION));
        return line.append(Component.literal(" " + FoodPreview.number(food.saturation())).withStyle(ChatFormatting.GOLD));
    }
    private static Component icon(Identifier sprite) { return Component.object(new AtlasSprite(GUI_ATLAS, sprite), Component.empty()); }
}
