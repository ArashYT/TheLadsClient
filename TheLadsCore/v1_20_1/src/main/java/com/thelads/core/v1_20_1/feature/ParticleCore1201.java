package com.thelads.core.v1_20_1.feature;

import com.thelads.core.client.ParticleCore;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.ParticleReductionOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Particle Core on 1.20.1 (plain Fabric and Forge + Sinytra Connector, i.e. Zero SMP): the mixins in mixin/particlecore call in
 * here. It stands down, with every hook doing nothing, when the real Particle Core jar is installed; it draws its own vertices
 * only when neither Sodium nor Embeddium (which draw particles their own way) is.
 */
public final class ParticleCore1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-ParticleCore");
    private static final ConcurrentHashMap<ParticleType<?>, String> IDS = new ConcurrentHashMap<>();
    /** The camera of the level pass being drawn: only its particles are culled (another view would need its own frustum). */
    public static Camera frameCamera;
    private static Object level;
    private static Boolean deferVertices;

    private ParticleCore1201() {}

    public static void register() {
        boolean real = modLoaded("particle_core", "particlecore", "particle-core");
        ParticleCore.standDown(real);
        if (real) {
            ModuleSupport.registerExternal(ParticleCore.MODULE, "Particle Core", "particle_core", true);
            LOGGER.info("Particle Core mod is installed: Lads Particle Core stands down");
            return;
        }
        ModuleSupport.registerBuiltIn(ParticleCore.MODULE);
        ((ParticleReductionOption) ParticleCore.module().getOption(ParticleCore.RATES)).setCatalog(ParticleCore1201::catalog);
        ClientTickEvents.END_CLIENT_TICK.register(ParticleCore1201::tick);
        LOGGER.info("Particle Core ready");
    }

    private static void tick(Minecraft mc) {
        ParticleCore.refresh();
        if (level != mc.level) { level = mc.level; ParticleCore.worldChanged(); }
    }

    /** Sodium and Embeddium (and Rubidium) draw particle quads themselves: leave the vertex work to them. */
    public static boolean deferVertices() {
        Boolean value = deferVertices;
        if (value == null) {
            deferVertices = value = modLoaded("sodium", "embeddium", "rubidium") && !"1".equals(System.getenv("LADS_PARTICLE_CORE_FORCE_VERTICES")); // QA: measure our own vertex work beside Sodium
            LOGGER.info("Particle Core vertex work: {}", value ? "left to Sodium/Embeddium" : "Lads");
        }
        return value;
    }

    /** A mod id, as Fabric sees it (Connector lists Forge mods there too), or as Forge's own mod list does through reflection. */
    static boolean modLoaded(String... ids) {
        for (String id : ids) {
            if (FabricLoader.getInstance().isModLoaded(id)) return true;
            if (forgeLoaded(id)) return true;
        }
        return false;
    }

    private static boolean forgeLoaded(String id) {
        ClassLoader loader = ParticleCore1201.class.getClassLoader();
        try {
            Object list = Class.forName("net.minecraftforge.fml.ModList", false, loader).getMethod("get").invoke(null);
            if (list != null && Boolean.TRUE.equals(list.getClass().getMethod("isLoaded", String.class).invoke(list, id))) return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // not Forge, or its mod list is not up yet: the loading list below, or nothing
        }
        try {
            Object loading = Class.forName("net.minecraftforge.fml.loading.LoadingModList", false, loader).getMethod("get").invoke(null);
            return loading != null && loading.getClass().getMethod("getModFileById", String.class).invoke(loading, id) != null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    /** The type's id as stored in the rates: "flame", "particlerain:rain". */
    public static String id(ParticleType<?> type) {
        return IDS.computeIfAbsent(type, key -> {
            ResourceLocation location = BuiltInRegistries.PARTICLE_TYPE.getKey(key);
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
        for (ResourceLocation key : BuiltInRegistries.PARTICLE_TYPE.keySet())
            ids.add(key.getNamespace().equals("minecraft") ? key.getPath() : key.toString());
        return ids;
    }
}
