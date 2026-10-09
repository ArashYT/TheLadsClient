package com.thelads.core.v26_2.feature;

import com.thelads.core.client.ParticleCore;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.ParticleReductionOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Particle Core on 26.3 (mixins in mixin/particlecore call in here); it replaces the old ParticleBudget module. It stands down,
 * every hook doing nothing, when the real Particle Core mod is installed. 26.3 already skips particles whose centre is outside
 * the view, so Culling only switches that on or off here; the rest is the spawn side (rates, potion swirls, decorative quota)
 * and cheaper quad building, which is left to Sodium when that is installed.
 */
public final class NativeParticleCore {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-ParticleCore");
    private static final ConcurrentHashMap<ParticleType<?>, String> IDS = new ConcurrentHashMap<>();
    private static Object level;
    private static Boolean deferVertices;

    private NativeParticleCore() {}

    public static void register() {
        boolean real = loaded("particle_core", "particlecore", "particle-core");
        ParticleCore.standDown(real);
        if (real) {
            ModuleSupport.registerExternal(ParticleCore.MODULE, "Particle Core", "particle_core", true);
            LOGGER.info("Particle Core mod is installed: Lads Particle Core stands down");
            return;
        }
        ModuleSupport.registerBuiltIn(ParticleCore.MODULE);
        ((ParticleReductionOption) ParticleCore.module().getOption(ParticleCore.RATES)).setCatalog(NativeParticleCore::catalog);
        ClientTickEvents.END_CLIENT_TICK.register(NativeParticleCore::tick);
        LOGGER.info("Particle Core ready");
    }

    private static void tick(Minecraft mc) {
        ParticleCore.refresh();
        if (level != mc.level) { level = mc.level; ParticleCore.worldChanged(); }
    }

    private static boolean loaded(String... ids) {
        for (String id : ids) if (FabricLoader.getInstance().isModLoaded(id)) return true;
        return false;
    }

    /** Sodium draws particle quads itself where it is installed: leave the vertex work to it. */
    public static boolean deferVertices() {
        Boolean value = deferVertices;
        if (value == null) {
            deferVertices = value = loaded("sodium", "embeddium");
            LOGGER.info("Particle Core vertex work: {}", value ? "left to Sodium" : "Lads");
        }
        return value;
    }

    /** The type's id as stored in the rates: "flame", "particlerain:rain". */
    public static String id(ParticleType<?> type) {
        return IDS.computeIfAbsent(type, key -> {
            var location = BuiltInRegistries.PARTICLE_TYPE.getKey(key);
            return location == null ? "?" : location.getNamespace().equals("minecraft") ? location.getPath() : location.toString();
        });
    }

    /** False drops the particle about to spawn. */
    public static boolean allow(ParticleOptions options, double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        String id = id(options.getType());
        var player = mc.player;
        if (player == null || mc.level == null) return ParticleCore.allowSpawn(id, false, 0, 0);
        boolean own = ParticleCore.isPotion(id) && player.getBoundingBox().inflate(0.6).contains(x, y, z);
        return ParticleCore.allowSpawn(id, own, player.distanceToSqr(x, y, z), mc.level.getGameTime());
    }

    private static List<String> catalog() {
        List<String> ids = new ArrayList<>();
        for (var key : BuiltInRegistries.PARTICLE_TYPE.keySet())
            ids.add(key.getNamespace().equals("minecraft") ? key.getPath() : key.toString());
        return ids;
    }
}
