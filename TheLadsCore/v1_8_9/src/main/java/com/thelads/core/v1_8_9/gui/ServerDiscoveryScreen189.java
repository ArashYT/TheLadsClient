package com.thelads.core.v1_8_9.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.gui.LadsPalette;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

public class ServerDiscoveryScreen189 extends GuiScreen {
    public static class ServerInfo {
        public final String name;
        public final String address;
        public final String category;
        public final String description;

        public ServerInfo(String name, String address, String category, String description) {
            this.name = name;
            this.address = address;
            this.category = category;
            this.description = description;
        }
    }

    private final GuiScreen parent;
    private final List<ServerInfo> allServers = new ArrayList<>();
    private final List<ServerInfo> filteredServers = new ArrayList<>();
    private final Set<String> savedAddresses = new HashSet<>();
    private GuiTextField searchBox;
    private int page = 0;
    private String selectedCategory = "All";
    private static final List<String> CATEGORIES = Arrays.asList("All", "Minigames", "Survival", "PvP", "Anarchy", "Creative");

    public ServerDiscoveryScreen189(GuiScreen parent) {
        this.parent = parent;
        loadKnownServers();
    }

    private void loadKnownServers() {
        allServers.clear();
        try (InputStream in = ServerDiscoveryScreen189.class.getResourceAsStream("/thelads/known_servers.json")) {
            if (in != null) {
                JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
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
        if (mc == null) return;
        try {
            ServerList list = new ServerList(mc);
            list.loadServerList();
            for (int i = 0; i < list.countServers(); i++) {
                ServerData data = list.getServerData(i);
                if (data != null && data.serverIP != null) {
                    savedAddresses.add(data.serverIP.trim().toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void initGui() {
        refreshSaved();
        int contentW = Math.min(width - 40, 520);
        int startX = (width - contentW) / 2;

        String query = searchBox != null ? searchBox.getText() : "";
        searchBox = new GuiTextField(0, fontRendererObj, startX, 36, contentW, 20);
        searchBox.setMaxStringLength(64);
        searchBox.setText(query);

        updateFilter();
    }

    private void updateFilter() {
        String query = searchBox != null ? searchBox.getText().trim().toLowerCase(Locale.ROOT) : "";
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
        buttonList.clear();
        int contentW = Math.min(width - 40, 520);
        int startX = (width - contentW) / 2;

        int catY = 62;
        int catBtnW = (contentW - (CATEGORIES.size() - 1) * 6) / CATEGORIES.size();
        for (int i = 0; i < CATEGORIES.size(); i++) {
            final String cat = CATEGORIES.get(i);
            int bx = startX + i * (catBtnW + 6);
            GuiButton btn = new GuiButton(10 + i, bx, catY, catBtnW, 18, cat);
            btn.enabled = !cat.equals(selectedCategory);
            buttonList.add(btn);
        }

        int itemH = 46;
        int maxItems = Math.max(1, (height - 130) / (itemH + 6));
        int totalPages = Math.max(1, (filteredServers.size() + maxItems - 1) / maxItems);
        if (page >= totalPages) page = Math.max(0, totalPages - 1);
        if (page < 0) page = 0;

        int listY = 88;
        int startIdx = page * maxItems;
        int endIdx = Math.min(startIdx + maxItems, filteredServers.size());

        for (int i = startIdx; i < endIdx; i++) {
            final ServerInfo server = filteredServers.get(i);
            int slot = i - startIdx;
            int itemY = listY + slot * (itemH + 6);

            int btnW = 56;
            int btnH = 20;
            int by = itemY + (itemH - btnH) / 2;
            int joinX = startX + contentW - btnW - 8;
            int addX = joinX - btnW - 6;

            boolean isSaved = savedAddresses.contains(server.address.trim().toLowerCase(Locale.ROOT));

            GuiButton addBtn = new GuiButton(100 + slot * 2, addX, by, btnW, btnH, isSaved ? "Added" : "+ Add");
            addBtn.enabled = !isSaved;
            buttonList.add(addBtn);

            GuiButton joinBtn = new GuiButton(100 + slot * 2 + 1, joinX, by, btnW, btnH, "Join");
            buttonList.add(joinBtn);
        }

        int bottomY = height - 32;
        if (totalPages > 1) {
            GuiButton prev = new GuiButton(1, startX, bottomY, 26, 20, "<");
            prev.enabled = page > 0;
            buttonList.add(prev);

            GuiButton next = new GuiButton(2, startX + 30, bottomY, 26, 20, ">");
            next.enabled = page + 1 < totalPages;
            buttonList.add(next);
        }

        buttonList.add(new GuiButton(3, startX + contentW - 74, bottomY, 74, 20, "Done"));
    }

    private void addServerToList(ServerInfo server) {
        if (mc == null) return;
        try {
            ServerList list = new ServerList(mc);
            list.loadServerList();
            list.addServerData(new ServerData(server.name, server.address, false));
            list.saveServerList();
        } catch (Exception ignored) {}
    }

    private void joinServer(ServerInfo server) {
        if (mc == null) return;
        try {
            ServerData data = new ServerData(server.name, server.address, false);
            mc.displayGuiScreen(new GuiConnecting(this, mc, data));
        } catch (Exception ignored) {}
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 3) {
            mc.displayGuiScreen(parent);
        } else if (button.id == 1) {
            page--;
            rebuildServerWidgets();
        } else if (button.id == 2) {
            page++;
            rebuildServerWidgets();
        } else if (button.id >= 10 && button.id < 10 + CATEGORIES.size()) {
            selectedCategory = CATEGORIES.get(button.id - 10);
            page = 0;
            updateFilter();
        } else if (button.id >= 100) {
            int slot = (button.id - 100) / 2;
            boolean isJoin = (button.id % 2) != 0;
            int itemH = 46;
            int maxItems = Math.max(1, (height - 130) / (itemH + 6));
            int idx = page * maxItems + slot;
            if (idx >= 0 && idx < filteredServers.size()) {
                ServerInfo s = filteredServers.get(idx);
                if (isJoin) {
                    joinServer(s);
                } else {
                    addServerToList(s);
                    refreshSaved();
                    rebuildServerWidgets();
                }
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (searchBox != null && searchBox.textboxKeyTyped(typedChar, keyCode)) {
            page = 0;
            updateFilter();
            return;
        }
        if (keyCode == 1) { // ESC
            mc.displayGuiScreen(parent);
            return;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (searchBox != null) {
            searchBox.mouseClicked(mouseX, mouseY, mouseButton);
        }
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Exception ignored) {}
    }

    @Override
    public void updateScreen() {
        if (searchBox != null) searchBox.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, LadsPalette.BACKGROUND);
        drawRect(0, 0, width, 2, LadsPalette.ACCENT);

        fontRendererObj.drawString("SERVER FINDER", 20, 12, LadsPalette.TEXT);
        fontRendererObj.drawString("Curated servers from The Lads Network", 20, 24, LadsPalette.MUTED);

        if (searchBox != null) {
            searchBox.drawTextBox();
            if (searchBox.getText().isEmpty() && !searchBox.isFocused()) {
                fontRendererObj.drawString("Search servers by name, address or category...", searchBox.xPosition + 4, searchBox.yPosition + 6, LadsPalette.MUTED);
            }
        }

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
            int slot = i - startIdx;
            int itemY = listY + slot * (itemH + 6);

            drawRect(startX, itemY, startX + contentW, itemY + itemH, LadsPalette.CARD);
            drawRect(startX, itemY, startX + 3, itemY + itemH, LadsPalette.ACCENT);

            fontRendererObj.drawString(server.name, startX + 10, itemY + 6, LadsPalette.TEXT);
            fontRendererObj.drawString(server.address, startX + 10, itemY + 18, LadsPalette.MUTED);
            if (!server.description.isEmpty()) {
                fontRendererObj.drawString(server.description, startX + 10, itemY + 30, 0xFFAAAAAA);
            }
        }

        if (filteredServers.isEmpty()) {
            String empty = "No servers found matching your search.";
            fontRendererObj.drawString(empty, startX + (contentW - fontRendererObj.getStringWidth(empty)) / 2, listY + 30, LadsPalette.MUTED);
        }

        if (totalPages > 1) {
            String pageStr = "Page " + (page + 1) + " of " + totalPages;
            fontRendererObj.drawString(pageStr, startX + 66, height - 26, LadsPalette.MUTED);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
