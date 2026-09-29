package com.thelads.core.v26_2.feature.food;

import com.google.gson.JsonElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Option;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.food.client.HUDOverlayHandler;
import com.thelads.core.v26_2.feature.food.client.TooltipOverlayHandler.FoodOverlayTextComponent;
import com.thelads.core.v26_2.feature.food.helpers.ExhaustionHelper;
import com.thelads.core.v26_2.feature.food.helpers.FoodHelper;
import com.thelads.core.v26_2.feature.food.network.ClientSyncHandler;
import com.thelads.core.v26_2.feature.food.network.ExhaustionSyncPayload;
import com.thelads.core.v26_2.feature.food.network.SaturationSyncPayload;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.slf4j.LoggerFactory;

/** Exercises real transformed tooltip factories and food data; all temporary state is restored. */
public final class NativeFoodProbe {
    private static int passed, ticks;
    private static boolean syncDone;
    public static int run() {
        require(NativeFoodOverlay.active(), "food code is built into Core without the external jar");
        var module = NativeQualityOfLife.module("AppleSkin");
        var mc = Minecraft.getInstance();
        boolean enabled = module.isEnabled();
        long modified = module.getLastModified();
        Map<Option, JsonElement> preferences = new LinkedHashMap<>();
        module.getOptions().forEach(option -> preferences.put(option, option.save().deepCopy()));
        var main = mc.player.getMainHandItem();
        var off = mc.player.getOffhandItem();
        int hunger = mc.player.getFoodData().getFoodLevel();
        float saturation = mc.player.getFoodData().getSaturationLevel();
        float exhaustion = ExhaustionHelper.getExhaustion(mc.player);
        boolean creativeFood = mc.player.getAbilities().invulnerable;
        boolean regeneration = ClientSyncHandler.naturalRegeneration;
        boolean regenerationSynced = ClientSyncHandler.regenerationSynced;
        boolean saturationSynced = ClientSyncHandler.saturationSynced;
        boolean exhaustionSynced = ClientSyncHandler.exhaustionSynced;
        try {
            module.setEnabled(false); FoodOverlayConfig.refresh();
            require(count(new ItemStack(Items.APPLE)) == 0, "disabled master removes food tooltip");
            module.setEnabled(true);
            for (var option : module.getOptions()) if (option instanceof BoolOption toggle) toggle.set(true);
            FoodOverlayConfig.refresh();
            for (var item : List.of(Items.APPLE, Items.COOKED_BEEF, Items.ROTTEN_FLESH, Items.GOLDEN_APPLE)) {
                var stack = new ItemStack(item);
                require(count(stack) == 1, "one food overlay for " + item);
                var component = lines(stack).stream().filter(FoodOverlayTextComponent.class::isInstance).findFirst().orElseThrow();
                var rendered = ClientTooltipComponent.create(component.getVisualOrderText());
                require(rendered.getWidth(mc.font) > 0 && rendered.getHeight(mc.font) == 20, "real tooltip factory gives food geometry");
                if (item == Items.APPLE) NativeFoodJeiProbe.run((FoodOverlayTextComponent) component);
            }
            require(count(new ItemStack(Items.DIAMOND_SWORD)) == 0, "non-food never gets food bars");
            var stack = new ItemStack(Items.APPLE);
            stack.remove(DataComponents.CONSUMABLE);
            require(count(stack) == 0, "food without consumable is not treated as edible");
            stack = new ItemStack(Items.APPLE);
            stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
            require(count(stack) == 0, "hidden tooltip respected");
            set("Food Tooltips", false);
            require(count(new ItemStack(Items.APPLE)) == 0, "tooltip switch removes overlay");
            set("Food Tooltips", true); set("Tooltips Always Visible", false);
            require(count(new ItemStack(Items.APPLE)) == 0, "shift-only mode hidden without shift");
            set("Tooltips Always Visible", true);
            var food = FoodHelper.query(new ItemStack(Items.APPLE), mc.player);
            require(food.modifiedFoodComponent.nutrition() == 4 && Math.abs(food.modifiedFoodComponent.saturation() - 2.4f) < .001, "actual apple component values");
            require(FoodHelper.isRotten(FoodHelper.query(new ItemStack(Items.ROTTEN_FLESH), mc.player).consumableComponent), "harmful food uses rotten icons");
            require(!FoodHelper.isRotten(food.consumableComponent), "ordinary food uses normal icons");
            mc.player.getFoodData().setFoodLevel(10);
            mc.player.getFoodData().setSaturation(3.5f);
            ExhaustionHelper.setExhaustion(mc.player, 1.25f);
            require(ExhaustionHelper.getExhaustion(mc.player) == 1.25f, "transformed food data reads actual exhaustion");
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
            mc.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.APPLE));
            require(new HUDOverlayHandler.HeldFoodCache().result(1, mc.player) != null, "edible offhand contributes preview");
            set("Offhand Food", false);
            require(new HUDOverlayHandler.HeldFoodCache().result(1, mc.player) == null, "offhand toggle removes preview");
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF));
            require(new HUDOverlayHandler.HeldFoodCache().result(1, mc.player).modifiedFoodComponent.nutrition() == 8, "main hand food has priority");
            mc.player.getFoodData().setFoodLevel(20);
            mc.player.getAbilities().invulnerable = false;
            require(new HUDOverlayHandler.HeldFoodCache().result(1, mc.player) == null, "ordinary food not edible at full hunger");
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
            require(new HUDOverlayHandler.HeldFoodCache().result(1, mc.player) != null, "always-edible food visible at full hunger");
            require(FoodHelper.getEstimatedHealthIncrement(17, 0, 0) == 0, "no natural recovery below hunger threshold");
            require(FoodHelper.getEstimatedHealthIncrement(20, 5, 0) > 0, "natural recovery estimate with sufficient food");
            require(FoodHelper.getEstimatedHealthIncrement(20, Float.NaN, 0) == 0, "nonfinite health prediction returns immediately");
            require(Float.isFinite(FoodHelper.getEstimatedHealthIncrement(Integer.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)), "oversized modded values stay bounded");
            require(!ClientSyncHandler.valid(Float.NaN, 20) && !ClientSyncHandler.valid(-1, 20) && !ClientSyncHandler.valid(21, 20), "invalid sync values rejected");
            require(ClientSyncHandler.valid(0, 20) && ClientSyncHandler.valid(20, 20), "sync boundaries accepted");
            ClientSyncHandler.ensurePlayer(mc.player);
            ClientSyncHandler.naturalRegeneration = false; ClientSyncHandler.regenerationSynced = true;
            ClientSyncHandler.saturationSynced = ClientSyncHandler.exhaustionSynced = true;
            ClientSyncHandler.ensurePlayer(null); ClientSyncHandler.ensurePlayer(mc.player);
            require(!ClientSyncHandler.naturalRegeneration && ClientSyncHandler.regenerationSynced,
                    "player replacement preserves the connection game rule supplied by upstream servers");
            require(!ClientSyncHandler.saturationSynced && !ClientSyncHandler.exhaustionSynced,
                    "replacement player waits for its own food values");
            ClientSyncHandler.reset(); ClientSyncHandler.ensurePlayer(mc.player);
            require(ClientSyncHandler.naturalRegeneration && !ClientSyncHandler.regenerationSynced,
                    "connection reset clears the previous server game rule");
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                SaturationSyncPayload.CODEC.encode(buffer, new SaturationSyncPayload(3.5f));
                require(SaturationSyncPayload.CODEC.decode(buffer).saturation() == 3.5f, "compatible saturation wire format");
                buffer.clear();
                ExhaustionSyncPayload.CODEC.encode(buffer, new ExhaustionSyncPayload(1.25f));
                require(ExhaustionSyncPayload.CODEC.decode(buffer).exhaustion() == 1.25f, "compatible exhaustion wire format");
            } finally { buffer.release(); }
            for (var entry : Map.of("Show Saturation", "showSaturationHudOverlay", "Show Saturation Overlay", "showGainedSaturationHudOverlay", "Show Food Values", "showFoodValuesHudOverlay", "Show Exhaustion", "showFoodExhaustionHudUnderlay", "Show Health Overlay", "showFoodHealthHudOverlay", "Vanilla Animations", "showVanillaAnimationsOverlay").entrySet()) {
                set(entry.getKey(), false);
                require(!FoodOverlayConfig.class.getField(entry.getValue()).getBoolean(FoodOverlayConfig.INSTANCE), entry.getKey() + " off reaches renderer");
                set(entry.getKey(), true);
                require(FoodOverlayConfig.class.getField(entry.getValue()).getBoolean(FoodOverlayConfig.INSTANCE), entry.getKey() + " on reaches renderer");
            }
            LoggerFactory.getLogger("TheLadsCore").info("Lads food feature probe END: {} passed, 0 failed", passed);
            return passed;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        finally {
            mc.player.getAbilities().invulnerable = creativeFood;
            ClientSyncHandler.ensurePlayer(mc.player);
            ClientSyncHandler.naturalRegeneration = regeneration; ClientSyncHandler.regenerationSynced = regenerationSynced;
            ClientSyncHandler.saturationSynced = saturationSynced; ClientSyncHandler.exhaustionSynced = exhaustionSynced;
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, main); mc.player.setItemInHand(InteractionHand.OFF_HAND, off);
            mc.player.getFoodData().setFoodLevel(hunger); mc.player.getFoodData().setSaturation(saturation);
            ExhaustionHelper.setExhaustion(mc.player, exhaustion);
            preferences.forEach(Option::load); module.setEnabled(enabled); module.setLastModified(modified);
            FoodOverlayConfig.refresh();
            HUDOverlayHandler.INSTANCE.heldFood.lastGuiTick = Integer.MIN_VALUE;
        }
    }
    public static void tick() {
        if (syncDone || !Boolean.getBoolean("thelads.verifyIntegrations") || !NativeFoodOverlay.active()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (++ticks < 60) return;
        syncDone = true;
        if (ClientSyncHandler.saturationSynced && ClientSyncHandler.exhaustionSynced && ClientSyncHandler.regenerationSynced)
            LoggerFactory.getLogger("TheLadsCore").info("Lads food server sync END: saturation, exhaustion and regeneration received from the integrated server");
        else LoggerFactory.getLogger("TheLadsCore").error("Lads native feature probe FAILED: missing integrated-server food payloads");
    }
    private static void set(String name, boolean value) { ((BoolOption) NativeQualityOfLife.module("AppleSkin").getOption(name)).set(value); FoodOverlayConfig.refresh(); }
    private static List<Component> lines(ItemStack item) { var mc = Minecraft.getInstance(); return item.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL); }
    private static long count(ItemStack item) { return lines(item).stream().filter(FoodOverlayTextComponent.class::isInstance).count(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException("Native food: " + message); passed++; }
}
