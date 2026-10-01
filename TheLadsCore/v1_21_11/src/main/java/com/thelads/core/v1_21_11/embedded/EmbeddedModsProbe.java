package com.thelads.core.v1_21_11.embedded;

import com.thelads.core.v1_21_11.feature.NativeWorldVerification;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.storage.LevelSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** QA only (-Dthelads.verifyAutoWorld): evidence for the embedded mods in the isolated QA world, logged, nothing changed. */
public final class EmbeddedModsProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static boolean done;
    private static int readyTicks, passed, failed;

    private EmbeddedModsProbe() {}

    public static void initialize() {
        if (!Boolean.getBoolean("thelads.verifyAutoWorld")) return;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (done || !NativeWorldVerification.worldReady() || mc.player == null) return;
            if (++readyTicks < 60) return;
            done = true;
            try { run(mc); }
            catch (Throwable failure) { failed++; LOGGER.error("Lads embedded mods probe FAILED: unexpected error", failure); }
            LOGGER.info("Lads embedded mods probe END: {} passed, {} failed", passed, failed);
        });
    }

    private static void check(boolean ok, String what) {
        if (ok) { passed++; LOGGER.info("Lads embedded mods probe: {}", what); }
        else { failed++; LOGGER.warn("Lads embedded mods probe FAILED: {}", what); }
    }

    private static void run(Minecraft mc) throws Exception {
        tooltips(mc);
        if (EmbeddedMods.active("hoveringhotbar")) {
            int offset = com.thelads.core.v1_21_11.embedded.hoveringhotbar.HoveringHotbar.CONFIG.getHotbarOffset();
            check(offset >= 0 && com.thelads.core.v1_21_11.embedded.hoveringhotbar.HoveringHotbar.hotbarLift() == offset,
                "Hovering Hotbar offset " + offset + " from config/hoveringhotbar-client.toml, Lads HUD lift follows it");
        }
        if (EmbeddedMods.active("fixbookgui")) {
            var book = new BookViewScreen();
            book.init(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            int expected = 159 + com.thelads.core.v1_21_11.embedded.fixbookgui.FixBookGui.getFixedY(book);
            var buttons = book.children().stream().filter(c -> c instanceof PageButton).map(c -> (PageButton) c).toList();
            check(!buttons.isEmpty() && buttons.stream().allMatch(b -> b.getY() == expected),
                "Fix Book GUI moves the book page buttons to y=" + expected + " (vanilla 159), found " + buttons.stream().map(b -> b.getY()).toList());
        }
        if (EmbeddedMods.active("worldplaytimereborn")) {
            List<LevelSummary> summaries = mc.getLevelSource().loadLevelSummaries(mc.getLevelSource().findLevelCandidates()).get(30, TimeUnit.SECONDS);
            for (LevelSummary summary : summaries) {
                var data = (com.thelads.core.v1_21_11.embedded.playtime.util.IWithPlayTime) summary;
                LOGGER.info("Lads embedded mods probe: world list '{}' play time {} ticks ({}), size {} bytes", summary.getLevelId(), data.getPlayTimeTicks(),
                    com.thelads.core.v1_21_11.embedded.playtime.client.util.PlayTimeRenderer.getPlayTimeComponent(data.getPlayTimeTicks()) instanceof Component c ? c.getString() : "-",
                    data.getWorldSizeBytes());
            }
            check(summaries.stream().anyMatch(s -> ((com.thelads.core.v1_21_11.embedded.playtime.util.IWithPlayTime) s).getWorldSizeBytes() > 0),
                "World Play Time Reborn adds play time/size to the world list summaries");
        }
        if (EmbeddedMods.active("capes")) {
            var skins = new net.minecraft.client.gui.screens.options.SkinCustomizationScreen(null, mc.options);
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            skins.init(w, h);
            check(capesHooked() && skins.children().stream().anyMatch(c -> c instanceof net.minecraft.client.gui.components.SpriteIconButton b
                    && b.getMessage().getString().isEmpty() && b.getWidth() == 20),
                "Capes loads capes.json5, hooks the player cape lookup and adds its cape options button to Skin Customization");
        }
        if (EmbeddedMods.active("nbtac")) {
            var suggestions = com.thelads.core.v1_21_11.embedded.nbtac.api.NBTacAPI.getNbtSuggestions("{", "entity/minecraft:zombie", null, false, null)
                .get(30, TimeUnit.SECONDS).getList().stream().map(s -> s.getText()).toList();
            check(suggestions.contains("CanBreakDoors"), "NBT Autocomplete suggests zombie tags (" + suggestions.size() + " suggestions)");
        }
    }

    /** Capes' config file exists and its mixins are merged into PlayerInfo (cape lookup) and SkinCustomizationScreen. */
    private static boolean capesHooked() {
        try {
            net.minecraft.client.gui.screens.options.SkinCustomizationScreen.class.getDeclaredField("capes$selectorMenu");
        } catch (NoSuchFieldException missing) {
            return false;
        }
        return com.thelads.core.v1_21_11.embedded.capes.Capes.getConfig() != null
            && java.nio.file.Files.isRegularFile(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("capes.json5"))
            && java.util.Arrays.stream(net.minecraft.client.multiplayer.PlayerInfo.class.getDeclaredMethods()).anyMatch(m -> m.getName().endsWith("getCapeTexture"));
    }

    /** Tooltip lines of sample items as the game builds them, with the colour of each line: shows whichever tooltip mod runs. */
    private static void tooltips(Minecraft mc) {
        var context = Item.TooltipContext.of(mc.level);
        List<ItemStack> samples = new ArrayList<>(List.of(new ItemStack(Items.BREAD), new ItemStack(Items.APPLE), new ItemStack(Items.GOLDEN_CARROT),
            new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.CAKE), new ItemStack(Items.COAL), new ItemStack(Items.LAVA_BUCKET),
            new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.NETHERITE_PICKAXE),
            new ItemStack(Items.MUSIC_DISC_CAT), new ItemStack(Items.BOOKSHELF), new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.OBSIDIAN),
            new ItemStack(Items.ELYTRA), new ItemStack(Items.IRON_HELMET)));
        ItemStack worn = new ItemStack(Items.WOODEN_PICKAXE);
        worn.setDamageValue(2);
        worn.set(DataComponents.REPAIR_COST, 3);
        samples.add(worn);
        for (ItemStack stack : samples) {
            var lines = new ArrayList<String>();
            for (Component line : stack.getTooltipLines(context, mc.player, TooltipFlag.NORMAL)) {
                var colors = new java.util.LinkedHashSet<String>();
                line.visit((style, text) -> {
                    if (!text.isEmpty()) colors.add((style.getColor() == null ? "-" : style.getColor().serialize()) + (style.isItalic() ? " italic" : ""));
                    return java.util.Optional.empty();
                }, net.minecraft.network.chat.Style.EMPTY);
                lines.add(line.getString() + " " + colors);
            }
            LOGGER.info("Lads embedded mods probe tooltip {}: {}", BuiltInRegistries.ITEM.getKey(stack.getItem()), String.join(" | ", lines));
        }
        passed++;
    }
}
