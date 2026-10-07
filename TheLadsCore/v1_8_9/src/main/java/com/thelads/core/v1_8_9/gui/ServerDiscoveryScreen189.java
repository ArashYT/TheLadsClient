package com.thelads.core.v1_8_9.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.gui.LadsPalette;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.network.OldServerPinger;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

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

    private static final Map<String, ServerData> SERVER_DATA_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, BufferedImage> PENDING_IMAGES = new ConcurrentHashMap<>();
    private static final Map<String, ResourceLocation> ICON_LOCATIONS = new ConcurrentHashMap<>();
    private static final Set<String> PENDING_PINGS = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> PENDING_ICON_FETCH = Collections.synchronizedSet(new HashSet<>());
    private static final ResourceLocation UNKNOWN_SERVER = new ResourceLocation("textures/misc/unknown_server.png");
    private static final ExecutorService PING_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "LadsServerPinger189");
        t.setDaemon(true);
        return t;
    });

    private final GuiScreen parent;
    private final List<ServerInfo> allServers = new ArrayList<>();
    private final List<ServerInfo> filteredServers = new ArrayList<>();
    private final Set<String> savedAddresses = new HashSet<>();
    private final OldServerPinger pinger = new OldServerPinger();

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

    private static void decodeIconB64(String key, String b64) {
        if (b64 == null || b64.isEmpty()) return;
        try {
            if (b64.startsWith("data:image/png;base64,")) {
                b64 = b64.substring("data:image/png;base64,".length());
            }
            byte[] bytes = Base64.getDecoder().decode(b64);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
                PENDING_IMAGES.put(key, img);
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
                ServerData saved = list.getServerData(i);
                if (saved != null && saved.serverIP != null) {
                    String ip = saved.serverIP.trim().toLowerCase(Locale.ROOT);
                    savedAddresses.add(ip);
                    ServerData cached = SERVER_DATA_CACHE.computeIfAbsent(ip, k -> new ServerData(saved.serverName, saved.serverIP, false));
                    if (saved.getBase64EncodedIconData() != null) {
                        cached.setBase64EncodedIconData(saved.getBase64EncodedIconData());
                        decodeIconB64(ip, saved.getBase64EncodedIconData());
                    }
                    if (saved.serverMOTD != null) {
                        cached.serverMOTD = saved.serverMOTD;
                    }
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

    private void ensureServerQueried(ServerInfo server) {
        String key = server.address.trim().toLowerCase(Locale.ROOT);
        ServerData data = SERVER_DATA_CACHE.computeIfAbsent(key, k -> new ServerData(server.name, server.address, false));

        if (data.getBase64EncodedIconData() != null && !ICON_LOCATIONS.containsKey(key) && !PENDING_IMAGES.containsKey(key)) {
            decodeIconB64(key, data.getBase64EncodedIconData());
        }

        // Native OldServerPinger query
        if (!PENDING_PINGS.contains(key) && (!data.field_78841_f || data.serverMOTD == null)) {
            data.field_78841_f = true;
            data.pingToServer = -2L;
            PENDING_PINGS.add(key);
            PING_EXECUTOR.submit(() -> {
                try {
                    pinger.ping(data);
                    if (data.getBase64EncodedIconData() != null) {
                        decodeIconB64(key, data.getBase64EncodedIconData());
                    }
                } catch (UnknownHostException e) {
                    data.pingToServer = -1L;
                    data.serverMOTD = "\u00a7cCan't resolve hostname";
                } catch (Exception e) {
                    data.pingToServer = -1L;
                    data.serverMOTD = "\u00a7cCan't connect to server.";
                } finally {
                    PENDING_PINGS.remove(key);
                }
            });
        }

        // Fast fallback CDN icon fetch
        if (!ICON_LOCATIONS.containsKey(key) && !PENDING_IMAGES.containsKey(key) && !PENDING_ICON_FETCH.contains(key)) {
            PENDING_ICON_FETCH.add(key);
            Thread iconThread = new Thread(() -> {
                try {
                    String urlStr = "https://api.mcsrvstat.us/icon/" + URLEncoder.encode(server.address, "UTF-8");
                    HttpURLConnection conn = (HttpURLConnection) new URI(urlStr).toURL().openConnection();
                    conn.setConnectTimeout(3500);
                    conn.setReadTimeout(3500);
                    conn.setRequestProperty("User-Agent", "TheLadsClient/1.8.2");
                    if (conn.getResponseCode() == 200) {
                        try (InputStream in = conn.getInputStream()) {
                            BufferedImage img = ImageIO.read(in);
                            if (img != null && img.getWidth() > 0 && img.getHeight() > 0) {
                                PENDING_IMAGES.put(key, img);
                            }
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    PENDING_ICON_FETCH.remove(key);
                }
            }, "LadsServerIcon189-" + key);
            iconThread.setDaemon(true);
            iconThread.start();
        }
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
            ServerData toAdd = new ServerData(server.name, server.address, false);
            String key = server.address.trim().toLowerCase(Locale.ROOT);
            ServerData cached = SERVER_DATA_CACHE.get(key);
            if (cached != null) {
                if (cached.serverMOTD != null) toAdd.serverMOTD = cached.serverMOTD;
                if (cached.getBase64EncodedIconData() != null) toAdd.setBase64EncodedIconData(cached.getBase64EncodedIconData());
            }
            list.addServerData(toAdd);
            list.saveServerList();
        } catch (Exception ignored) {}
    }

    private void joinServer(ServerInfo server) {
        if (mc == null) return;
        try {
            ServerData data = new ServerData(server.name, server.address, false);
            String key = server.address.trim().toLowerCase(Locale.ROOT);
            ServerData cached = SERVER_DATA_CACHE.get(key);
            if (cached != null) {
                if (cached.serverMOTD != null) data.serverMOTD = cached.serverMOTD;
                if (cached.getBase64EncodedIconData() != null) data.setBase64EncodedIconData(cached.getBase64EncodedIconData());
            }
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
        try {
            pinger.pingPendingNetworks();
        } catch (Exception ignored) {}
    }

    @Override
    public void onGuiClosed() {
        try {
            pinger.clearPendingNetworks();
        } catch (Exception ignored) {}
        super.onGuiClosed();
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
            String key = server.address.trim().toLowerCase(Locale.ROOT);
            ensureServerQueried(server);

            ServerData data = SERVER_DATA_CACHE.get(key);
            int slot = i - startIdx;
            int itemY = listY + slot * (itemH + 6);

            drawRect(startX, itemY, startX + contentW, itemY + itemH, LadsPalette.CARD);
            drawRect(startX, itemY, startX + 3, itemY + itemH, LadsPalette.ACCENT);

            // Server Logo (32x32)
            int iconX = startX + 10;
            int iconY = itemY + (itemH - 32) / 2;

            ResourceLocation loc = ICON_LOCATIONS.get(key);
            if (loc == null) {
                BufferedImage img = PENDING_IMAGES.remove(key);
                if (img != null && mc != null) {
                    try {
                        DynamicTexture dynTex = new DynamicTexture(img);
                        loc = mc.getTextureManager().getDynamicTextureLocation("lads_srv_" + key.replaceAll("[^a-zA-Z0-9_]", "_"), dynTex);
                        ICON_LOCATIONS.put(key, loc);
                    } catch (Exception ignored) {}
                }
            }

            drawRect(iconX - 1, iconY - 1, iconX + 33, iconY + 33, 0x33000000);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            mc.getTextureManager().bindTexture(loc != null ? loc : UNKNOWN_SERVER);
            Gui.drawModalRectWithCustomSizedTexture(iconX, iconY, 0.0F, 0.0F, 32, 32, 32.0F, 32.0F);

            // Text column to the right of the icon
            int textX = iconX + 32 + 8;
            int textMaxW = contentW - (textX - startX) - 130;

            // Line 1: Server name & Category / Address
            fontRendererObj.drawString(server.name, textX, itemY + 5, LadsPalette.TEXT);
            int nameW = fontRendererObj.getStringWidth(server.name);
            String sub = "  \u00b7  " + server.category + "  (" + server.address + ")";
            fontRendererObj.drawString(sub, textX + nameW, itemY + 5, LadsPalette.MUTED);

            // Line 2 & 3: MOTD!
            if (data != null && data.serverMOTD != null && !data.serverMOTD.isEmpty()) {
                List<String> lines = fontRendererObj.listFormattedStringToWidth(data.serverMOTD, textMaxW);
                if (!lines.isEmpty()) {
                    fontRendererObj.drawString(lines.get(0), textX, itemY + 17, 0xFFE0E0E0);
                }
                if (lines.size() > 1) {
                    fontRendererObj.drawString(lines.get(1), textX, itemY + 29, 0xFFA0A1AA);
                } else if (!server.description.isEmpty()) {
                    fontRendererObj.drawString(server.description, textX, itemY + 29, 0xFF70727D);
                }
            } else {
                if (!server.description.isEmpty()) {
                    fontRendererObj.drawString(server.description, textX, itemY + 17, 0xFFA0A1AA);
                }
                fontRendererObj.drawString("Pinging server...", textX, itemY + 29, 0xFF60626D);
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
