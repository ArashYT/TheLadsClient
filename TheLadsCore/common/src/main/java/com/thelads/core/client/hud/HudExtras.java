package com.thelads.core.client.hud;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.RaisedModule;
import com.thelads.core.modules.SubtitlesModule;
import com.thelads.core.modules.TabListModule;
import com.thelads.core.modules.TitleTweakerModule;
import com.thelads.core.modules.ToastControlModule;
import com.thelads.core.modules.VanillaHudModule;

/**
 * The HUD widgets and vanilla HUD tweaks of parity 1.9.19 (Damage Indicator to Tab list extras). Each version calls {@link #tick} once
 * a client tick (HudExtras189, HudExtras1201, HudExtras26): the modules cache their options here, and the version then writes what the
 * switched-on widgets show into these fields, so no widget asks the game or looks up an option while it draws.
 */
public final class HudExtras {
    public static final String DAMAGE_INDICATOR = "DamageIndicator", JUMP_RESET = "JumpReset", MEDIA = "MediaPlayer", HORSE = "HorseStats",
        PLAYERS = "PlayerCounter", TOTEMS = "TotemPopCounter", BLOCK_INFO = "BlockInfo", CUSTOM_TEXT = "CustomText",
        CUSTOM_SHAPE = "CustomShape", CUSTOM_IMAGE = "CustomImage", HEAD = "PlayerHead", ABOVE = "BlockAbove", ENTITIES = "EntityCounter",
        SHADER = "ShaderPack", LORE = "HeldItemLore";

    public static final SubtitlesModule SUBTITLES = get(SubtitlesModule.NAME);
    public static final TitleTweakerModule TITLES = get(TitleTweakerModule.NAME);
    public static final ToastControlModule TOASTS = get(ToastControlModule.NAME);
    public static final VanillaHudModule VANILLA = get(VanillaHudModule.NAME);
    public static final TabListModule TAB = get("TabList");
    public static final RaisedModule RAISED = RaisedModule.get();
    private static final Module DAMAGE = get(DAMAGE_INDICATOR), HORSES = get(HORSE), COUNTER = get(PLAYERS), BLOCK = get(BLOCK_INFO),
        BLOCK_ABOVE = get(ABOVE), ENTITY_COUNTER = get(ENTITIES), SHADERS = get(SHADER), HELD_LORE = get(LORE), TOTEM = get(TOTEMS),
        JUMP = get(JUMP_RESET), PLAYER = get(MEDIA);

    /** Damage Indicator: the target HUD is on, the floating damage numbers are on, how long a number floats (ms). */
    public static boolean target, numbers;
    public static int numberMillis = 1000;
    /** The entity the target HUD shows (version writes; id -1 = none) until targetUntil (System.currentTimeMillis). */
    public static int targetId = -1;
    public static String targetName = "";
    public static float targetHealth, targetMax;
    public static long targetUntil;

    /** Horse Stats: on; shown (riding a horse or looking at one); jump height (blocks), speed (blocks/s), health and max health. */
    public static boolean horse, horseShown;
    public static float horseJump, horseSpeed, horseHealth, horseMax;

    /** Player Counter: on; players in the tab list and other players loaded near you. */
    public static boolean players;
    public static int playersTotal, playersNearby;

    /** Block Info: on; a block is targeted; its name, the tool it wants, whether your held item harvests it (1 yes, 0 no, -1 any),
     *  the block and sky light on its targeted face and how far your digging has got (0 to 1). */
    public static boolean block, blockShown;
    public static String blockName = "", blockTool = "";
    public static int blockHarvest, blockLight, skyLight;
    public static float blockProgress;

    /** Block Above: on; the first block above your head within 32 (name, blocks of air between), none = "". */
    public static boolean above;
    public static String aboveName = "";
    public static int aboveGap;

    /** Entity Counter, Shader Pack, Held Item Lore: on, and what they show. */
    public static boolean entities, shader, lore, totems, jumpReset;
    /** Totem Pop Counter: a player's count starts over when they die; your own chat gets a line per pop. */
    public static boolean totemReset, totemChat;
    public static int entityCount;
    public static String shaderName = "", loreText = "";

    private HudExtras() {}

    private static <T extends Module> T get(String name) {
        @SuppressWarnings("unchecked") T module = (T) ModuleManager.getInstance().getModule(name);
        return module;
    }

    /** Once a client tick, on the client thread: the modules cache their options, then the widget switches. */
    public static void tick() {
        SUBTITLES.refresh();
        TITLES.refresh();
        TOASTS.refresh();
        VANILLA.refresh();
        TAB.refresh();
        if (RAISED != null) RAISED.refresh();
        int display = DAMAGE.getOption("Display") instanceof DropdownOption d ? d.getIndex() : 0;
        boolean damage = DAMAGE.isEnabled();
        target = damage && display != 2;
        numbers = damage && display != 1;
        numberMillis = DAMAGE.getOption("Number Duration") instanceof SliderOption s ? s.getIntValue() : 1000;
        horse = HORSES.isEnabled();
        players = COUNTER.isEnabled();
        block = BLOCK.isEnabled();
        above = BLOCK_ABOVE.isEnabled();
        entities = ENTITY_COUNTER.isEnabled();
        shader = SHADERS.isEnabled();
        lore = HELD_LORE.isEnabled();
        totems = TOTEM.isEnabled();
        totemReset = option(TOTEM, "Reset On Death");
        totemChat = option(TOTEM, "Chat Message");
        long now = System.currentTimeMillis();
        if (totems) TotemPops.expire(now, 1000L * (TOTEM.getOption("Duration") instanceof SliderOption s ? s.getIntValue() : 30));
        else if (TotemPops.size() > 0) TotemPops.clear();
        jumpReset = JUMP.isEnabled();
        if (PLAYER.isEnabled()) MediaSession.want();
        if (!target) targetId = -1;
        if (!horse) horseShown = false;
        if (!block) blockShown = false;
    }

    /** Totem Pop Counter: a totem saved player {@code id}; with Chat Message a line in your own chat. */
    public static void totemPopped(int id, String name) {
        if (!totems) return;
        int count = TotemPops.pop(id, name, System.currentTimeMillis());
        if (totemChat) com.thelads.core.client.bridge.LadsGameBridge.get().displayChatMessage(name + " popped " + count + (count == 1 ? " totem" : " totems"));
    }

    /** Totem Pop Counter: player {@code id} died; with Reset On Death their count starts over. */
    public static void died(int id) {
        if (totems && totemReset) TotemPops.died(id);
    }

    /** Title Tweaker: a title or subtitle the server sent; copied into your chat with Send to Chat. True: do not show it (Hide on Screen). */
    public static boolean title(String text, boolean subtitle) {
        String line = TITLES.chatLine(text, subtitle);
        if (line != null) com.thelads.core.client.bridge.LadsGameBridge.get().displayChatMessage(line);
        return TITLES.hidden;
    }

    private static boolean option(Module module, String option) {
        return module.getOption(option) instanceof BoolOption b && b.get();
    }

    /** A horse's jump height in blocks from its jump strength (the fitted curve horse tools use). */
    public static float jumpHeight(double strength) {
        return (float) (-0.1817584952 * strength * strength * strength + 3.689713992 * strength * strength + 2.128599134 * strength - 0.343930367);
    }

    /** Blocks per second from a movement speed attribute (a player's 0.1 walks 4.317 blocks/s). */
    public static float blocksPerSecond(double movementSpeed) {
        return (float) (movementSpeed * 43.17);
    }

    /** Block Info's tool name from what the version found ("pickaxe", "axe", "shovel", "hoe" or null) and the tier it needs (0 any). */
    public static String toolName(String tool, int tier) {
        if (tool == null) return "Any tool";
        String kind = Character.toUpperCase(tool.charAt(0)) + tool.substring(1);
        return switch (tier) {
            case 1 -> "Stone " + kind;
            case 2 -> "Iron " + kind;
            case 3 -> "Diamond " + kind;
            default -> kind;
        };
    }
}
