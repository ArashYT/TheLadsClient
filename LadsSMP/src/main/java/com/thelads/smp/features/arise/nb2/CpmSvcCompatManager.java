package com.thelads.smp.features.arise.nb2;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges Simple Voice Chat audio speaking indicators and mute state
 * to Customizable Player Models (CPM) (Customizable Player Models x Simple Voice Chat).
 */
public final class CpmSvcCompatManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/CPMSVC");
    private static final Map<UUID, Float> VOICE_LEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_VOICE_TIME = new ConcurrentHashMap<>();
    private static final Set<UUID> MUTED_PLAYERS = ConcurrentHashMap.newKeySet();

    private CpmSvcCompatManager() {}

    public static boolean isCompatEnabled() {
        return NB2Config.get().cpmSvcCompat;
    }

    public static void handleAudio(UUID uuid, short[] rawAudio) {
        if (!isCompatEnabled() || uuid == null || rawAudio == null || rawAudio.length == 0) {
            return;
        }

        float level = calculateAudioLevel(rawAudio);
        VOICE_LEVELS.put(uuid, level);
        LAST_VOICE_TIME.put(uuid, System.currentTimeMillis());
    }

    public static float getVoiceLevel(UUID uuid) {
        if (!isCompatEnabled() || uuid == null) {
            return 0.0f;
        }

        Long lastTime = LAST_VOICE_TIME.get(uuid);
        if (lastTime == null || (System.currentTimeMillis() - lastTime) > 250L) {
            VOICE_LEVELS.remove(uuid);
            return 0.0f;
        }

        return VOICE_LEVELS.getOrDefault(uuid, 0.0f);
    }

    public static void setMuted(UUID uuid, boolean muted) {
        if (uuid == null) {
            return;
        }
        if (muted) {
            MUTED_PLAYERS.add(uuid);
        } else {
            MUTED_PLAYERS.remove(uuid);
        }
    }

    public static boolean isMuted(UUID uuid) {
        if (!isCompatEnabled() || uuid == null) {
            return false;
        }
        return MUTED_PLAYERS.contains(uuid);
    }

    public static float calculateAudioLevel(short[] audio) {
        if (audio == null || audio.length == 0) {
            return 0.0f;
        }
        long sum = 0;
        for (short sample : audio) {
            sum += (long) sample * sample;
        }
        double rms = Math.sqrt((double) sum / audio.length);
        // Normalize 16-bit PCM (max 32767) to a clean 0.0 - 1.0 range
        float normalized = (float) (rms / 32768.0);
        return Math.max(0.0f, Math.min(1.0f, normalized * 3.0f));
    }

    public static void init() {
        if (!isCompatEnabled()) {
            return;
        }

        boolean hasCpm = FabricLoader.getInstance().isModLoaded("cpm");
        boolean hasSvc = FabricLoader.getInstance().isModLoaded("voicechat");

        if (hasCpm && hasSvc) {
            LOGGER.info("[CPMSVC] Initializing CPM x Simple Voice Chat speaking indicator bridge.");
        }
    }
}
