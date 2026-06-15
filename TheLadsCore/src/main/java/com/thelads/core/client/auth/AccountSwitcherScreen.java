package com.thelads.core.client.auth;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.input.MouseButtonEvent;
import com.thelads.core.mixin.MinecraftClientAccessor;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.nio.file.Files;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

public class AccountSwitcherScreen extends Screen {
    private final Screen parent;
    private String statusMessage = "Select an option below";
    private final MicrosoftAuthenticator auth = new MicrosoftAuthenticator();
    private final List<LadsAccount> accounts = new ArrayList<>();

    public static class LadsAccount {
        public final String username;
        public final String uuid;
        public final String type;
        public final String accessToken;

        public LadsAccount(String username, String uuid, String type, String accessToken) {
            this.username = username;
            this.uuid = uuid;
            this.type = type;
            this.accessToken = accessToken;
        }
    }

    public AccountSwitcherScreen(Screen parent) {
        super(Component.literal("Account Switcher"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        loadAccounts();
    }

    private void loadAccounts() {
        accounts.clear();
        File f = new File("C:/The Lads Client/lads_accounts.json");
        if (f.exists()) {
            try {
                JsonArray arr = JsonParser.parseString(Files.readString(f.toPath())).getAsJsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject obj = arr.get(i).getAsJsonObject();
                    String username = obj.has("username") ? obj.get("username").getAsString() : "";
                    String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : "";
                    String type = obj.has("type") ? obj.get("type").getAsString() : "offline";
                    String accessToken = obj.has("accessToken") ? obj.get("accessToken").getAsString() : "0";
                    if (!username.isEmpty()) {
                        accounts.add(new LadsAccount(username, uuid, type, accessToken));
                    }
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        
        // Fallbacks if list is empty
        if (accounts.isEmpty()) {
            accounts.add(new LadsAccount("LadsPlayer", UUID.nameUUIDFromBytes("OfflinePlayer:LadsPlayer".getBytes()).toString(), "offline", "0"));
            accounts.add(new LadsAccount("TestPlayer", UUID.nameUUIDFromBytes("OfflinePlayer:TestPlayer".getBytes()).toString(), "offline", "0"));
        }
    }

    private void writeActiveProfile(String username, String uuid, String type, String accessToken) {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("username", username);
            obj.addProperty("uuid", uuid);
            obj.addProperty("type", type);
            obj.addProperty("accessToken", accessToken);
            obj.addProperty("skinUrl", "");
            obj.addProperty("capeUrl", "");
            obj.addProperty("lastUpdated", java.time.Instant.now().toString());

            File profileFile = new File("C:/The Lads Client/lads_profile.json");
            Files.writeString(profileFile.toPath(), obj.toString());
        } catch (Exception e) {
            // Ignore
        }
    }

    private void startMicrosoftAuth() {
        statusMessage = "Requesting device code…";
        auth.requestDeviceCode().thenAcceptAsync(info -> {
            statusMessage = "Open " + info.verificationUri + " — code: " + info.userCode;
            auth.pollForToken(info, msg -> statusMessage = msg)
                .thenCompose(ms  -> { statusMessage = "Got MS token, authenticating…"; return auth.authXboxLive(ms); })
                .thenCompose(xbl -> auth.authXsts(xbl))
                .thenCompose(xsts -> {
                    String tok = xsts.get("Token").getAsString();
                    String uhs = xsts.getAsJsonObject("DisplayClaims")
                                     .getAsJsonArray("xui").get(0)
                                     .getAsJsonObject().get("uhs").getAsString();
                    return auth.authMinecraft(uhs, tok);
                })
                .thenCompose(mc -> {
                    String token = mc.get("access_token").getAsString();
                    return auth.getMinecraftProfile(token).thenApply(profile -> {
                        String name = profile.get("name").getAsString();
                        String id   = profile.get("id").getAsString();
                        UUID uuid = UUID.fromString(id.replaceFirst(
                            "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                            "$1-$2-$3-$4-$5"));
                        User msUser = new User(name, uuid, token, Optional.empty(), Optional.empty());
                        ((MinecraftClientAccessor) Minecraft.getInstance()).setUser(msUser);
                        statusMessage = "Logged in as " + name + "!";
                        
                        writeActiveProfile(name, uuid.toString(), "microsoft", token);
                        loadAccounts(); // Reload list
                        return profile;
                    });
                })
                .exceptionally(e -> { statusMessage = "Error: " + e.getMessage(); return null; });
        });
    }

    private void switchAccount(LadsAccount acc) {
        try {
            UUID uuid;
            if (acc.uuid != null && !acc.uuid.isEmpty()) {
                String id = acc.uuid.replace("-", "");
                uuid = UUID.fromString(
                    id.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5"
                    )
                );
            } else {
                uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + acc.username).getBytes());
            }
            
            String token = (acc.accessToken != null && !acc.accessToken.isEmpty() && !acc.accessToken.equals("0"))
                ? acc.accessToken : "0";
                
            User u = new User(acc.username, uuid, token, Optional.empty(), Optional.empty());
            ((MinecraftClientAccessor) Minecraft.getInstance()).setUser(u);
            statusMessage = "Switched to: " + acc.username;
            
            writeActiveProfile(acc.username, uuid.toString(), acc.type, token);
        } catch (Exception ex) {
            statusMessage = "Error: " + ex.getMessage();
        }
    }

    private void drawLadsButton(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        int bg = hover ? 0xCC2A1010 : 0xCC180A0A;
        int border = hover ? 0xFFFF5252 : 0x22FF5555;
        
        g.fill(x, y, x + w, y + h, bg);
        // Draw 1px border
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
        
        int textColor = hover ? 0xFFFFFFFF : 0xFFCCCCCC;
        g.centeredText(this.font, label, x + w / 2, y + h / 2 - 4, textColor);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean active) {
        double mx = event.x(), my = event.y();
        if (event.button() != 0) return super.mouseClicked(event, active);
        
        int cx = this.width / 2;
        int startY = this.height / 2 - 30;
        
        // Login with Microsoft
        if (mx >= cx - 100 && mx < cx + 100 && my >= startY && my < startY + 20) {
            startMicrosoftAuth();
            return true;
        }
        
        int buttonY = startY + 26;
        for (LadsAccount acc : accounts) {
            if (mx >= cx - 100 && mx < cx + 100 && my >= buttonY && my < buttonY + 20) {
                switchAccount(acc);
                return true;
            }
            buttonY += 26;
        }
        
        // Back
        if (mx >= cx - 100 && mx < cx + 100 && my >= buttonY + 8 && my < buttonY + 28) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        
        return super.mouseClicked(event, active);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // No-op: background is drawn manually in extractRenderState
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Draw background
        g.fill(0, 0, this.width, this.height, 0xEE050508);
        g.fill(0, 0, this.width, 2, 0xFFD32F2F);

        // Header Title
        int cx = this.width / 2;
        g.centeredText(this.font, "⇄  Account Switcher", cx, 22, 0xFFFF5252);
        g.fill(cx - 90, 36, cx + 90, 37, 0x22FF5555);

        // Active account card
        User u = Minecraft.getInstance().getUser();
        String activeLabel = "Active: " + u.getName();
        int cardW = 200, cardH = 24;
        int cardX = cx - cardW / 2, cardY = this.height / 2 - 72;
        
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xCC180A0A);
        g.fill(cardX, cardY, cardX + cardW, cardY + 1, 0xFFD32F2F);

        // Draw Player Head
        try {
            net.minecraft.world.entity.player.PlayerSkin skin = Minecraft.getInstance().getSkinManager().createLookup(new com.mojang.authlib.GameProfile(u.getProfileId(), u.getName()), false).get();
            net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(g, skin, cardX + 6, cardY + 4, 16);
        } catch (Exception ex) {
            // Ignore
        }

        g.text(this.font, activeLabel, cardX + 28, cardY + 8, 0xFF00FF88, true);

        // Render themed buttons
        int startY = this.height / 2 - 30;
        drawLadsButton(g, "Login with Microsoft", cx - 100, startY, 200, 20, mouseX, mouseY);

        int buttonY = startY + 26;
        for (LadsAccount acc : accounts) {
            String label = acc.type.equalsIgnoreCase("microsoft") ? acc.username + " (MS)" : acc.username + " (Offline)";
            drawLadsButton(g, label, cx - 100, buttonY, 200, 20, mouseX, mouseY);
            buttonY += 26;
        }

        drawLadsButton(g, "Back", cx - 100, buttonY + 8, 200, 20, mouseX, mouseY);

        // Dynamic status positioning below Back button
        String status = "Status: " + statusMessage;
        int sw = this.font.width(status);
        if (sw > this.width - 20) {
            status = status.substring(0, Math.max(0, (this.width - 20) * status.length() / sw - 3)) + "...";
        }
        g.centeredText(this.font, status, cx, buttonY + 36, 0xFFAAAAAA);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }
}
