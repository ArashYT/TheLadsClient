package com.thelads.core.v1_20_1.hud.items;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.client.AppleSkinSync;
import com.thelads.core.client.FoodPreview;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_20_1.hud.Hud1201;
import com.thelads.core.v1_20_1.hud.Mods1201;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

/**
 * The AppleSkin module on 1.20.1, ported from 26.3 NativeFood: what a held food would restore (hunger, saturation and health,
 * pulsing), the current saturation as gold outlines on the hunger icons, exhaustion as a band behind them, food values in tooltips
 * and an F3 line. Singleplayer and LAN hosts read the integrated server's own player, so every value is exact. A server running
 * AppleSkin's server side sends exact saturation and exhaustion (and the natural regeneration rule) on AppleSkin's channels
 * (AppleSkinSync), which are used there. Other servers send hunger, and saturation only with a health or hunger change, never
 * exhaustion: there saturation is the last value received, exhaustion is not drawn and health estimates assume none. It never
 * changes food, health or packets. A loaded AppleSkin jar (Zero SMP ships appleskin-forge) keeps the job and the channels.
 * <p>
 * 1.20.1 draws the hunger row inside Gui.renderPlayerHealth (plain Fabric) or ForgeGui.renderFood (Forge + Connector) and the
 * hearts in Gui.renderHearts on both: FoodGuiMixin / FoodForgeGuiMixin call the hooks below at the places vanilla draws.
 * Icons come from vanilla's icons.png (resource packs apply); the saturation outline is Lads' own texture.
 */
public final class Food1201 {
    static final String MODULE = "AppleSkin";
    static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    static final ResourceLocation SATURATION = new ResourceLocation("theladscore", "textures/gui/hud/food_saturation.png");
    // icons.png: hunger full / half, and the hunger effect's green full / half (v 27); hearts from u 52 (v 0, hardcore v 45).
    private static final int FULL_U = 52, HALF_U = 61, ROTTEN_FULL_U = 88, ROTTEN_HALF_U = 97, FOOD_V = 27;
    private static final int HEART_FULL_U = 52, HEART_HALF_U = 61;
    private static boolean active;
    /** QA only: a fixed preview opacity in place of the pulse. */
    public static Float qaPulse;
    /** QA only: read as on a remote server, even in the QA world. */
    public static boolean qaRemote;
    /** What a server running AppleSkin sent since the last join; NaN or null until it sends one. */
    private static volatile float syncedSaturation = Float.NaN, syncedExhaustion = Float.NaN;
    private static volatile Boolean syncedRegeneration;
    // Where vanilla drew this frame's hunger icons (y by slot) and heart containers (by heart index): previews sit exactly on them.
    private static int foodRight, heartsTop, heartsRowHeight, heartsLeft;
    private static final int[] foodY = new int[10];
    private static int[] heartX = new int[0], heartY = new int[0];

    private static com.thelads.core.config.Module cachedModule;
    private static com.thelads.core.config.BoolOption optOffhandFood;
    private static com.thelads.core.config.BoolOption optShowExhaustion;
    private static com.thelads.core.config.BoolOption optVanillaAnimations;
    private static com.thelads.core.config.BoolOption optShowSaturation;
    private static com.thelads.core.config.BoolOption optShowFoodValues;
    private static com.thelads.core.config.BoolOption optShowSaturationOverlay;
    private static com.thelads.core.config.BoolOption optShowHealthOverlay;
    private static com.thelads.core.config.BoolOption optFoodTooltips;
    private static com.thelads.core.config.BoolOption optTooltipsAlwaysVisible;
    private static com.thelads.core.config.SliderOption optOverlayOpacity;

    private Food1201() {}

    private static com.thelads.core.config.Module ensureModule() {
        com.thelads.core.config.Module m = cachedModule;
        if (m == null) {
            m = Hud1201.module(MODULE);
            if (m != null) {
                optOffhandFood = (com.thelads.core.config.BoolOption) m.getOption("Offhand Food");
                optShowExhaustion = (com.thelads.core.config.BoolOption) m.getOption("Show Exhaustion");
                optVanillaAnimations = (com.thelads.core.config.BoolOption) m.getOption("Vanilla Animations");
                optShowSaturation = (com.thelads.core.config.BoolOption) m.getOption("Show Saturation");
                optShowFoodValues = (com.thelads.core.config.BoolOption) m.getOption("Show Food Values");
                optShowSaturationOverlay = (com.thelads.core.config.BoolOption) m.getOption("Show Saturation Overlay");
                optShowHealthOverlay = (com.thelads.core.config.BoolOption) m.getOption("Show Health Overlay");
                optFoodTooltips = (com.thelads.core.config.BoolOption) m.getOption("Food Tooltips");
                optTooltipsAlwaysVisible = (com.thelads.core.config.BoolOption) m.getOption("Tooltips Always Visible");
                optOverlayOpacity = (com.thelads.core.config.SliderOption) m.getOption("Overlay Opacity");
                cachedModule = m;
            }
        }
        return m;
    }

    /** Built in unless the AppleSkin jar is loaded (then the card shows the installed mod and nothing here runs). */
    static void register() {
        if (active) return;
        if (Mods1201.loaded("appleskin")) {
            ModuleSupport.registerExternal(MODULE, "AppleSkin", "appleskin", true);
            return;
        }
        active = true;
        ModuleSupport.registerBuiltIn(MODULE);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetSync());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetSync());
    }

    /**
     * AppleSkinPayloadMixin, on the network thread: an AppleSkin server payload is read here instead of through a registered
     * receiver, so its channels are never announced (AppleSkinSync). The bytes are copied now and applied on the client thread in
     * packet order (after the join reset). False: not one of the three channels, or the module is not built in.
     */
    public static boolean payload(ClientboundCustomPayloadPacket packet) {
        ResourceLocation id = packet.getIdentifier();
        String channel = active ? AppleSkinSync.channel(id.getNamespace(), id.getPath()) : null;
        if (channel == null) return false;
        FriendlyByteBuf buf = packet.getData(); // a copy
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        buf.release();
        Minecraft.getInstance().execute(() -> receive(channel, data));
        return true;
    }

    /** An AppleSkin server payload's bytes; a value that cannot be the player's is ignored. */
    public static void receive(String channel, byte[] data) {
        switch (channel) {
            case AppleSkinSync.SATURATION -> { float value = AppleSkinSync.decodeFloat(data, AppleSkinSync.MAX_SATURATION); if (!Float.isNaN(value)) syncedSaturation = value; }
            case AppleSkinSync.EXHAUSTION -> { float value = AppleSkinSync.decodeFloat(data, AppleSkinSync.MAX_EXHAUSTION); if (!Float.isNaN(value)) syncedExhaustion = value; }
            default -> { Boolean value = AppleSkinSync.decodeBoolean(data); if (value != null) syncedRegeneration = value; }
        }
    }

    /** A new connection (or none): nothing from an AppleSkin server yet. */
    public static void resetSync() {
        syncedSaturation = syncedExhaustion = Float.NaN;
        syncedRegeneration = null;
    }

    public static boolean on() {
        if (!active) return false;
        com.thelads.core.config.Module m = ensureModule();
        return m != null && m.isEnabled();
    }

    static boolean option(String name) {
        ensureModule();
        com.thelads.core.config.BoolOption opt = switch (name) {
            case "Offhand Food" -> optOffhandFood;
            case "Show Exhaustion" -> optShowExhaustion;
            case "Vanilla Animations" -> optVanillaAnimations;
            case "Show Saturation" -> optShowSaturation;
            case "Show Food Values" -> optShowFoodValues;
            case "Show Saturation Overlay" -> optShowSaturationOverlay;
            case "Show Health Overlay" -> optShowHealthOverlay;
            case "Food Tooltips" -> optFoodTooltips;
            case "Tooltips Always Visible" -> optTooltipsAlwaysVisible;
            default -> null;
        };
        return opt != null ? opt.get() : Hud1201.bool(MODULE, name, true);
    }

    /** The integrated server's copy of the local player (singleplayer, LAN host); null on other servers. */
    public static ServerPlayer serverPlayer(Player player) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        return server == null || qaRemote ? null : server.getPlayerList().getPlayer(player.getUUID());
    }
    private static FoodData foodData(Player player, ServerPlayer own) { return own != null ? own.getFoodData() : player.getFoodData(); }
    /** Exact on the integrated server and on AppleSkin servers; elsewhere the last value vanilla sent. */
    public static float saturation(Player player, ServerPlayer own) {
        float synced = syncedSaturation;
        return own != null || Float.isNaN(synced) ? foodData(player, own).getSaturationLevel() : synced;
    }
    /** 0 where it is not known (exhaustionKnown false). */
    public static float exhaustion(ServerPlayer own) {
        if (own != null) return own.getFoodData().getExhaustionLevel();
        float synced = syncedExhaustion;
        return Float.isNaN(synced) ? 0 : synced;
    }
    public static boolean exhaustionKnown(ServerPlayer own) { return own != null || !Float.isNaN(syncedExhaustion); }
    /** The natural regeneration game rule: the integrated server's, an AppleSkin server's, else assumed on. */
    public static boolean naturalRegeneration(ServerPlayer own) {
        if (own != null) return own.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        Boolean synced = syncedRegeneration;
        return synced == null || synced;
    }

    /** The F3 line (FoodDebugMixin), or null when the module is off or there is no player. */
    public static String debugLine() {
        var player = Minecraft.getInstance().player;
        if (!on() || player == null) return null;
        ServerPlayer own = serverPlayer(player);
        return FoodPreview.debugLine(foodData(player, own).getFoodLevel(), saturation(player, own), exhaustion(own), exhaustionKnown(own));
    }

    static FoodProperties food(ItemStack stack) { return stack.isEdible() ? stack.getItem().getFoodProperties() : null; }
    /** Saturation points a food gives (vanilla FoodData.eat: nutrition x modifier x 2). */
    static float saturationGain(FoodProperties food) { return food.getNutrition() * food.getSaturationModifier() * 2; }

    /** The food eating would use now: the main hand, else the off hand ("Offhand Food"); null when neither can be eaten. */
    public static ItemStack heldFood(Player player) {
        if (edible(player, player.getMainHandItem())) return player.getMainHandItem();
        return option("Offhand Food") && edible(player, player.getOffhandItem()) ? player.getOffhandItem() : null;
    }
    private static boolean edible(Player player, ItemStack stack) {
        FoodProperties food = food(stack);
        return food != null && player.canEat(food.canAlwaysEat());
    }

    /** Food that gives a harmful effect (rotten flesh, spider eyes) shows the hunger effect's green icons. */
    public static boolean harmful(ItemStack stack) {
        FoodProperties food = food(stack);
        if (food != null) for (var effect : food.getEffects())
            if (effect.getFirst() != null && effect.getFirst().getEffect().getCategory() == MobEffectCategory.HARMFUL) return true;
        return false;
    }
    /** Health a food's own Regeneration gives (golden apples), weighted by its chance. */
    public static float regenerationEffect(ItemStack stack) {
        FoodProperties food = food(stack);
        float health = 0;
        if (food != null) for (var effect : food.getEffects())
            if (effect.getFirst() != null && effect.getFirst().getEffect() == MobEffects.REGENERATION)
                health += FoodPreview.regenerationEffect(effect.getFirst().getDuration(), effect.getFirst().getAmplifier()) * effect.getSecond();
        return health;
    }

    /** Preview opacity 0..1: the pulse times "Overlay Opacity". */
    private static float previewAlpha() {
        ensureModule();
        float pulse = qaPulse != null ? qaPulse : FoodPreview.pulse(System.nanoTime() / 1_000_000L % 1200 / 50f);
        double opacity = optOverlayOpacity != null ? optOverlayOpacity.getValue() : 65.0;
        return pulse * Mth.clamp((float) opacity / 100, 0, 1);
    }

    /** Hunger row start (before vanilla's first icon): the exhaustion band behind the icons, where exhaustion is known. */
    /** True between the profiler's "food" and "air" sections of renderPlayerHealth (FoodRowMixin). */
    public static boolean row;

    /** The player whose HUD this is: the camera entity when it is a player, else the local player. */
    public static Player cameraPlayer() {
        var minecraft = Minecraft.getInstance();
        return minecraft.getCameraEntity() instanceof Player player ? player : minecraft.player;
    }

    public static void beforeFood(GuiGraphics graphics, Player player, int top, int right) {
        foodRight = right;
        Arrays.fill(foodY, top);
        if (!on() || !option("Show Exhaustion")) return;
        ServerPlayer own = serverPlayer(player);
        if (!exhaustionKnown(own)) return;
        int width = Math.round(81 * Mth.clamp(exhaustion(own), 0, FoodPreview.MAX_EXHAUSTION) / FoodPreview.MAX_EXHAUSTION);
        if (width > 0) graphics.fill(right - width, top, right, top + 9, 0x50FFFFFF);
    }

    /** Every hunger icon vanilla blits: its height, which shakes while saturation is empty ("Vanilla Animations" off: steady). */
    public static void foodIcon(int x, int y) {
        int slot = (foodRight - 9 - x) / 8;
        if (slot >= 0 && slot < 10 && option("Vanilla Animations")) foodY[slot] = y;
    }

    /** Hunger row end: saturation outlines, then what the held food would add. */
    public static void afterFood(GuiGraphics graphics, Player player, int top, int right) {
        if (!on()) return;
        ServerPlayer own = serverPlayer(player);
        int food = foodData(player, own).getFoodLevel();
        float saturation = saturation(player, own);
        boolean outlines = option("Show Saturation");
        if (outlines) outline(graphics, right, 0, saturation, 1);
        ItemStack held = heldFood(player);
        if (held == null || !option("Show Food Values")) return;
        FoodProperties values = food(held);
        int after = FoodPreview.foodAfter(food, values.getNutrition());
        float alpha = previewAlpha();
        if (Math.round(255 * alpha) == 0) return;
        boolean rotten = harmful(held);
        tint(graphics, alpha);
        for (int slot = food / 2; slot < (after + 1) / 2; slot++) {
            boolean half = slot * 2 + 1 == after;
            graphics.blit(ICONS, right - slot * 8 - 9, foodY[slot], rotten ? (half ? ROTTEN_HALF_U : ROTTEN_FULL_U) : half ? HALF_U : FULL_U, FOOD_V, 9, 9);
        }
        untint(graphics);
        if (outlines && option("Show Saturation Overlay"))
            outline(graphics, right, saturation, FoodPreview.saturationAfter(saturation, saturationGain(values), after), alpha);
    }

    /** Gold outlines for saturation {@code from} to {@code to}; a part-filled icon shows its right side, like vanilla's half icon. */
    private static void outline(GuiGraphics graphics, int right, float from, float to, float alpha) {
        tint(graphics, alpha);
        for (int slot = 0; slot < 10; slot++) {
            int start = Math.round(9 * FoodPreview.iconFill(from, slot)), end = Math.round(9 * FoodPreview.iconFill(to, slot));
            if (end > start) graphics.blit(SATURATION, right - slot * 8 - end, foodY[slot], 9 - end, 0, end - start, 9, 9, 9);
        }
        untint(graphics);
    }

    private static void tint(GuiGraphics graphics, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1, 1, 1, alpha);
    }
    private static void untint(GuiGraphics graphics) { graphics.setColor(1, 1, 1, 1); }

    /** Gui.renderHearts HEAD: hearts come from the last index down, each container first. */
    public static void beforeHearts(int top, int rowHeight, float maxHealth, int absorption) {
        int total = Mth.ceil(maxHealth / 2) + Mth.ceil(absorption / 2.0);
        heartsTop = top;
        heartsRowHeight = rowHeight;
        heartsLeft = total > 0 && total <= 4096 ? total : 0;
        if (heartX.length < heartsLeft) { heartX = new int[heartsLeft]; heartY = new int[heartsLeft]; }
    }

    /** Every heart vanilla blits (Gui.renderHeart): containers record where heart {@code index} sits. */
    public static void heart(boolean container, int x, int y) {
        if (!container || heartsLeft <= 0) return;
        int index = --heartsLeft;
        heartX[index] = x;
        heartY[index] = option("Vanilla Animations") ? y : heartsTop - index / 10 * heartsRowHeight;
    }

    /** Gui.renderHearts RETURN: the health the held food would give back, over the hearts it would fill. */
    public static void afterHearts(GuiGraphics graphics, Player player, float maxHealth, int health) {
        if (!on() || !option("Show Health Overlay") || heartsLeft != 0 || player.level().getDifficulty() == Difficulty.PEACEFUL) return;
        ItemStack held = heldFood(player);
        if (held == null) return;
        FoodProperties values = food(held);
        ServerPlayer own = serverPlayer(player);
        int food = FoodPreview.foodAfter(foodData(player, own).getFoodLevel(), values.getNutrition());
        float saturation = FoodPreview.saturationAfter(saturation(player, own), saturationGain(values), food);
        float gain = (naturalRegeneration(own) ? FoodPreview.regenerated(food, saturation, exhaustion(own), maxHealth - player.getHealth(), false) : 0)
            + regenerationEffect(held);
        int after = Mth.ceil(Math.min(maxHealth, player.getHealth() + gain)), hearts = Math.min(Mth.ceil(maxHealth / 2), heartX.length);
        float alpha = previewAlpha();
        if (Math.round(255 * alpha) == 0) return;
        int v = player.level().getLevelData().isHardcore() ? 45 : 0;
        tint(graphics, alpha);
        for (int i = Math.max(0, health / 2); i < Math.min((after + 1) / 2, hearts); i++)
            graphics.blit(ICONS, heartX[i], heartY[i], i * 2 + 1 == after ? HEART_HALF_U : HEART_FULL_U, v, 9, 9);
        untint(graphics);
    }

    /**
     * The tooltip image of a food (ItemTooltipMixin): hunger icons and points, then saturation outlines and points. Null when it
     * shows none, and off the client thread. 1.20.1 has no sprite text, so the row is a tooltip image (vanilla places it under the
     * item name, where AppleSkin draws it on 1.20.1) instead of 26.3's last text line.
     */
    public static FoodTooltip tooltip(ItemStack stack) {
        FoodProperties food = food(stack);
        if (food == null || !Minecraft.getInstance().isSameThread() || !on() || !option("Food Tooltips")
            || !option("Tooltips Always Visible") && !Screen.hasShiftDown()) return null;
        return new FoodTooltip(food.getNutrition(), saturationGain(food), harmful(stack));
    }

    /** One row: up to 10 hunger icons, " N  " in gray, up to 10 saturation outlines, " S" in gold. */
    public record FoodTooltip(int nutrition, float saturation, boolean rotten) implements TooltipComponent, ClientTooltipComponent {
        private static final int ICON = 9, GRAY = 0xFFAAAAAA, GOLD = 0xFFFFAA00;
        private int hungerIcons() { return Math.min((Math.max(nutrition, 0) + 1) / 2, 10); }
        private int saturationIcons() { return Math.min(Mth.ceil(saturation / 2), 10); }
        private String points() { return " " + nutrition + "  "; }
        private String saturationText() { return " " + FoodPreview.number(saturation); }

        @Override public int getHeight() { return 10; }
        @Override public int getWidth(Font font) {
            return (hungerIcons() + saturationIcons()) * ICON + font.width(points()) + font.width(saturationText());
        }
        @Override public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            int left = x;
            for (int point = 0; point < hungerIcons() * 2; point += 2, left += ICON) {
                boolean half = point + 1 == nutrition;
                graphics.blit(ICONS, left, y, rotten ? (half ? ROTTEN_HALF_U : ROTTEN_FULL_U) : half ? HALF_U : FULL_U, FOOD_V, ICON, ICON);
            }
            left = graphics.drawString(font, points(), left, y + 1, GRAY, true);
            for (int icon = 0; icon < saturationIcons(); icon++, left += ICON) graphics.blit(SATURATION, left, y, 0, 0, ICON, ICON, ICON, ICON);
            graphics.drawString(font, saturationText(), left, y + 1, GOLD, true);
        }
    }

    /** For QA: the sprites a tooltip row draws, in order (vanilla names as in 26.3's FoodProbe). */
    public static List<String> sprites(FoodTooltip row) {
        var found = new java.util.ArrayList<String>();
        for (int point = 0; point < Math.min(row.nutrition(), 20); point += 2) {
            boolean half = point + 1 == row.nutrition();
            found.add(row.rotten() ? (half ? "hud/food_half_hunger" : "hud/food_full_hunger") : half ? "hud/food_half" : "hud/food_full");
        }
        for (int icon = 0; icon < Math.min(Mth.ceil(row.saturation() / 2), 10); icon++) found.add("theladscore:hud/food_saturation");
        return found;
    }
}
