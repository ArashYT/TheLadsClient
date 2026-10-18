package com.thelads.smp.features.arise.nb6.client;

import com.thelads.smp.features.arise.nb6.AriseNB6Items;
import com.thelads.smp.features.arise.nb6.FishingJournalManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * Screen displaying the player's discovered fishing species, completion stats, and habitat clues.
 */
public class FishingJournalScreen extends Screen {
    private static final Set<String> CLIENT_CAUGHT = new HashSet<>();

    public static void updateClientCaught(List<String> list) {
        CLIENT_CAUGHT.clear();
        if (list != null) {
            CLIENT_CAUGHT.addAll(list);
        }
    }

    private static final List<SpeciesInfo> SPECIES = List.of(
            new SpeciesInfo("bass", "Striped Bass", "Rivers & Plains"),
            new SpeciesInfo("bluegill", "Bluegill Sunfish", "Forest Ponds & Lakes"),
            new SpeciesInfo("carp", "Golden Carp", "Cherry Groves & Flowers"),
            new SpeciesInfo("trout", "Rainbow Trout", "Mountain Streams"),
            new SpeciesInfo("glacier_cod", "Glacier Cod", "Frozen Oceans & Rivers"),
            new SpeciesInfo("frostbite_flounder", "Frostbite Flounder", "Ice Spikes & Deep Ice"),
            new SpeciesInfo("tuna", "Bluefin Tuna", "Deep Oceans"),
            new SpeciesInfo("snapper", "Red Snapper", "Warm Reefs & Corals"),
            new SpeciesInfo("sailfish", "Sunlit Sailfish", "Sunny Open Oceans"),
            new SpeciesInfo("catfish", "Mud Catfish", "Swamps & Mud"),
            new SpeciesInfo("bullhead", "Mangrove Bullhead", "Mangrove Swamps"),
            new SpeciesInfo("abyssal_angler", "Abyssal Angler", "Deep Underground (Y < 0)"),
            new SpeciesInfo("cave_tetra", "Blind Cave Tetra", "Dripstone & Caves"),
            new SpeciesInfo("lunar_guppy", "Lunar Guppy", "Night Waters (Midnight)"),
            new SpeciesInfo("storm_perch", "Storm Perch", "Rain & Thunderstorms"),
            new SpeciesInfo("magma_mackerel", "Magma Mackerel", "Nether Lava Lakes"),
            new SpeciesInfo("crimson_fangjaw", "Crimson Fangjaw", "Crimson Forest Waters"),
            new SpeciesInfo("warped_guppy", "Warped Guppy", "Warped Forest Waters"),
            new SpeciesInfo("void_tetra", "Void Tetra", "The End Void Shore"),
            new SpeciesInfo("chorus_driftfish", "Chorus Driftfish", "End Highlands & Chorus")
    );

    public record SpeciesInfo(String id, String name, String habitat) {}

    public FishingJournalScreen() {
        super(Component.literal("Fishing Journal"));
    }

    @Override
    protected void init() {
        int buttonW = 80;
        int buttonH = 20;
        this.addRenderableWidget(
                Button.builder(Component.literal("Close"), b -> this.onClose())
                        .bounds((this.width - buttonW) / 2, this.height - 30, buttonW, buttonH)
                        .build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Dark translucent overlay
        graphics.fill(0, 0, this.width, this.height, 0xC0050810);

        int boxW = 380;
        int boxH = 220;
        int left = (this.width - boxW) / 2;
        int top = 20;

        // Journal book parchment background
        graphics.fill(left - 4, top - 4, left + boxW + 4, top + boxH + 4, 0xFF2B1D0C);
        graphics.fill(left - 2, top - 2, left + boxW + 2, top + boxH + 2, 0xFFC6A972);
        graphics.fill(left, top, left + boxW, top + boxH, 0xFF17120E);

        // Header
        graphics.centeredText(this.font, "§6§lThe Lads SMP — Fishing Journal", this.width / 2, top + 8, 0xFFFFAA00);

        int caughtCount = CLIENT_CAUGHT.size();
        int percent = (int) Math.round((caughtCount / 20.0) * 100.0);
        String sub = "§eDiscovered: §a" + caughtCount + "§f/§a20 §eSpecies §7(" + percent + "% Complete)";
        graphics.centeredText(this.font, sub, this.width / 2, top + 22, 0xFFFFFFFF);

        // Grid of 20 species: 2 columns of 10 rows
        int col1X = left + 14;
        int col2X = left + boxW / 2 + 10;
        int startY = top + 38;
        int rowH = 16;

        for (int i = 0; i < SPECIES.size(); i++) {
            SpeciesInfo s = SPECIES.get(i);
            boolean caught = CLIENT_CAUGHT.contains(s.id());
            int colX = (i < 10) ? col1X : col2X;
            int rowY = startY + (i % 10) * rowH;

            Item item = AriseNB6Items.getItem(s.id());
            ItemStack stack = (item != null && caught) ? new ItemStack(item) : new ItemStack(Items.STRUCTURE_VOID);

            if (caught) {
                graphics.item(stack, colX, rowY);
                graphics.text(this.font, "§a" + s.name(), colX + 18, rowY + 1, 0xFFFFFFFF, false);
                graphics.text(this.font, "§7" + s.habitat(), colX + 18, rowY + 9, 0xFFAAAAAA, false);
            } else {
                graphics.text(this.font, "§8??? Undiscovered", colX + 18, rowY + 1, 0xFF777777, false);
                graphics.text(this.font, "§8" + s.habitat(), colX + 18, rowY + 9, 0xFF555555, false);
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
