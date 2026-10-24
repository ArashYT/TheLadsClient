package com.thelads.core.v1_8_9.mixin.optifine;

import com.thelads.core.modules.FogCustomizerModule;
import com.thelads.core.modules.OptiFine189Module;
import com.thelads.core.modules.WeatherChangerModule;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * OptiFine Fog and Weather options:
 * - Fog Fancy / Fast / OFF (OFF removes the distance fog only, as OptiFine's: Blindness, water and lava keep theirs)
 * - Fog Start distance scaling
 * - Clear Water underwater fog enhancement
 * - Rain & Snow precipitation gating
 * Fog Customizer (FogCustomizerModule) and Weather Changer's precipitation (WeatherChangerModule) on 1.8.9 too.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererFogOfMixin189 {
    @Shadow @Final private Minecraft mc;
    @Shadow private float farPlaneDistance;
    @Shadow private boolean cloudFog;
    /** Weather Changer's weather for this renderRainSnow or addRainParticles call (read once, not per rain column); -1 off. */
    @Unique private int lads$weather = -1;

    @Inject(method = "renderRainSnow", at = @At("HEAD"), cancellable = true)
    private void lads$renderRainSnow(float partialTicks, CallbackInfo ci) {
        lads$weather = lads$forcedWeather();
        if (OptiFine189Module.sOfRain == 3) { // OFF
            ci.cancel();
        }
    }

    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private void lads$setupFogHead(int startEnd, float partialTicks, CallbackInfo ci) {
        if (OptiFine189Module.sOfFogType == 2 && lads$medium(partialTicks) == FogCustomizerModule.TERRAIN) { // Fog OFF
            GlStateManager.disableFog();
            ci.cancel();
        }
    }

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void lads$setupFogReturn(int startEnd, float partialTicks, CallbackInfo ci) {
        if (lads$customFog(startEnd, partialTicks)) return;
        if (OptiFine189Module.sOfFogType == 2) return;

        Entity entity = mc.getRenderViewEntity();
        if (entity == null) return;

        boolean inWater = ActiveRenderInfo.getBlockAtEntityViewpoint(mc.theWorld, entity, partialTicks).getMaterial() == Material.water;

        if (inWater && OptiFine189Module.sOfClearWater) {
            // Clear water: push fog start & end much farther
            GlStateManager.setFogStart(farPlaneDistance * 0.1f);
            GlStateManager.setFogEnd(farPlaneDistance * 0.8f);
            GlStateManager.setFogDensity(0.01f);
            return;
        }

        if (startEnd >= 0) {
            float start = farPlaneDistance * OptiFine189Module.sOfFogStart;
            GlStateManager.setFogStart(start);
            if (OptiFine189Module.sOfFogType == 0) {
                // Fast fog uses GL_EXP
                GlStateManager.setFog(GL11.GL_EXP);
            }
        }
    }

    /** setupFog's branches in vanilla's order: Blindness, the cloud fog (left alone: -1), water, lava, else the distance fog. */
    @Unique
    private int lads$medium(float partialTicks) {
        Entity entity = mc.getRenderViewEntity();
        if (entity == null) return -1;
        if (entity instanceof EntityLivingBase && ((EntityLivingBase) entity).isPotionActive(Potion.blindness)) return FogCustomizerModule.EFFECT;
        if (cloudFog) return -1;
        Material material = ActiveRenderInfo.getBlockAtEntityViewpoint(mc.theWorld, entity, partialTicks).getMaterial();
        return FogCustomizerModule.medium(material == Material.lava, false, material == Material.water);
    }

    /**
     * Fog Customizer: a cleared fog goes past the far plane; the distance fog's start and end (vanilla's, with OptiFine's Fog Start, or the
     * Nether-like close fog) scale by Fog Start and Fog End. True when it set the fog, so the OptiFine options leave it.
     */
    @Unique
    private boolean lads$customFog(int startEnd, float partialTicks) {
        FogCustomizerModule fog = FogCustomizerModule.get();
        if (fog == null || !fog.isEnabled()) return false;
        int medium = lads$medium(partialTicks);
        if (medium < 0) return false;
        if (fog.clears(medium)) {
            GlStateManager.setFog(GL11.GL_LINEAR);
            GlStateManager.setFogStart(FogCustomizerModule.FAR);
            GlStateManager.setFogEnd(FogCustomizerModule.FAR * 2);
            return true;
        }
        if (medium != FogCustomizerModule.TERRAIN) return false;
        Entity entity = mc.getRenderViewEntity();
        boolean close = mc.theWorld.provider.doesXZShowFog((int) entity.posX, (int) entity.posZ);
        float start = fog.start(close ? farPlaneDistance * 0.05f : startEnd == -1 ? 0 : farPlaneDistance * OptiFine189Module.sOfFogStart);
        GlStateManager.setFog(GL11.GL_LINEAR);
        GlStateManager.setFogStart(start);
        GlStateManager.setFogEnd(fog.end(close ? Math.min(farPlaneDistance, 192) * 0.5f : farPlaneDistance, start));
        return true;
    }

    @Unique
    private static int lads$forcedWeather() {
        WeatherChangerModule changer = WeatherChangerModule.get();
        return changer == null ? -1 : changer.forced();
    }

    /**
     * Weather Changer: rain, thunder and snow fall in every biome, dry ones too (renderRainSnow's columns, addRainParticles' splashes).
     * require = 2 and the temperature redirect's require = 1 hold with OptiFine too: HD U M5's and M6 pre2's patched EntityRenderer
     * (rebuilt from their patch files) keep exactly one canRain call in each method and one getTemperatureAtHeight in renderRainSnow.
     */
    @Redirect(method = {"renderRainSnow", "addRainParticles"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/biome/BiomeGenBase;canRain()Z"),
        require = 2)
    private boolean lads$weatherEverywhere(BiomeGenBase biome) {
        return lads$weather > WeatherChangerModule.CLEAR || biome.canRain();
    }

    /** Weather Changer: renderRainSnow draws snow below 0.15 and rain above; what falls follows WeatherChangerModule.precipitation. */
    @Redirect(method = "renderRainSnow", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/biome/WorldChunkManager;getTemperatureAtHeight(FI)F"),
        require = 1)
    private float lads$weatherFalls(WorldChunkManager chunks, float temperature, int height) {
        float vanilla = chunks.getTemperatureAtHeight(temperature, height);
        if (lads$weather < 0) return vanilla;
        int falls = WeatherChangerModule.precipitation(lads$weather, vanilla >= 0.15f ? WeatherChangerModule.FALLS_RAIN : WeatherChangerModule.FALLS_SNOW);
        return falls == WeatherChangerModule.FALLS_SNOW ? 0 : Math.max(vanilla, 0.15f);
    }

    /** Weather Changer's snow: no rain splashes or rain sounds. */
    @Inject(method = "addRainParticles", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$weatherSplashes(CallbackInfo ci) {
        lads$weather = lads$forcedWeather();
        if (lads$weather == WeatherChangerModule.SNOW) ci.cancel();
    }
}
