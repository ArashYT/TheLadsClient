package com.thelads.core.client.ladsapi;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.thelads.core.LadsVersion;
import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.client.hud.ToastAction;
import java.util.Collections;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The version adapters' only door into LadsClientAPI. Each adapter hands over raw lads:api payloads and connection events; this class
 * parses, validates and answers (PROTOCOL.md sections 3-4). All methods run on the game thread.
 *
 * FROZEN INTERFACE (phase 0): adapters (lanes T26, T1201, T189) call exactly these; lane CORE implements the bodies.
 */
public final class LadsApiClient {
    /** Sends one lads:api payload to the current server. Set by the version adapter at startup. */
    public interface Transport {
        void send(byte[] payload);
    }

    private static volatile Transport transport;
    private static volatile String minecraftVersion = "";
    private static volatile boolean sessionOk = false;
    private static volatile String serverName = "";
    private static int helloReplies = 0;
    /** Incoming messages: 8 a second on average, bursts of 16 (a plugin's join-time overrides arrive together, the newest last). */
    static final int RATE_PER_SECOND = 8, RATE_BURST = 16;
    private static double rateTokens = RATE_BURST;
    private static long rateLast = 0;
    private static Long lastHashedSeed = null;
    private static Integer lastDimension = null;
    private static volatile int sentBeforeHelloCount = 0;
    private static final Gson GSON = new Gson();

    private static void sendPayload(byte[] bytes) {
        Transport t = transport;
        if (t != null) {
            if (!sessionOk) {
                sentBeforeHelloCount++;
            }
            t.send(bytes);
        }
    }

    private LadsApiClient() {}

    /** Called once by the version adapter at startup, e.g. install(bytes -> ..., "26.3"). */
    public static void install(Transport sender, String mcVersion) {
        transport = sender;
        minecraftVersion = mcVersion == null ? "" : mcVersion;
    }

    /** One lads:api payload from the server (already on the game thread). Oversized, malformed or rate-limited payloads are dropped. */
    public static void receive(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > LadsApi.MAX_IN) {
            return;
        }

        long now = System.currentTimeMillis();
        rateTokens = Math.min(RATE_BURST, rateTokens + Math.max(0, now - rateLast) * RATE_PER_SECOND / 1000.0);
        rateLast = now;
        if (rateTokens < 1) {
            return;
        }
        rateTokens--;

        JsonObject json;
        try {
            String str = new String(payload, StandardCharsets.UTF_8);
            json = GSON.fromJson(str, JsonObject.class);
            if (json == null) return;
        } catch (Exception e) {
            return;
        }

        if (!json.has("t") || !json.get("t").isJsonPrimitive()) {
            return;
        }
        String type = json.get("t").getAsString();

        if ("hello".equalsIgnoreCase(type)) {
            handleHello(json);
        } else if ("reset".equalsIgnoreCase(type)) {
            handleReset();
        } else if (!sessionOk) {
            return; // policy, presence and toasts only from a server that said hello (any server can use the channel)
        } else if ("policy".equalsIgnoreCase(type)) {
            handlePolicy(json);
        } else if ("presence".equalsIgnoreCase(type)) {
            handlePresence(json);
        } else if ("toast".equalsIgnoreCase(type) || "notification".equalsIgnoreCase(type)) {
            handleToast(json);
        }
    }

    private static void handleHello(JsonObject json) {
        // hello: reset old state, store serverName (<=32 chars) and sessionOk
        ServerPolicy.set(null);
        LadsPresence.serverClear();
        LadsApiUi.policyCleared();
        sessionOk = false;

        String name = "";
        if (json.has("server") && json.get("server").isJsonPrimitive()) {
            name = json.get("server").getAsString();
            if (name.length() > 32) {
                name = name.substring(0, 32);
            }
        }
        serverName = name;
        sessionOk = true;

        if (helloReplies < 2) {
            helloReplies++;
            JsonObject reply = new JsonObject();
            reply.addProperty("t", "hello");
            reply.addProperty("v", 1);
            reply.addProperty("lads", LadsVersion.VERSION);
            reply.addProperty("mc", minecraftVersion != null ? minecraftVersion : "");
            sendPayload(reply.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void handlePolicy(JsonObject json) {
        int rev = 1;
        if (json.has("rev") && json.get("rev").isJsonPrimitive()) {
            try {
                rev = json.get("rev").getAsInt();
            } catch (Exception ignored) {}
        }
        String notice = "";
        if (json.has("notice") && json.get("notice").isJsonPrimitive()) {
            notice = json.get("notice").getAsString();
            if (notice.length() > 256) {
                notice = notice.substring(0, 256);
            }
        }

        PolicyValidationResult result = validatePolicy(json, serverName, notice);
        ServerPolicy.set(result.policy);

        JsonObject ack = new JsonObject();
        ack.addProperty("t", "ack");
        ack.addProperty("rev", rev);
        JsonArray ignArr = new JsonArray();
        for (String s : result.ignored) ignArr.add(new JsonPrimitive(s));
        ack.add("ignored", ignArr);
        JsonArray refArr = new JsonArray();
        for (String s : result.refused) refArr.add(new JsonPrimitive(s));
        ack.add("refused", refArr);
        sendPayload(ack.toString().getBytes(StandardCharsets.UTF_8));

        List<ToastAction> actions = parseToastActions(json.get("actions"));
        LadsApiUi.policyApplied(serverName, result.changedCount, result.unlocked, notice, actions);
    }

    private static void handlePresence(JsonObject json) {
        boolean full = json.has("full") && json.get("full").isJsonPrimitive() && json.get("full").getAsBoolean();
        if (full) {
            List<UUID> players = parseUuids(json.get("add"), 1000);
            LadsPresence.serverReplace(players);
        } else {
            if (json.has("add")) {
                List<UUID> toAdd = parseUuids(json.get("add"), 1000);
                LadsPresence.serverAdd(toAdd);
            }
            if (json.has("remove")) {
                List<UUID> toRemove = parseUuids(json.get("remove"), 1000);
                LadsPresence.serverRemove(toRemove);
            }
        }
    }

    
    private static void handleToast(JsonObject json) {
        String title = json.has("title") && json.get("title").isJsonPrimitive() ? json.get("title").getAsString() : (serverName.isEmpty() ? "Server Notification" : serverName);
        String msg = json.has("message") && json.get("message").isJsonPrimitive() ? json.get("message").getAsString()
            : (json.has("notice") && json.get("notice").isJsonPrimitive() ? json.get("notice").getAsString() : null);
        List<ToastAction> actions = parseToastActions(json.get("actions"));
        LadsApiUi.showNotification(cap(title, 64), cap(msg, 256), actions); // the HUD measures and draws both every frame
    }

    private static String cap(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    private static List<ToastAction> parseToastActions(JsonElement element) {
        if (element == null || !element.isJsonArray()) return Collections.emptyList();
        JsonArray array = element.getAsJsonArray();
        List<ToastAction> list = new ArrayList<>();
        for (JsonElement e : array) {
            if (!e.isJsonObject()) continue;
            JsonObject obj = e.getAsJsonObject();
            String label = obj.has("label") && obj.get("label").isJsonPrimitive() ? obj.get("label").getAsString() : "Action";
            String typeStr = obj.has("type") && obj.get("type").isJsonPrimitive() ? obj.get("type").getAsString()
                : (obj.has("action") && obj.get("action").isJsonPrimitive() ? obj.get("action").getAsString() : null);
            String payload = "";
            if (obj.has("url") && obj.get("url").isJsonPrimitive()) {
                payload = obj.get("url").getAsString();
                if (typeStr == null) typeStr = "url";
            } else if (obj.has("command") && obj.get("command").isJsonPrimitive()) {
                payload = obj.get("command").getAsString();
                if (typeStr == null) typeStr = "command";
            } else if (obj.has("suggest") && obj.get("suggest").isJsonPrimitive()) {
                payload = obj.get("suggest").getAsString();
                if (typeStr == null) typeStr = "suggest";
            } else if (obj.has("payload") && obj.get("payload").isJsonPrimitive()) {
                payload = obj.get("payload").getAsString();
            }
            list.add(ToastAction.parse(cap(label, 32), typeStr, cap(payload, 512)));
            if (list.size() >= 4) break;
        }
        return list;
    }

    /**
     * Server reset, JoinGame/Login, 26.3 configuration start, a seed-changing Respawn: the old backend's or world's policy, presence and
     * toast go, and the next hello is answered again. The session stays: a proxy (proxy-rules) or a multi-world server sends its next
     * policy without a new hello, which was dropped for the rest of the connection. Only a disconnect ends the session ({@link #reset}).
     */
    private static void handleReset() {
        ServerPolicy.set(null);
        LadsPresence.serverClear();
        LadsApiUi.policyCleared();
        helloReplies = 0;
    }

    /** Disconnect: everything from the connection goes, the session too. */
    public static void reset() {
        handleReset();
        sessionOk = false;
        serverName = "";
        rateTokens = RATE_BURST;
        rateLast = 0;
        lastHashedSeed = null;
        lastDimension = null;
    }

    /** JoinGame/Login packet with the world's hashed seed: the old backend's rules go, then the seed is kept for {@link #respawn}. */
    public static void joinGame(long hashedSeed) {
        serverSwitch();
        lastHashedSeed = hashedSeed;
    }

    /** 26.3 configuration start (a backend switch, or the first join): the old backend's rules go. */
    public static void serverSwitch() {
        handleReset();
        lastHashedSeed = null;
        lastDimension = null;
    }

    /** Respawn packet: the rules go only when the hashed seed differs from the last one (a proxy switch or another world), not on death. */
    public static void respawn(long hashedSeed) {
        if (lastHashedSeed == null || lastHashedSeed.longValue() != hashedSeed) {
            handleReset();
            lastHashedSeed = hashedSeed;
        }
    }

    /**
     * 1.8.9 JoinGame/Respawn dimension (its Respawn has no seed). A dimension change is also how BungeeCord switches 1.8 backends, and the
     * next backend may never have opted in: every by-name "allow" goes (anti-ban), blocks and limits stay. A server that runs the jar
     * sends its policy again on a world change.
     */
    public static void dimension(int dimension) {
        if (lastDimension != null && lastDimension != dimension) {
            ServerPolicy policy = ServerPolicy.active();
            if (policy != null) ServerPolicy.set(policy.withoutAllows());
        }
        lastDimension = dimension;
    }

    static Transport transport() { return transport; }

    static String minecraftVersion() { return minecraftVersion; }

    static boolean isSessionOk() { return sessionOk; }

    public static String serverName() { return serverName; }

    /** QA check: whether any lads:api packet was ever sent before a valid server hello. */
    public static int sentBeforeHello() { return sentBeforeHelloCount; }

    public static void resetSentBeforeHello() { sentBeforeHelloCount = 0; }

    // ---- Validation & UUID helpers ----

    private static final class PolicyValidationResult {
        final ServerPolicy policy;
        final List<String> ignored;
        final List<String> refused;
        final int changedCount, unlocked;

        PolicyValidationResult(ServerPolicy policy, List<String> ignored, List<String> refused, int changedCount, int unlocked) {
            this.policy = policy;
            this.ignored = ignored;
            this.refused = refused;
            this.changedCount = changedCount;
            this.unlocked = unlocked;
        }
    }

    private static void addLimited(List<String> list, String item) {
        if (list.size() < 64 && !list.contains(item)) {
            list.add(item);
        }
    }

    private static Module findModule(String name) {
        if (name == null) return null;
        ModuleManager mm = ModuleManager.getInstance();
        Module m = mm.getModule(name);
        if (m != null) return m;
        for (Module mod : mm.getModules()) {
            if (mod.getName().equalsIgnoreCase(name)) {
                return mod;
            }
        }
        if ("Crosshair".equalsIgnoreCase(name)) {
            return mm.getModule("Crosshair Tweaks");
        }
        return null;
    }

    private static Option findOption(Module m, String name) {
        if (m == null || name == null) return null;
        Option opt = m.getOption(name);
        if (opt != null) return opt;
        for (Option o : m.getOptions()) {
            if (o.getName().equalsIgnoreCase(name)) {
                return o;
            }
        }
        if ("Style".equalsIgnoreCase(name) && m.getName().equalsIgnoreCase("Crosshair Tweaks")) {
            return m.getOption("Shape");
        }
        return null;
    }

    private static boolean isPlayerRefusingChanges() {
        try {
            Module sc = ModuleManager.getInstance().getModule("Server Control");
            if (sc != null) {
                Option opt = sc.getOption("Let servers change my Lads settings");
                if (opt instanceof BoolOption b) {
                    return !b.getOwn();
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static PolicyValidationResult validatePolicy(JsonObject json, String sName, String notice) {
        List<String> ignored = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        boolean playerRefuses = isPlayerRefusingChanges();

        ModuleState unlistedState = ModuleState.INHERIT; // "allow" too: never lifts a safety block (ServerPolicy.module)
        if (json.has("unlisted") && json.get("unlisted").isJsonPrimitive()) {
            String u = json.get("unlisted").getAsString();
            if ("block".equalsIgnoreCase(u)) {
                unlistedState = ModuleState.BLOCK;
            }
        }

        Map<String, ModuleState> modulesMap = new HashMap<>();
        int changedCount = 0, unlocked = 0;

        if (json.has("modules") && json.get("modules").isJsonObject()) {
            JsonObject modsObj = json.getAsJsonObject("modules");
            for (Map.Entry<String, JsonElement> e : modsObj.entrySet()) {
                String rawName = e.getKey();
                if ("Server Control".equalsIgnoreCase(rawName)) {
                    addLimited(ignored, rawName);
                    continue;
                }

                String canonicalName = null;
                Module mod = findModule(rawName);
                if (mod != null) {
                    canonicalName = mod.getName();
                } else if (rawName.equalsIgnoreCase(HypixelSafetyManager.USE_WHILE_MINING)) {
                    canonicalName = HypixelSafetyManager.USE_WHILE_MINING;
                } else {
                    addLimited(ignored, rawName);
                    continue;
                }

                if (!e.getValue().isJsonPrimitive()) {
                    addLimited(ignored, rawName);
                    continue;
                }

                String stateStr = e.getValue().getAsString();
                ModuleState state;
                if ("allow".equalsIgnoreCase(stateStr)) {
                    state = ModuleState.ALLOW;
                } else if ("block".equalsIgnoreCase(stateStr)) {
                    state = ModuleState.BLOCK;
                } else if ("on".equalsIgnoreCase(stateStr)) {
                    if (LadsApiRules.isNeverForced(canonicalName)) {
                        addLimited(ignored, rawName);
                        continue;
                    }
                    state = ModuleState.ON;
                } else if ("inherit".equalsIgnoreCase(stateStr)) {
                    state = ModuleState.INHERIT;
                } else {
                    addLimited(ignored, rawName);
                    continue;
                }

                if (state == ModuleState.ON && playerRefuses) {
                    addLimited(refused, rawName);
                }

                modulesMap.put(canonicalName, state);
                if (!canonicalName.equals(rawName)) {
                    modulesMap.put(rawName, state);
                }
                if (state == ModuleState.ALLOW) {
                    if (LadsApiRules.isGated(canonicalName)) unlocked++; // lifts the client's safety block: the join toast says so
                } else if (state != ModuleState.INHERIT) {
                    changedCount++;
                }
            }
        }

        Map<String, Map<String, OptionRule>> optionsMap = new HashMap<>();

        if (json.has("options") && json.get("options").isJsonObject()) {
            JsonObject optsObj = json.getAsJsonObject("options");
            for (Map.Entry<String, JsonElement> modEntry : optsObj.entrySet()) {
                String rawModName = modEntry.getKey();
                if ("Server Control".equalsIgnoreCase(rawModName)) {
                    if (modEntry.getValue().isJsonObject()) {
                        for (String optKey : modEntry.getValue().getAsJsonObject().keySet()) {
                            addLimited(ignored, rawModName + "." + optKey);
                        }
                    } else {
                        addLimited(ignored, rawModName);
                    }
                    continue;
                }

                Module mod = findModule(rawModName);
                if (mod == null) {
                    if (modEntry.getValue().isJsonObject()) {
                        for (String optKey : modEntry.getValue().getAsJsonObject().keySet()) {
                            addLimited(ignored, rawModName + "." + optKey);
                        }
                    } else {
                        addLimited(ignored, rawModName);
                    }
                    continue;
                }

                String canonicalModName = mod.getName();
                if (!modEntry.getValue().isJsonObject()) {
                    addLimited(ignored, canonicalModName);
                    continue;
                }

                JsonObject modOpts = modEntry.getValue().getAsJsonObject();
                for (Map.Entry<String, JsonElement> optEntry : modOpts.entrySet()) {
                    String rawOptName = optEntry.getKey();
                    Option option = findOption(mod, rawOptName);
                    if (option == null) {
                        addLimited(ignored, rawModName + "." + rawOptName);
                        continue;
                    }

                    String canonicalOptName = option.getName();
                    String fullKey = rawModName + "." + rawOptName;

                    if (option.serverControl() == ServerControl.NEVER || LadsApiRules.isNeverForced(canonicalModName, canonicalOptName)) {
                        addLimited(ignored, fullKey);
                        continue;
                    }

                    if (!optEntry.getValue().isJsonObject()) {
                        addLimited(ignored, fullKey);
                        continue;
                    }

                    JsonObject ruleObj = optEntry.getValue().getAsJsonObject();
                    OptionRule rule = validateOptionRule(option, ruleObj, fullKey, ignored, refused, playerRefuses);
                    if (rule != null) {
                        optionsMap.computeIfAbsent(canonicalModName, k -> new HashMap<>()).put(canonicalOptName, rule);
                        if (!canonicalModName.equals(rawModName) || !canonicalOptName.equals(rawOptName)) {
                            optionsMap.computeIfAbsent(rawModName, k -> new HashMap<>()).put(rawOptName, rule);
                            optionsMap.computeIfAbsent(canonicalModName, k -> new HashMap<>()).put(rawOptName, rule);
                            optionsMap.computeIfAbsent(rawModName, k -> new HashMap<>()).put(canonicalOptName, rule);
                        }
                        changedCount++;
                    }
                }
            }
        }

        ServerPolicy snapshot = new ServerPolicy(sName, unlistedState, modulesMap, optionsMap, changedCount, notice);
        return new PolicyValidationResult(snapshot, ignored, refused, changedCount, unlocked);
    }

    private static OptionRule validateOptionRule(Option option, JsonObject ruleObj, String fullKey,
                                                List<String> ignored, List<String> refused, boolean playerRefuses) {
        boolean lock = ruleObj.has("lock") && ruleObj.get("lock").isJsonPrimitive() && ruleObj.get("lock").getAsBoolean();

        if (option instanceof BoolOption) {
            Boolean forcedBool = null;
            if (ruleObj.has("min") || ruleObj.has("max") || ruleObj.has("choices")) {
                addLimited(ignored, fullKey);
                return null;
            }
            if (ruleObj.has("value")) {
                JsonElement v = ruleObj.get("value");
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
                    if (option.serverControl() == ServerControl.LIMIT_ONLY) {
                        addLimited(ignored, fullKey);
                        return null;
                    }
                    forcedBool = v.getAsBoolean();
                } else {
                    addLimited(ignored, fullKey);
                    return null;
                }
            }
            if (forcedBool != null && playerRefuses) {
                addLimited(refused, fullKey);
            }
            return new OptionRule(forcedBool, Double.NaN, Double.NaN, Double.NaN, null, null, null, lock);
        }

        if (option instanceof SliderOption slider) {
            if (ruleObj.has("choices")) {
                addLimited(ignored, fullKey);
                return null;
            }
            double forcedVal = Double.NaN;
            double min = Double.NaN;
            double max = Double.NaN;

            if (ruleObj.has("value")) {
                JsonElement v = ruleObj.get("value");
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                    double num = v.getAsDouble();
                    if (!Double.isFinite(num) || option.serverControl() == ServerControl.LIMIT_ONLY) {
                        addLimited(ignored, fullKey);
                        return null;
                    }
                    double clamped = num;
                    if (clamped < slider.getMin()) clamped = slider.getMin();
                    if (clamped > slider.getMax()) clamped = slider.getMax();
                    if (slider.getStep() > 0) {
                        clamped = slider.getMin() + Math.round((clamped - slider.getMin()) / slider.getStep()) * slider.getStep();
                        if (clamped > slider.getMax()) clamped = slider.getMax();
                        if (clamped < slider.getMin()) clamped = slider.getMin();
                    }
                    forcedVal = clamped;
                } else {
                    addLimited(ignored, fullKey);
                    return null;
                }
            }

            if (ruleObj.has("min")) {
                JsonElement v = ruleObj.get("min");
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                    double num = v.getAsDouble();
                    if (!Double.isFinite(num)) {
                        addLimited(ignored, fullKey);
                        return null;
                    }
                    min = num;
                } else {
                    addLimited(ignored, fullKey);
                    return null;
                }
            }

            if (ruleObj.has("max")) {
                JsonElement v = ruleObj.get("max");
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                    double num = v.getAsDouble();
                    if (!Double.isFinite(num)) {
                        addLimited(ignored, fullKey);
                        return null;
                    }
                    max = num;
                } else {
                    addLimited(ignored, fullKey);
                    return null;
                }
            }

            if (!Double.isNaN(min) && !Double.isNaN(max) && min > max) {
                addLimited(ignored, fullKey);
                return null;
            }

            if (!Double.isNaN(forcedVal) && playerRefuses) {
                addLimited(refused, fullKey);
            }

            return new OptionRule(null, forcedVal, min, max, null, null, null, lock);
        }

        if (option instanceof DropdownOption dropdown) {
            if (ruleObj.has("min") || ruleObj.has("max")) {
                addLimited(ignored, fullKey);
                return null;
            }
            String forcedChoice = null;
            String[] allowedChoices = null;
            String[] valid = dropdown.getChoices();

            if (ruleObj.has("value")) {
                JsonElement v = ruleObj.get("value");
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                    String str = v.getAsString();
                    boolean match = false;
                    for (String optChoice : valid) {
                        if (optChoice.equals(str)) {
                            match = true;
                            forcedChoice = optChoice;
                            break;
                        }
                    }
                    if (!match || option.serverControl() == ServerControl.LIMIT_ONLY) {
                        addLimited(ignored, fullKey);
                        return null;
                    }
                } else {
                    addLimited(ignored, fullKey);
                    return null;
                }
            }

            if (ruleObj.has("choices")) {
                JsonElement v = ruleObj.get("choices");
                if (v.isJsonArray()) {
                    JsonArray arr = v.getAsJsonArray();
                    List<String> list = new ArrayList<>();
                    for (JsonElement item : arr) {
                        if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                            String itemStr = item.getAsString();
                            boolean ok = false;
                            for (String optChoice : valid) {
                                if (optChoice.equals(itemStr)) {
                                    ok = true;
                                    break;
                                }
                            }
                            if (!ok) {
                                addLimited(ignored, fullKey);
                                return null;
                            }
                            list.add(itemStr);
                        } else {
                            addLimited(ignored, fullKey);
                            return null;
                        }
                    }
                    if (list.isEmpty()) {
                        addLimited(ignored, fullKey);
                        return null;
                    }
                    allowedChoices = list.toArray(new String[0]);
                } else {
                    addLimited(ignored, fullKey);
                    return null;
                }
            }

            if (forcedChoice != null && playerRefuses) {
                addLimited(refused, fullKey);
            }

            return new OptionRule(null, Double.NaN, Double.NaN, Double.NaN, forcedChoice, allowedChoices, null, lock);
        }

        if (option instanceof ColorOption) {
            if (ruleObj.has("min") || ruleObj.has("max") || ruleObj.has("choices")) {
                addLimited(ignored, fullKey);
                return null;
            }
            Integer forcedColor = null;

            JsonElement v = ruleObj.has("value") ? ruleObj.get("value") : ruleObj.get("color");
            if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                    String hex = v.getAsString().trim();
                    if (hex.startsWith("#")) hex = hex.substring(1);
                    if (hex.length() == 6) {
                        try {
                            int rgb = (int) Long.parseLong(hex, 16);
                            forcedColor = 0xFF000000 | (rgb & 0x00FFFFFF);
                        } catch (Exception e) {
                            addLimited(ignored, fullKey);
                            return null;
                        }
                    } else if (hex.length() == 8) {
                        try {
                            int argb = (int) Long.parseLong(hex, 16);
                            int alpha = (argb >>> 24) & 0xFF;
                            if (alpha < 0x40) {
                                alpha = 0x40;
                                argb = (alpha << 24) | (argb & 0x00FFFFFF);
                            }
                            forcedColor = argb;
                        } catch (Exception e) {
                            addLimited(ignored, fullKey);
                            return null;
                        }
                    } else {
                        addLimited(ignored, fullKey);
                        return null;
                    }
                    if (option.serverControl() == ServerControl.LIMIT_ONLY) {
                        addLimited(ignored, fullKey);
                        return null;
                    }
                } else {
                    addLimited(ignored, fullKey);
                    return null;
                }

            if (forcedColor != null && playerRefuses) {
                addLimited(refused, fullKey);
            }

            return new OptionRule(null, Double.NaN, Double.NaN, Double.NaN, null, null, forcedColor, lock);
        }

        addLimited(ignored, fullKey);
        return null;
    }

    private static List<UUID> parseUuids(JsonElement elem, int limit) {
        List<UUID> list = new ArrayList<>();
        if (elem != null && elem.isJsonArray()) {
            for (JsonElement item : elem.getAsJsonArray()) {
                if (list.size() >= limit) break;
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                    UUID u = parseUuid(item.getAsString());
                    if (u != null) list.add(u);
                }
            }
        }
        return list;
    }

    static UUID parseUuid(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.length() == 36) {
            try {
                return UUID.fromString(trimmed);
            } catch (Exception e) {
                return null;
            }
        } else if (trimmed.length() == 32) {
            try {
                long mostSig = Long.parseUnsignedLong(trimmed.substring(0, 16), 16);
                long leastSig = Long.parseUnsignedLong(trimmed.substring(16, 32), 16);
                return new UUID(mostSig, leastSig);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
