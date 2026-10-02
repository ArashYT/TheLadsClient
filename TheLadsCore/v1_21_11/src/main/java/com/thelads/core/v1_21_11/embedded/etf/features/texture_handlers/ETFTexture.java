package com.thelads.core.v1_21_11.embedded.etf.features.texture_handlers;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_21_11.embedded.etf.ETF;
import com.thelads.core.v1_21_11.embedded.etf.ETFException;
import com.thelads.core.v1_21_11.embedded.etf.config.ETFConfig;
import com.thelads.core.v1_21_11.embedded.etf.features.ETFManager;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFEntity;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFUtils2;

import java.util.Optional;
import java.util.Properties;


//can either refer to a vanilla identifier or a variant
public class ETFTexture {
    public final static String PATCH_NAMESPACE_PREFIX = "etf_patched_";
    public final Identifier thisIdentifier;
    public TextureReturnState currentTextureState = TextureReturnState.NORMAL;
    public String eSuffix = null;
    //a variation of thisIdentifier but with emissive texture pixels removed for z-fighting solution
    private Identifier thisIdentifier_Patched = null;
    //the emissive version of this texture
    private Identifier emissiveIdentifier = null;
    private Identifier emissiveBlinkIdentifier = null;
    private Identifier emissiveBlink2Identifier = null;
    private Identifier enchantIdentifier = null;
    private Identifier enchantBlinkIdentifier = null;
    private Identifier enchantBlink2Identifier = null;
    private Identifier blinkIdentifier = null;
    private Identifier blink2Identifier = null;
    private Identifier blinkIdentifier_Patched = null;
    private Identifier blink2Identifier_Patched = null;
    private Integer blinkLength = ETF.config().getConfig().blinkLength;
    private Integer blinkFrequency = ETF.config().getConfig().blinkFrequency;
    private ETFSprite atlasSprite = null;
    private Boolean resourceExists = null;
    private boolean guiBlink = false;
    private boolean hasPatched = false;

    public ETFTexture(Identifier variantIdentifier) {
        if (variantIdentifier == null) {
            ETFUtils2.logError("ETFTexture had a null identifier this should NOT happen");
            thisIdentifier = null;
            return;
        }

        this.thisIdentifier = variantIdentifier;

        if (variantIdentifier.getPath().endsWith(".png")) {
            setupBlinking();
            setupEmissives();
            setupEnchants();
        }
    }

    public static ETFTexture manual(@NotNull Identifier modifiedSkinIdentifier,
                                    @Nullable Identifier blinkIdentifier,
                                    @Nullable Identifier blink2Identifier,
                                    @Nullable Identifier emissiveIdentifier,
                                    @Nullable Identifier blinkEmissiveIdentifier,
                                    @Nullable Identifier blink2EmissiveIdentifier,
                                    @Nullable Identifier enchantIdentifier,
                                    @Nullable Identifier blinkenchantIdentifier,
                                    @Nullable Identifier blink2enchantIdentifier,
                                    @Nullable Identifier patchIdentifier,
                                    @Nullable Identifier blinkpatchIdentifier,
                                    @Nullable Identifier blink2patchIdentifier){
        return new ETFTexture(modifiedSkinIdentifier, blinkIdentifier, blink2Identifier, emissiveIdentifier, blinkEmissiveIdentifier, blink2EmissiveIdentifier, enchantIdentifier, blinkenchantIdentifier, blink2enchantIdentifier, patchIdentifier, blinkpatchIdentifier, blink2patchIdentifier);
    }

    public static ETFTexture manual(@NotNull Identifier modifiedSkinIdentifier,
                                    @Nullable Identifier emissiveIdentifier,
                                    @Nullable Identifier enchantIdentifier){
        return new ETFTexture(modifiedSkinIdentifier, null, null, emissiveIdentifier, null, null, enchantIdentifier, null, null, null, null, null);
    }

    //alternative initiator for already known textures used for players
    private ETFTexture(@NotNull Identifier modifiedSkinIdentifier,
                      @Nullable Identifier blinkIdentifier,
                      @Nullable Identifier blink2Identifier,
                      @Nullable Identifier emissiveIdentifier,
                      @Nullable Identifier blinkEmissiveIdentifier,
                      @Nullable Identifier blink2EmissiveIdentifier,
                      @Nullable Identifier enchantIdentifier,
                      @Nullable Identifier blinkenchantIdentifier,
                      @Nullable Identifier blink2enchantIdentifier,
                      @Nullable Identifier patchIdentifier,
                      @Nullable Identifier blinkpatchIdentifier,
                      @Nullable Identifier blink2patchIdentifier) {
        //ALL input already tested and confirmed existing
        this.thisIdentifier = modifiedSkinIdentifier;
        this.blinkIdentifier = blinkIdentifier;
        this.blink2Identifier = blink2Identifier;
        this.emissiveIdentifier = emissiveIdentifier;
        this.emissiveBlinkIdentifier = blinkEmissiveIdentifier;
        this.emissiveBlink2Identifier = blink2EmissiveIdentifier;
        this.thisIdentifier_Patched = patchIdentifier;
        this.blinkIdentifier_Patched = blinkpatchIdentifier;
        this.blink2Identifier_Patched = blink2patchIdentifier;
        this.enchantIdentifier = enchantIdentifier;
        this.enchantBlinkIdentifier = blinkenchantIdentifier;
        this.enchantBlink2Identifier = blink2enchantIdentifier;

        hasPatched = thisIdentifier_Patched != null;
        if (hasPatched) {
            ETFManager.getInstance().ETF_TEXTURE_CACHE.put(thisIdentifier_Patched, this);
            if (blinkIdentifier_Patched != null)
                ETFManager.getInstance().ETF_TEXTURE_CACHE.put(blinkIdentifier_Patched, this);
            if (blink2Identifier_Patched != null)
                ETFManager.getInstance().ETF_TEXTURE_CACHE.put(blink2Identifier_Patched, this);
        }

        //register this etf texture
        ETFManager.getInstance().ETF_TEXTURE_CACHE.put(thisIdentifier, this);
        if (blinkIdentifier != null)
            ETFManager.getInstance().ETF_TEXTURE_CACHE.put(blinkIdentifier, this);
        if (blink2Identifier != null)
            ETFManager.getInstance().ETF_TEXTURE_CACHE.put(blink2Identifier, this);
    }


    //alternative initiator for already known textures used for MooShroom's mushrooms
    private ETFTexture(@NotNull Identifier modifiedSkinIdentifier,
                       @Nullable Identifier emissiveIdentifier) {

        //ALL input already tested and confirmed existing
        this.thisIdentifier = modifiedSkinIdentifier;
        this.emissiveIdentifier = emissiveIdentifier;
    }

    public static ETFTexture ofUnmodifiable(@NotNull Identifier identifier, @Nullable Identifier emissiveIdentifier) {
        return new ETFTexture(identifier, emissiveIdentifier);
    }

    public static void patchTextureToRemoveZFightingWithOtherTexture(NativeImage baseImage, NativeImage otherImage) throws IndexOutOfBoundsException {
        //here we alter the first image removing all pixels that are present in the second image to prevent z fighting
        //this does not support transparency and is a hard counter to z-fighting
        try {
            if (otherImage.getWidth() == baseImage.getWidth() && otherImage.getHeight() == baseImage.getHeight()) {
                for (int x = 0; x < baseImage.getWidth(); x++) {
                    for (int y = 0; y < baseImage.getHeight(); y++) {
                        if (otherImage.getLuminanceOrAlpha(x, y) != 0) {
                            ETFUtils2.setPixel(baseImage, x, y, 0);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new ETFException("additional texture is not the correct size, ETF has crashed in the patching stage");
        }
    }

    private static boolean doesAnimaticaVersionExist(Identifier identifier) {
        if (identifier == null) return false;
        String idString = identifier.toString();
        //check if its already an anim and animatica has already gotten to replacing it before etf sees it
        if (idString.endsWith("-anim")) return true;
        //check if animatica has registered its animated version of this texture
        //noinspection ConstantValue
        return Minecraft.getInstance().getTextureManager().getTexture(ETFUtils2.res(idString + "-anim")
                ) != null;
    }


    private void setupBlinking() {
        if (!ETF.config().getConfig().enableBlinking) return;

        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        Optional<Resource> vanillaR1 = resourceManager.getResource(thisIdentifier);
        if (vanillaR1.isEmpty()) return;

        Identifier possibleBlinkIdentifier = ETFUtils2.replaceIdentifier(thisIdentifier, ".png", "_blink.png");
        Optional<Resource> blinkR1 = resourceManager.getResource(possibleBlinkIdentifier);
        if (blinkR1.isEmpty()) return;

        String blink1PackName = blinkR1.get().sourcePackId();
        if (!blink1PackName.equals(ETFUtils2.returnNameOfHighestPackFromTheseTwo(blink1PackName, vanillaR1.get().sourcePackId())))
            return;

        blinkIdentifier = possibleBlinkIdentifier;

        Identifier possibleBlink2Identifier = ETFUtils2.replaceIdentifier(thisIdentifier, ".png", "_blink2.png");
        Optional<Resource> blinkR2 = resourceManager.getResource(possibleBlink2Identifier);
        if (blinkR2.isPresent() && blink1PackName.equals(blinkR2.get().sourcePackId())) {
            blink2Identifier = possibleBlink2Identifier;
        }

        Identifier propertyIdentifier = ETFUtils2.replaceIdentifier(possibleBlinkIdentifier, ".png", ".properties");
        Properties blinkingProps = ETFUtils2.readAndReturnPropertiesElseNull(propertyIdentifier);
        if (blinkingProps == null) return;

        Optional<Resource> propertyResource = resourceManager.getResource(propertyIdentifier);
        if (propertyResource.isEmpty() || !propertyResource.get().sourcePackId().equals(ETFUtils2.returnNameOfHighestPackFromTheseTwo(propertyResource.get().sourcePackId(), blink1PackName)))
            return;

        blinkLength = blinkingProps.containsKey("blinkLength") ? Integer.parseInt(blinkingProps.getProperty("blinkLength").replaceAll("\\D", "")) : ETF.config().getConfig().blinkLength;
        blinkFrequency = blinkingProps.containsKey("blinkFrequency") ? Integer.parseInt(blinkingProps.getProperty("blinkFrequency").replaceAll("\\D", "")) : ETF.config().getConfig().blinkFrequency;
    }

    public boolean exists() {
        if (resourceExists == null) {
            resourceExists = Minecraft.getInstance().getResourceManager().getResource(thisIdentifier).isPresent();
        }
        return resourceExists;
    }

    private void setupEmissives() {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();

        for (String suffix : ETFManager.getInstance().EMISSIVE_SUFFIX_LIST) {
            var baseResource = getResourceOrModifyForTrims(resourceManager);
            if (baseResource.isEmpty()) continue;

            Identifier emissiveId = ETFUtils2.replaceIdentifier(thisIdentifier, ".png", suffix + ".png");
            Optional<Resource> emissiveResource = resourceManager.getResource(emissiveId);
            if (emissiveResource.isEmpty()) continue;

            String emissivePack = emissiveResource.get().sourcePackId();
            if (!emissivePack.equals(ETFUtils2.returnNameOfHighestPackFromTheseTwo(emissivePack, baseResource.get().sourcePackId())))
                continue;

            emissiveIdentifier = emissiveId;
            eSuffix = suffix;

            Identifier blinkId = ETFUtils2.replaceIdentifier(thisIdentifier, ".png", "_blink" + suffix + ".png");
            Optional<Resource> blinkResource = resourceManager.getResource(blinkId);
            if (blinkResource.isPresent() && emissivePack.equals(blinkResource.get().sourcePackId())) {
                emissiveBlinkIdentifier = blinkId;

                Identifier blink2Id = ETFUtils2.replaceIdentifier(thisIdentifier, ".png", "_blink2" + suffix + ".png");
                Optional<Resource> blink2Resource = resourceManager.getResource(blink2Id);
                if (blink2Resource.isPresent() && emissivePack.equals(blink2Resource.get().sourcePackId())) {
                    emissiveBlink2Identifier = blink2Id;
                }
            }
            break;
        }

    }

    private void setupEnchants() {
        if (!ETF.config().getConfig().enableEnchantedTextures) return;

        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        String enchantSuffix = "_enchant";
        Optional<Resource> vanillaR1 = getResourceOrModifyForTrims(resourceManager);
        if (vanillaR1.isEmpty()) return;

        Identifier possibleEnchantIdentifier = ETFUtils2.replaceIdentifier(thisIdentifier, ".png", enchantSuffix + ".png");
        Optional<Resource> enchantR1 = resourceManager.getResource(possibleEnchantIdentifier);
        if (enchantR1.isEmpty()) return;

        String enchantPackName = enchantR1.get().sourcePackId();
        if (!enchantPackName.equals(ETFUtils2.returnNameOfHighestPackFromTheseTwo(enchantPackName, vanillaR1.get().sourcePackId()))) return;

        enchantIdentifier = possibleEnchantIdentifier;
        Identifier possibleEnchantBlinkIdentifier = ETFUtils2.replaceIdentifier(thisIdentifier, ".png", "_blink" + enchantSuffix + ".png");
        Optional<Resource> enchantBlinkR1 = resourceManager.getResource(possibleEnchantBlinkIdentifier);
        if (enchantBlinkR1.isEmpty()) return;

        String enchantBlinkPackName = enchantBlinkR1.get().sourcePackId();
        if (!enchantBlinkPackName.equals(ETFUtils2.returnNameOfHighestPackFromTheseTwo(enchantBlinkPackName, vanillaR1.get().sourcePackId()))) return;

        enchantBlinkIdentifier = possibleEnchantBlinkIdentifier;
        Identifier possibleEnchantBlink2Identifier = ETFUtils2.replaceIdentifier(thisIdentifier, ".png", "_blink2" + enchantSuffix + ".png");
        Optional<Resource> enchantBlink2R1 = resourceManager.getResource(possibleEnchantBlink2Identifier);
        if (enchantBlink2R1.isEmpty()) return;

        String enchantBlink2PackName = enchantBlink2R1.get().sourcePackId();
        if (enchantBlink2PackName.equals(ETFUtils2.returnNameOfHighestPackFromTheseTwo(enchantBlink2PackName, vanillaR1.get().sourcePackId()))) {
            enchantBlink2Identifier = possibleEnchantBlink2Identifier;
        }
    }

    private Optional<Resource> getResourceOrModifyForTrims(final ResourceManager resourceManager) {
        Optional<Resource> vanillaR1 = resourceManager.getResource(thisIdentifier);

        if (vanillaR1.isEmpty() &&
                (thisIdentifier.getPath().contains("textures/trims/models/armor/")//prior to 1.21.2
                        || thisIdentifier.getPath().contains("textures/trims/entity/")//1.21.2
                )) {

            //create this armor trim as an identifier because fuck Sprites, all my homies hate Sprites

            //try get an armor trims base texture just to match what texture pack level it is
            PackResources pack;
            vanillaR1 = resourceManager.getResource(ETFUtils2.res(thisIdentifier.getNamespace(), thisIdentifier.getPath().replaceAll("_(.*?)(?=\\.png)", "")));
            pack = vanillaR1.map(Resource::source)
                    .orElseGet(() -> Minecraft.getInstance().getVanillaPackResources()
                    );
            //create resource object sufficient for following code
            vanillaR1 = Optional.of(new Resource(pack, null));
        }
        return vanillaR1;
    }

    @NotNull
    public Identifier getTextureIdentifier(@Nullable ETFEntityRenderState entity) {
        if (canPatch()) {
            //patched required
            currentTextureState = TextureReturnState.NORMAL_PATCHED;
        } else {
            currentTextureState = TextureReturnState.NORMAL;
        }
        return getBlinkingIdentifier(entity);
    }

    @NotNull
    private Identifier getBlinkingIdentifier(@Nullable ETFEntityRenderState state) {
        if (!doesBlink() || !(state != null && state.entity() instanceof LivingEntity)) {
            return identifierOfCurrentState();
        }

        var entity = state.entity();
        if (guiBlink) {
            setBlink(Math.abs((int) System.currentTimeMillis() / 20 % 50000), 0);
        } else if (((LivingEntity) entity).hasPose(Pose.SLEEPING)) {
            //force eyes closed if asleep
            modifyTextureState(TextureReturnState.APPLY_BLINK);
        } else if (((LivingEntity) entity).hasEffect(MobEffects.BLINDNESS)) {
            //force eyes closed if blinded
            modifyTextureState(doesBlink2() ? TextureReturnState.APPLY_BLINK2 : TextureReturnState.APPLY_BLINK);
        } else {
            //do regular blinking
            setBlink(((LivingEntity) entity).tickCount, Math.abs(state.uuid().hashCode()));
        }
        return identifierOfCurrentState();
    }

    private void setBlink(int currentTime, int hash) {
        int uuidHash = hash % (blinkFrequency * 2) + 20 + blinkFrequency;
        int timeModulated = Math.abs((currentTime % uuidHash));

        if (timeModulated <= blinkLength + blinkLength) {
            if (doesBlink2()) {
                if (timeModulated >= (blinkLength / 1.5) && timeModulated <= blinkLength + 1 + (blinkLength / 3)) {
                    modifyTextureState(TextureReturnState.APPLY_BLINK);
                } else {
                    modifyTextureState(TextureReturnState.APPLY_BLINK2);
                }
            } else {
                modifyTextureState(TextureReturnState.APPLY_BLINK);
            }
        }
    }

    public void setGUIBlink() {
        blinkFrequency = 100;
        blinkLength = 40;
        guiBlink = true;
    }

    public boolean isEmissive() {
        return this.emissiveIdentifier != null;
    }

    public boolean isEnchanted() {
        return this.enchantIdentifier != null;
    }

    public boolean canPatch() {
        return ETFState.allowTexturePatching && this.thisIdentifier_Patched != null;
    }

    public boolean doesBlink() {
        return this.blinkIdentifier != null;
    }

    @NotNull
    public ETFSprite getPaintingSprite(@NotNull TextureAtlasSprite originalSprite, @Nullable Identifier originalID) {
        if (atlasSprite == null) {
            atlasSprite = new ETFSprite(originalSprite, this, thisIdentifier.equals(originalID));
        }
        return atlasSprite;
    }

    public boolean doesBlink2() {
        return this.blink2Identifier != null;
    }

    @Override
    public String toString() {
        return "[" + this.thisIdentifier.toString() + ", emissive=" + isEmissive() + ", blinks=" + doesBlink() + "]";
    }

    public void renderEmissive(PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, ModelPart modelPart) {
        renderEmissive(matrixStack, vertexConsumerProvider, modelPart, ETFManager.getEmissiveMode());
    }

    public void renderEmissive(PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, ModelPart modelPart, ETFConfig.EmissiveRenderModes modeToUsePossiblyManuallyChosen) {
        VertexConsumer vertexC = getEmissiveVertexConsumer(vertexConsumerProvider, null, modeToUsePossiblyManuallyChosen);
        if (vertexC != null) {
            ETFState.startSpecialRenderOverlayPhase();
            modelPart.render(matrixStack, vertexC, ETF.EMISSIVE_FEATURE_LIGHT_VALUE, OverlayTexture.NO_OVERLAY);
            ETFState.endSpecialRenderOverlayPhase();
        }
    }

    public void renderEmissive(PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, Model model) {
        renderEmissive(matrixStack, vertexConsumerProvider, model, ETFManager.getEmissiveMode());
    }

    public void renderEmissive(PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, Model model, ETFConfig.EmissiveRenderModes modeToUsePossiblyManuallyChosen) {
        VertexConsumer vertexC = getEmissiveVertexConsumer(vertexConsumerProvider, model, modeToUsePossiblyManuallyChosen);
        if (vertexC != null) {
            ETFState.startSpecialRenderOverlayPhase();
            model.renderToBuffer(matrixStack, vertexC, ETF.EMISSIVE_FEATURE_LIGHT_VALUE, OverlayTexture.NO_OVERLAY
                    );
            ETFState.endSpecialRenderOverlayPhase();
        }
    }

    @Nullable
    public VertexConsumer getEmissiveVertexConsumer(MultiBufferSource vertexConsumerProvider, @Nullable Model model, ETFConfig.EmissiveRenderModes modeToUsePossiblyManuallyChosen) {
        var type = getEmissiveRenderLayer(model, modeToUsePossiblyManuallyChosen);
        if (type == null) return null;
        return vertexConsumerProvider.getBuffer(type);
    }

    @Nullable
    public RenderType getEmissiveRenderLayer(@Nullable Model model) {
        return getEmissiveRenderLayer(model, ETFManager.getEmissiveMode());
    }

    @Nullable
    public RenderType getEmissiveRenderLayer(@Nullable Model model, ETFConfig.EmissiveRenderModes modeToUsePossiblyManuallyChosen) {
        ETFState.pushRenderLayerModifyState(false);
        var type = getEmissiveVertexConsumerWrapped(model, modeToUsePossiblyManuallyChosen);
        ETFState.popRenderLayerModifyState();
        return type;
    }

    @Nullable
    private RenderType getEmissiveVertexConsumerWrapped(@Nullable Model model, ETFConfig.EmissiveRenderModes modeToUsePossiblyManuallyChosen) {
        if (isEmissive()) {
            Identifier emissiveToUse = getEmissiveIdentifierOfCurrentState();
            if (emissiveToUse != null) {
                if (modeToUsePossiblyManuallyChosen == ETFConfig.EmissiveRenderModes.BRIGHT) {
                    return
                            net.minecraft.client.renderer.rendertype.RenderTypes
                                    .beaconBeam(emissiveToUse, true);
                } else {
                    if (model == null) {
                        return
                                net.minecraft.client.renderer.rendertype.RenderTypes
                                        .entityCutoutNoCull /*RenderLayer.getEntityTranslucent*/(emissiveToUse);
                    } else {
                        return model.renderType(emissiveToUse);
                    }
                }
            }
        }
        // return null for any fail
        return null;
    }

    private void modifyTextureState(TextureReturnState givenState) {
        switch (givenState) {
            case APPLY_BLINK ->
                    currentTextureState = currentTextureState == TextureReturnState.NORMAL_PATCHED ? TextureReturnState.BLINK_PATCHED : TextureReturnState.BLINK;
            case APPLY_BLINK2 -> currentTextureState = switch (currentTextureState) {
                case NORMAL_PATCHED, BLINK_PATCHED -> TextureReturnState.BLINK2_PATCHED;
                default -> TextureReturnState.BLINK2;
            };
        }
    }

    @NotNull
    private Identifier identifierOfCurrentState() {
        return switch (currentTextureState) {
            case NORMAL -> thisIdentifier;
            case NORMAL_PATCHED -> thisIdentifier_Patched;
            case BLINK -> blinkIdentifier;
            case BLINK_PATCHED -> blinkIdentifier_Patched;
            case BLINK2 -> blink2Identifier;
            case BLINK2_PATCHED -> blink2Identifier_Patched;
            default ->
                //ETFUtils.logError("identifierOfCurrentState failed, it should not have, returning default");
                    thisIdentifier;
        };
    }

    @Nullable
    public Identifier getEmissiveIdentifierOfCurrentState() {
        return switch (currentTextureState) {
            case NORMAL, NORMAL_PATCHED -> emissiveIdentifier;
            case BLINK, BLINK_PATCHED -> emissiveBlinkIdentifier;
            case BLINK2, BLINK2_PATCHED -> emissiveBlink2Identifier;
            default ->
                //ETFUtils.logError("identifierOfCurrentState failed, it should not have, returning default");
                    emissiveIdentifier;
        };
    }

    @Nullable
    public Identifier getEnchantIdentifierOfCurrentState() {
        return switch (currentTextureState) {
            case NORMAL, NORMAL_PATCHED -> enchantIdentifier;
            case BLINK, BLINK_PATCHED -> enchantBlinkIdentifier;
            case BLINK2, BLINK2_PATCHED -> enchantBlink2Identifier;
            default ->
                //ETFUtils.logError("identifierOfCurrentState failed, it should not have, returning default");
                    enchantIdentifier;
        };
    }

    public void assertPatchedTextures() {
        if (!this.isEmissive() || hasPatched) {
            return;
        }
        hasPatched = true;

        ResourceManager files = Minecraft.getInstance().getResourceManager();
        //should this process cancel itself due to presence of PBR textures
        if ((ETF.isThisModLoaded("iris") || ETF.isThisModLoaded("oculus")) &&
                //do pbr files exist?
                (files.getResource(ETFUtils2.replaceIdentifier(thisIdentifier, ".png", "_s.png")).isPresent() ||
                        files.getResource(ETFUtils2.replaceIdentifier(thisIdentifier, ".png", "_n.png")).isPresent())
        ) {
            //here the setting to cancel is enabled, iris is present, and pbr files exist, so cancel patching
            return;
        }

        //should patching cancel due to presence of animation mods and animated textures
        if (((ETF.isThisModLoaded("animatica")) && (
                doesAnimaticaVersionExist(thisIdentifier)
                        || doesAnimaticaVersionExist(emissiveIdentifier)
        ) || (ETF.isThisModLoaded("moremcmeta") &&
                (files.getResource(ETFUtils2.replaceIdentifier(thisIdentifier, ".png", ".png.mcmeta")).isPresent() ||
                        files.getResource(ETFUtils2.replaceIdentifier(thisIdentifier, ".png", ".png.moremcmeta")).isPresent())
        )
        )) {
            //here the setting to cancel is enabled, animation mod is present, and mcmeta files exist, so cancel patching
            return;
        }

        //here we will 'patch' the base texture to prevent z-fighting when it occurs

        //null depending on existence
        NativeImage newBaseTexture = ETFUtils2.getNativeImageElseNull(thisIdentifier);
        NativeImage newBlinkTexture = ETFUtils2.getNativeImageElseNull(blinkIdentifier);
        NativeImage newBlink2Texture = ETFUtils2.getNativeImageElseNull(blink2Identifier);

        boolean didPatch = false;

        //patch out emissive textures for shader z fighting fix
        if (this.emissiveIdentifier != null) {// && ETF.config().getConfig().enableEmissiveTextures) {
            //create patched texture
            NativeImage emissiveImage = ETFUtils2.getNativeImageElseNull(emissiveIdentifier);
            try {
                patchTextureToRemoveZFightingWithOtherTexture(newBaseTexture, emissiveImage);
                didPatch = true;
                //no errors here means it all , and we have a patched texture in originalCopyToPatch
                //thisIdentifier_Patched = new Identifier(PATCH_NAMESPACE_PREFIX + thisIdentifier.getNamespace(), thisIdentifier.getPath());
                //ETFUtils2.registerNativeImageToIdentifier(originalCopyToPatch, thisIdentifier_Patched);

                if (doesBlink() && emissiveBlinkIdentifier != null) {
                    NativeImage emissiveBlinkImage = ETFUtils2.getNativeImageElseNull(emissiveBlinkIdentifier);
                    patchTextureToRemoveZFightingWithOtherTexture(newBlinkTexture, emissiveBlinkImage);
                    //no errors here means it all worked, and we have a patched texture in
                    //blinkIdentifier_Patched = new Identifier(PATCH_NAMESPACE_PREFIX + blinkIdentifier.getNamespace(), blinkIdentifier.getPath());
                    //ETFUtils2.registerNativeImageToIdentifier(blinkCopyToPatch, blinkIdentifier_Patched);

                    if (doesBlink2() && emissiveBlink2Identifier != null) {
                        NativeImage emissiveBlink2Image = ETFUtils2.getNativeImageElseNull(emissiveBlink2Identifier);
                        patchTextureToRemoveZFightingWithOtherTexture(newBlink2Texture, emissiveBlink2Image);
                        //no errors here means it all worked, and we have a patched texture in
                        //blink2Identifier_Patched = new Identifier(PATCH_NAMESPACE_PREFIX + blink2Identifier.getNamespace(), blink2Identifier.getPath());
                        //ETFUtils2.registerNativeImageToIdentifier(blink2CopyToPatch, blinkIdentifier_Patched);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            //save successful patches after any iris or other future patching reasons
            if (didPatch) {
                thisIdentifier_Patched = ETFUtils2.res(PATCH_NAMESPACE_PREFIX + thisIdentifier.getNamespace(), thisIdentifier.getPath());
                ETFUtils2.registerNativeImageToIdentifier(newBaseTexture, thisIdentifier_Patched);
                ETFManager.getInstance().ETF_TEXTURE_CACHE.put(thisIdentifier_Patched, this);
                if (doesBlink()) {
                    blinkIdentifier_Patched = ETFUtils2.res(PATCH_NAMESPACE_PREFIX + blinkIdentifier.getNamespace(), blinkIdentifier.getPath());
                    ETFUtils2.registerNativeImageToIdentifier(newBlinkTexture, blinkIdentifier_Patched);
                    ETFManager.getInstance().ETF_TEXTURE_CACHE.put(blinkIdentifier_Patched, this);
                    if (doesBlink2()) {
                        blink2Identifier_Patched = ETFUtils2.res(PATCH_NAMESPACE_PREFIX + blink2Identifier.getNamespace(), blink2Identifier.getPath());
                        ETFUtils2.registerNativeImageToIdentifier(newBlink2Texture, blink2Identifier_Patched);
                        ETFManager.getInstance().ETF_TEXTURE_CACHE.put(blink2Identifier_Patched, this);
                    }
                }
            }
        }
    }

    public enum TextureReturnState {
        NORMAL,
        NORMAL_PATCHED,
        BLINK,
        BLINK_PATCHED,
        BLINK2,
        BLINK2_PATCHED,
        APPLY_PATCH,
        APPLY_BLINK,
        APPLY_BLINK2;


        @Override
        public String toString() {
            return switch (this) {
                case NORMAL -> "normal";
                case BLINK -> "blink";
                case BLINK2 -> "blink2";
                case NORMAL_PATCHED -> "normal_patched";
                case BLINK_PATCHED -> "blink_patched";
                case BLINK2_PATCHED -> "blink2_patched";
                case APPLY_BLINK -> "apply_blink";
                case APPLY_BLINK2 -> "apply_blink2";
                case APPLY_PATCH -> "apply_patch";
            };
        }
    }

}
