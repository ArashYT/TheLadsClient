package com.thelads.core.client.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.social.SocialState;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.client.util.ClientPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AccountSwitcherScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountSwitcherScreen.class);
    private final LiveSession live; // null: this version only records the choice for the next launch
    private final AccountTokens tokens;
    /** The switch under way: its token and preparation run off the render thread, and tick() finishes it on the render thread. */
    private CompletableFuture<Runnable> switching;
    private LadsAccount switchTarget, confirming;
    private LadsAccount addedRow; // an offline row added for a live switch: it stays only if the switch succeeds
    /** The removal under way: the launcher does it off the render thread, and tick() reloads the list after it. */
    private CompletableFuture<Void> removing;
    private LadsAccount removeTarget, confirmingRemoval;
    private final boolean nextLaunchOnly;
    private boolean fromLauncher; // the list is the launcher's published one, not this game folder's own file
    private boolean statusIsError;
    private final List<LadsAccount> accounts = new ArrayList<>();
    private LadsAccount selectedAccount = null;
    private String statusMessage = "Select an account or add a new one";
    private int scrollOffset = 0;
    private Runnable onClose = () -> {};
    private int lastWidth = 800;
    private int lastHeight = 600;

    public AccountSwitcherScreen() {
        this(null, new AccountTokens());
    }

    public AccountSwitcherScreen(LiveSession live) {
        this(live, new AccountTokens());
    }

    public AccountSwitcherScreen(LiveSession live, AccountTokens tokens) {
        // A game the launcher gave no way back to it (a third-party modpack) only records the choice, as a version without live switching.
        this.nextLaunchOnly = tokens.nextLaunchOnly();
        this.live = nextLaunchOnly ? null : live;
        this.tokens = tokens;
        loadAccounts();
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    /** Reads the list afresh (each opening of the switcher): the launcher's published one when there is one, else this game folder's own file. */
    public void loadAccounts() {
        accounts.clear();
        selectedAccount = null;
        fromLauncher = readAccounts(ClientPaths.getLauncherAccountsFile());
        if (!fromLauncher) readAccounts(ClientPaths.getAccountsFile());

        if (accounts.isEmpty() && !fromLauncher) { // a game no Lads launcher manages
            LadsAccount defaultAcc = new LadsAccount("LadsPlayer", UUID.nameUUIDFromBytes("OfflinePlayer:LadsPlayer".getBytes(StandardCharsets.UTF_8)).toString(), "offline", "0");
            defaultAcc.setSelected(true);
            selectedAccount = defaultAcc;
            accounts.add(defaultAcc);
            saveAccounts();
        }

        if (live != null) {
            // PLAYING is what the game really plays as, never a file's word for it.
            LadsAccount playing = null;
            for (LadsAccount account : accounts) if (isLive(account)) { playing = account; break; }
            markSelected(playing);
            if (playing == null && fromLauncher) statusMessage = "Playing as " + live.username() + ", who is not saved in the launcher.";
        } else if (selectedAccount == null && !accounts.isEmpty()) {
            selectedAccount = accounts.get(0);
            selectedAccount.setSelected(true);
        }
    }

    /** Fills the list from one file, the same account twice (by name or id) as one row. False when the file is missing or unreadable. */
    private boolean readAccounts(File file) {
        if (!file.exists()) return false;
        try {
            JsonArray arr = JsonParser.parseString(Files.readString(file.toPath())).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject obj = arr.get(i).getAsJsonObject();
                String username = obj.has("username") ? obj.get("username").getAsString() : "";
                String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : "";
                String type = obj.has("type") ? obj.get("type").getAsString() : "offline";
                boolean sel = obj.has("selected") && obj.get("selected").getAsBoolean();
                if (username.isEmpty() || isListed(username, uuid)) continue;
                LadsAccount acc = new LadsAccount(username, uuid, type, "0"); // never a token from a file: old launchers' files can hold one
                acc.setSelected(sel);
                if (sel && selectedAccount == null) {
                    selectedAccount = acc;
                }
                accounts.add(acc);
            }
            return true;
        } catch (Exception e) {
            accounts.clear();
            selectedAccount = null;
            return false;
        }
    }

    private boolean isListed(String username, String uuid) {
        String id = uuid.replace("-", "").trim();
        for (LadsAccount account : accounts) {
            if (account.getUsername().equalsIgnoreCase(username)) return true;
            if (!id.isEmpty() && account.getUuid().replace("-", "").equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    /** The launcher's list stays the launcher's (it rewrites it from its own store): this only keeps a choice made while it is closed, until it is back. */
    public void saveAccounts() {
        try {
            JsonArray arr = new JsonArray();
            for (LadsAccount acc : accounts) {
                JsonObject obj = new JsonObject();
                obj.addProperty("username", acc.getUsername());
                obj.addProperty("uuid", acc.getUuid());
                obj.addProperty("type", acc.isOffline() ? "offline" : "microsoft");
                obj.addProperty("selected", acc.isSelected());
                arr.add(obj);
            }
            File f = fromLauncher ? ClientPaths.getLauncherAccountsFile() : ClientPaths.getAccountsFile();
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            writeAtomically(f.toPath(), "accounts-", arr.toString());
        } catch (Exception ignored) {}
    }

    public void selectAccount(LadsAccount account) {
        if (account == null) return;
        confirmingRemoval = null;
        if (live != null) {
            switchTo(account);
            return;
        }
        markSelected(account);
        saveAccounts();
        writeNextLaunchRequest(account);
        statusMessage = "Next launch: " + account.getUsername() + ". Restart from the launcher.";
    }

    private void dropAddedRow() {
        if (addedRow != null) accounts.remove(addedRow);
        addedRow = null;
    }

    private void markSelected(LadsAccount account) {
        for (LadsAccount acc : accounts) {
            acc.setSelected(acc == account);
        }
        selectedAccount = account;
    }

    private boolean isLive(LadsAccount account) {
        String accountUuid = account.isOffline()
            ? account.sessionUuid().toString().replace("-", "")
            : account.getUuid().replace("-", "").trim();
        return accountUuid.equalsIgnoreCase(live.uuid().replace("-", "").trim());
    }

    private void switchTo(LadsAccount target) {
        if (switching != null || removing != null) return;
        statusIsError = false;
        if (isLive(target)) {
            confirming = null;
            markSelected(target);
            statusMessage = "Already playing as " + target.getUsername() + ".";
            return;
        }
        if (live.inWorld() && confirming != target) {
            confirming = target;
            statusMessage = "This leaves the world. Click " + target.getUsername() + " again to switch.";
            return;
        }
        confirming = null;
        switchTarget = target;
        statusMessage = "Switching to " + target.getUsername() + "...";
        CompletableFuture<Runnable> work = switching = new CompletableFuture<>();
        boolean sessionIsOffline = LadsAccount.offlineUuid(live.username()).toString().replace("-", "").equalsIgnoreCase(live.uuid().replace("-", "").trim());
        Thread thread = new Thread(() -> {
            try {
                work.complete(live.prepare(target, tokens.fetch(target, sessionIsOffline)));
            } catch (Throwable e) {
                work.completeExceptionally(e);
            }
        }, "Lads Account Switch");
        thread.setDaemon(true);
        thread.start();
    }

    /** Render thread, between frames (each version's screen tick): finishes a switch whose token and preparation are done. */
    public void tick() {
        if (removing != null && removing.isDone()) finishRemoval();
        if (switching == null || !switching.isDone()) return;
        CompletableFuture<Runnable> work = switching;
        LadsAccount target = switchTarget;
        switching = null;
        switchTarget = null;
        Runnable swap;
        try {
            swap = work.join();
        } catch (RuntimeException | Error e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            statusMessage = cause instanceof AccountTokens.Refused ? cause.getMessage()
                : "Minecraft's services refused " + target.getUsername() + ". Try again from the launcher.";
            statusIsError = true;
            LOGGER.warn("[Lads] Account switch to {} failed: {}", target.getUsername(), cause.getClass().getSimpleName());
            dropAddedRow();
            return;
        }
        boolean finished = true;
        try {
            swap.run();
        } catch (RuntimeException | Error e) {
            LOGGER.warn("[Lads] Account switch to {} failed partway: {}", target.getUsername(), e.getClass().getSimpleName());
            // The step can fail on either side of the session change: the game's own session says which.
            if (!target.sessionUuid().toString().replace("-", "").equalsIgnoreCase(live.uuid().replace("-", ""))) {
                statusMessage = "Could not switch to " + target.getUsername() + ". Still playing as " + live.username() + ".";
                statusIsError = true;
                dropAddedRow();
                return;
            }
            finished = false;
        }
        addedRow = null;
        markSelected(target);
        saveAccounts();
        writeNextLaunchRequest(target);
        tokens.switched();
        SocialState.get().onAccountChanged();
        statusMessage = "Now playing as " + target.getUsername() + "." + (finished ? "" : " Restart the game from the launcher to finish the switch.");
        statusIsError = !finished;
        LOGGER.info("[Lads] Account switched without a restart: now {}", target.getUsername());
    }

    /** Forgets a switch under way: what it fetched is dropped and the current account stays. */
    public void cancel() {
        switching = null;
        switchTarget = null;
        removing = null;
        removeTarget = null;
        dropAddedRow();
    }

    public boolean isSwitching() {
        return switching != null || removing != null;
    }

    /** Asked twice, as leaving a world is. The launcher owns the accounts, so it removes them; the account being played stays until another is. */
    public void removeAccount(LadsAccount account) {
        if (account == null || switching != null || removing != null) return;
        statusIsError = false;
        if (live != null && isLive(account)) {
            statusMessage = "You are playing as " + account.getUsername() + ". Switch to another account first.";
            return;
        }
        confirming = null;
        if (confirmingRemoval != account) {
            confirmingRemoval = account;
            statusMessage = "Click again to remove " + account.getUsername() + ".";
            return;
        }
        confirmingRemoval = null;
        removeTarget = account;
        statusMessage = "Removing " + account.getUsername() + "...";
        CompletableFuture<Void> work = removing = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                tokens.remove(account);
                work.complete(null);
            } catch (Throwable e) {
                work.completeExceptionally(e);
            }
        }, "Lads Account Remove");
        thread.setDaemon(true);
        thread.start();
    }

    private void finishRemoval() {
        CompletableFuture<Void> work = removing;
        String name = removeTarget.getUsername();
        removing = null;
        removeTarget = null;
        try {
            work.join();
            loadAccounts();
            statusMessage = "Removed " + name + ".";
            statusIsError = false;
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            statusMessage = cause instanceof AccountTokens.Refused ? cause.getMessage() : "The launcher could not remove " + name + ".";
            statusIsError = true;
        }
    }

    public String getStatusMessage() {
        return statusMessage;
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
        dropAddedRow();
        if (live != null) addedRow = newAcc;
        accounts.add(newAcc);
        selectAccount(newAcc);
    }

    private void writeNextLaunchRequest(LadsAccount acc) {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("username", acc.getUsername());
            obj.addProperty("uuid", acc.isOffline() ? acc.sessionUuid().toString() : acc.getUuid());
            obj.addProperty("type", acc.isOffline() ? "offline" : "microsoft");
            obj.addProperty("lastUpdated", Instant.now().toString());

            File profileFile = ClientPaths.getBaseDir().resolve("lads_next_account.json").toFile();
            if (profileFile.getParentFile() != null) profileFile.getParentFile().mkdirs();
            writeAtomically(profileFile.toPath(), "next-account-", obj.toString());
        } catch (Exception ignored) {}
    }

    private static void writeAtomically(Path file, String prefix, String content) throws java.io.IOException {
        Path temporary = Files.createTempFile(file.getParent(), prefix, ".tmp");
        try {
            Files.writeString(temporary, content);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
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
        g.drawText(statusMessage, cardX + 16, cardY + 12, statusIsError ? 0xFFFF6666 : LadsPalette.MUTED, false);

        // Account list
        int listY = cardY + 32;
        int itemH = 36;
        int maxVisible = Math.max(1, (cardH - 82) / itemH);
        int maxScroll = Math.max(0, accounts.size() - maxVisible);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        for (int i = scrollOffset; i < accounts.size(); i++) {
            LadsAccount acc = accounts.get(i);
            int itemY = listY + ((i - scrollOffset) * itemH);
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
                g.drawText(live != null ? "PLAYING" : "NEXT", cardX + cardW - 65, itemY + 10, LadsPalette.TEXT, false);
            } else if (!nextLaunchOnly) {
                boolean hot = acc == confirmingRemoval || overBin(mouseX, mouseY, cardX, cardW, itemY, itemH);
                TitleScreenTheme.icon(g, "delete", cardX + cardW - BIN_INSET, itemY + 11, 10, hot ? 0xFFFF6666 : LadsPalette.MUTED);
            }
        }

        // More accounts than fit: how far down the list is, beside it
        if (maxScroll > 0) {
            int trackH = maxVisible * itemH - 4, barH = Math.max(8, trackH * maxVisible / accounts.size());
            int barY = listY + (trackH - barH) * scrollOffset / maxScroll;
            g.fill(cardX + cardW - 12, listY, cardX + cardW - 10, listY + trackH, LadsPalette.PANEL);
            g.fill(cardX + cardW - 12, barY, cardX + cardW - 10, barY + barH, LadsPalette.MUTED);
        }

        // Bottom action buttons
        int btnY = cardY + cardH - 38;
        // Add Offline button
        boolean hoverAdd = mouseX >= cardX + 16 && mouseX <= cardX + 180 && mouseY >= btnY && mouseY <= btnY + 24;
        g.fill(cardX + 16, btnY, cardX + 180, btnY + 24, hoverAdd ? LadsPalette.HOVER : LadsPalette.BORDER);
        com.thelads.core.client.gui.MenuGraphics.iconLabel(g, "Add Offline Account", null, cardX + 98, btnY + 7, 164, LadsPalette.TEXT, false);

        // Done button
        boolean hoverDone = mouseX >= cardX + cardW - 120 && mouseX <= cardX + cardW - 16 && mouseY >= btnY && mouseY <= btnY + 24;
        g.fill(cardX + cardW - 120, btnY, cardX + cardW - 16, btnY + 24, hoverDone ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY);
        com.thelads.core.client.gui.MenuGraphics.iconLabel(g, "Done", null, cardX + cardW - 68, btnY + 7, 104, LadsPalette.TEXT, false);
    }

    private static final int BIN_INSET = 32; // the row's bin: this far left of the card's right edge, 10 wide

    private static boolean overBin(double mouseX, double mouseY, int cardX, int cardW, int itemY, int itemH) {
        int binX = cardX + cardW - BIN_INSET;
        return mouseX >= binX - 4 && mouseX <= binX + 14 && mouseY >= itemY && mouseY <= itemY + itemH - 4;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount == 0) return false;
        int height = (lastHeight > 0) ? lastHeight : 600;
        int cardH = Math.min(320, height - 80);
        int maxVisible = Math.max(1, (cardH - 82) / 36);
        int maxScroll = Math.max(0, accounts.size() - maxVisible);
        if (maxScroll == 0) return false;
        int next = Math.max(0, Math.min(maxScroll, scrollOffset - (amount > 0 ? 1 : -1)));
        if (next == scrollOffset) return false;
        scrollOffset = next;
        return true;
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
        int maxVisible = Math.max(1, (cardH - 82) / itemH);
        int maxScroll = Math.max(0, accounts.size() - maxVisible);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        for (int i = scrollOffset; i < accounts.size(); i++) {
            int itemY = listY + ((i - scrollOffset) * itemH);
            if (itemY + itemH > cardY + cardH - 50) break;
            if (mouseX >= cardX + 16 && mouseX <= cardX + cardW - 16 && mouseY >= itemY && mouseY <= itemY + itemH - 4) {
                if (!nextLaunchOnly && !accounts.get(i).isSelected() && overBin(mouseX, mouseY, cardX, cardW, itemY, itemH)) removeAccount(accounts.get(i));
                else selectAccount(accounts.get(i));
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
            cancel();
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
