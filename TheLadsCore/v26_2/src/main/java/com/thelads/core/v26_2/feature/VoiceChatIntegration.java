package com.thelads.core.v26_2.feature;

import com.thelads.core.client.bridge.LadsGameBridge.VoiceChatState;
import com.thelads.core.client.bridge.LadsGameBridge.VoiceMember;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.LoggerFactory;

/**
 * Simple Voice Chat's HUD state for the Lads Voice Chat elements (1.7.0), read by reflection so the Core runs without it. It mirrors
 * SVC's own RenderEvents.onRenderHUD: the same icon in the same order, its settings (hide icons, HUD icons, group HUD, own group
 * icon) and its plugins' veto. VoiceChatHudMixin stops SVC drawing its own icons while the Lads elements are on.
 */
public final class VoiceChatIntegration {
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("voicechat");
    private static boolean failed;
    private static long cachedAt;
    private static VoiceChatState cached;

    private VoiceChatIntegration() {}

    public static boolean loaded() { return LOADED && !failed; }

    /** At most once a frame's worth of time apart: both elements read it every HUD frame. */
    public static VoiceChatState state() {
        if (!loaded()) return null;
        long now = System.nanoTime();
        if (cached != null && now - cachedAt < 16_000_000L) return cached;
        try {
            cached = read();
            cachedAt = now;
            return cached;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            failed = true;
            LoggerFactory.getLogger("TheLadsCore").warn("Simple Voice Chat HUD integration unavailable", failure);
            return null;
        }
    }

    private static VoiceChatState read() throws ReflectiveOperationException {
        Class<?> manager = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
        Object states = invoke(manager, null, "getPlayerStateManager"), client = invoke(manager, null, "getClient");
        Object config = Class.forName("de.maxhenkel.voicechat.VoicechatClient").getField("CLIENT_CONFIG").get(null);
        Object plugins = invoke(Class.forName("de.maxhenkel.voicechat.plugins.ClientPluginManager"), null, "instance");
        if (states == null || config == null || !showIcons(client, plugins) || setting(config, "hideIcons") || !setting(config, "showHudIcons"))
            return new VoiceChatState(null, List.of());
        boolean disconnected = (Boolean) call(states, "isDisconnected");
        boolean startup = client != null && System.currentTimeMillis() - (Long) call(client, "getStartTime") < 5000L;
        if ((disconnected && startup) || !(Boolean) call(plugins, "shouldRenderHudIcons")) return new VoiceChatState(null, List.of());
        String icon = null;
        if (disconnected) icon = "disconnected";
        else if ((Boolean) call(states, "isDisabled")) icon = "speaker_off";
        else if ((Boolean) call(states, "isMuted") && String.valueOf(entry(config, "microphoneActivationType")).equals("VOICE")) icon = "microphone_off";
        else if (client != null && call(client, "getMicThread") != null) {
            Object mic = call(client, "getMicThread");
            if ((Boolean) call(mic, "isWhispering")) icon = "microphone_whisper";
            else if ((Boolean) call(mic, "isTalking")) icon = "microphone";
        }
        List<VoiceMember> group = new ArrayList<>();
        Object groupId = call(states, "getGroupID");
        if (groupId != null && setting(config, "showGroupHud") && client != null) {
            Object talk = call(client, "getTalkCache");
            for (Object state : (List<?>) call(states, "getPlayerStates", setting(config, "showOwnGroupIcon"))) {
                if (!(Boolean) call(state, "hasGroup") || !groupId.equals(call(state, "getGroup"))) continue;
                UUID uuid = (UUID) call(state, "getUuid");
                group.add(new VoiceMember((String) call(state, "getName"), uuid.toString(), (Boolean) call(talk, "isTalking", uuid),
                    (Boolean) call(state, "isDisabled")));
            }
            group.sort(Comparator.comparing(VoiceMember::name));
        }
        return new VoiceChatState(icon == null ? null : "voicechat:icons/" + icon, group);
    }

    /** RenderEvents.shouldShowIcons: forced by a plugin, else not while onboarding, else when connected or not in a closed singleplayer world. */
    private static boolean showIcons(Object client, Object plugins) throws ReflectiveOperationException {
        if ((Boolean) call(plugins, "shouldForceShowIcons")) return true;
        if ((Boolean) invoke(Class.forName("de.maxhenkel.voicechat.gui.onboarding.OnboardingManager"), null, "isOnboarding")) return false;
        Object connection = client != null ? call(client, "getConnection") : null;
        if (connection != null && (Boolean) call(connection, "isInitialized")) return true;
        var server = Minecraft.getInstance().getSingleplayerServer();
        return server == null || server.isPublished();
    }

    private static boolean setting(Object config, String name) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(entry(config, name));
    }

    private static Object entry(Object config, String name) throws ReflectiveOperationException {
        return call(config.getClass().getField(name).get(config), "get");
    }

    private static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        return invoke(target.getClass(), target, name, args);
    }

    /** A public method by name and argument count (SVC's are not overloaded by count), looked up once per class. */
    private static Object invoke(Class<?> type, Object target, String name, Object... args) throws ReflectiveOperationException {
        Method method = METHODS.get(type.getName() + "#" + name + args.length);
        if (method == null) {
            for (Method candidate : type.getMethods())
                if (candidate.getName().equals(name) && candidate.getParameterCount() == args.length
                    && (args.length == 0 || !candidate.getParameterTypes()[0].isAssignableFrom(net.minecraft.world.entity.Entity.class))) {
                    method = candidate;
                    break;
                }
            if (method == null) throw new NoSuchMethodException(type.getName() + "." + name);
            method.setAccessible(true); // public methods of SVC's package-private implementation classes
            METHODS.put(type.getName() + "#" + name + args.length, method);
        }
        return method.invoke(target, args);
    }
}
