// The Lads: independent implementation (clean room), not derived from Tooltips TXF; see META-INF/lads-sources/tooltips/NOTICE.txt.
package com.thelads.core.v1_20_1.embedded.tooltips;

import com.thelads.core.v1_20_1.perf.SmallModsProbe1201;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/** Entry point called by EmbeddedMods1201 while the original Tooltips TXF (mod id tooltipstxf) is not installed. */
public final class ExtraTooltipsInit1201 {
    private ExtraTooltipsInit1201() {}

    public static void init() {
        ExtraTooltips.init();
        SmallModsProbe1201.runOnce("tooltips", false, client -> probe());
    }

    /**
     * Builds the tooltip lines of real items (with every switch on) and checks the translated text, then fires the game's tooltip event
     * once to prove the callback is registered. Needs the language file, so it also proves the resource ships.
     */
    private static void probe() {
        TooltipsConfig all = TooltipsConfig.allOn();
        List<String> failures = new ArrayList<>();
        expect(failures, all, new ItemStack(Items.GOLDEN_APPLE), "Nutrition: 4", "Saturation: 9.6");
        expect(failures, all, new ItemStack(Items.COAL), "Burn Time: 1600");
        expect(failures, all, new ItemStack(Items.WHEAT), "Compostable: 65%");
        expect(failures, all, new ItemStack(Items.MUSIC_DISC_13), "Duration: 178s");
        expect(failures, all, new ItemStack(Items.IRON_PICKAXE), "Mining Level: 2", "Mining Speed: 6", "Enchantability: 14", "Durability: 250 / 250");
        expect(failures, all, new ItemStack(Items.OBSIDIAN), "Hardness: 50 Resistance: 1200");
        expect(failures, all, new ItemStack(Items.BOOKSHELF), "Enchantment Power: 1");
        List<Component> viaEvent = new ArrayList<>(List.of(Component.literal("Golden Apple")));
        ItemTooltipCallback.EVENT.invoker().getTooltip(new ItemStack(Items.GOLDEN_APPLE), TooltipFlag.Default.NORMAL, viaEvent);
        TooltipsConfig live = ExtraTooltips.current();
        boolean foodShown = live != null && live.on("enableMod") && live.on("showFoodValues"); // the player may have switched it off in tooltipstxf.json5
        boolean registered = !foodShown || viaEvent.size() > 1;
        SmallModsProbe1201.result("tooltips", failures.isEmpty() && registered,
            "linesViaGameEvent=" + viaEvent.stream().map(Component::getString).toList() + (failures.isEmpty() ? "" : " mismatches=" + failures));
    }

    private static void expect(List<String> failures, TooltipsConfig config, ItemStack stack, String... wanted) {
        List<Component> lines = new ArrayList<>(List.of(Component.literal(stack.getHoverName().getString())));
        ExtraTooltips.append(config, stack, TooltipFlag.Default.NORMAL, lines);
        String text = String.join("\n", lines.stream().map(Component::getString).toList());
        for (String part : wanted) if (!text.contains(part)) failures.add(stack.getItem() + " lacks '" + part + "' in " + lines.stream().map(Component::getString).toList());
    }
}
