package com.thelads.core.client.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.client.util.SkinFetcher;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AccountSwitcherScreen {
    private final List<LadsAccount> accounts = new ArrayList<>();
    private LadsAccount selectedAccount = null;
    private String statusMessage = "Select an account or add a new one";
    private int scrollOffset = 0;
    private Runnable onClose = () -> {};
    private int lastWidth = 800;
    private int lastHeight = 600;

    public AccountSwitcherScreen() {
        loadAccounts();
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void loadAccounts() {
        accounts.clear();
        selectedAccount = null;
        File f = ClientPaths.getAccountsFile();
        if (f.exists()) {
            try {
                String content = Files.readString(f.toPath());
                JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject obj = arr.get(i).getAsJsonObject();
                    String username = obj.has("username") ? obj.get("username").getAsString() : "";
                    String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : "";
                    String type = obj.has("type") ? obj.get("type").getAsString() : "offline";
                    String token = obj.has("accessToken") ? obj.get("accessToken").getAsString() : "0";
                    boolean sel = obj.has("selected") && obj.get("selected").getAsBoolean();
                    if (!username.isEmpty()) {
                        LadsAccount acc = new LadsAccount(username, uuid, type, token);
                        acc.setSelected(sel);
                        if (sel && selectedAccount == null) {
                            selectedAccount = acc;
                        }
                        accounts.add(acc);
                    }
                }
            } catch (Exception ignored) {}
        }

        if (accounts.isEmpty()) {
            LadsAccount defaultAcc = new LadsAccount("LadsPlayer", UUID.nameUUIDFromBytes("OfflinePlayer:LadsPlayer".getBytes(StandardCharsets.UTF_8)).toString(), "offline", "0");
            defaultAcc.setSelected(true);
            selectedAccount = defaultAcc;
            accounts.add(defaultAcc);
            saveAccounts();
        }

        if (selectedAccount == null && !accounts.isEmpty()) {
            selectedAccount = accounts.get(0);
            selectedAccount.setSelected(true);
        }
    }

    public void saveAccounts() {
        try {
            JsonArray arr = new JsonArray();
            for (LadsAccount acc : accounts) {
                JsonObject obj = new JsonObject();
                obj.addProperty("username", acc.getUsername());
                obj.addProperty("uuid", acc.getUuid());
                obj.addProperty("type", acc.getType());
                obj.addProperty("selected", acc.isSelected());
                arr.add(obj);
            }
            File f = ClientPaths.getAccountsFile();
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            Files.writeString(f.toPath(), arr.toString());
        } catch (Exception ignored) {}
    }

    public void selectAccount(LadsAccount account) {
        if (account == null) return;
        for (LadsAccount acc : accounts) {
            acc.setSelected(acc == account);
        }
        selectedAccount = account;
        saveAccounts();
        writeNextLaunchRequest(account);
        statusMessage = "Next launch: " + account.getUsername() + ". Restart from the launcher.";
    }

    public void addOfflineAccount(String username) {
        if (username == null || username.trim().isEmpty()) return;
        String name = username.trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            statusMessage = "Use 3-16 letters, numbers or underscores.";
            return;
        }
        for (LadsAccount account : accounts) {
            if (account.getUsername().equalsIgnoreCase(name)) {
                selectAccount(account);
                return;
            }
        }
        String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
        LadsAccount newAcc = new LadsAccount(name, uuid, "offline", "0");
        accounts.add(newAcc);
        selectAccount(newAcc);
    }

    private void writeNextLaunchRequest(LadsAccount acc) {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("username", acc.getUsername());
            obj.addProperty("uuid", acc.getUuid());
            obj.addProperty("type", acc.getType());
            obj.addProperty("lastUpdated", Instant.now().toString());

            File profileFile = ClientPaths.getBaseDir().resolve("lads_next_account.json").toFile();
            if (profileFile.getParentFile() != null) profileFile.getParentFile().mkdirs();
            Path temporary = Files.createTempFile(profileFile.toPath().getParent(), "next-account-", ".tmp");
            try {
                Files.writeString(temporary, obj.toString());
                Files.move(temporary, profileFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
        } catch (Exception ignored) {}
    }

    public void render(LadsGraphics g, int mouseX, int mouseY) {
        this.lastWidth = g.getScaledWidth();
        this.lastHeight = g.getScaledHeight();
        int width = this.lastWidth;
        int height = this.lastHeight;

        // Opaque Lads background
        g.fill(0, 0, width, height, LadsPalette.BACKGROUND);

        // Header
        g.fill(0, 0, width, 40, LadsPalette.PANEL);
        g.drawCenteredText("THE LADS CLIENT — ACCOUNT SWITCHER", width / 2, 14, LadsPalette.TEXT, true);

        // Center card
        int cardW = Math.min(500, width - 40);
        int cardH = Math.min(320, height - 80);
        int cardX = (width - cardW) / 2;
        int cardY = 50;

        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, LadsPalette.CARD);
        g.fill(cardX, cardY, cardX + cardW, cardY + 2, LadsPalette.ACCENT);

        // Status bar
        g.drawText(statusMessage, cardX + 16, cardY + 12, LadsPalette.MUTED, false);

        // Account list
        int listY = cardY + 32;
        int itemH = 36;
        for (int i = 0; i < accounts.size(); i++) {
            LadsAccount acc = accounts.get(i);
            int itemY = listY + (i * itemH);
            if (itemY + itemH > cardY + cardH - 50) break;

            boolean isHover = mouseX >= cardX + 16 && mouseX <= cardX + cardW - 16 && mouseY >= itemY && mouseY <= itemY + itemH - 4;
            boolean isSel = acc.isSelected();

            int bgCol = isSel ? LadsPalette.PRIMARY_PRESSED : (isHover ? LadsPalette.HOVER : LadsPalette.PANEL);
            g.fill(cardX + 16, itemY, cardX + cardW - 16, itemY + itemH - 4, bgCol);

            // Draw player head
            g.drawHead(acc.getUsername(), acc.getUuid(), cardX + 22, itemY + 4, 24);

            // Account details
            g.drawText(acc.getUsername(), cardX + 54, itemY + 6, isSel ? LadsPalette.TEXT : LadsPalette.TEXT, false);
            String badge = "microsoft".equalsIgnoreCase(acc.getType()) ? "Microsoft" : "Offline";
            int badgeCol = "microsoft".equalsIgnoreCase(acc.getType()) ? LadsPalette.ACCENT : LadsPalette.MUTED;
            g.drawText(badge, cardX + 54, itemY + 18, badgeCol, false);

            if (isSel) {
                g.drawText("NEXT", cardX + cardW - 65, itemY + 10, LadsPalette.TEXT, false);
            }
        }

        // Bottom action buttons
        int btnY = cardY + cardH - 38;
        // Add Offline button
        boolean hoverAdd = mouseX >= cardX + 16 && mouseX <= cardX + 180 && mouseY >= btnY && mouseY <= btnY + 24;
        g.fill(cardX + 16, btnY, cardX + 180, btnY + 24, hoverAdd ? LadsPalette.HOVER : LadsPalette.BORDER);
        g.drawCenteredText("+ Add Offline Account", cardX + 98, btnY + 7, LadsPalette.TEXT, false);

        // Done button
        boolean hoverDone = mouseX >= cardX + cardW - 120 && mouseX <= cardX + cardW - 16 && mouseY >= btnY && mouseY <= btnY + 24;
        g.fill(cardX + cardW - 120, btnY, cardX + cardW - 16, btnY + 24, hoverDone ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
        g.drawCenteredText("Done", cardX + cardW - 68, btnY + 7, LadsPalette.TEXT, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int width = (lastWidth > 0) ? lastWidth : 800;
        int height = (lastHeight > 0) ? lastHeight : 600;
        int cardW = Math.min(500, width - 40);
        int cardH = Math.min(320, height - 80);
        int cardX = (width - cardW) / 2;
        int cardY = 50;

        // Check account selection
        int listY = cardY + 32;
        int itemH = 36;
        for (int i = 0; i < accounts.size(); i++) {
            int itemY = listY + (i * itemH);
            if (itemY + itemH > cardY + cardH - 50) break;
            if (mouseX >= cardX + 16 && mouseX <= cardX + cardW - 16 && mouseY >= itemY && mouseY <= itemY + itemH - 4) {
                selectAccount(accounts.get(i));
                return true;
            }
        }

        // Check Add Offline Account
        int btnY = cardY + cardH - 38;
        if (mouseX >= cardX + 16 && mouseX <= cardX + 180 && mouseY >= btnY && mouseY <= btnY + 24) {
            String newName = "Player" + (accounts.size() + 1);
            addOfflineAccount(newName);
            return true;
        }

        // Check Done
        if (mouseX >= cardX + cardW - 120 && mouseX <= cardX + cardW - 16 && mouseY >= btnY && mouseY <= btnY + 24) {
            if (onClose != null) onClose.run();
            return true;
        }

        return false;
    }

    public List<LadsAccount> getAccounts() {
        return accounts;
    }

    public LadsAccount getSelectedAccount() {
        return selectedAccount;
    }
}
