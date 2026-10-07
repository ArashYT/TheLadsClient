package com.thelads.core.v26_2.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.gui.LadsPalette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.FormattedCharSequence;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clean curated server discovery matching The Lads Launcher Servers tab,
 * with server logos and live MOTDs.
 */
public class ServerDiscoveryScreen extends Screen {
    public record ServerInfo(String name, String address, String category, String description) {}

    private static final Map<String, ServerData> SERVER_DATA_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> ICON_BYTES_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING_PINGS = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> PENDING_ICON_FETCH = Collections.synchronizedSet(new HashSet<>());

    private final Screen parent;
    private final List<ServerInfo> allServers = new ArrayList<>();
    private final List<ServerInfo> filteredServers = new ArrayList<>();
    private final Set<String> savedAddresses = new HashSet<>();
    private final Map<String, FaviconTexture> faviconCache = new HashMap<>();
    private final Set<String> uploadedFavicons = new HashSet<>();
    private ServerStatusPinger pinger;

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
                ServerData saved = list.get(i);
                if (saved != null && saved.ip != null) {
                    String ip = saved.ip.trim().toLowerCase(Locale.ROOT);
                    savedAddresses.add(ip);
                    ServerData cached = SERVER_DATA_CACHE.computeIfAbsent(ip, k -> new ServerData(saved.name, saved.ip, ServerData.Type.OTHER));
                    if (saved.getIconBytes() != null) {
                        cached.setIconBytes(saved.getIconBytes());
                        ICON_BYTES_CACHE.put(ip, saved.getIconBytes());
                    }
                    if (saved.motd != null) {
                        cached.motd = saved.motd;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void init() {
        if (pinger != null) {
            pinger.removeAll();
        }
        pinger = new ServerStatusPinger();
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

    @Override
    public void tick() {
        super.tick();
        if (pinger != null) {
            pinger.tick();
        }
    }

    private void updateFilter() {
        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        filteredServers.clear();
        for (ServerInfo s : allServers) {
            boolean matchCat = selectedCategory.equals("All") || s.category().equalsIgnoreCase(selectedCategory);
            boolean matchQuery = query.isEmpty()
                || s.name().toLowerCase(Locale.ROOT).contains(query)
                || s.address().toLowerCase(Locale.ROOT).contains(query)
                || s.category().toLowerCase(Locale.ROOT).contains(query)
                || s.description().toLowerCase(Locale.ROOT).contains(query);
            if (matchCat && matchQuery) {
                filteredServers.add(s);
            }
        }
        rebuildServerWidgets();
    }

    private void ensureServerQueried(ServerInfo server) {
        String key = server.address().trim().toLowerCase(Locale.ROOT);
        ServerData data = SERVER_DATA_CACHE.computeIfAbsent(key, k -> new ServerData(server.name(), server.address(), ServerData.Type.OTHER));

        if (data.getIconBytes() != null && !ICON_BYTES_CACHE.containsKey(key)) {
            ICON_BYTES_CACHE.put(key, data.getIconBytes());
        }

        // Native ServerStatusPinger query
        if (pinger != null && !PENDING_PINGS.contains(key) && (data.state() == ServerData.State.INITIAL || data.motd == null)) {
            PENDING_PINGS.add(key);
            try {
                pinger.pingServer(data, () -> {
                    PENDING_PINGS.remove(key);
                    if (data.getIconBytes() != null) {
                        ICON_BYTES_CACHE.put(key, data.getIconBytes());
                        uploadedFavicons.remove(key);
                    }
                }, () -> {
                    PENDING_PINGS.remove(key);
                }, EventLoopGroupHolder.remote(false));
            } catch (Exception e) {
                PENDING_PINGS.remove(key);
            }
        }

        // Fast fallback CDN icon fetch
        if (!ICON_BYTES_CACHE.containsKey(key) && !PENDING_ICON_FETCH.contains(key)) {
            PENDING_ICON_FETCH.add(key);
            Thread iconThread = new Thread(() -> {
                try {
                    String urlStr = "https://api.mcsrvstat.us/icon/" + URLEncoder.encode(server.address(), StandardCharsets.UTF_8);
                    HttpURLConnection conn = (HttpURLConnection) new URI(urlStr).toURL().openConnection();
                    conn.setConnectTimeout(3500);
                    conn.setReadTimeout(3500);
                    conn.setRequestProperty("User-Agent", "TheLadsClient/1.8.2");
                    if (conn.getResponseCode() == 200) {
                        try (InputStream in = conn.getInputStream()) {
                            byte[] bytes = in.readAllBytes();
                            if (bytes.length > 0) {
                                try (NativeImage test = NativeImage.read(bytes)) {
                                    if (test.getWidth() == 64 && test.getHeight() == 64) {
                                        ICON_BYTES_CACHE.put(key, bytes);
                                        data.setIconBytes(bytes);
                                        uploadedFavicons.remove(key);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    PENDING_ICON_FETCH.remove(key);
                }
            }, "LadsServerIcon-" + key);
            iconThread.setDaemon(true);
            iconThread.start();
        }
    }

    private void rebuildServerWidgets() {
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

            int btnW = 56;
            int btnH = 20;
            int by = itemY + (itemH - btnH) / 2;
            int joinX = startX + contentW - btnW - 8;
            int addX = joinX - btnW - 6;

            boolean isSaved = savedAddresses.contains(server.address().trim().toLowerCase(Locale.ROOT));

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
            ServerData toAdd = new ServerData(server.name(), server.address(), ServerData.Type.OTHER);
            String key = server.address().trim().toLowerCase(Locale.ROOT);
            ServerData cached = SERVER_DATA_CACHE.get(key);
            if (cached != null) {
                if (cached.motd != null) toAdd.motd = cached.motd;
                if (cached.getIconBytes() != null) toAdd.setIconBytes(cached.getIconBytes());
            }
            list.add(toAdd, false);
            list.save();
        } catch (Exception ignored) {}
    }

    private void joinServer(ServerInfo server) {
        if (minecraft == null) return;
        try {
            ServerData data = new ServerData(server.name(), server.address(), ServerData.Type.OTHER);
            String key = server.address().trim().toLowerCase(Locale.ROOT);
            ServerData cached = SERVER_DATA_CACHE.get(key);
            if (cached != null) {
                if (cached.motd != null) data.motd = cached.motd;
                if (cached.getIconBytes() != null) data.setIconBytes(cached.getIconBytes());
            }
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(server.address()), data, false, null);
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
            String key = server.address().trim().toLowerCase(Locale.ROOT);
            ensureServerQueried(server);

            ServerData data = SERVER_DATA_CACHE.get(key);
            int itemY = listY + (i - startIdx) * (itemH + 6);

            g.fill(startX, itemY, startX + contentW, itemY + itemH, LadsPalette.CARD);
            g.fill(startX, itemY, startX + 3, itemY + itemH, LadsPalette.ACCENT);

            // Server Logo (32x32)
            int iconX = startX + 10;
            int iconY = itemY + (itemH - 32) / 2;

            FaviconTexture fav = faviconCache.computeIfAbsent(key, k -> FaviconTexture.forServer(minecraft.getTextureManager(), server.address()));
            byte[] iconBytes = ICON_BYTES_CACHE.get(key);
            if (iconBytes != null && !uploadedFavicons.contains(key) && !fav.isClosed()) {
                try {
                    NativeImage img = NativeImage.read(iconBytes);
                    if (img.getWidth() == 64 && img.getHeight() == 64) {
                        fav.upload(img);
                        uploadedFavicons.add(key);
                    } else {
                        img.close();
                    }
                } catch (Exception ignored) {}
            }

            g.fill(iconX - 1, iconY - 1, iconX + 33, iconY + 33, 0x33000000);
            g.blit(RenderPipelines.GUI_TEXTURED, fav.textureLocation(), iconX, iconY, 0.0F, 0.0F, 32, 32, 32, 32);

            // Text column to the right of the icon
            int textX = iconX + 32 + 8;
            int textMaxW = contentW - (textX - startX) - 130;

            // Line 1: Server name & Category / Address
            g.text(font, server.name(), textX, itemY + 5, LadsPalette.TEXT, false);
            int nameW = font.width(server.name());
            String sub = "  ·  " + server.category() + "  (" + server.address() + ")";
            g.text(font, sub, textX + nameW, itemY + 5, LadsPalette.MUTED, false);

            // Line 2 & 3: MOTD!
            if (data != null && data.motd != null) {
                List<FormattedCharSequence> lines = font.split(data.motd, textMaxW);
                if (!lines.isEmpty()) {
                    g.text(font, lines.get(0), textX, itemY + 17, 0xFFE0E0E0, false);
                }
                if (lines.size() > 1) {
                    g.text(font, lines.get(1), textX, itemY + 29, 0xFFA0A1AA, false);
                } else if (!server.description().isEmpty()) {
                    g.text(font, server.description(), textX, itemY + 29, 0xFF70727D, false);
                }
            } else {
                if (!server.description().isEmpty()) {
                    g.text(font, server.description(), textX, itemY + 17, 0xFFA0A1AA, false);
                }
                long animTick = (System.currentTimeMillis() / 360L) % 4L;
                String dots = animTick == 0 ? "" : animTick == 1 ? "." : animTick == 2 ? ".." : "...";
                g.text(font, "Pinging server" + dots, textX, itemY + 29, 0xFF7E8B9D, false);
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
        if (pinger != null) {
            pinger.removeAll();
        }
        for (FaviconTexture fav : faviconCache.values()) {
            fav.close();
        }
        faviconCache.clear();
        uploadedFavicons.clear();
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
