package com.thelads.core.features.alwayson.betterstatisticscreen.client.gui.screen;

import com.thelads.core.features.alwayson.betterstatisticscreen.BetterStats;
import com.thelads.core.features.alwayson.betterstatisticscreen.resource.dto.BetterStatsRestAPI;
import com.thelads.core.features.alwayson.betterstatisticscreen.resource.dto.credits.CreditsEntry;
import com.thelads.core.features.alwayson.betterstatisticscreen.resource.dto.credits.CreditsSection;
import com.thelads.core.mixin.alwayson.betterstatisticscreen.AccessorStatsCounter;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class BetterStatsScreen extends Screen {
    private final Screen parent;
    private String currentTab = "General";
    private double scrollOffset = 0;
    private int maxScroll = 0;

    private EditBox searchBox;
    private List<CreditsSection> creditsSections = Collections.emptyList();

    // Stats data lists
    private List<GeneralStatEntry> generalStats = new ArrayList<>();
    private List<ItemStatEntry> itemStats = new ArrayList<>();
    private List<BlockStatEntry> blockStats = new ArrayList<>();
    private List<MobStatEntry> mobStats = new ArrayList<>();

    // Styling constants matching LadsSettingsScreen
    private static final int BG           = 0xDD050508;
    private static final int SIDEBAR      = 0xEE0A0202;
    private static final int ACCENT       = 0xFFD32F2F;
    private static final int ACCENT2      = 0xFFFF5252;
    private static final int CARD         = 0xCC180A0A;
    private static final int CARD_HOV     = 0xCC2A1010;
    private static final int TEXT_HI      = 0xFFFFFFFF;
    private static final int TEXT_MED     = 0xFFCCCCCC;
    private static final int TEXT_LO      = 0xFF885555;
    private static final int DIVIDER      = 0x22FF5555;

    private static final int GAP          = 6;
    private static final String[] TABS    = {"General", "Items", "Blocks", "Mobs", "Credits"};
    private static final String[] ICONS   = {"📊", "⚔", "🧱", "☠", "♥"};

    public BetterStatsScreen(Screen parent) {
        super(Component.translatable("gui.stats"));
        this.parent = parent;
    }

    public Screen getAsScreen() {
        return this;
    }

    @Override
    protected void init() {
        scrollOffset = 0;

        // Search Box setup
        int sbW = 130, sbH = 16;
        int sbX = this.width - sbW - 8;
        int sbY = 16;
        searchBox = new EditBox(this.font, sbX, sbY, sbW, sbH, Component.literal("Search..."));
        searchBox.setMaxLength(40);
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFFCCCCCC);
        searchBox.setHint(Component.literal("§8Search…"));
        this.addRenderableWidget(searchBox);

        // Fetch local credits
        BetterStatsRestAPI.fetchBuiltInCreditsAsync().thenAccept(sections -> {
            this.creditsSections = sections;
        });

        // Request updated stats from server
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().send(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.REQUEST_STATS
            ));
        }

        // Load stats from player stats counter
        loadStats();
    }

    private void loadStats() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        
        generalStats.clear();
        itemStats.clear();
        blockStats.clear();
        mobStats.clear();

        Object2IntMap<Stat<?>> statsMap = ((AccessorStatsCounter) this.minecraft.player.getStats()).getStats();
        Map<net.minecraft.world.item.Item, ItemStatEntry> itemMap = new HashMap<>();
        Map<net.minecraft.world.level.block.Block, BlockStatEntry> blockMap = new HashMap<>();
        Map<net.minecraft.world.entity.EntityType<?>, MobStatEntry> mobMap = new HashMap<>();

        for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Stat<?>> entry : statsMap.object2IntEntrySet()) {
            Stat<?> stat = entry.getKey();
            int val = entry.getIntValue();
            if (val <= 0) continue;

            if (stat.getType() == net.minecraft.stats.Stats.CUSTOM) {
                generalStats.add(new GeneralStatEntry((Stat<Identifier>) stat, val));
            } else if (stat.getType() == net.minecraft.stats.Stats.ITEM_CRAFTED) {
                net.minecraft.world.item.Item item = (net.minecraft.world.item.Item) stat.getValue();
                itemMap.computeIfAbsent(item, ItemStatEntry::new).crafted = val;
            } else if (stat.getType() == net.minecraft.stats.Stats.ITEM_USED) {
                net.minecraft.world.item.Item item = (net.minecraft.world.item.Item) stat.getValue();
                itemMap.computeIfAbsent(item, ItemStatEntry::new).used = val;
            } else if (stat.getType() == net.minecraft.stats.Stats.ITEM_BROKEN) {
                net.minecraft.world.item.Item item = (net.minecraft.world.item.Item) stat.getValue();
                itemMap.computeIfAbsent(item, ItemStatEntry::new).broken = val;
            } else if (stat.getType() == net.minecraft.stats.Stats.ITEM_PICKED_UP) {
                net.minecraft.world.item.Item item = (net.minecraft.world.item.Item) stat.getValue();
                itemMap.computeIfAbsent(item, ItemStatEntry::new).pickedUp = val;
            } else if (stat.getType() == net.minecraft.stats.Stats.ITEM_DROPPED) {
                net.minecraft.world.item.Item item = (net.minecraft.world.item.Item) stat.getValue();
                itemMap.computeIfAbsent(item, ItemStatEntry::new).dropped = val;
            } else if (stat.getType() == net.minecraft.stats.Stats.BLOCK_MINED) {
                net.minecraft.world.level.block.Block block = (net.minecraft.world.level.block.Block) stat.getValue();
                blockMap.computeIfAbsent(block, BlockStatEntry::new).mined = val;
            } else if (stat.getType() == net.minecraft.stats.Stats.ENTITY_KILLED) {
                net.minecraft.world.entity.EntityType<?> entityType = (net.minecraft.world.entity.EntityType<?>) stat.getValue();
                mobMap.computeIfAbsent(entityType, MobStatEntry::new).killed = val;
            } else if (stat.getType() == net.minecraft.stats.Stats.ENTITY_KILLED_BY) {
                net.minecraft.world.entity.EntityType<?> entityType = (net.minecraft.world.entity.EntityType<?>) stat.getValue();
                mobMap.computeIfAbsent(entityType, MobStatEntry::new).killedBy = val;
            }
        }

        itemStats.addAll(itemMap.values());
        blockStats.addAll(blockMap.values());
        mobStats.addAll(mobMap.values());

        // Sort initially by name/value
        generalStats.sort(Comparator.comparing(e -> e.name.getString().toLowerCase(Locale.ROOT)));
        itemStats.sort(Comparator.comparing(e -> e.name.getString().toLowerCase(Locale.ROOT)));
        blockStats.sort(Comparator.comparing(e -> e.name.getString().toLowerCase(Locale.ROOT)));
        mobStats.sort(Comparator.comparing(e -> e.name.getString().toLowerCase(Locale.ROOT)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
        long now = System.currentTimeMillis();

        // Background
        g.fill(0, 0, this.width, this.height, BG);
        drawStarfield(g, now);

        // Sidebar
        int sw = 120;
        g.fill(0, 0, sw, this.height, SIDEBAR);
        g.fill(sw, 0, sw + 1, this.height, DIVIDER);

        // Header Title
        g.text(this.font, "§lBETTER STATS", 14, 14, ACCENT, true);
        g.text(this.font, "§8SCREEN", 14, 24, TEXT_LO, false);
        g.fill(10, 40, sw - 10, 41, DIVIDER);

        // Tab rendering
        int tabY = 55;
        for (int ti = 0; ti < TABS.length; ti++) {
            String tab = TABS[ti];
            boolean sel = tab.equals(currentTab);
            boolean hov = mouseX >= 0 && mouseX < sw && mouseY >= tabY && mouseY < tabY + 22;
            if (sel) g.fill(3, tabY, sw, tabY + 22, 0x22FFFFFF);
            else if (hov) g.fill(3, tabY, sw, tabY + 22, 0x11FFFFFF);
            int col = sel ? TEXT_HI : (hov ? TEXT_MED : TEXT_LO);
            g.text(this.font, ICONS[ti] + " " + tab, 14, tabY + 7, col, false);
            tabY += 26;
        }

        // Close button (top-right)
        boolean closeHov = mouseX >= this.width - 22 && mouseX < this.width - 4 && mouseY >= 4 && mouseY < 22;
        g.fill(this.width - 22, 4, this.width - 4, 22, closeHov ? 0xCC7D2E2E : 0x44FFFFFF);
        g.centeredText(this.font, "✕", this.width - 13, 9, closeHov ? 0xFFFF5555 : TEXT_MED);

        // Content Area setup
        int cx = sw + 14, cw = this.width - sw - 28, cy = 14;
        g.text(this.font, "§l" + currentTab, cx, cy, TEXT_HI, true);
        cy += 16;
        g.fill(cx, cy, cx + cw, cy + 1, DIVIDER);
        cy += 6;

        // Search Box visibility
        if ("Credits".equals(currentTab)) {
            searchBox.setVisible(false);
        } else {
            searchBox.setVisible(true);
            int sbX = this.width - 138, sbY2 = 14;
            g.fill(sbX - 4, sbY2 - 2, sbX + 134, sbY2 + 18, 0xBB120505);
            g.fill(sbX - 4, sbY2 + 16, sbX + 134, sbY2 + 18, ACCENT);
        }

        String filter = searchBox.isVisible() ? searchBox.getValue().toLowerCase(Locale.ROOT) : "";

        // Render contents based on tab
        if ("General".equals(currentTab)) {
            renderGeneral(g, mouseX, mouseY, cx, cy, cw, filter);
        } else if ("Items".equals(currentTab)) {
            renderItems(g, mouseX, mouseY, cx, cy, cw, filter);
        } else if ("Blocks".equals(currentTab)) {
            renderBlocks(g, mouseX, mouseY, cx, cy, cw, filter);
        } else if ("Mobs".equals(currentTab)) {
            renderMobs(g, mouseX, mouseY, cx, cy, cw, filter);
        } else if ("Credits".equals(currentTab)) {
            renderCredits(g, mouseX, mouseY, cx, cy, cw);
        }

        super.extractRenderState(g, mouseX, mouseY, pt);
    }

    private void renderGeneral(GuiGraphicsExtractor g, int mx, int my, int sx, int sy, int cw, String filter) {
        List<GeneralStatEntry> filtered = new ArrayList<>();
        for (GeneralStatEntry entry : generalStats) {
            if (filter.isEmpty() || entry.name.getString().toLowerCase(Locale.ROOT).contains(filter)) {
                filtered.add(entry);
            }
        }

        int itemH = 22;
        maxScroll = Math.max(0, filtered.size() * (itemH + GAP) - (this.height - sy - 8));
        int y = sy - (int) scrollOffset;

        for (GeneralStatEntry entry : filtered) {
            if (y + itemH >= sy && y < this.height - 8) {
                boolean hov = mx >= sx && mx < sx + cw && my >= y && my < y + itemH;
                g.fill(sx, y, sx + cw, y + itemH, hov ? CARD_HOV : CARD);
                g.fill(sx, y + 2, sx + 3, y + itemH - 2, ACCENT);

                g.text(this.font, entry.name, sx + 8, y + 7, TEXT_HI, false);
                g.text(this.font, entry.valueStr, sx + cw - 12 - this.font.width(entry.valueStr), y + 7, ACCENT2, false);
            }
            y += itemH + GAP;
        }

        if (filtered.isEmpty()) {
            g.centeredText(this.font, "No statistics found.", sx + cw / 2, sy + 30, TEXT_LO);
        }
    }

    private void renderItems(GuiGraphicsExtractor g, int mx, int my, int sx, int sy, int cw, String filter) {
        List<ItemStatEntry> filtered = new ArrayList<>();
        for (ItemStatEntry entry : itemStats) {
            if (filter.isEmpty() || entry.name.getString().toLowerCase(Locale.ROOT).contains(filter)) {
                filtered.add(entry);
            }
        }

        int itemH = 32;
        maxScroll = Math.max(0, filtered.size() * (itemH + GAP) - (this.height - sy - 8));
        int y = sy - (int) scrollOffset;

        for (ItemStatEntry entry : filtered) {
            if (y + itemH >= sy && y < this.height - 8) {
                boolean hov = mx >= sx && mx < sx + cw && my >= y && my < y + itemH;
                g.fill(sx, y, sx + cw, y + itemH, hov ? CARD_HOV : CARD);
                g.fill(sx, y + 2, sx + 3, y + itemH - 2, ACCENT);

                // Draw item icon
                g.item(new ItemStack(entry.item), sx + 8, y + 8);

                // Draw item name
                g.text(this.font, entry.name, sx + 28, y + 4, TEXT_HI, true);

                // Draw counts line
                StringBuilder sb = new StringBuilder();
                if (entry.crafted > 0) sb.append("Crafted: ").append(entry.crafted).append("  ");
                if (entry.used > 0) sb.append("Used: ").append(entry.used).append("  ");
                if (entry.broken > 0) sb.append("Broken: ").append(entry.broken).append("  ");
                if (entry.pickedUp > 0) sb.append("Picked Up: ").append(entry.pickedUp).append("  ");
                if (entry.dropped > 0) sb.append("Dropped: ").append(entry.dropped).append("  ");

                String detail = sb.toString().trim();
                g.text(this.font, detail, sx + 28, y + 18, TEXT_MED, false);
            }
            y += itemH + GAP;
        }

        if (filtered.isEmpty()) {
            g.centeredText(this.font, "No statistics found.", sx + cw / 2, sy + 30, TEXT_LO);
        }
    }

    private void renderBlocks(GuiGraphicsExtractor g, int mx, int my, int sx, int sy, int cw, String filter) {
        List<BlockStatEntry> filtered = new ArrayList<>();
        for (BlockStatEntry entry : blockStats) {
            if (filter.isEmpty() || entry.name.getString().toLowerCase(Locale.ROOT).contains(filter)) {
                filtered.add(entry);
            }
        }

        int itemH = 28;
        maxScroll = Math.max(0, filtered.size() * (itemH + GAP) - (this.height - sy - 8));
        int y = sy - (int) scrollOffset;

        for (BlockStatEntry entry : filtered) {
            if (y + itemH >= sy && y < this.height - 8) {
                boolean hov = mx >= sx && mx < sx + cw && my >= y && my < y + itemH;
                g.fill(sx, y, sx + cw, y + itemH, hov ? CARD_HOV : CARD);
                g.fill(sx, y + 2, sx + 3, y + itemH - 2, ACCENT);

                // Draw block item icon
                g.item(new ItemStack(entry.block), sx + 8, y + 6);

                // Draw block name
                g.text(this.font, entry.name, sx + 28, y + 10, TEXT_HI, false);

                // Draw mined count
                String valStr = "Mined: " + entry.mined;
                g.text(this.font, valStr, sx + cw - 12 - this.font.width(valStr), y + 10, ACCENT2, false);
            }
            y += itemH + GAP;
        }

        if (filtered.isEmpty()) {
            g.centeredText(this.font, "No statistics found.", sx + cw / 2, sy + 30, TEXT_LO);
        }
    }

    private void renderMobs(GuiGraphicsExtractor g, int mx, int my, int sx, int sy, int cw, String filter) {
        List<MobStatEntry> filtered = new ArrayList<>();
        for (MobStatEntry entry : mobStats) {
            if (filter.isEmpty() || entry.name.getString().toLowerCase(Locale.ROOT).contains(filter)) {
                filtered.add(entry);
            }
        }

        int itemH = 28;
        maxScroll = Math.max(0, filtered.size() * (itemH + GAP) - (this.height - sy - 8));
        int y = sy - (int) scrollOffset;

        for (MobStatEntry entry : filtered) {
            if (y + itemH >= sy && y < this.height - 8) {
                boolean hov = mx >= sx && mx < sx + cw && my >= y && my < y + itemH;
                g.fill(sx, y, sx + cw, y + itemH, hov ? CARD_HOV : CARD);
                g.fill(sx, y + 2, sx + 3, y + itemH - 2, ACCENT);

                // Draw mob name
                g.text(this.font, entry.name, sx + 8, y + 10, TEXT_HI, false);

                // Draw counts
                StringBuilder sb = new StringBuilder();
                if (entry.killed > 0) sb.append("Killed: ").append(entry.killed).append("  ");
                if (entry.killedBy > 0) sb.append("Killed By: ").append(entry.killedBy).append("  ");

                String detail = sb.toString().trim();
                g.text(this.font, detail, sx + cw - 12 - this.font.width(detail), y + 10, ACCENT2, false);
            }
            y += itemH + GAP;
        }

        if (filtered.isEmpty()) {
            g.centeredText(this.font, "No statistics found.", sx + cw / 2, sy + 30, TEXT_LO);
        }
    }

    private void renderCredits(GuiGraphicsExtractor g, int mx, int my, int sx, int sy, int cw) {
        int y = sy - (int) scrollOffset;
        int listTop = sy;

        // Calculate total scroll height
        int totalH = 0;
        for (CreditsSection sec : creditsSections) {
            totalH += 22; // section name
            if (sec.getSummary() != null) totalH += 14;
            totalH += sec.getEntries().size() * 18 + 10; // entries and padding
        }
        maxScroll = Math.max(0, totalH - (this.height - listTop - 8));

        for (CreditsSection sec : creditsSections) {
            if (y + 20 >= listTop && y < this.height - 8) {
                g.text(this.font, sec.getName(), sx + 4, y, ACCENT, true);
            }
            y += 18;

            if (sec.getSummary() != null) {
                if (y + 14 >= listTop && y < this.height - 8) {
                    g.text(this.font, sec.getSummary(), sx + 6, y, TEXT_MED, false);
                }
                y += 14;
            }

            for (CreditsEntry entry : sec.getEntries()) {
                if (y + 16 >= listTop && y < this.height - 8) {
                    boolean hov = mx >= sx + 10 && mx < sx + cw && my >= y && my < y + 16;
                    g.fill(sx + 8, y, sx + cw, y + 16, hov ? 0x22FFFFFF : 0x11FFFFFF);
                    g.text(this.font, entry.getName(), sx + 12, y + 4, TEXT_HI, false);
                    if (entry.getSummary() != null) {
                        g.text(this.font, entry.getSummary(), sx + 120, y + 4, TEXT_MED, false);
                    }
                }
                y += 18;
            }
            y += 8;
        }

        if (creditsSections.isEmpty()) {
            g.centeredText(this.font, "Loading credits…", sx + cw / 2, sy + 30, TEXT_LO);
        }
    }

    private void drawStarfield(GuiGraphicsExtractor g, long now) {
        Random rng = new Random(42);
        for (int i = 0; i < 60; i++) {
            int sx = rng.nextInt(this.width);
            int sy = rng.nextInt(this.height);
            float phase = rng.nextFloat() * 6.28f;
            float twinkle = 0.3f + 0.7f * (float)(0.5 + 0.5 * Math.sin(now / 1800.0 + phase));
            int a = (int)(twinkle * 55) & 0xFF;
            g.fill(sx, sy, sx + 1, sy + 1, (a << 24) | 0xFF3333);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        scrollOffset = Mth.clamp(scrollOffset - va * 20, 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean active) {
        double mx = event.x(), my = event.y();
        if (event.button() != 0) return super.mouseClicked(event, active);
        int sw = 120;

        // Close button click
        if (mx >= this.width - 22 && mx < this.width - 4 && my >= 4 && my < 22) {
            onClose();
            return true;
        }

        // Sidebar tabs click
        int tabY = 55;
        for (String tab : TABS) {
            if (mx >= 0 && mx < sw && my >= tabY && my < tabY + 22) {
                currentTab = tab;
                scrollOffset = 0;
                return true;
            }
            tabY += 26;
        }

        return super.mouseClicked(event, active);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == 256 || event.key() == 344) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Custom Entry Helper Classes
    public static class GeneralStatEntry {
        public final Stat<Identifier> stat;
        public final Component name;
        public final String valueStr;
        public final int value;

        public GeneralStatEntry(Stat<Identifier> stat, int value) {
            this.stat = stat;
            this.value = value;
            this.name = Component.translatable("stat." + stat.getValue().toString().replace(':', '.'));
            this.valueStr = stat.format(value);
        }
    }

    public static class ItemStatEntry {
        public final net.minecraft.world.item.Item item;
        public final Component name;
        public int crafted = 0;
        public int used = 0;
        public int broken = 0;
        public int pickedUp = 0;
        public int dropped = 0;

        public ItemStatEntry(net.minecraft.world.item.Item item) {
            this.item = item;
            this.name = new ItemStack(item).getHoverName();
        }
    }

    public static class BlockStatEntry {
        public final net.minecraft.world.level.block.Block block;
        public final Component name;
        public int mined = 0;

        public BlockStatEntry(net.minecraft.world.level.block.Block block) {
            this.block = block;
            this.name = block.getName();
        }
    }

    public static class MobStatEntry {
        public final net.minecraft.world.entity.EntityType<?> entityType;
        public final Component name;
        public int killed = 0;
        public int killedBy = 0;

        public MobStatEntry(net.minecraft.world.entity.EntityType<?> entityType) {
            this.entityType = entityType;
            this.name = entityType.getDescription();
        }
    }
}
