package com.thelads.core.v1_21_11.embedded.etf.config.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Guardian;
import org.joml.Quaternionf;
import com.thelads.core.v1_21_11.embedded.etf.ETF;
import com.thelads.core.v1_21_11.embedded.etf.config.ETFConfigWarning;
import com.thelads.core.v1_21_11.embedded.etf.config.ETFConfigWarnings;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFUtils2;
import com.thelads.core.v1_21_11.embedded.etf.utils.UScreen;
import com.thelads.core.v1_21_11.embedded.etf.tconfig.gui.TConfigScreenMain;
import com.thelads.core.v1_21_11.embedded.etf.tconfig.gui.entries.TConfigEntryCategory;

import java.util.*;

public class ETFConfigScreenMain extends TConfigScreenMain {

    final Set<ETFConfigWarning> warningsFound = new HashSet<>();

    private final Random rand = new Random();
    private final LogoCreeperRenderer LOGO_CREEPER = new LogoCreeperRenderer();
    private final Identifier BLUE = ETFUtils2.res("entity_features", "textures/gui/entity/e.png");
    private final Identifier RED = ETFUtils2.res("entity_features", "textures/gui/entity/t.png");
    private final Identifier YELLOW = ETFUtils2.res("entity_features", "textures/gui/entity/f.png");
    private final Identifier LOGO_HACK = ETFUtils2.res("entity_texture_features", "textures/gui/logo.png");

    boolean shownWarning = false;
    int warningCount = 0;
    private long timer = 0;
    private LivingEntity livingEntity = null;

    public ETFConfigScreenMain(Screen parent) {
        super("config.entity_features", parent, ETF.configHandlers, List.of(
                new TConfigEntryCategory("config.entity_features.textures_main"),
                new TConfigEntryCategory("config.entity_features.models_main").setEmptyTooltip("config.entity_features.empty_emf"),
                new TConfigEntryCategory("config.entity_features.sounds_main").setEmptyTooltip("config.entity_features.empty_esf"),
                new TConfigEntryCategory("config.entity_features.general_settings.title"),
                new TConfigEntryCategory("config.entity_features.optifine_settings"),
                new TConfigEntryCategory("config.entity_features.per_entity_settings")
        ));


        for (ETFConfigWarning warning :
                ETFConfigWarnings.getRegisteredWarnings()) {
            if (warning.isConditionMet()) {
                shownWarning = true;
                warningCount++;
                warningsFound.add(warning);
            }
        }
    }

    public void drawEntity12106(GuiGraphics context, int x, int y, int size, int width, int height, LivingEntity entity, int mouseX, int mouseY) {
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                context,
                x, y, width, height,
                size, 0.0625F, //30, 0.0625F,
                mouseX, mouseY,
                entity

        );//todo
    }

    @Override
    protected void init() {
        super.init();
        if (shownWarning) {
            this.addRenderableWidget(Button.builder(Component.translatable("config.entity_features.warnings_main"),
                            (button) -> UScreen.setScreen(new ETFConfigScreenWarnings(this, warningsFound)))
                    .bounds((int) (this.width * 0.1), (int) (this.height * 0.1) - 15, (int) (this.width * 0.2), 20
                    ).build());
        }
    }

    @Override
    public void
        render
    (final GuiGraphics context, final int mouseX, final int mouseY, final float delta) {
        super.
                render
                        (context, mouseX, mouseY, delta);
        //get a random entity and display for 5 seconds
        //noinspection ConstantValue
        if (timer + 5000 < System.currentTimeMillis() && Minecraft.getInstance().player != null && Minecraft.getInstance().player.level() != null) {
            List<Entity> entityList = Minecraft.getInstance().player.level().getEntities(null, Minecraft.getInstance().player.getBoundingBox().inflate(128));
            Entity entity = null;
            for (int i = 0; i < Math.min(entityList.size(), 24); i++) {
                entity = entityList.get(rand.nextInt(entityList.size()));
                if (entity instanceof LivingEntity) break;
            }
            if (entity instanceof LivingEntity) {
                livingEntity = (LivingEntity) entity;
                timer = System.currentTimeMillis();
            }
        }

        //draw the entity else ETF logo creepers
        if (livingEntity != null && !livingEntity.isRemoved()) {
            renderEntitySample(context, mouseX, mouseY);
        } else {
            int sin = (int) (float) (Math.sin(System.currentTimeMillis() / 500d) * height * 0.015f);
            float yAdjust = (((float) mouseY / height) - 0.5f) * height * 0.05f - sin;
            float xAdjust = (((float) mouseX / width) - 0.5f) * width * 0.05f - sin;
            int y = (int) (this.height * 0.25f + yAdjust);
            int x = (int) (this.width * 0.15f + xAdjust);
            int square = this.height / 2 + sin * 2;
            context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO_HACK, x, y, 0, 0, square, square, square, square);
        }
    }

    private void renderEntitySample(final GuiGraphics context, final int mouseX, final int mouseY) {
        int y = (int) (this.height * 0.75);
        if (livingEntity.getBbHeight() < 0.7) y -= (int) (this.height * 0.15);
        int x = (int) (this.width * 0.33);
        //float f = (float)Math.atan((double)(-mouseX / 40.0F));
        float g = (float) Math.atan(((-mouseY + this.height / 2f) / 40.0F));
        Quaternionf quaternionf = (new Quaternionf()).rotateZ(3.1415927F).rotateY((float) (System.currentTimeMillis() / 1000d % (2 * Math.PI)));
        Quaternionf quaternionf2 = (new Quaternionf()).rotateX(-(g * 20.0F * 0.017453292F));
        quaternionf.mul(quaternionf2);
        double scale;
        double autoScale = (this.height * 0.4) / ((Math.max(1, Math.max(livingEntity.getBbHeight(), livingEntity.getBbWidth()))));
        if (livingEntity instanceof Squid) {
            y -= (int) (this.height * 0.15);
            scale = autoScale * 0.5;
        } else if (livingEntity instanceof Guardian || livingEntity instanceof Sniffer) {
            y -= (int) (this.height * 0.1);
            scale = autoScale * 0.7;
        } else if (livingEntity instanceof EnderDragon) {
            y -= (int) (this.height * 0.15);
            scale = autoScale * 1.5;
        } else {
            scale = autoScale;
        }

        double scaleModify = Math.sin((System.currentTimeMillis() - timer) / 5000d * Math.PI) * 6;
        double scaleModify2 = Math.max(Math.min(Math.abs(scaleModify), 1d), 0);//clamp
        int modelHeight = (int) Math.min(scale * scaleModify2, (this.height * 0.4));

        drawEntity12106(context, 0, (int) (this.height * 0.15),
                modelHeight, (int) (this.width * 0.6), (int) (this.height * 0.7),
                 livingEntity, mouseX, mouseY);
    }

    public static class LogoCreeperRenderer {


        private final ModelPart root;

        public LogoCreeperRenderer() {
            root = CreeperModel.createBodyLayer(CubeDeformation.NONE).bakeRoot();
            root.getChild("right_hind_leg").xRot = (float) -Math.toRadians(25);
            root.getChild("left_hind_leg").xRot = (float) Math.toRadians(25);
            root.getChild("left_hind_leg").z -= 2;
            root.getChild("right_front_leg").xRot = (float) Math.toRadians(25);
            root.getChild("left_front_leg").xRot = (float) -Math.toRadians(25);
            root.getChild("left_front_leg").z += 2;
        }

    }
}
