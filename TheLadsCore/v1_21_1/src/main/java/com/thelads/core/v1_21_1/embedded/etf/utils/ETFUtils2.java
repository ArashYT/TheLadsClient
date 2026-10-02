package com.thelads.core.v1_21_1.embedded.etf.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;

import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_21_1.embedded.etf.ETF;
import com.thelads.core.v1_21_1.embedded.etf.config.ETFConfigWarning;
import com.thelads.core.v1_21_1.embedded.etf.config.ETFConfigWarnings;
import com.thelads.core.v1_21_1.embedded.etf.features.ETFManager;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_1.embedded.etf.features.texture_handlers.ETFTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

import net.minecraft.ChatFormatting;
import net.minecraft.ResourceLocationException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.MutableComponent;


import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;


public abstract class ETFUtils2 {

    public static @NotNull ResourceLocation res(String fullPath){
        return ResourceLocation.parse(fullPath);
    }

    public static @NotNull ResourceLocation res(String namespace, String path){
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static void setPixel(NativeImage image, int x, int y, int color) {
        image.setPixelRGBA(x, y, color);
    }

    public static int getPixel(NativeImage image, int x, int y) {
        return image.getPixelRGBA(x, y);
    }

    public static final int FULL_BRIGHT = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;

    public static int packLight(int sky, int block) {
        return net.minecraft.client.renderer.LightTexture.pack(sky, block);
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

    public static ResourceLocation getETFVariantNotNullForInjector(ResourceLocation identifier) {
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
        ResourceLocation modified = etfTexture.getTextureIdentifier(ETFState.state());

        // check not null just to be safe, it shouldn't be however
        //noinspection ConstantValue
        return modified == null ? identifier : modified;
    }

    public static boolean renderEmissive(ETFTexture texture, URenderTypeToVertexConsumer provider, RenderMethodForOverlay renderer) {
        if (!ETF.config().getConfig().canDoEmissiveTextures()) return false;
        ResourceLocation emissive = texture.getEmissiveIdentifierOfCurrentState();
        if (emissive != null) {
            ETFState.pushRenderLayerModifyState(false);

            VertexConsumer emissiveConsumer = provider.getBuffer(
                    ETFState.canRenderInBrightMode() ?

                            RenderType
                                    .beaconBeam(emissive, true) :
                                ETFState.shouldEmissiveUseCullingLayer() ?
                                    RenderType.entityTranslucentCull(emissive) :

                            RenderType
                                    .entityTranslucent(emissive));

            ETFState.popRenderLayerModifyState();

            ETFState.startSpecialRenderOverlayPhase();
            renderer.render(emissiveConsumer, ETF.EMISSIVE_FEATURE_LIGHT_VALUE);
            ETFState.endSpecialRenderOverlayPhase();
            return true;
        }
        return false;
    }


    public static boolean renderEnchanted(ETFTexture texture, URenderTypeToVertexConsumer provider, int light, RenderMethodForOverlay renderer) {
        // attempt enchanted render
        ResourceLocation enchanted = texture.getEnchantIdentifierOfCurrentState();
        if (enchanted != null) {
            ETFState.pushRenderLayerModifyState(false);
            VertexConsumer enchantedVertex =
                    ItemRenderer.getArmorFoilBuffer(provider.delegate,
                    RenderType.armorCutoutNoCull(enchanted),
                        true);
            ETFState.popRenderLayerModifyState();

            ETFState.startSpecialRenderOverlayPhase();
            renderer.render(enchantedVertex, light);
            ETFState.endSpecialRenderOverlayPhase();
            return true;
        }
        return false;
    }

    @Nullable
    public static ResourceLocation addVariantNumberSuffix(@NotNull ResourceLocation identifier, int variant) {
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
    public static ResourceLocation replaceIdentifier(ResourceLocation id, String regex, String replace) {
        if (id == null) return null;
        try {
            return ETFUtils2.res(id.getNamespace(), id.getPath().replaceFirst(regex, replace));
        } catch (ResourceLocationException idFail) {
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
    public static Properties readAndReturnPropertiesElseNull(ResourceLocation path) {
        Properties props = new Properties();
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResource(path).get().open()) {
            props.load(in);
            return props;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static List<Properties> readAndReturnAllLayeredPropertiesElseNull(ResourceLocation path) {
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

    public static NativeImage getNativeImageElseNull(@Nullable ResourceLocation identifier) {

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
                player.displayClientMessage(MutableComponent.create(
                        new PlainTextContents.LiteralContents
                            ("§a[INFO]§r [ETF]: " + obj))/*.formatted(Formatting.GRAY, Formatting.ITALIC)*/ , false);
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
                player.displayClientMessage(MutableComponent.create(
                        new PlainTextContents.LiteralContents
                                ("§e[WARN]§r [Entity Texture Features]: " + obj)).withStyle(ChatFormatting.YELLOW), false);
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
                player.displayClientMessage(MutableComponent.create(
                        new PlainTextContents.LiteralContents
                                ("§4[ERROR]§r [Entity Texture Features]: " + obj)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
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

    public static boolean registerNativeImageToIdentifier(NativeImage image, ResourceLocation identifier) {
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
