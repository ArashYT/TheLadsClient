package com.thelads.core.v1_20_1.hud.items;

import com.google.gson.JsonElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.config.TextOption;
import com.thelads.core.v1_20_1.hud.Hud1201;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only, opt in (-Dthelads.verifyItems1201=true, or a ".lads-qa-items" file in the game directory, deleted when read): once a
 * singleplayer world is ready, checks the hud-items modules through the real vanilla paths (ItemStack.getTooltipLines,
 * getTooltipImage, ClientTooltipComponent.create, the held items) like 26.3's FoodProbe, NativeDurabilityProbe and
 * NativeShulkerParityProbe, then puts every option, toggle and held item back. Logs "Lads items probe END: N passed, 0 failed"
 * or "Lads items probe FAILED". The HUD drawing and the world icon are checked by screenshots.
 */
public final class ItemsProbe1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static boolean done;
    private static Boolean qaFolder;
    private static int passed;
    private ItemsProbe1201() {}

    static void tick(Minecraft mc) {
        if (done || mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (qaFolder == null)
            qaFolder = mc.gameDirectory.toPath().toAbsolutePath().toString().replace('\\', '/').toLowerCase(java.util.Locale.ROOT).contains("/artifacts/verification/");
        // The marker file and the script line only count in a harness folder (under artifacts/verification): a player's game never
        // touches the disk here. -Dthelads.verifyItems1201 is set by the harness itself.
        if (!qaFolder && !Boolean.getBoolean("thelads.verifyItems1201")) return;
        Path request = mc.gameDirectory.toPath().resolve(".lads-qa-items");
        boolean file = qaFolder && Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS);
        if (!file && !Boolean.getBoolean("thelads.verifyItems1201") && !scriptAsks(mc)) return;
        done = true;
        try {
            if (file) Files.delete(request);
            passed = 0;
            food(mc);
            durability();
            shulker(mc);
            LOGGER.info("Lads items probe END: {} passed, 0 failed", passed);
        } catch (Throwable failure) {
            LOGGER.error("Lads items probe FAILED after {} checks", passed, failure);
        }
    }

    /** A "# items-qa" comment line in the harness's QA script file (.lads-qa-script.txt) also asks for the probe. */
    private static boolean scriptAsks(Minecraft mc) {
        try {
            Path script = mc.gameDirectory.toPath().resolve(".lads-qa-script.txt");
            return Files.isRegularFile(script) && Files.readAllLines(script).stream().anyMatch(line -> line.strip().equals("# items-qa"));
        } catch (java.io.IOException failure) {
            return false;
        }
    }

    private static void food(Minecraft mc) {
        Module module = Hud1201.module(Food1201.MODULE);
        require(Food1201.on() || !module.isEnabled(), "AppleSkin is built in (no AppleSkin jar)");
        var main = mc.player.getMainHandItem();
        var off = mc.player.getOffhandItem();
        boolean invulnerable = mc.player.getAbilities().invulnerable;
        try (var saved = new Saved(module)) {
            module.setEnabled(false);
            require(Food1201.tooltip(new ItemStack(Items.APPLE)) == null, "module off: no food row");
            module.setEnabled(true);
            for (Option option : module.getOptions()) if (option instanceof BoolOption toggle) toggle.set(true);
            var apple = new ItemStack(Items.APPLE).getTooltipImage().orElse(null);
            require(apple instanceof Food1201.FoodTooltip row && row.nutrition() == 4 && Math.abs(row.saturation() - 2.4f) < 1e-4,
                "apple: 4 hunger, 2.4 saturation through getTooltipImage: " + apple);
            require(ClientTooltipComponent.create(apple) == apple, "the tooltip factory draws Lads' food row");
            require(Food1201.sprites((Food1201.FoodTooltip) apple).equals(List.of("hud/food_full", "hud/food_full",
                "theladscore:hud/food_saturation", "theladscore:hud/food_saturation")), "apple icons: two shanks, two saturation outlines");
            require(Food1201.sprites(Food1201.tooltip(new ItemStack(Items.ROTTEN_FLESH))).get(0).equals("hud/food_full_hunger"), "rotten flesh shows the hunger effect's icons");
            require(!Food1201.sprites(Food1201.tooltip(new ItemStack(Items.COOKED_BEEF))).contains("hud/food_half")
                && Food1201.sprites(Food1201.tooltip(new ItemStack(Items.BREAD))).contains("hud/food_half"), "even hunger is whole shanks, odd ends in a half");
            require(new ItemStack(Items.DIAMOND_SWORD).getTooltipImage().isEmpty(), "non-food has no food row");
            set(module, "Food Tooltips", false);
            require(new ItemStack(Items.APPLE).getTooltipImage().isEmpty(), "Food Tooltips off");
            set(module, "Food Tooltips", true);
            set(module, "Tooltips Always Visible", false);
            require(net.minecraft.client.gui.screens.Screen.hasShiftDown() || Food1201.tooltip(new ItemStack(Items.APPLE)) == null, "shift-only without shift");
            set(module, "Tooltips Always Visible", true);

            var own = Food1201.serverPlayer(mc.player);
            require(own != null, "singleplayer reads the integrated server's player");
            float exhaustion = Food1201.exhaustion(own); // the server thread may change it any time: range only
            require(exhaustion >= 0 && exhaustion <= 40 && Food1201.exhaustionKnown(own), "server exhaustion readable: " + exhaustion);
            String line = Food1201.debugLine();
            require(line != null && line.startsWith("Food: ") && !line.contains("?"), "exact F3 line in singleplayer: " + line);
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
            mc.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLDEN_APPLE));
            require(Food1201.heldFood(mc.player) == mc.player.getOffhandItem(), "always-edible off-hand food is previewed");
            set(module, "Offhand Food", false);
            require(Food1201.heldFood(mc.player) == null, "Offhand Food off");
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF));
            mc.player.getAbilities().invulnerable = false;
            require(mc.player.getFoodData().getFoodLevel() < 20 || Food1201.heldFood(mc.player) == null, "ordinary food is not previewed at full hunger");
            require(Food1201.regenerationEffect(new ItemStack(Items.GOLDEN_APPLE)) == 4, "golden apple: Regeneration II heals 4");
            require(Food1201.harmful(new ItemStack(Items.SPIDER_EYE)) && !Food1201.harmful(new ItemStack(Items.BREAD)), "harmful food detected");

            com.thelads.core.v1_20_1.feature.QuietChannelsQa1201.sample("items probe (AppleSkin built in)", false);
            // An AppleSkin server's payloads (the bytes the channels carry), read as on a remote server: handed to the client's own
            // packet handler (ClientboundCustomPayloadPacket, AppleSkinPayloadMixin): no receiver is registered for these channels.
            Food1201.qaRemote = true;
            Food1201.resetSync();
            require(Food1201.serverPlayer(mc.player) == null && !Food1201.exhaustionKnown(null), "remote: no exhaustion before AppleSkin sends one");
            payload(mc, com.thelads.core.client.AppleSkinSync.SATURATION, com.thelads.core.client.AppleSkinSync.encodeFloat(3.5f));
            payload(mc, com.thelads.core.client.AppleSkinSync.EXHAUSTION, com.thelads.core.client.AppleSkinSync.encodeFloat(2f));
            payload(mc, com.thelads.core.client.AppleSkinSync.NATURAL_REGENERATION, new byte[] {0});
            require(Food1201.saturation(mc.player, null) == 3.5f && Food1201.exhaustion(null) == 2f && !Food1201.naturalRegeneration(null), "AppleSkin payload values used");
            payload(mc, com.thelads.core.client.AppleSkinSync.EXHAUSTION, new byte[] {0x40});
            payload(mc, com.thelads.core.client.AppleSkinSync.SATURATION, com.thelads.core.client.AppleSkinSync.encodeFloat(25f));
            require(Food1201.exhaustion(null) == 2f && Food1201.saturation(mc.player, null) == 3.5f, "a short or impossible payload is ignored");
            Food1201.resetSync();
            require(!Food1201.exhaustionKnown(null) && Food1201.naturalRegeneration(null), "a new join forgets the AppleSkin values");
        } finally {
            Food1201.qaRemote = false;
            Food1201.resetSync();
            mc.player.getAbilities().invulnerable = invulnerable;
            mc.player.setItemInHand(InteractionHand.MAIN_HAND, main);
            mc.player.setItemInHand(InteractionHand.OFF_HAND, off);
        }
    }

    /** An AppleSkin server payload as the network delivers it: the client's own custom payload handler, no Fabric receiver. */
    private static void payload(Minecraft mc, String channel, byte[] bytes) {
        mc.getConnection().handleCustomPayload(new net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket(
            new net.minecraft.resources.ResourceLocation(com.thelads.core.client.AppleSkinSync.NAMESPACE, channel),
            new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bytes))));
    }

    private static void durability() {
        Module bars = Hud1201.module("EnhancedToolbars");
        try (var saved = new Saved(bars)) {
            var sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.setDamageValue(100);
            int maximum = sword.getMaxDamage(), remaining = maximum - 100;
            bars.setEnabled(false);
            require(durability(sword, TooltipFlag.NORMAL).isEmpty(), "disabled adds no Lads durability line");
            bars.setEnabled(true);
            for (String name : List.of("Detailed Durability", "Show Max Durability", "Colorize Durability", "Show Item Attributes", "Show Durability Hint", "Show When Full"))
                set(bars, name, true);
            set(bars, "Only Vanilla Tools", false);
            ((TextOption) bars.getOption("Excluded Mods")).setValue("");
            ((DropdownOption) bars.getOption("Durability Style")).setIndex(0);
            ((DropdownOption) bars.getOption("Durability Color Style")).setIndex(0);
            var color = (ColorOption) bars.getOption("Durability Base Color");
            color.setUseGlobal(false);
            color.setColor(0xff123456);
            require(durability(sword, TooltipFlag.NORMAL).equals(List.of("Uses left: " + remaining + " / " + maximum)), "numbers show remaining / max: " + durability(sword, TooltipFlag.NORMAL));
            require(sword.getTooltipLines(null, TooltipFlag.ADVANCED).stream().noneMatch(ItemsProbe1201::vanillaDurability)
                && durability(sword, TooltipFlag.ADVANCED).size() == 1, "advanced tooltip: Lads' line replaces vanilla's");
            set(bars, "Show Max Durability", false);
            require(durability(sword, TooltipFlag.NORMAL).equals(List.of("Uses left: " + remaining)), "Show Max Durability off");
            set(bars, "Show Max Durability", true);
            int withAttributes = sword.getTooltipLines(null, TooltipFlag.NORMAL).size();
            set(bars, "Show Item Attributes", false);
            require(sword.getTooltipLines(null, TooltipFlag.NORMAL).size() < withAttributes, "Show Item Attributes off hides the attribute block");
            set(bars, "Show Item Attributes", true);
            ((DropdownOption) bars.getOption("Durability Style")).setIndex(2);
            set(bars, "Show Durability Hint", false);
            require(durability(sword, TooltipFlag.NORMAL).equals(List.of("Good")), "text style without hint");
            ((TextOption) bars.getOption("Excluded Mods")).setValue("minecraft:diamond_sword");
            require(durability(sword, TooltipFlag.NORMAL).isEmpty(), "a single item id can be excluded");
            ((TextOption) bars.getOption("Excluded Mods")).setValue("");
            set(bars, "Detailed Durability", false);
            require(durability(sword, TooltipFlag.NORMAL).isEmpty(), "Detailed Durability off");
        }
    }

    private static void shulker(Minecraft mc) {
        Module module = Hud1201.module("ShulkerBoxUtils");
        try (var saved = new Saved(module)) {
            module.setEnabled(true);
            module.getOptions().forEach(Option::reset);
            set(module, "Persist Observed Contents", false);
            ItemStack box = box();
            ShulkerSummary1201 summary = ShulkerInventory1201.contents(box);
            require(summary.occupied() == 2 && summary.count() == 10 && summary.slots().get(0).isEmpty() && summary.slots().get(25).is(Items.GOLD_INGOT),
                "NBT slots, positions and total count");
            var image = box.getTooltipImage().orElse(null);
            require(image instanceof ShulkerInventory1201.Tooltip && ClientTooltipComponent.create(image) == image
                && ((ShulkerInventory1201.Tooltip) image).getWidth(mc.font) == 174 && ((ShulkerInventory1201.Tooltip) image).getHeight() == 76, "grid tooltip image");
            require(text(box).stream().noneMatch(line -> line.endsWith(" x7")), "vanilla item list is replaced by the grid: " + text(box));
            set(module, "Contents Preview", false);
            require(box.getTooltipImage().isEmpty() && text(box).stream().anyMatch(line -> line.endsWith(" x7")), "Contents Preview off: vanilla list returns");
            set(module, "Contents Preview", true);
            box.getOrCreateTag().putInt("HideFlags", ItemStack.TooltipPart.ADDITIONAL.getMask());
            require(box.getTooltipImage().isEmpty() && !ShulkerInventory1201.eligible(box), "hidden additional tooltip hides the contents");
            ItemStack loot = new ItemStack(Items.SHULKER_BOX);
            loot.getOrCreateTagElement("BlockEntityTag").putString("LootTable", "minecraft:chests/end_city_treasure");
            require(loot.getTooltipImage().isEmpty() && text(loot).contains("???????"), "a loot-table box keeps vanilla's unknown contents");
            ((DropdownOption) module.getOption("Display Mode")).setIndex(1);
            require(!summary.badgeVisible(), "single-item-type mode hides a mixed box badge");
            ((DropdownOption) module.getOption("Display Mode")).setIndex(0);
            require(summary.badgeVisible() && !ShulkerSummary1201.EMPTY.badgeVisible(), "first-item mode badges mixed boxes, never empty ones");
            require(ShulkerSummary1201.EMPTY.freeBarWidth() == 13 && ShulkerSummary1201.EMPTY.freeBarColor() == 0xff00ff00, "empty box: full green bar");
            module.setEnabled(false);
            require(box().getTooltipImage().isEmpty(), "module off restores vanilla");
        }
    }

    private static ItemStack box() {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        ListTag items = new ListTag();
        items.add(slot(1, new ItemStack(Items.DIAMOND, 7)));
        items.add(slot(25, new ItemStack(Items.GOLD_INGOT, 3)));
        box.getOrCreateTagElement("BlockEntityTag").put("Items", items);
        return box;
    }
    private static CompoundTag slot(int slot, ItemStack stack) {
        CompoundTag tag = stack.save(new CompoundTag());
        tag.putByte("Slot", (byte) slot);
        return tag;
    }
    private static List<String> text(ItemStack stack) { return stack.getTooltipLines(null, TooltipFlag.NORMAL).stream().map(Component::getString).toList(); }
    private static List<String> durability(ItemStack stack, TooltipFlag flag) {
        return stack.getTooltipLines(null, flag).stream().filter(line -> !vanillaDurability(line)).map(Component::getString)
            .filter(text -> text.startsWith("Uses left: ") || text.startsWith("Condition: ") || text.startsWith("|")
                || com.thelads.core.client.DurabilityPresentation.WEAR_WORDS.contains(text)).toList();
    }
    private static boolean vanillaDurability(Component line) {
        return line.getContents() instanceof TranslatableContents text && "item.durability".equals(text.getKey());
    }
    private static void set(Module module, String name, boolean value) { ((BoolOption) module.getOption(name)).set(value); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Items probe: " + message);
        passed++;
    }

    /** A module's toggle, options and modification time, put back on close. */
    private static final class Saved implements AutoCloseable {
        private final Module module;
        private final boolean enabled;
        private final long modified;
        private final Map<Option, JsonElement> options = new LinkedHashMap<>();
        Saved(Module module) {
            this.module = module;
            enabled = module.isEnabled();
            modified = module.getLastModified();
            module.getOptions().forEach(option -> options.put(option, option.save().deepCopy()));
        }
        @Override public void close() {
            options.forEach(Option::load);
            module.setEnabled(enabled);
            module.setLastModified(modified);
        }
    }
}
