package com.thelads.core.v26_2.adapter;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.GameTimeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

public class VanillaGameBridge26 implements LadsGameBridge {
    @Override public boolean hasMinimap(){return com.thelads.core.v26_2.feature.MinimapIntegration.available();}
    @Override public int[] minimapSize(){return com.thelads.core.v26_2.feature.MinimapIntegration.size();}
    @Override public void positionMinimap(int x,int y){com.thelads.core.v26_2.feature.MinimapIntegration.position(x,y);}
    @Override public VoiceChatState voiceChat(){return com.thelads.core.v26_2.feature.VoiceChatIntegration.state();}
    @Override public boolean hasPaperDollRenderer() {
        return com.thelads.core.v26_2.feature.paperdoll.PaperDoll26.active();
    }
    private final GameTimeText gameTimeText = new GameTimeText();
    private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR_SLOTS = {
        net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.LEGS,
        net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.HEAD
    };
    private Object armorPlayer;
    private int armorTick = Integer.MIN_VALUE;
    private List<ArmorPiece> armor = List.of();
    private Object scoreboardPlayer;
    private int scoreboardTick = Integer.MIN_VALUE;
    private ScoreboardSnapshot scoreboardSnapshot;
    private static final java.util.Comparator<net.minecraft.world.scores.PlayerScoreEntry> SCORE_ORDER =
        java.util.Comparator.comparingInt(net.minecraft.world.scores.PlayerScoreEntry::value).reversed()
            .thenComparing(net.minecraft.world.scores.PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    @Override
    public ScoreboardSnapshot getScoreboard() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            scoreboardPlayer = null;
            scoreboardSnapshot = null;
            return null;
        }
        if (scoreboardPlayer == mc.player && scoreboardTick == mc.player.tickCount) return scoreboardSnapshot;
        scoreboardPlayer = mc.player;
        scoreboardTick = mc.player.tickCount;
        var board = mc.level.getScoreboard();
        var team = board.getPlayersTeam(mc.player.getScoreboardName());
        net.minecraft.world.scores.Objective objective = null;
        if (team != null && team.getColor().isPresent()) {
            objective = board.getDisplayObjective(team.getColor().get().displaySlot());
        }
        if (objective == null) objective = board.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
        if (objective == null) {
            scoreboardSnapshot = null;
            return null;
        }
        var format = objective.numberFormatOrDefault(net.minecraft.network.chat.numbers.StyledFormat.SIDEBAR_DEFAULT);
        List<ScoreLine> lines = board.listPlayerScores(objective).stream()
            .filter(score -> !score.isHidden()).sorted(SCORE_ORDER).limit(15)
            .map(score -> new ScoreLine(legacy(net.minecraft.world.scores.PlayerTeam.formatNameForTeam(
                board.getPlayersTeam(score.owner()), score.ownerName())),
                legacy(score.formatValue(format)))).toList();
        // Keep blank/custom number-format display text; never substitute the raw integer score.
        scoreboardSnapshot = new ScoreboardSnapshot(legacy(objective.getDisplayName()), lines);
        return scoreboardSnapshot;
    }

    /** The text with its colours and styles as section-sign codes, which the Lads HUD draws (getString() dropped them before 1.7.0). */
    private static String legacy(net.minecraft.network.chat.Component text) {
        StringBuilder out = new StringBuilder();
        text.visit((style, part) -> {
            if (part.isEmpty()) return java.util.Optional.empty();
            out.append("\u00a7r");
            if (style.getColor() != null) out.append('\u00a7').append(com.thelads.core.client.LegacyText.code(style.getColor().getValue()));
            if (style.isBold()) out.append("\u00a7l");
            if (style.isItalic()) out.append("\u00a7o");
            if (style.isUnderlined()) out.append("\u00a7n");
            if (style.isStrikethrough()) out.append("\u00a7m");
            if (style.isObfuscated()) out.append("\u00a7k");
            out.append(part);
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out.toString();
    }

    @Override
    public String getBiomeId() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || mc.player == null ? null
            : mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                .map(key -> key.identifier().toString()).orElse(null);
    }

    @Override
    public float getYaw() {
        var player = Minecraft.getInstance().player;
        if (player == null) return -1;
        float yaw = player.getYRot() % 360;
        return yaw < 0 ? yaw + 360 : yaw;
    }

    @Override
    public float getPitch() {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        return player.getXRot();
    }

    @Override
    public int getClientTicks() {
        var player = Minecraft.getInstance().player;
        if (player != null) return player.tickCount;
        return (int) (System.currentTimeMillis() / 50);
    }

    @Override
    public float getAbsorption() {
        var player = Minecraft.getInstance().player;
        return player == null ? -1 : player.getAbsorptionAmount();
    }

    @Override
    public float getSaturation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        // Vanilla health packets do not keep saturation exact between hunger/health changes.
        // The integrated server owns exact values; no invented multiplayer value.
        var server = mc.getSingleplayerServer();
        if (server == null) return -1;
        var authoritativePlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
        return authoritativePlayer == null ? -1 : authoritativePlayer.getFoodData().getSaturationLevel();
    }

    @Override
    public int bossBarCount() {
        var mc = Minecraft.getInstance();
        if (mc.gui == null || mc.gui.hud == null || mc.gui.hud.getBossOverlay() == null) return 0;
        try {
            var map = ((com.thelads.core.v26_2.mixin.BossBarAccessor) mc.gui.hud.getBossOverlay()).ladsEvents();
            if (map == null) return 0;
            synchronized (map) {
                return map.size();
            }
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Override
    public boolean offhandSlotLeft() {
        var player = Minecraft.getInstance().player;
        return player != null && !player.getOffhandItem().isEmpty() && player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
    }

    @Override
    public List<ArmorPiece> getArmor() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            armorPlayer = null;
            armor = List.of();
            return armor;
        }
        if (armorPlayer == player && armorTick == player.tickCount) return armor;
        armorPlayer = player;
        armorTick = player.tickCount;
        List<ArmorPiece> result = new ArrayList<>(4);
        for (var slot : ARMOR_SLOTS) {
            var stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            int maximum = stack.isDamageableItem() ? stack.getMaxDamage() : 0;
            result.add(new ArmorPiece(stack.getHoverName().getString(),
                maximum <= 0 ? 0 : Math.max(0, maximum - stack.getDamageValue()), maximum));
        }
        armor = List.copyOf(result);
        return armor;
    }

    @Override
    public boolean isIngame() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null;
    }

    @Override
    public int getFps() {
        return Minecraft.getInstance().getFps();
    }

    @Override
    public int getRenderedChunks() {
        Minecraft mc = Minecraft.getInstance();
        return mc.levelExtractor != null ? mc.levelExtractor.countRenderedSections() : 0;
    }

    @Override
    public boolean hasPlayer() {
        return Minecraft.getInstance().player != null;
    }

    @Override
    public String getPlayerName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getGameProfile().name() : "";
    }

    @Override
    public int getPlayerX() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getBlockX() : 0;
    }

    @Override
    public int getPlayerY() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getBlockY() : 0;
    }

    @Override
    public int getPlayerZ() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getBlockZ() : 0;
    }

    @Override
    public String getBiomeName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            BlockPos pos = mc.player.blockPosition();
            var biomeHolder = mc.level.getBiome(pos);
            if (biomeHolder.isBound()) {
                return biomeHolder.unwrapKey().<String>map(k -> k.identifier().getPath()).orElse("Plains");
            }
        }
        return "Plains";
    }

    @Override
    public String getPlayerDirection() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            return mc.player.getDirection().getName();
        }
        return "North";
    }

    @Override
    public long getDayCount() {
        Minecraft mc = Minecraft.getInstance();
        return (mc.level != null) ? (mc.level.getOverworldClockTime() / 24000L) : 0;
    }

    @Override
    public String getGameTime() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "12:00";
        return gameTimeText.get(mc.level.getOverworldClockTime());
    }

    @Override
    public float getHealth() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getHealth() : 20.0f;
    }

    @Override
    public float getMaxHealth() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getMaxHealth() : 20.0f;
    }

    @Override
    public int getFoodLevel() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getFoodData().getFoodLevel() : 20;
    }

    @Override
    public int getXpLevel() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.experienceLevel : 0;
    }

    @Override
    public float getXpProgress() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.experienceProgress : 0.0f;
    }

    @Override
    public int getPing() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null && mc.player != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) {
                return info.getLatency();
            }
        }
        return 0;
    }

    @Override public int[] getListedLatencies() {
        var connection = Minecraft.getInstance().getConnection();
        return connection == null ? new int[0] : connection.getListedOnlinePlayers().stream().mapToInt(PlayerInfo::getLatency).toArray();
    }

    @Override public java.net.InetSocketAddress getServerSocketAddress() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.getConnection().getRemoteAddress() instanceof java.net.InetSocketAddress address ? address : null;
    }

    @Override public void sendChatLine(String text) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        if (text.startsWith("/")) connection.sendCommand(text.substring(1));
        else connection.sendChat(text);
    }

    @Override public void displayChatMessage(String text) {
        var mc = Minecraft.getInstance(); if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) mc.gui.hud.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal(text));
    }

    @Override public List<String> getOnlinePlayerNames() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return List.of();
        var players = connection.getOnlinePlayers();
        if (players == null || players.isEmpty()) return List.of();
        List<String> names = new ArrayList<>(players.size());
        for (var p : players) {
            if (p != null && p.getProfile() != null && p.getProfile().name() != null) {
                names.add(p.getProfile().name());
            }
        }
        return names;
    }

    @Override public void showToast(String title, String message) {
        var mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null && mc.gui.toastManager() != null) {
            net.minecraft.client.gui.components.toasts.SystemToast.add(
                mc.gui.toastManager(),
                net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                net.minecraft.network.chat.Component.literal(title),
                net.minecraft.network.chat.Component.literal(message)
            );
        }
    }

    @Override public boolean sendPluginMessage(String channel, byte[] data) {
        return com.thelads.core.v26_2.feature.NativeHypixel.send(channel, data);
    }

    @Override
    public double getSpeed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            var delta = mc.player.getDeltaMovement();
            return Math.sqrt(delta.x * delta.x + delta.z * delta.z) * 20.0;
        }
        return 0.0;
    }

    @Override
    public boolean isKeyDown(String keyName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return false;
        switch (keyName.toUpperCase()) {
            case "W": return mc.options.keyUp.isDown();
            case "S": return mc.options.keyDown.isDown();
            case "A": return mc.options.keyLeft.isDown();
            case "D": return mc.options.keyRight.isDown();
            case "SPACE": return mc.options.keyJump.isDown();
            case "LMB": return mc.options.keyAttack.isDown();
            case "RMB": return mc.options.keyUse.isDown();
            case "SNK":
            case "SNEAK":
            case "SHIFT": return mc.options.keyShift.isDown();
            case "SPR":
            case "SPRINT": return mc.options.keySprint.isDown();
            default: return false;
        }
    }

    private net.minecraft.client.player.LocalPlayer potionPlayer;
    private int potionTick = Integer.MIN_VALUE;
    private List<String> potions = List.of();
    private List<PotionEffectInfo> detailedPotions = List.of();
    private java.util.Collection<net.minecraft.server.packs.repository.Pack> packSelection = List.of();
    private List<String> packNames = List.of();

    private static String romanNumeral(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(value);
        };
    }

    @Override public List<PotionEffectInfo> getActivePotions() {
        var player = Minecraft.getInstance().player;
        if (player == null) { potionPlayer = null; potionTick = Integer.MIN_VALUE; detailedPotions = List.of(); potions = List.of(); return detailedPotions; }
        if (player != potionPlayer || player.tickCount != potionTick) {
            potionPlayer = player; potionTick = player.tickCount;
            var snapshot = new ArrayList<String>();
            var detailed = new ArrayList<PotionEffectInfo>();
            for (var effect : player.getActiveEffects()) {
                String name = net.minecraft.network.chat.Component.translatable(effect.getEffect().value().getDescriptionId()).getString();
                if (effect.getAmplifier() > 0) {
                    name += " " + romanNumeral(effect.getAmplifier() + 1);
                }
                int totalSec = effect.getDuration() / 20;
                String dur = totalSec >= 60 ? String.format("%d:%02d", totalSec / 60, totalSec % 60) : totalSec + "s";
                String effectId = effect.getEffect().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
                detailed.add(new PotionEffectInfo(name, dur, 0, effectId, 0xFFFFFFFF));
                snapshot.add(name + " (" + totalSec + "s)");
            }
            potions = List.copyOf(snapshot);
            detailedPotions = List.copyOf(detailed);
        }
        return detailedPotions;
    }

    @Override public List<String> getActivePotionEffects() {
        getActivePotions();
        return potions;
    }
    @Override public List<String> getActiveResourcePacks() {
        var repository = Minecraft.getInstance().getResourcePackRepository();
        if (repository == null) { packSelection = List.of(); return packNames = List.of(); }
        var selected = repository.getSelectedPacks();
        if (!packSelection.equals(selected)) {
            packSelection = List.copyOf(selected);
            packNames = ownPacks(selected);
            com.thelads.core.v26_2.gui.PackIcons.retain(packNames);
        }
        return packNames;
    }

    /** What the player picked, highest priority first (the top of the pack screen): not the required, fixed-position or built-in packs (vanilla, mods' own). */
    private static List<String> ownPacks(java.util.Collection<net.minecraft.server.packs.repository.Pack> selected) {
        var own = new java.util.ArrayList<String>();
        for (var pack : selected)
            if (!pack.isRequired() && !pack.isFixedPosition() && pack.getPackSource() != net.minecraft.server.packs.repository.PackSource.BUILT_IN) own.add(0, pack.getId());
        return List.copyOf(own);
    }
    @Override public String getDimensionId() {
        var level = Minecraft.getInstance().level;
        return level == null ? "" : level.dimension().identifier().toString();
    }
    @Override public Session session() {
        var user = Minecraft.getInstance().getUser();
        return user == null ? null : new Session(user.getProfileId(), user.getName(), user.getAccessToken());
    }
    @Override public String getServerAddress() {
        var server = Minecraft.getInstance().getCurrentServer();
        return server == null ? "Singleplayer" : server.ip;
    }
    @Override public String getItemCountText(int selection) { return com.thelads.core.v26_2.feature.NativeClientTools.itemCount(selection); }

    @Override
    public int getHeldItemBlockCount() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof net.minecraft.world.item.BlockItem)) return -1;
        int total = 0;
        for (int i = 0; i < 9; i++) {
            net.minecraft.world.item.ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == held.getItem()) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    public int getHotbarBlockCount() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        int total = 0;
        for (int i = 0; i < 9; i++) {
            net.minecraft.world.item.ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem) {
                total += stack.getCount();
            }
        }
        return total;
    }
    @Override public String getRecentReachText() { return com.thelads.core.v26_2.feature.NativeClientTools.reachText(); }

    @Override
    public boolean isHudHidden() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gui != null && mc.gui.hud != null && mc.gui.hud.isHidden();
    }

    @Override
    public boolean isDebugScreenOpen() {
        Minecraft mc = Minecraft.getInstance();
        return mc.debugEntries != null && mc.debugEntries.isOverlayVisible();
    }

    @Override
    public float partialTick() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    @Override
    public List<com.thelads.core.mods.LoadedMod> loadedMods() {
        List<com.thelads.core.mods.LoadedMod> mods = new ArrayList<>();
        for (net.fabricmc.loader.api.ModContainer container : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
            var meta = container.getMetadata();
            List<String> depends = new ArrayList<>();
            for (var dependency : meta.getDependencies())
                if (dependency.getKind() == net.fabricmc.loader.api.metadata.ModDependency.Kind.DEPENDS) depends.add(dependency.getModId());
            List<String> authors = new ArrayList<>();
            for (var person : meta.getAuthors()) authors.add(person.getName());
            // The containing mod, never ModOrigin.getParentModId(): that throws for builtin mods such as minecraft and java.
            String parent = container.getContainingMod().map(containing -> containing.getMetadata().getId()).orElse(null);
            mods.add(new com.thelads.core.mods.LoadedMod(meta.getId(), meta.getName(), meta.getVersion().getFriendlyString(), parent,
                authors, String.join(", ", meta.getLicense()), depends, List.copyOf(meta.getProvides()), libraryBadge(meta), meta.getType()));
        }
        return mods;
    }

    /** Mod Menu's custom "badges" may hold anything; only a string "library" entry in an array counts. */
    private static boolean libraryBadge(net.fabricmc.loader.api.metadata.ModMetadata meta) {
        var modmenu = meta.getCustomValue("modmenu");
        if (modmenu == null || modmenu.getType() != net.fabricmc.loader.api.metadata.CustomValue.CvType.OBJECT) return false;
        var badges = modmenu.getAsObject().get("badges");
        if (badges == null || badges.getType() != net.fabricmc.loader.api.metadata.CustomValue.CvType.ARRAY) return false;
        for (var badge : badges.getAsArray())
            if (badge.getType() == net.fabricmc.loader.api.metadata.CustomValue.CvType.STRING && "library".equals(badge.getAsString())) return true;
        return false;
    }

    @Override public void previewKillBannerSound(String skin, float volume) { com.thelads.core.v26_2.feature.NativeKillBanner.previewSound(skin, volume); }
    @Override public float getMasterVolume() { return net.minecraft.client.Minecraft.getInstance().options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER); }
    @Override public java.util.concurrent.CompletableFuture<java.nio.file.Path> gameFileDialog(String title, boolean folder, String filterName, String... extensions) { return com.thelads.core.v26_2.gui.NativeFileDialogs.choose(folder, title, filterName, extensions); }
    @Override public void playComboSound(float pitch, float volume) {
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), pitch, volume));
    }
    @Override public void playBowSound(boolean headshot, float volume) {
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(headshot
            ? net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.ANVIL_LAND, 1.8f, volume * .5f)
            : net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1f, volume));
    }

    @Override
    public void disconnectWithReason(String title, String message) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            boolean local = mc.isLocalServer();
            if (mc.level != null) {
                mc.level.disconnect(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
                if (local) mc.disconnectWithSavingScreen();
                else mc.disconnectWithProgressScreen();
            }
            net.minecraft.client.gui.screens.Screen parent = local
                ? new net.minecraft.client.gui.screens.TitleScreen()
                : new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(new net.minecraft.client.gui.screens.TitleScreen());
            mc.gui.setScreen(new net.minecraft.client.gui.screens.DisconnectedScreen(
                parent,
                net.minecraft.network.chat.Component.literal(title),
                net.minecraft.network.chat.Component.literal(message)
            ));
        });
    }

    @Override
    public DefenseEnchants getDefenseEnchants() {
        var player = Minecraft.getInstance().player;
        if (player == null) return new DefenseEnchants(0, 0, 0, 0, 0);
        int prot = 0, fire = 0, blast = 0, proj = 0, ff = 0;
        for (var slot : ARMOR_SLOTS) {
            var stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                var enchants = stack.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
                if (enchants != null) {
                    for (var entry : enchants.entrySet()) {
                        String path = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
                        int lvl = entry.getIntValue();
                        if (path.contains("fire_protection")) fire += lvl;
                        else if (path.contains("blast_protection")) blast += lvl;
                        else if (path.contains("projectile_protection")) proj += lvl;
                        else if (path.contains("feather_falling") || path.contains("fall_protection")) ff += lvl;
                        else if (path.contains("protection")) prot += lvl;
                    }
                }
            }
        }
        return new DefenseEnchants(prot, fire, blast, proj, ff);
    }

    private int lastCooldownTick = -1;
    private List<CooldownEntry> cachedCooldowns = List.of();
    private int lastInventoryScanTick = -1;
    private int cachedPotionCount = 0;
    private int cachedSoupCount = 0;
    private boolean lastAttackKeyDown = false;

    private static java.lang.reflect.Field COOLDOWNS_MAP_26;
    private static java.lang.reflect.Field TICK_COUNT_26;
    private static java.lang.reflect.Field END_TIME_26;

    static {
        try {
            for (var f : net.minecraft.world.item.ItemCooldowns.class.getDeclaredFields()) {
                if (java.util.Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    COOLDOWNS_MAP_26 = f;
                } else if (f.getType() == int.class) {
                    f.setAccessible(true);
                    TICK_COUNT_26 = f;
                }
            }
            for (var inner : net.minecraft.world.item.ItemCooldowns.class.getDeclaredClasses()) {
                int intCount = 0;
                for (var f : inner.getDeclaredFields()) {
                    if (f.getType() == int.class) {
                        intCount++;
                        if (intCount == 2 || f.getName().equals("endTime")) {
                            f.setAccessible(true);
                            END_TIME_26 = f;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public List<CooldownEntry> getActiveCooldowns() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return List.of();
        int currentTick = player.tickCount;
        if (currentTick == lastCooldownTick) {
            return cachedCooldowns;
        }
        lastCooldownTick = currentTick;

        var cooldowns = player.getCooldowns();
        List<CooldownEntry> list = new ArrayList<>();
        java.util.Set<net.minecraft.resources.Identifier> seen = new java.util.HashSet<>();
        int tickNow = currentTick;
        java.util.Map<?, ?> cdMap = null;
        if (COOLDOWNS_MAP_26 != null && TICK_COUNT_26 != null) {
            try {
                cdMap = (java.util.Map<?, ?>) COOLDOWNS_MAP_26.get(cooldowns);
                tickNow = TICK_COUNT_26.getInt(cooldowns);
            } catch (Throwable ignored) {}
        }

        for (int i = 0; i < 37; i++) {
            var stack = i == 36 ? player.getOffhandItem() : player.getInventory().getItem(i);
            if (!stack.isEmpty() && seen.add(cooldowns.getCooldownGroup(stack))) {
                float remainingPct = cooldowns.getCooldownPercent(stack, 0.0f);
                if (remainingPct > 0.0f) {
                    float remainingSec = remainingPct;
                    if (cdMap != null && END_TIME_26 != null) {
                        Object inst = cdMap.get(cooldowns.getCooldownGroup(stack));
                        if (inst != null) {
                            try {
                                int endTime = END_TIME_26.getInt(inst);
                                remainingSec = Math.max(0.0f, (endTime - tickNow) / 20.0f);
                            } catch (Throwable ignored) {}
                        }
                    }
                    list.add(new CooldownEntry(stack.getHoverName().getString(), remainingPct, remainingSec));
                }
            }
        }
        cachedCooldowns = list.isEmpty() ? List.of() : list;
        return cachedCooldowns;
    }

    @Override
    public float getToughness() {
        var player = Minecraft.getInstance().player;
        return player != null ? (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS) : 0.0f;
    }

    @Override
    public int getArmorValue() {
        var player = Minecraft.getInstance().player;
        return player != null ? player.getArmorValue() : 0;
    }

    private void updateInventoryCountsIfNeeded() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            cachedPotionCount = 0;
            cachedSoupCount = 0;
            return;
        }
        int curTick = player.tickCount;
        if (curTick == lastInventoryScanTick) return;
        lastInventoryScanTick = curTick;
        int pots = 0, soups = 0;
        for (int i = 0; i < 37; i++) {
            var stack = i == 36 ? player.getOffhandItem() : player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                if (stack.is(net.minecraft.world.item.Items.POTION) || stack.is(net.minecraft.world.item.Items.SPLASH_POTION) || stack.is(net.minecraft.world.item.Items.LINGERING_POTION))
                    pots += stack.getCount();
                else if (stack.is(net.minecraft.world.item.Items.MUSHROOM_STEW) || stack.is(net.minecraft.world.item.Items.SUSPICIOUS_STEW) || stack.is(net.minecraft.world.item.Items.RABBIT_STEW))
                    soups += stack.getCount();
            }
        }
        cachedPotionCount = pots;
        cachedSoupCount = soups;
    }

    @Override
    public int getPotionCount() {
        updateInventoryCountsIfNeeded();
        return cachedPotionCount;
    }

    @Override
    public int getSoupCount() {
        updateInventoryCountsIfNeeded();
        return cachedSoupCount;
    }

    @Override
    public void tickClient() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null) return;
        int currentTick = player.tickCount;

        // 1. Air swing detection (attack key pressed with no entity under crosshair)
        if (mc.options != null) {
            boolean attackDown = mc.options.keyAttack.isDown();
            if (attackDown && !lastAttackKeyDown && mc.gui.screen() == null) {
                if (mc.hitResult == null || mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.ENTITY) {
                    com.thelads.core.client.CombatTracker.get().onAirSwing();
                }
            }
            lastAttackKeyDown = attackDown;

            // 2. Block counter placed detection (decrease within 2 ticks of use on block)
            boolean useDown = mc.options.keyUse.isDown();
            boolean hitBlock = mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
            if (useDown && hitBlock && mc.gui.screen() == null) {
                com.thelads.core.client.BlockTracker.get().onUseKeyOnBlock(currentTick);
            }
        }
        var held = player.getMainHandItem();
        boolean isBlock = !held.isEmpty() && held.getItem() instanceof net.minecraft.world.item.BlockItem;
        int heldCount = held.getCount();
        int slot = player.getInventory().getSelectedSlot();
        com.thelads.core.client.BlockTracker.get().checkHeldBlockTick(currentTick, slot, isBlock, heldCount);

        // 3. Golden apple eating detection
        boolean using = player.isUsingItem();
        var useItem = player.getUseItem();
        boolean isGapple = !useItem.isEmpty() && (useItem.is(net.minecraft.world.item.Items.GOLDEN_APPLE) || useItem.is(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE));
        int gappleCount = isGapple ? useItem.getCount() : 0;
        net.minecraft.world.InteractionHand hand = player.getUsedItemHand();
        int eatSlot = (using && hand == net.minecraft.world.InteractionHand.OFF_HAND) ? 36 : player.getInventory().getSelectedSlot();
        var stackInSlot = eatSlot == 36 ? player.getOffhandItem() : player.getInventory().getItem(eatSlot);
        boolean isGappleInSlot = !stackInSlot.isEmpty() && (stackInSlot.is(net.minecraft.world.item.Items.GOLDEN_APPLE) || stackInSlot.is(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE));
        int slotGCount = isGappleInSlot ? stackInSlot.getCount() : 0;
        int totalDuration = isGapple ? useItem.getUseDuration(player) : 32;
        int ticksUsed = isGapple ? (totalDuration - player.getUseItemRemainingTicks()) : 0;
        com.thelads.core.client.GappleTracker.get().onTick(using && isGapple, isGappleInSlot, eatSlot, slotGCount, ticksUsed, totalDuration);

        // 4. Item tracker inventory diffing (every 10 client ticks)
        if (currentTick % 10 == 0) {
            boolean containerOpen = mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen);
            com.thelads.core.client.InventoryDiffTracker diff = com.thelads.core.client.InventoryDiffTracker.get();
            diff.beginScan(containerOpen);
            if (!containerOpen) {
                for (int i = 0; i < 37; i++) {
                    var stack = i == 36 ? player.getOffhandItem() : player.getInventory().getItem(i);
                    if (!stack.isEmpty()) {
                        diff.addScannedItem(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(stack.getItem()), stack.getCount(), stack.getHoverName().getString());
                    }
                }
            }
            diff.endScan();
        }
    }
}
