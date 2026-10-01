package com.thelads.core.v26_2.embedded.etf.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v26_2.embedded.etf.ETF;
import com.thelads.core.v26_2.embedded.etf.config.ETFConfigWarning;
import com.thelads.core.v26_2.embedded.etf.config.ETFConfigWarnings;
import com.thelads.core.v26_2.embedded.etf.features.ETFManager;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFState;
import com.thelads.core.v26_2.embedded.etf.features.texture_handlers.ETFTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

import net.minecraft.ChatFormatting;
import net.minecraft.IdentifierException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.MutableComponent;

import net.minecraft.util.ARGB;

import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;


public abstract class ETFUtils2 {

    public static @NotNull Identifier res(String fullPath){
        return Identifier.parse(fullPath);
    }

    public static @NotNull Identifier res(String namespace, String path){
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    public static void setPixel(NativeImage image, int x, int y, int color) {
        image.setPixel(x, y, ARGB.toABGR(color));
    }

    public static int getPixel(NativeImage image, int x, int y) {
        return ARGB.fromABGR( image.getPixel(x, y));
    }

    public static final int FULL_BRIGHT = 15728880;

    public static int packLight(int sky, int block) {
        return sky << 4 | block << 20;
    }

    public static void submitModelPart(final PoseStack matrixStack, final SubmitNodeCollector submit, final int light, final ModelPart modelPart, final ETFTexture etfTexture, @Nullable ETFEntityRenderState state) {
        var texture = etfTexture.getTextureIdentifier(state);
        var emissive = etfTexture.getEmissiveIdentifierOfCurrentState();
        var enchanted = etfTexture.getEnchantIdentifierOfCurrentState();
        submitModelPart(matrixStack, submit, light, modelPart, texture, emissive, enchanted);
    }

    public static void submitModelPart(final PoseStack matrixStack, final SubmitNodeCollector submit, final int light, final ModelPart modelPart, @NotNull final Identifier texture, @Nullable final Identifier emissive, @Nullable final Identifier enchanted) {
        submit.submitModelPart(modelPart, matrixStack,
                net.minecraft.client.renderer.rendertype.RenderTypes
                        .entityTranslucent(texture), light, OverlayTexture.NO_OVERLAY, null);
        if (emissive != null) {
            submitEmissiveModelPart(matrixStack, submit, modelPart, emissive);
        }
    }

    public static void submitEmissiveModelPart(final PoseStack matrixStack, final SubmitNodeCollector submit, final ModelPart modelPart, final @NotNull Identifier emissive) {
        submit.submitModelPart(modelPart, matrixStack,
                net.minecraft.client.renderer.rendertype.RenderTypes
                        .entityTranslucent(emissive), ETF.EMISSIVE_FEATURE_LIGHT_VALUE, OverlayTexture.NO_OVERLAY, null);
    }

    public static void submitEnchantedModelPart(final PoseStack matrixStack, final SubmitNodeCollector submit, final int light, final ModelPart modelPart, final @NotNull Identifier enchanted) {
        submit.submitModelPart(modelPart, matrixStack,
                //TODO enchanted models REALLY needs refactoring
                net.minecraft.client.renderer.rendertype.RenderTypes.entityGlint(), light, OverlayTexture.NO_OVERLAY, null);
    }

    public static int optifineHashing(int x) {
        // OptiFine integer hashing algorithm
        x ^= 0x3D ^ x >> 16;
        x += x << 3;
        x ^= x >> 4;
        x *= 668265261;
        x ^= x >> 15;
        return x;
    }

    @Deprecated // just to ide highlight usages
    public static void printDebugImage(NativeImage image) {
        if ((ETF.isFabric() == ETF.FABRIC_API) && ETF.getConfigDirectory() != null) {
            Path outputDirectory = Path.of(ETF.getConfigDirectory().toFile().getParent(), "\\ETF_debug_printout.png");
            try {
                image.writeToFile(outputDirectory);
                ETFUtils2.logMessage("printed debug image to: " + outputDirectory, false);
            } catch (Exception e) {
                ETFUtils2.logError(e.toString(), false);
            }
        }
    }

    public static Identifier getETFVariantNotNullForInjector(Identifier identifier) {
        // do not modify texture
        if (identifier == null
                || !ETFState.isStateActive()
                || !ETFState.isAllowedToRenderLayerTextureModify())
            return identifier;

        // get etf modified texture
        ETFTexture etfTexture = ETFManager.getInstance().getETFTextureVariant(identifier, ETFState.state());
        if (ETFState.allowTexturePatching) {
            etfTexture.assertPatchedTextures();
        }
        Identifier modified = etfTexture.getTextureIdentifier(ETFState.state());

        // check not null just to be safe, it shouldn't be however
        //noinspection ConstantValue
        return modified == null ? identifier : modified;
    }

    public static boolean renderEmissive(ETFTexture texture, URenderTypeToVertexConsumer provider, RenderMethodForOverlay renderer) {
        if (!ETF.config().getConfig().canDoEmissiveTextures()) return false;
        Identifier emissive = texture.getEmissiveIdentifierOfCurrentState();
        if (emissive != null) {
            ETFState.pushRenderLayerModifyState(false);

            VertexConsumer emissiveConsumer = provider.getBuffer(
                    ETFState.canRenderInBrightMode() ?

                            net.minecraft.client.renderer.rendertype.RenderTypes
                                    .beaconBeam(emissive, true) :

                            net.minecraft.client.renderer.rendertype.RenderTypes
                                    .entityTranslucent(emissive));

            ETFState.popRenderLayerModifyState();

            ETFState.startSpecialRenderOverlayPhase();
            renderer.render(emissiveConsumer, ETF.EMISSIVE_FEATURE_LIGHT_VALUE);
            ETFState.endSpecialRenderOverlayPhase();
            return true;
        }
        return false;
    }

    //todo actual impl
    public static VertexConsumer getFoilBuffer(URenderTypeToVertexConsumer multiBufferSource, RenderType renderType) {
        return multiBufferSource.getBuffer(RenderTypes.entityGlint());
    }

    public static boolean renderEnchanted(ETFTexture texture, URenderTypeToVertexConsumer provider, int light, RenderMethodForOverlay renderer) {
        // attempt enchanted render
        Identifier enchanted = texture.getEnchantIdentifierOfCurrentState();
        if (enchanted != null) {
            ETFState.pushRenderLayerModifyState(false);
            VertexConsumer enchantedVertex =
                    getFoilBuffer(provider, RenderTypes.armorCutoutNoCull(enchanted));
            ETFState.popRenderLayerModifyState();

            ETFState.startSpecialRenderOverlayPhase();
            renderer.render(enchantedVertex, light);
            ETFState.endSpecialRenderOverlayPhase();
            return true;
        }
        return false;
    }

    @Nullable
    public static Identifier addVariantNumberSuffix(@NotNull Identifier identifier, int variant) {
        var changed = ETFUtils2.res(addVariantNumberSuffix(identifier.toString(), variant));
        return identifier.equals(changed) ? null : changed;
    }

    @NotNull
    public static String addVariantNumberSuffix(String identifierString, int variant) {
        if (variant < 2) return identifierString;

        String file = identifierString.endsWith(".png") ? "png" : identifierString.substring(identifierString.lastIndexOf('.') + 1);

        if (identifierString.matches("\\D+\\d+\\." + file)) {
            return identifierString.replace("." + file, "." + variant + "." + file);
        }
        return identifierString.replace("." + file, variant + "." + file);
    }

    @Nullable
    public static Identifier replaceIdentifier(Identifier id, String regex, String replace) {
        if (id == null) return null;
        try {
            return ETFUtils2.res(id.getNamespace(), id.getPath().replaceFirst(regex, replace));
        } catch (IdentifierException idFail) {
            ETFUtils2.logError(ETF.getTextFromTranslation("config.entity_texture_features.illegal_path_recommendation").getString() + "\n" + idFail);
        } catch (Exception ignored) {}
        return null;
    }

    @Nullable
    public static String returnNameOfHighestPackFromTheseMultiple(String[] packNameList) {
        ArrayList<String> packNames = new ArrayList<>(Arrays.asList(packNameList));
        // loop through and remove the one from the lowest pack of the first 2 entries
        // this iterates over the whole array
        final ArrayList<String> knownResourcepackOrder = ETFManager.getInstance().KNOWN_RESOURCEPACK_ORDER;
        while (packNames.size() > 1) {
            packNames.remove(knownResourcepackOrder.indexOf(packNames.get(0)) >= knownResourcepackOrder.indexOf(packNames.get(1)) ? 1 : 0);
        }
        // here the array is down to 1 entry which should be the one in the highest pack
        return packNames.get(0);
    }

    @Nullable
    public static String returnNameOfHighestPackFromTheseTwo(@Nullable String pack1, @Nullable String pack2) {
        if (pack1 == null) return null;
        if (pack1.equals(pack2) || pack2 == null) return pack1;

        return ETFManager.getInstance().KNOWN_RESOURCEPACK_ORDER.indexOf(pack1) >= ETFManager.getInstance().KNOWN_RESOURCEPACK_ORDER.indexOf(pack2) ? pack1 : pack2;
    }

    @Nullable
    public static Properties readAndReturnPropertiesElseNull(Identifier path) {
        Properties props = new Properties();
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResource(path).get().open()) {
            props.load(in);
            return props;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static List<Properties> readAndReturnAllLayeredPropertiesElseNull(Identifier path) {
        List<Properties> props = new ArrayList<>();
        try {
            var resources = Minecraft.getInstance().getResourceManager().getResourceStack(path);
            for (Resource resource : resources) {
                if (resource == null) continue;
                try (InputStream in = resource.open()) {
                    Properties prop = new Properties();
                    prop.load(in);
                    if (!prop.isEmpty()) {
                        props.add(prop);
                    }
                } catch (Exception ignored) {}
            }
            return props.isEmpty() ? null : props;
        } catch (Exception e) {
            return null;
        }
    }

    public static NativeImage getNativeImageElseNull(@Nullable Identifier identifier) {

        try {
            // try catch is intended
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(identifier);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open()) {
                    return NativeImage.read(in);
                } catch (Exception e) {
                    return null;
                }
            } else {
                AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(identifier);
                if (texture instanceof DynamicTexture nativeImageBackedTexture) {
                    var image2 = nativeImageBackedTexture.getPixels();
                    if (image2 == null) return null;
                    NativeImage image3 = new NativeImage(image2.getWidth(), image2.getHeight(), false);
                    image3.copyFrom(image2);
                    return image3;
                }
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    // improvements to logging by @Maximum#8760
    public static void logMessage(String obj) {
        logMessage(obj, false);
    }

    public static void logMessage(String obj, boolean inChat) {
        if (inChat) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(MutableComponent.create(new PlainTextContents.LiteralContents(
                    "§a[INFO]§r [ETF]: " + obj)));
            } else {
                ETF.LOGGER.info("[ETF]: {}", obj);
            }
        } else {
            ETF.LOGGER.info("[ETF]: {}", obj);
        }
    }

    //improvements to logging by @Maximum#8760
    public static void logWarn(String obj) {
        logWarn(obj, false);
    }

    public static void logWarn(String obj, boolean inChat) {
        if (inChat) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(MutableComponent.create(new PlainTextContents.LiteralContents(
                    "§e[WARN]§r [Entity Texture Features]: " + obj)).withStyle(ChatFormatting.YELLOW));
            } else {
                ETF.LOGGER.warn("[ETF]: {}", obj);
            }
        } else {
            ETF.LOGGER.warn("[ETF]: {}", obj);
        }
    }

    public static void logError(String obj) {
        logError(obj, false);
    }

    public static void logError(String obj, boolean inChat) {
        if (inChat) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(MutableComponent.create(new PlainTextContents.LiteralContents(
                    "§4[ERROR]§r [Entity Texture Features]: " + obj)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            } else {
                ETF.LOGGER.error("[ETF]: {}", obj);
            }
        } else {
            ETF.LOGGER.error("[ETF]: {}", obj);
        }
    }

    public static NativeImage emptyNativeImage() {
        return emptyNativeImage(64, 64);
    }

    public static NativeImage emptyNativeImage(int Width, int Height) {
        NativeImage empty = new NativeImage(Width, Height, false);
        empty.fillRect(0, 0, Width, Height, 0);
        return empty;
    }

    public static boolean registerNativeImageToIdentifier(NativeImage image, Identifier identifier) {
        if (image == null || identifier == null) {
            logError("registering native image failed: " + image + ", " + identifier);
            return false;
        }
        try {
            Minecraft.getInstance().execute(() -> {
                try {
                    NativeImage closableImage = new NativeImage(image.getWidth(), image.getHeight(), true);
                    closableImage.copyFrom(image);

                    Minecraft.getInstance().getTextureManager().release(identifier);

                    DynamicTexture closableBackedTexture = new DynamicTexture(
                            null,
                            closableImage);
                    Minecraft.getInstance().getTextureManager().register(identifier, closableBackedTexture);
                } catch (Exception e) {
                    logError("registering native image failed (inner): " + e);
                }
            });

            return true;
        } catch (Exception e) {
            logError("registering native image failed: " + e);
            return false;
        }

    }

    public static void checkModCompatibility() {
        for (ETFConfigWarning warning :
                ETFConfigWarnings.getRegisteredWarnings()) {
            warning.testWarningAndApplyFixIfEnabled();
        }
    }


    public interface RenderMethodForOverlay {
        void render(VertexConsumer consumer, int light);
    }

}
