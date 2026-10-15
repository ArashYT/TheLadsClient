package terrablender.api;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;

/**
 * Compatibility bridge for Minecraft 26.3 mods (such as Biolith 3.8.0-alpha.2 in Promenade 6.0.0
 * and Citadel/Alex's Caves 1.1.1) that reference the pre-26.3 {@code terrablender.api.SurfaceRuleManager}
 * class, which TerraBlender 26.3 renamed to {@code terrablender.api.MaterialRuleManager}.
 */
public class SurfaceRuleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ladssmp");

    public enum RuleCategory {
        OVERWORLD,
        NETHER,
        END
    }

    public enum RuleStage {
        BEFORE_BEDROCK,
        AFTER_BEDROCK
    }

    @FunctionalInterface
    public interface RuleBuilder extends Function<RegistryAccess, MaterialRule> {
    }

    private static Class<?> materialRuleManagerClass;
    private static Class<?> tbRuleCategoryClass;
    private static Class<?> tbRuleStageClass;
    private static Class<?> tbRuleBuilderClass;
    private static Method addRulesByBuilderMethod;
    private static Method addRulesByKeyMethod;
    private static Method addToDefaultRulesAtStageMethod;
    private static Method setDefaultRulesMethod;
    private static Method removeRulesMethod;
    private static Method getDefaultRulesMethod;
    private static Method getNamespacedRulesMethod;
    private static boolean initialized = false;

    private static synchronized boolean ensureInitialized() {
        if (initialized) {
            return materialRuleManagerClass != null;
        }
        initialized = true;
        try {
            ClassLoader cl = SurfaceRuleManager.class.getClassLoader();
            materialRuleManagerClass = Class.forName("terrablender.api.MaterialRuleManager", true, cl);
            tbRuleCategoryClass = Class.forName("terrablender.api.MaterialRuleManager$RuleCategory", true, cl);
            tbRuleStageClass = Class.forName("terrablender.api.MaterialRuleManager$RuleStage", true, cl);
            tbRuleBuilderClass = Class.forName("terrablender.api.MaterialRuleManager$RuleBuilder", true, cl);

            addRulesByBuilderMethod = materialRuleManagerClass.getMethod(
                    "addRules", tbRuleCategoryClass, String.class, tbRuleBuilderClass);
            addRulesByKeyMethod = materialRuleManagerClass.getMethod(
                    "addRules", tbRuleCategoryClass, String.class, ResourceKey.class);
            addToDefaultRulesAtStageMethod = materialRuleManagerClass.getMethod(
                    "addToDefaultRulesAtStage", tbRuleCategoryClass, tbRuleStageClass, int.class, tbRuleBuilderClass);
            setDefaultRulesMethod = materialRuleManagerClass.getMethod(
                    "setDefaultRules", tbRuleCategoryClass, tbRuleBuilderClass);
            removeRulesMethod = materialRuleManagerClass.getMethod(
                    "removeRules", tbRuleCategoryClass, String.class);
            getDefaultRulesMethod = materialRuleManagerClass.getMethod(
                    "getDefaultRules", tbRuleCategoryClass);
            getNamespacedRulesMethod = materialRuleManagerClass.getMethod(
                    "getNamespacedRules", tbRuleCategoryClass, MaterialRule.class);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[LadsSMP] Could not initialize TerraBlender MaterialRuleManager bridge: {}", t.toString());
            materialRuleManagerClass = null;
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object toTbCategory(RuleCategory category) {
        if (category == null || tbRuleCategoryClass == null) {
            return null;
        }
        return Enum.valueOf((Class<Enum>) tbRuleCategoryClass, category.name());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object toTbStage(RuleStage stage) {
        if (stage == null || tbRuleStageClass == null) {
            return null;
        }
        return Enum.valueOf((Class<Enum>) tbRuleStageClass, stage.name());
    }

    private static Object toTbRuleBuilder(RuleBuilder builder) {
        if (builder == null || tbRuleBuilderClass == null) {
            return null;
        }
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("apply".equals(name) && args != null && args.length == 1) {
                return builder.apply((RegistryAccess) args[0]);
            }
            if ("equals".equals(name) && args != null && args.length == 1) {
                return proxy == args[0];
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(name)) {
                return "LadsSurfaceRuleBuilderProxy[" + builder + "]";
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            throw new UnsupportedOperationException(method.toString());
        };
        return Proxy.newProxyInstance(tbRuleBuilderClass.getClassLoader(), new Class<?>[]{tbRuleBuilderClass}, handler);
    }

    /**
     * Adapts Biolith's {@code MaterialRuleBootstrapper} ({@code Function<HolderGetter<Biome>, MaterialRule>})
     * or any {@code Function<?, MaterialRule>} into a {@link RuleBuilder} ({@code Function<RegistryAccess, MaterialRule>}).
     */
    @SuppressWarnings("unchecked")
    public static RuleBuilder wrapBootstrapper(Object bootstrapper) {
        if (bootstrapper == null) {
            return null;
        }
        if (bootstrapper instanceof RuleBuilder rb) {
            return rb;
        }
        if (bootstrapper instanceof Function<?, ?> fn) {
            Function<Object, MaterialRule> rawFn = (Function<Object, MaterialRule>) fn;
            return registryAccess -> {
                try {
                    HolderGetter<Biome> biomeLookup = registryAccess.lookupOrThrow(Registries.BIOME);
                    return rawFn.apply(biomeLookup);
                } catch (ClassCastException e) {
                    return rawFn.apply(registryAccess);
                }
            };
        }
        return null;
    }

    public static void addSurfaceRules(RuleCategory category, String namespace, MaterialRule ruleSource) {
        if (ruleSource == null) {
            return;
        }
        addSurfaceRules(category, namespace, (RuleBuilder) registryAccess -> ruleSource);
    }

    public static void addSurfaceRules(RuleCategory category, String namespace, RuleBuilder ruleBuilder) {
        if (!ensureInitialized() || category == null || namespace == null || ruleBuilder == null) {
            return;
        }
        try {
            addRulesByBuilderMethod.invoke(null, toTbCategory(category), namespace, toTbRuleBuilder(ruleBuilder));
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException re) {
                throw re;
            }
            LOGGER.warn("[LadsSMP] Failed to bridge addSurfaceRules for {}: {}", namespace, e.getTargetException().toString());
        } catch (Throwable t) {
            LOGGER.warn("[LadsSMP] Failed to bridge addSurfaceRules for {}: {}", namespace, t.toString());
        }
    }

    public static void addSurfaceRules(RuleCategory category, String namespace, ResourceKey<MaterialRule> ruleKey) {
        if (!ensureInitialized() || category == null || namespace == null || ruleKey == null) {
            return;
        }
        try {
            addRulesByKeyMethod.invoke(null, toTbCategory(category), namespace, ruleKey);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException re) {
                throw re;
            }
            LOGGER.warn("[LadsSMP] Failed to bridge addSurfaceRules(key) for {}: {}", namespace, e.getTargetException().toString());
        } catch (Throwable t) {
            LOGGER.warn("[LadsSMP] Failed to bridge addSurfaceRules(key) for {}: {}", namespace, t.toString());
        }
    }

    public static void addToDefaultSurfaceRulesAtStage(RuleCategory category, RuleStage stage, int index, MaterialRule ruleSource) {
        if (ruleSource == null) {
            return;
        }
        addToDefaultSurfaceRulesAtStage(category, stage, index, (RuleBuilder) registryAccess -> ruleSource);
    }

    public static void addToDefaultSurfaceRulesAtStage(RuleCategory category, RuleStage stage, int index, RuleBuilder ruleBuilder) {
        if (!ensureInitialized() || category == null || stage == null || ruleBuilder == null) {
            return;
        }
        try {
            addToDefaultRulesAtStageMethod.invoke(null, toTbCategory(category), toTbStage(stage), index, toTbRuleBuilder(ruleBuilder));
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException re) {
                throw re;
            }
            LOGGER.warn("[LadsSMP] Failed to bridge addToDefaultSurfaceRulesAtStage: {}", e.getTargetException().toString());
        } catch (Throwable t) {
            LOGGER.warn("[LadsSMP] Failed to bridge addToDefaultSurfaceRulesAtStage: {}", t.toString());
        }
    }

    public static void setDefaultSurfaceRules(RuleCategory category, MaterialRule ruleSource) {
        if (ruleSource == null) {
            return;
        }
        setDefaultSurfaceRules(category, (RuleBuilder) registryAccess -> ruleSource);
    }

    public static void setDefaultSurfaceRules(RuleCategory category, RuleBuilder ruleBuilder) {
        if (!ensureInitialized() || category == null || ruleBuilder == null) {
            return;
        }
        try {
            setDefaultRulesMethod.invoke(null, toTbCategory(category), toTbRuleBuilder(ruleBuilder));
        } catch (Throwable t) {
            LOGGER.warn("[LadsSMP] Failed to bridge setDefaultSurfaceRules: {}", t.toString());
        }
    }

    public static void removeSurfaceRules(RuleCategory category, String namespace) {
        if (!ensureInitialized() || category == null || namespace == null) {
            return;
        }
        try {
            removeRulesMethod.invoke(null, toTbCategory(category), namespace);
        } catch (Throwable t) {
            LOGGER.warn("[LadsSMP] Failed to bridge removeSurfaceRules: {}", t.toString());
        }
    }

    public static MaterialRule getDefaultSurfaceRules(RuleCategory category) {
        if (!ensureInitialized() || category == null) {
            return null;
        }
        try {
            return (MaterialRule) getDefaultRulesMethod.invoke(null, toTbCategory(category));
        } catch (Throwable t) {
            return null;
        }
    }

    public static MaterialRule getNamespacedRules(RuleCategory category, MaterialRule fallback) {
        if (!ensureInitialized() || category == null) {
            return fallback;
        }
        try {
            return (MaterialRule) getNamespacedRulesMethod.invoke(null, toTbCategory(category), fallback);
        } catch (Throwable t) {
            return fallback;
        }
    }
}
