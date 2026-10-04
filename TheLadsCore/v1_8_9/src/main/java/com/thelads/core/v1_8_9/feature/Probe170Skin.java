package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.thelads.core.client.QaSkin;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.SkinLayersModule;
import com.thelads.core.v1_8_9.mixin.NetworkPlayerInfoAccessor;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * QA only: the SkinLayers module over the bundled 3D Skin Layers, run by CoreProbe first in its QA world. The local player wears
 * QaSkin (checkerboard outer layers) and no armour, seen from the front at full brightness. Module on: the mod has made 3D layers for them and hides
 * the flat ones (170-skinlayers-on.png). Off: no 3D layers and vanilla's flat layers show (170-skinlayers-off.png). Skin, module,
 * camera, pitch, brightness and armour are put back as found.
 */
final class Probe170Skin {
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe170Skin::start, Probe170Skin::on, Probe170Skin::off, Probe170Skin::restore);
    private static final ResourceLocation SKIN = new ResourceLocation("theladscore", "qa/skinlayers");
    private static boolean started, wasEnabled, texturesWere;
    private static long modifiedWas;
    private static int viewWas;
    private static float pitchWas, gammaWas;
    private static ResourceLocation skinWas;
    private static String typeWas;
    private static ItemStack[] armourWas;

    private Probe170Skin() {}

    private static boolean start(Minecraft mc) {
        check(SkinLayers189.LOADED, "3D Skin Layers is loaded next to OptiFine and the Core");
        Module module = module();
        NetworkPlayerInfoAccessor info = info(mc);
        wasEnabled = module.isEnabled();
        modifiedWas = module.getLastModified();
        viewWas = mc.gameSettings.thirdPersonView;
        pitchWas = mc.thePlayer.rotationPitch;
        gammaWas = mc.gameSettings.gammaSetting;
        skinWas = info.ladsGetSkin();
        texturesWere = info.ladsGetTexturesLoaded();
        typeWas = info.ladsGetSkinType();
        armourWas = mc.thePlayer.inventory.armorInventory.clone();
        started = true;
        mc.getTextureManager().loadTexture(SKIN, new DynamicTexture(QaSkin.image()));
        info.ladsSetTexturesLoaded(true); // never replaced by a skin download
        info.ladsSetSkin(SKIN);
        info.ladsSetSkinType("default");
        Arrays.fill(mc.thePlayer.inventory.armorInventory, null);
        module.setEnabled(true);
        mc.gameSettings.thirdPersonView = 2;
        mc.gameSettings.gammaSetting = 100; // the QA world may be at night
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 0;
        check(SKIN.equals(mc.thePlayer.getLocationSkin()), "the local player wears the QA skin");
        return after(40);
    }

    private static boolean on(Minecraft mc) throws Exception {
        ModelPlayer model = model(mc);
        check(layers(mc) != null, "SkinLayers on: 3D Skin Layers made 3D layers for the player");
        check(model.bipedHeadwear.isHidden && model.bipedBodyWear.isHidden && model.bipedRightArmwear.isHidden,
            "SkinLayers on: the flat hat, jacket and sleeve are hidden behind the 3D ones");
        screenshot(mc, "170-skinlayers-on");
        module().setEnabled(false);
        return after(30);
    }

    private static boolean off(Minecraft mc) throws Exception {
        ModelPlayer model = model(mc);
        check(layers(mc) == null, "SkinLayers off: the player's 3D layers are gone and none are made");
        check(!model.bipedHeadwear.isHidden && !model.bipedBodyWear.isHidden && !model.bipedLeftArmwear.isHidden && !model.bipedRightArmwear.isHidden
            && !model.bipedLeftLegwear.isHidden && !model.bipedRightLegwear.isHidden, "SkinLayers off: vanilla's flat layers show");
        screenshot(mc, "170-skinlayers-off");
        return after(1);
    }

    private static boolean restore(Minecraft mc) throws Exception {
        stop();
        check(module().isEnabled() == wasEnabled && mc.gameSettings.thirdPersonView == viewWas && mc.thePlayer.getLocationSkin().equals(
            skinWas != null ? skinWas : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(mc.thePlayer.getUniqueID())) && layers(mc) == null,
            "SkinLayers, camera and the player's own skin are back");
        return after(10);
    }

    /** Puts everything back; also when the self-test fails part way. */
    static void stop() {
        if (!started) return;
        started = false;
        Minecraft mc = Minecraft.getMinecraft();
        Module module = module();
        module.setEnabled(wasEnabled);
        module.setLastModified(modifiedWas);
        mc.gameSettings.thirdPersonView = viewWas;
        mc.gameSettings.gammaSetting = gammaWas;
        if (mc.thePlayer == null) return;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = pitchWas;
        System.arraycopy(armourWas, 0, mc.thePlayer.inventory.armorInventory, 0, armourWas.length);
        NetworkPlayerInfoAccessor info = info(mc);
        info.ladsSetSkin(skinWas);
        info.ladsSetSkinType(typeWas);
        info.ladsSetTexturesLoaded(texturesWere);
        mc.getTextureManager().deleteTexture(SKIN);
        SkinLayers189.refresh(mc.thePlayer);
    }

    private static Module module() { return ModuleManager.getInstance().getModule(SkinLayersModule.NAME); }

    private static NetworkPlayerInfoAccessor info(Minecraft mc) {
        return (NetworkPlayerInfoAccessor) mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
    }

    private static ModelPlayer model(Minecraft mc) {
        return ((RenderPlayer) (Object) mc.getRenderManager().getEntityRenderObject(mc.thePlayer)).getMainModel();
    }

    /** The player's 3D layers from the mod (PlayerSettings.getSkinLayers, mixed into EntityPlayer), or null. */
    private static Object layers(Minecraft mc) throws Exception {
        return mc.thePlayer.getClass().getMethod("getSkinLayers").invoke(mc.thePlayer);
    }
}
