package com.thelads.core.client.capes.render;

import com.mojang.authlib.GameProfile;
import com.thelads.core.client.capes.Capes;
import com.thelads.core.client.capes.PlayerHandler;
import com.thelads.core.mixin.capes.AccessorEntityRenderDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

public final class PlaceholderEntity {
    public static final PlaceholderEntity INSTANCE = new PlaceholderEntity();

    private final GameProfile gameProfile;
    private PlayerSkin skin;
    private boolean slim;
    private boolean showBody = true;
    private boolean showElytra;
    private boolean capeLoaded;
    private float limbDistance;
    private float lastLimbDistance;
    private float limbAngle;
    private float yaw;
    private float prevYaw;
    private double x;
    private double prevX;
    private PlaceholderEntityRenderer renderer;

    private PlaceholderEntity() {
        Minecraft mc = Minecraft.getInstance();
        this.gameProfile = mc.getGameProfile();
        this.skin = DefaultPlayerSkin.get(this.gameProfile);

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        AccessorEntityRenderDispatcher accessor = (AccessorEntityRenderDispatcher) dispatcher;
        BlockModelResolver blockModelResolver = accessor.getBlockModelResolver();
        ItemModelResolver itemModelResolver = mc.getItemModelResolver();
        MapRenderer mapRenderer = mc.getMapRenderer();
        ResourceManager resourceManager = mc.getResourceManager();
        EntityModelSet entityModelSet = mc.getEntityModels();

        EntityRendererProvider.Context ctx = new EntityRendererProvider.Context(
            dispatcher,
            blockModelResolver,
            itemModelResolver,
            mapRenderer,
            resourceManager,
            entityModelSet,
            accessor.getEquipmentAssets(),
            mc.getAtlasManager(),
            mc.font,
            mc.playerSkinRenderCache()
        );

        this.renderer = new PlaceholderEntityRenderer(ctx, this.slim);
        mc.getSkinManager().get(this.gameProfile).thenAccept(opt -> {
            if (opt.isPresent()) {
                this.skin = opt.get();
                this.slim = this.skin.model() == PlayerModelType.SLIM;
                this.renderer = new PlaceholderEntityRenderer(ctx, this.slim);
            }
        });
    }

    public GameProfile getGameProfile() {
        return gameProfile;
    }

    public boolean getSlim() {
        return slim;
    }

    public void setSlim(boolean slim) {
        this.slim = slim;
    }

    public boolean getShowBody() {
        return showBody;
    }

    public void setShowBody(boolean showBody) {
        this.showBody = showBody;
    }

    public boolean getShowElytra() {
        return showElytra;
    }

    public void setShowElytra(boolean showElytra) {
        this.showElytra = showElytra;
    }

    public boolean getCapeLoaded() {
        return capeLoaded;
    }

    public void setCapeLoaded(boolean capeLoaded) {
        this.capeLoaded = capeLoaded;
    }

    public float getLimbDistance() {
        return limbDistance;
    }

    public void setLimbDistance(float limbDistance) {
        this.limbDistance = limbDistance;
    }

    public float getLastLimbDistance() {
        return lastLimbDistance;
    }

    public void setLastLimbDistance(float lastLimbDistance) {
        this.lastLimbDistance = lastLimbDistance;
    }

    public float getLimbAngle() {
        return limbAngle;
    }

    public void setLimbAngle(float limbAngle) {
        this.limbAngle = limbAngle;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPrevYaw() {
        return prevYaw;
    }

    public void setPrevYaw(float prevYaw) {
        this.prevYaw = prevYaw;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getPrevX() {
        return prevX;
    }

    public void setPrevX(double prevX) {
        this.prevX = prevX;
    }

    public PlaceholderEntityRenderer getRenderer() {
        return renderer;
    }

    public void setRenderer(PlaceholderEntityRenderer renderer) {
        this.renderer = renderer;
    }

    public void updateLimbs() {
        this.lastLimbDistance = this.limbDistance;
        double d = this.x - this.prevX;
        float g = (float) Math.sqrt(d * d) * 4.0f;
        if (g > 1.0f) {
            g = 1.0f;
        }
        this.limbDistance += (g - this.limbDistance) * 0.4f;
        this.limbAngle += this.limbDistance;
    }

    public ClientAsset.Texture getCapeTexture() {
        if (!this.capeLoaded) {
            this.capeLoaded = true;
            PlayerHandler.onLoadTexture(this.gameProfile);
        }
        PlayerHandler handler = PlayerHandler.fromProfile(this.gameProfile);
        return handler.getHasCape() ? handler.getCape() : this.skin.cape();
    }

    public ClientAsset.Texture getElytraTexture() {
        PlayerHandler handler = PlayerHandler.fromProfile(this.gameProfile);
        ClientAsset.Texture capeTexture = this.getCapeTexture();
        if (handler.getHasElytraTexture() && Capes.INSTANCE.getCONFIG().getEnableElytraTexture() && capeTexture != null) {
            return capeTexture;
        }
        return new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("entity/equipment/wings/elytra"));
    }

    public PlayerSkin getSkinTextures() {
        return new PlayerSkin(
            this.skin.body(),
            this.getCapeTexture(),
            this.getElytraTexture(),
            this.skin.model(),
            this.skin.secure()
        );
    }
}
