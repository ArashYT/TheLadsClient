package com.thelads.core.v26_2.feature.food;

import com.google.gson.JsonElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Option;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.food.mixin.FoodDataAccess;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.slf4j.LoggerFactory;

/** QA only (thelads.verifyIntegrations, from NativeQualityProbe in the QA world): NativeFood through real item tooltips and food data. */
public final class FoodProbe {
    private static int passed;
    private FoodProbe() {}

    public static int run() {
        passed = 0;
        require(NativeFood.on() || !NativeQualityOfLife.module(NativeFood.MODULE).isEnabled(), "the food module is built into Core (no AppleSkin jar)");
        var module = NativeQualityOfLife.module(NativeFood.MODULE);
        var mc = Minecraft.getInstance();
        boolean enabled = module.isEnabled();
        long modified = module.getLastModified();
        Map<Option, JsonElement> preferences = new LinkedHashMap<>();
        module.getOptions().forEach(option -> preferences.put(option, option.save().deepCopy()));
        var main = mc.player.getMainHandItem();
        var off = mc.player.getOffhandItem();
        boolean invulnerable = mc.player.getAbilities().invulnerable;
        try {
            module.setEnabled(false);
            require(foodLine(new ItemStack(Items.APPLE)) == null, "module off: no food line");
            module.setEnabled(true);
            for (var option : module.getOptions()) if (option instanceof BoolOption toggle) toggle.set(true);
            var apple = foodLine(new ItemStack(Items.APPLE));
            require(apple != null && apple.getString().contains("4") && apple.getString().contains("2.4"), "apple: 4 hunger, 2.4 saturation: " + text(apple));
            require(sprites(apple).equals(List.of("hud/food_full", "hud/food_full", "theladscore:hud/food_saturation", "theladscore:hud/food_saturation")),
                "apple icons: two shanks, two saturation outlines " + sprites(apple));
            require(sprites(foodLine(new ItemStack(Items.ROTTEN_FLESH))).getFirst().equals("hud/food_full_hunger"), "rotten flesh shows the hunger effect's icons");
            require(sprites(foodLine(new ItemStack(Items.COOKED_COD))).contains("hud/food_half") == false
                && sprites(foodLine(new ItemStack(Items.BREAD))).contains("hud/food_half"), "odd hunger ends in a half shank (bread 5)");
            require(foodLine(new ItemStack(Items.DIAMOND_SWORD)) == null, "non-food has no food line");
            var hidden = new ItemStack(Items.APPLE);
            hidden.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(false, new java.util.LinkedHashSet<>(List.of(DataComponents.FOOD))));
            require(foodLine(hidden) == null, "hidden food component respected");
            var inedible = new ItemStack(Items.APPLE);
            inedible.remove(DataComponents.CONSUMABLE);
            require(foodLine(inedible) == null, "food without a consumable component is not food");
            set("Food Tooltips", false);
            require(foodLine(new ItemStack(Items.APPLE)) == null, "Food Tooltips off");
            set("Food Tooltips", true);
            set("Tooltips Always Visible", false);
            require(mc.hasShiftDown() || foodLine(new ItemStack(Items.APPLE)) == null, "shift-only without shift");
            set("Tooltips Always Visible", true);

            var own = NativeFood.serverPlayer(mc.player);
            require(own != null, "singleplayer reads the integrated server's player");
            float exhaustion = ((FoodDataAccess) own.getFoodData()).lads$exhaustion();
            require(exhaustion >= 0 && exhaustion <= 40 && NativeFood.exhaustion(own) == exhaustion, "server exhaustion readable: " + exhaustion);
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
            mc.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLDEN_APPLE));
            require(NativeFood.heldFood(mc.player) == mc.player.getOffhandItem(), "always-edible off-hand food is previewed");
            set("Offhand Food", false);
            require(NativeFood.heldFood(mc.player) == null, "Offhand Food off");
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF));
            mc.player.getAbilities().invulnerable = false;
            require(mc.player.getFoodData().getFoodLevel() < 20 || NativeFood.heldFood(mc.player) == null, "ordinary food is not previewed at full hunger");
            require(NativeFood.regenerationEffect(new ItemStack(Items.GOLDEN_APPLE)) == 4, "golden apple: Regeneration II heals 4");
            require(NativeFood.harmful(new ItemStack(Items.SPIDER_EYE)) && !NativeFood.harmful(new ItemStack(Items.BREAD)), "harmful food detected");
            LoggerFactory.getLogger("TheLadsCore").info("Lads food probe END: {} passed, 0 failed", passed);
            return passed;
        } finally {
            mc.player.getAbilities().invulnerable = invulnerable;
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, main);
            mc.player.setItemInHand(InteractionHand.OFF_HAND, off);
            preferences.forEach(Option::load);
            module.setEnabled(enabled);
            module.setLastModified(modified);
        }
    }

    /** The food line the real ItemStack.getTooltipLines gives (NativeTooltips through ItemTooltipMixin): the one with sprites. */
    private static Component foodLine(ItemStack stack) {
        var mc = Minecraft.getInstance();
        var found = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL).stream().filter(line -> !sprites(line).isEmpty()).toList();
        require(found.size() <= 1, "at most one food line: " + found.size());
        return found.isEmpty() ? null : found.getFirst();
    }
    private static List<String> sprites(Component line) {
        return line == null ? List.of() : line.toFlatList().stream().filter(part -> part.getContents() instanceof ObjectContents)
            .map(part -> ((AtlasSprite) ((ObjectContents) part.getContents()).contents()).sprite())
            .map(id -> "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString()).toList();
    }
    private static String text(Component line) { return line == null ? "none" : line.getString(); }
    private static void set(String name, boolean value) { ((BoolOption) NativeQualityOfLife.module(NativeFood.MODULE).getOption(name)).set(value); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Native food: " + message);
        passed++;
    }
}
