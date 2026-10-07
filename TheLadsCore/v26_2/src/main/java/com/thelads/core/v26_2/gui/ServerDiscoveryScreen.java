package com.thelads.core.v26_2.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Clean curated server discovery matching The Lads Launcher Servers tab.
 */
public class ServerDiscoveryScreen extends Screen {
    public record ServerInfo(String name, String address, String category, String description) {}

    private final Screen parent;
    private final List<ServerInfo> allServers = new ArrayList<>();
    private final List<ServerInfo> filteredServers = new ArrayList<>();
    private final Set<String> savedAddresses = new HashSet<>();
    private EditBox searchBox;
    private int page = 0;
    private String selectedCategory = "All";
    private static final List<String> CATEGORIES = List.of("All", "Minigames", "Survival", "PvP", "Anarchy", "Creative");

    public ServerDiscoveryScreen(Screen parent) {
        super(Component.literal("Discover Servers"));
        this.parent = parent;
        loadKnownServers();
    }

    private void loadKnownServers() {
        allServers.clear();
        try (InputStream in = ServerDiscoveryScreen.class.getResourceAsStream("/thelads/known_servers.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray servers = root.getAsJsonArray("servers");
                for (JsonElement el : servers) {
                    JsonObject s = el.getAsJsonObject();
                    String name = s.get("name").getAsString();
                    String address = s.get("address").getAsString();
                    String category = s.has("category") ? s.get("category").getAsString() : "Community";
                    String description = s.has("description") ? s.get("description").getAsString() : "";
                    allServers.add(new ServerInfo(name, address, category, description));
                }
            }
        } catch (Exception ignored) {}
    }

    private void refreshSaved() {
        savedAddresses.clear();
        if (minecraft == null) return;
        try {
            ServerList list = new ServerList(minecraft);
            list.load();
            for (int i = 0; i < list.size(); i++) {
                ServerData data = list.get(i);
                if (data != null && data.ip != null) {
                    savedAddresses.add(data.ip.trim().toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void init() {
        refreshSaved();
        int contentW = Math.min(width - 40, 520);
        int startX = (width - contentW) / 2;

        String query = searchBox != null ? searchBox.getValue() : "";
        searchBox = new EditBox(font, startX, 36, contentW, 20, Component.literal("Search servers"));
        searchBox.setHint(Component.literal("Search servers by name, address or category..."));
        searchBox.setMaxLength(64);
        searchBox.setValue(query);
        searchBox.setResponder(val -> {
            page = 0;
            updateFilter();
        });
        addRenderableWidget(searchBox);

        // Category buttons
        int catY = 62;
        int catBtnW = (contentW - (CATEGORIES.size() - 1) * 6) / CATEGORIES.size();
        for (int i = 0; i < CATEGORIES.size(); i++) {
            final String cat = CATEGORIES.get(i);
            int bx = startX + i * (catBtnW + 6);
            addRenderableWidget(Button.builder(Component.literal(cat), b -> {
                selectedCategory = cat;
                page = 0;
                updateFilter();
            }).bounds(bx, catY, catBtnW, 18).build());
        }

        updateFilter();
    }

    private void updateFilter() {
        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        filteredServers.clear();
        for (ServerInfo s : allServers) {
            boolean matchCat = selectedCategory.equals("All") || s.category.equalsIgnoreCase(selectedCategory);
            boolean matchQuery = query.isEmpty()
                || s.name.toLowerCase(Locale.ROOT).contains(query)
                || s.address.toLowerCase(Locale.ROOT).contains(query)
                || s.category.toLowerCase(Locale.ROOT).contains(query)
                || s.description.toLowerCase(Locale.ROOT).contains(query);
            if (matchCat && matchQuery) {
                filteredServers.add(s);
            }
        }
        rebuildServerWidgets();
    }

    private void rebuildServerWidgets() {
        // Clear all widgets except search and category buttons
        clearWidgets();
        addRenderableWidget(searchBox);
        int contentW = Math.min(width - 40, 520);
        int startX = (width - contentW) / 2;

        int catY = 62;
        int catBtnW = (contentW - (CATEGORIES.size() - 1) * 6) / CATEGORIES.size();
        for (int i = 0; i < CATEGORIES.size(); i++) {
            final String cat = CATEGORIES.get(i);
            int bx = startX + i * (catBtnW + 6);
            Button btn = addRenderableWidget(Button.builder(Component.literal(cat), b -> {
                selectedCategory = cat;
                page = 0;
                updateFilter();
            }).bounds(bx, catY, catBtnW, 18).build());
            btn.active = !cat.equals(selectedCategory);
        }

        int itemH = 46;
        int maxItems = Math.max(1, (height - 130) / (itemH + 6));
        int totalPages = Math.max(1, (filteredServers.size() + maxItems - 1) / maxItems);
        page = Math.clamp(page, 0, totalPages - 1);

        int listY = 88;
        int startIdx = page * maxItems;
        int endIdx = Math.min(startIdx + maxItems, filteredServers.size());

        for (int i = startIdx; i < endIdx; i++) {
            final ServerInfo server = filteredServers.get(i);
            int itemY = listY + (i - startIdx) * (itemH + 6);

            // Add action buttons on the right of the row
            int btnW = 56;
            int btnH = 20;
            int by = itemY + (itemH - btnH) / 2;
            int joinX = startX + contentW - btnW - 8;
            int addX = joinX - btnW - 6;

            boolean isSaved = savedAddresses.contains(server.address.trim().toLowerCase(Locale.ROOT));

            Button addBtn = addRenderableWidget(Button.builder(Component.literal(isSaved ? "Added" : "+ Add"), b -> {
                addServerToList(server);
                refreshSaved();
                rebuildServerWidgets();
            }).bounds(addX, by, btnW, btnH).build());
            addBtn.active = !isSaved;

            addRenderableWidget(Button.builder(Component.literal("Join"), b -> {
                joinServer(server);
            }).bounds(joinX, by, btnW, btnH).build());
        }

        // Bottom pagination & Back button
        int bottomY = height - 32;
        if (totalPages > 1) {
            Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page--;
                rebuildServerWidgets();
            }).bounds(startX, bottomY, 26, 20).build());
            prev.active = page > 0;

            Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page++;
                rebuildServerWidgets();
            }).bounds(startX + 30, bottomY, 26, 20).build());
            next.active = page + 1 < totalPages;
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(startX + contentW - 74, bottomY, 74, 20).build());
    }

    private void addServerToList(ServerInfo server) {
        if (minecraft == null) return;
        try {
            ServerList list = new ServerList(minecraft);
            list.load();
            list.add(new ServerData(server.name, server.address, ServerData.Type.OTHER), false);
            list.save();
        } catch (Exception ignored) {}
    }

    private void joinServer(ServerInfo server) {
        if (minecraft == null) return;
        try {
            ServerData data = new ServerData(server.name, server.address, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(server.address), data, false, null);
        } catch (Exception ignored) {}
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, LadsPalette.BACKGROUND);
        g.fill(0, 0, width, 2, LadsPalette.ACCENT);

        g.text(font, "SERVER FINDER", 20, 12, LadsPalette.TEXT, false);
        g.text(font, "Curated servers from The Lads Network", 20, 24, LadsPalette.MUTED, false);

        int contentW = Math.min(width - 40, 520);
        int startX = (width - contentW) / 2;
        int itemH = 46;
        int maxItems = Math.max(1, (height - 130) / (itemH + 6));
        int totalPages = Math.max(1, (filteredServers.size() + maxItems - 1) / maxItems);
        int listY = 88;

        int startIdx = page * maxItems;
        int endIdx = Math.min(startIdx + maxItems, filteredServers.size());

        for (int i = startIdx; i < endIdx; i++) {
            ServerInfo server = filteredServers.get(i);
            int itemY = listY + (i - startIdx) * (itemH + 6);

            g.fill(startX, itemY, startX + contentW, itemY + itemH, LadsPalette.CARD);
            g.fill(startX, itemY, startX + 3, itemY + itemH, LadsPalette.ACCENT);

            g.text(font, server.name, startX + 12, itemY + 6, LadsPalette.TEXT, false);
            g.text(font, server.category + "  ·  " + server.address, startX + 12, itemY + 18, LadsPalette.MUTED, false);
            if (!server.description.isEmpty()) {
                g.text(font, server.description, startX + 12, itemY + 30, 0xFFA0A1AA, false);
            }
        }

        if (filteredServers.isEmpty()) {
            g.text(font, "No servers found matching your query.", startX + 12, listY + 16, LadsPalette.MUTED, false);
        }

        if (totalPages > 1) {
            String pageStr = "Page " + (page + 1) + " of " + totalPages;
            g.text(font, pageStr, startX + 64, height - 26, LadsPalette.MUTED, false);
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
