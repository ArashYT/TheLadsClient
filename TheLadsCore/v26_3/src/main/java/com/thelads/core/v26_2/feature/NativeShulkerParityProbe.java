package com.thelads.core.v26_2.feature;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import org.slf4j.LoggerFactory;

/** Real item components and GUI extraction; disk tests use a separate temporary QA directory. */
final class NativeShulkerParityProbe {
    private static int passed;
    private NativeShulkerParityProbe() {}

    static int run() throws IOException {
        Minecraft minecraft = Minecraft.getInstance();
        var module = NativeQualityOfLife.module("ShulkerBoxUtils");
        var before = new LinkedHashMap<Option, JsonElement>();
        module.getOptions().forEach(option -> before.put(option, option.save().deepCopy()));
        boolean enabled = module.isEnabled();
        long modified = module.getLastModified();
        passed = 0;
        try {
            module.setEnabled(true);
            module.getOptions().forEach(Option::reset);
            set("Persist Observed Contents", false);
            ItemStack box = new ItemStack(Items.SHULKER_BOX);
            var slots = new ArrayList<>(Collections.nCopies(27, ItemStack.EMPTY));
            slots.set(1, new ItemStack(Items.DIAMOND, 7)); slots.set(25, new ItemStack(Items.GOLD_INGOT, 3));
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
            ShulkerSummary summary = ShulkerSummary.of(box.get(DataComponents.CONTAINER));
            check(summary.occupied() == 2 && summary.count() == 10 && summary.slots().size() == 27, "real slots and total item count");
            check(summary.slots().get(0).isEmpty() && summary.slots().get(25).is(Items.GOLD_INGOT), "sparse slot positions retained");
            slots.get(1).setCount(1);
            check(summary.first().getCount() == 7, "summary is detached from source stacks");
            check(!summary.uniform() && summary.badgeVisible(), "first-item mode includes mixed boxes");
            ((DropdownOption) module.getOption("Display Mode")).setIndex(1);
            check(!summary.badgeVisible(), "uniform mode excludes mixed box badge");
            ItemStack named = new ItemStack(Items.DIAMOND, 2);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Observed gem"));
            check(ShulkerSummary.of(List.of(named, new ItemStack(Items.DIAMOND))).badgeVisible(), "uniform mode compares item types, preserving components");
            ((DropdownOption) module.getOption("Display Mode")).setIndex(0);
            ShulkerSummary empty = ShulkerSummary.of(List.of());
            check(empty.freeBarWidth() == 13 && empty.freeBarColor() == 0xff00ff00, "empty box has full green free-slot bar");
            ShulkerSummary full = ShulkerSummary.of(Collections.nCopies(27, new ItemStack(Items.DIAMOND)));
            check(full.freeBarWidth() == 1 && full.freeBarColor() == 0xffff0000, "full box has one-pixel red free-slot bar");
            check(!empty.badgeVisible(), "empty box has no invented badge");

            var image = box.getTooltipImage();
            check(image.orElse(null) instanceof ShulkerTooltip, "transformed ItemStack supplies grid component");
            var tooltip = ClientTooltipComponent.create(image.orElseThrow());
            check(tooltip instanceof ShulkerTooltip && tooltip.getWidth(minecraft.font) == 174 && tooltip.getHeight(minecraft.font) == 76,
                "real tooltip factory recognizes native grid");
            var grid = new GuiRenderState();
            tooltip.extractImage(minecraft.font, 4, 4, 174, 76, new GuiGraphicsExtractor(minecraft, grid, 0, 0));
            check(items(grid) == 2, "actual grid extracts both occupied item models");
            check(texts(grid) == 3, "actual grid extracts stack counts and totals");
            check(elements(grid) == 29, "actual grid draws 27 slots and Lads panel");
            set("Item Counts", false);
            var noCounts = new GuiRenderState();
            tooltip.extractImage(minecraft.font, 4, 4, 174, 76, new GuiGraphicsExtractor(minecraft, noCounts, 0, 0));
            check(texts(noCounts) == 1 && items(noCounts) == 2, "count toggle removes count text while preserving item models");
            set("Item Counts", true);
            int gridLines = box.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL).size();
            set("Contents Preview", false);
            check(box.getTooltipImage().isEmpty(), "preview toggle restores vanilla tooltip-image behavior");
            check(box.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL).size() > gridLines,
                "vanilla textual contents return when grid is disabled");
            set("Contents Preview", true);
            box.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.CONTAINER, true));
            check(box.getTooltipImage().isEmpty(), "hidden container component cannot leak through grid");
            check(items(draw(box)) == 1 && elements(draw(box)) == 0, "hidden container also suppresses badge and fill details");
            box.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
            check(box.getTooltipImage().isEmpty(), "fully hidden tooltip remains hidden");
            box.remove(DataComponents.TOOLTIP_DISPLAY);
            var decorated = draw(box);
            check(items(decorated) == 2 && elements(decorated) == 2, "actual inventory hook extracts badge and free-slot bar");
            set("Inventory Badge", false);
            check(items(draw(box)) == 1 && elements(draw(box)) == 2, "badge can be disabled independently");
            set("Free Slot Bar", false);
            check(items(draw(box)) == 1 && elements(draw(box)) == 0, "bar can be disabled independently");
            set("Inventory Badge", true);
            check(items(draw(box)) == 2 && elements(draw(box)) == 0, "badge can remain while bar is off");
            ItemStack nested = new ItemStack(Items.SHULKER_BOX);
            nested.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(box)));
            check(items(draw(nested)) == 2, "nested component cannot recursively draw inventory badges");
            module.setEnabled(false);
            check(box.getTooltipImage().isEmpty() && items(draw(box)) == 1 && elements(draw(box)) == 0, "module disable restores vanilla grid and inventory behavior");
            module.setEnabled(true);
            check(!ShulkerInventory.eligible(new ItemStack(Items.APPLE)), "ordinary items are unaffected");
            persistence(named);
            LoggerFactory.getLogger("TheLadsCore").info("Lads shulker parity probe END: {} passed, 0 failed (real tooltip/inventory extraction; isolated cache round trips)", passed);
            return passed;
        } finally {
            before.forEach(Option::load);
            module.setEnabled(enabled); module.setLastModified(modified);
        }
    }

    private static void persistence(ItemStack named) throws IOException {
        Minecraft minecraft = Minecraft.getInstance();
        var ops = RegistryOps.create(JsonOps.INSTANCE, minecraft.level.registryAccess());
        var directory = Files.createTempDirectory(minecraft.gameDirectory.toPath(), "lads-shulker-cache-check-");
        var file = directory.resolve("observations.json");
        String key = ShulkerObservations.scopeKey("qa-world", "overworld", "qa-player");
        check(key.matches("[0-9a-f]{64}"), "scope is a fixed path-safe hash");
        check(!key.equals(ShulkerObservations.scopeKey("other-world", "overworld", "qa-player")), "world scope separation");
        check(!key.equals(ShulkerObservations.scopeKey("qa-world", "nether", "qa-player")), "dimension scope separation");
        check(!key.equals(ShulkerObservations.scopeKey("qa-world", "overworld", "other-player")), "account scope separation");
        long now = System.currentTimeMillis();
        var rows = new LinkedHashMap<Long, ShulkerObservations.Observation>();
        rows.put(13L, new ShulkerObservations.Observation(named, false, "minecraft:purple_shulker_box", now));
        ShulkerObservations.write(file, key, ops, rows);
        var loaded = ShulkerObservations.read(file, key, ops, now + 1);
        check(loaded.size() == 1 && ItemStack.matches(named, loaded.get(13L).first()), "persisted first stack preserves real count and components");
        check(!loaded.get(13L).uniform() && loaded.get(13L).block().equals("minecraft:purple_shulker_box"), "uniform and block identity persist");
        check(ShulkerObservations.read(file, "different-scope", ops, now + 1).isEmpty(), "wrong scope is never restored");
        check(ShulkerObservations.read(file, key, ops, now + ShulkerObservations.MAX_AGE_MILLIS + 1).isEmpty(), "expired observations are never restored");
        check(!ShulkerObservations.fresh(now + 1, now), "future timestamps are rejected");
        for (long index = 0; index < ShulkerObservations.MAX_ENTRIES + 20; index++) rows.put(index,
            new ShulkerObservations.Observation(named, true, "minecraft:shulker_box", now));
        ShulkerObservations.write(file, key, ops, rows);
        check(ShulkerObservations.read(file, key, ops, System.currentTimeMillis()).size() == ShulkerObservations.MAX_ENTRIES, "persistent entry count is bounded");
        check(Files.size(file) < ShulkerObservations.MAX_BYTES, "written cache stays inside byte budget");
        Files.writeString(directory.resolve("invalid.json"), "{not valid json");
        check(ShulkerObservations.read(directory.resolve("invalid.json"), key, ops, now).isEmpty(), "corrupt cache degrades to unknown");
        Files.write(directory.resolve("oversized.json"), new byte[ShulkerObservations.MAX_BYTES + 1]);
        check(ShulkerObservations.read(directory.resolve("oversized.json"), key, ops, now).isEmpty(), "oversized cache is rejected before parsing");
        ShulkerObservations.write(file, key, ops, java.util.Map.of());
        check(ShulkerObservations.read(file, key, ops, System.currentTimeMillis()).isEmpty(), "atomic replacement removes invalidated observations");
    }

    private static GuiRenderState draw(ItemStack stack) {
        var state = new GuiRenderState();
        var graphics = new GuiGraphicsExtractor(Minecraft.getInstance(), state, 0, 0);
        var pose = new org.joml.Matrix3x2f(graphics.pose());
        graphics.item(stack, 10, 10);
        if (!graphics.pose().equals(pose)) throw new IllegalStateException("inventory badge must restore the GUI pose");
        return state;
    }
    private static int items(GuiRenderState state) { int[] count = {0}; state.forEachItem(item -> count[0]++); return count[0]; }
    private static int texts(GuiRenderState state) { int[] count = {0}; state.forEachText(text -> count[0]++); return count[0]; }
    private static int elements(GuiRenderState state) { int[] count = {0}; state.forEachElement(element -> count[0]++, GuiRenderState.TraverseRange.ALL); return count[0]; }
    private static void set(String name, boolean value) { ((BoolOption) NativeQualityOfLife.module("ShulkerBoxUtils").getOption(name)).set(value); }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); passed++; }
}
