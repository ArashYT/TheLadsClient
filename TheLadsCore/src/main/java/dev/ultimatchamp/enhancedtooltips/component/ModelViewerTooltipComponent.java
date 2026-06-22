/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.client.renderer.entity.state.EntityRenderState
 *  net.minecraft.core.Holder
 *  net.minecraft.core.Holder$Reference
 *  net.minecraft.core.HolderGetter$Provider
 *  net.minecraft.core.Registry
 *  net.minecraft.core.RegistryAccess
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntitySpawnReason
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.EquipmentSlot$Type
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.Mob
 *  net.minecraft.world.entity.animal.Bucketable
 *  net.minecraft.world.entity.animal.equine.Horse
 *  net.minecraft.world.entity.animal.fish.AbstractSchoolingFish
 *  net.minecraft.world.entity.animal.fish.Pufferfish
 *  net.minecraft.world.entity.animal.golem.SnowGolem
 *  net.minecraft.world.entity.animal.nautilus.Nautilus
 *  net.minecraft.world.entity.animal.wolf.Wolf
 *  net.minecraft.world.entity.decoration.ArmorStand
 *  net.minecraft.world.entity.npc.villager.Villager
 *  net.minecraft.world.entity.npc.villager.VillagerDataHolder
 *  net.minecraft.world.entity.npc.villager.VillagerProfession
 *  net.minecraft.world.entity.npc.villager.VillagerType
 *  net.minecraft.world.entity.player.PlayerModelPart
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.item.MobBucketItem
 *  net.minecraft.world.item.SmithingTemplateItem
 *  net.minecraft.world.item.SpawnEggItem
 *  net.minecraft.world.item.component.CustomData
 *  net.minecraft.world.item.equipment.Equippable
 *  net.minecraft.world.item.equipment.trim.ArmorTrim
 *  net.minecraft.world.level.GameType
 *  net.minecraft.world.level.Level
 *  org.apache.commons.lang3.StringUtils
 *  org.jetbrains.annotations.NotNull
 *  org.joml.Quaternionf
 *  org.joml.Vector3f
 */
package dev.ultimatchamp.enhancedtooltips.component;

import dev.ultimatchamp.enhancedtooltips.component.TooltipBorderColorComponent;
import dev.ultimatchamp.enhancedtooltips.config.EnhancedTooltipsConfig;
import dev.ultimatchamp.enhancedtooltips.mixin.accessors.MobBucketItemTypeAccessor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Bucketable;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.fish.AbstractSchoolingFish;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.nautilus.Nautilus;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerDataHolder;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ModelViewerTooltipComponent
extends TooltipBorderColorComponent {
    private static float currentRotation = 0.0f;
    private static final int SPACING = 20;
    private static final int MAX_TOOLTIP_SIZE = 80;
    private static final float REFERENCE_SIZE = 3.5f;
    private final ItemStack stack;
    private final EnhancedTooltipsConfig config;
    private final float ROTATION_INCREMENT;

    public ModelViewerTooltipComponent(ItemStack stack) {
        super(stack);
        this.stack = stack;
        this.config = EnhancedTooltipsConfig.load();
        this.ROTATION_INCREMENT = this.config.mobs.rotationSpeed;
    }

    public static EquipmentSlot getEquipmentSlot(ItemStack itemStack) {
        Equippable equippable = (Equippable)itemStack.get(DataComponents.EQUIPPABLE);
        return equippable != null ? equippable.slot() : EquipmentSlot.MAINHAND;
    }

    @Override
    public void render(GuiGraphicsExtractor context, int x, int y, int width, int height, int z, int page) throws Exception {
        super.render(context, x, y, width, height, z, page);
        if (page != 0) {
            return;
        }
        currentRotation = (currentRotation + this.ROTATION_INCREMENT) % 360.0f;
        if (ModelViewerTooltipComponent.getEquipmentSlot(this.stack).getType() == EquipmentSlot.Type.HUMANOID_ARMOR || this.stack.is(Items.ELYTRA)) {
            if (this.config.mobs.armorTooltip == EnhancedTooltipsConfig.ArmorTooltipMode.OFF) {
                return;
            }
            this.renderArmor(context, x, y, z);
        } else if (this.stack.getItem() instanceof SmithingTemplateItem) {
            if (this.config.mobs.armorTooltip == EnhancedTooltipsConfig.ArmorTooltipMode.OFF) {
                return;
            }
            this.renderArmorTrim(context, x, y, z);
        } else if (this.stack.getItem().toString().contains("horse_armor")) {
            if (!this.config.mobs.horseArmorTooltip) {
                return;
            }
            this.renderHorseArmor(context, x, y, z);
        } else if (this.stack.getItem().toString().contains("nautilus_armor")) {
            if (!this.config.mobs.nautilusArmorTooltip) {
                return;
            }
            this.renderNautilusArmor(context, x, y, z);
        } else if (this.stack.is(Items.WOLF_ARMOR)) {
            if (!this.config.mobs.wolfArmorTooltip) {
                return;
            }
            this.renderWolfArmor(context, x, y, z);
        } else {
            Item item = this.stack.getItem();
            if (item instanceof MobBucketItem) {
                MobBucketItem bucketItem = (MobBucketItem)item;
                if (!this.config.mobs.bucketTooltip) {
                    return;
                }
                this.renderBucketEntity(context, x, y, z, bucketItem);
            } else {
                item = this.stack.getItem();
                if (item instanceof SpawnEggItem) {
                    SpawnEggItem spawnEggItem = (SpawnEggItem)item;
                    if (!this.config.mobs.spawnEggTooltip) {
                        return;
                    }
                    this.renderSpawnEggEntity(context, x, y, z, spawnEggItem);
                }
            }
        }
    }

    private void renderArmor(GuiGraphicsExtractor context, int x, int y, int z) throws Exception {
        net.minecraft.world.entity.LivingEntity entity;
        if (this.config.mobs.armorTooltip == EnhancedTooltipsConfig.ArmorTooltipMode.PLAYER) {
            entity = this.createFakePlayer();
        } else if (this.config.mobs.armorTooltip == EnhancedTooltipsConfig.ArmorTooltipMode.ARMOR_STAND) {
            entity = this.createArmorStand();
        } else {
            return;
        }
        if (entity == null) {
            return;
        }
        entity.setItemSlot(ModelViewerTooltipComponent.getEquipmentSlot(this.stack), this.stack.copy());
        super.render(context, x - 65, y, 40, 70, z, -1);
        ModelViewerTooltipComponent.drawEntity(context, x - 15 - 20 - 10, y + 65, 30.0f, currentRotation, (Entity)entity);
    }

    private void renderArmorTrim(GuiGraphicsExtractor context, int x, int y, int z) throws Exception {
        net.minecraft.world.entity.LivingEntity entity;
        if (this.config.mobs.armorTooltip == EnhancedTooltipsConfig.ArmorTooltipMode.PLAYER) {
            entity = this.createFakePlayer();
        } else if (this.config.mobs.armorTooltip == EnhancedTooltipsConfig.ArmorTooltipMode.ARMOR_STAND) {
            entity = this.createArmorStand();
        } else {
            return;
        }
        if (entity == null) {
            return;
        }
        List<ItemStack> armorPieces = List.of(Items.NETHERITE_HELMET.getDefaultInstance(), Items.NETHERITE_CHESTPLATE.getDefaultInstance(), Items.NETHERITE_LEGGINGS.getDefaultInstance(), Items.NETHERITE_BOOTS.getDefaultInstance());
        for (ItemStack armor : armorPieces) {
            Identifier id = Identifier.parse(StringUtils.substringBefore((String)this.stack.getItem().toString(), (String)"_armor_trim_smithing_template"));
            ClientLevel world = Minecraft.getInstance().level;
            if (world == null) {
                return;
            }
            RegistryAccess registryManager = world.registryAccess();
            Optional mat = registryManager.lookup(Registries.TRIM_MATERIAL);
            Optional pat = registryManager.lookup(Registries.TRIM_PATTERN);
            if (mat.isEmpty() || pat.isEmpty()) {
                return;
            }
            Holder.Reference material = (Holder.Reference)((Registry)mat.get()).get(Identifier.withDefaultNamespace("diamond")).orElseThrow();
            Holder.Reference pattern = (Holder.Reference)((Registry)pat.get()).get(id).orElseThrow();
            armor.set(DataComponents.TRIM, new ArmorTrim((Holder)material, (Holder)pattern));
            entity.setItemSlot(ModelViewerTooltipComponent.getEquipmentSlot(armor), armor.copy());
        }
        super.render(context, x - 65, y, 40, 70, z, -1);
        ModelViewerTooltipComponent.drawEntity(context, x - 15 - 20 - 10, y + 65, 30.0f, currentRotation, (Entity)entity);
    }

    private AbstractClientPlayer createFakePlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        ClientLevel world = Minecraft.getInstance().level;
        if (player == null || world == null) {
            return null;
        }
        AbstractClientPlayer fakePlayer = new AbstractClientPlayer(world, Minecraft.getInstance().getGameProfile()){

            @NotNull
            public GameType gameMode() {
                return GameType.ADVENTURE;
            }

            public boolean isModelPartShown(PlayerModelPart part) {
                return true;
            }
        };
        fakePlayer.setUUID(player.getUUID());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack equipped = player.getItemBySlot(slot);
            fakePlayer.setItemSlot(slot, equipped.copy());
        }
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer((Entity)fakePlayer).createRenderState().nameTag = Component.empty();
        return fakePlayer;
    }

    private ArmorStand createArmorStand() {
        return new ArmorStand(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, (Level)Minecraft.getInstance().level);
    }

    private void renderHorseArmor(GuiGraphicsExtractor context, int x, int y, int z) throws Exception {
        EntityType entityType = net.minecraft.world.entity.EntityTypes.HORSE;
        Horse horse = (Horse)entityType.create((Level)Minecraft.getInstance().level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (horse == null) {
            return;
        }
        horse.setItemSlot(EquipmentSlot.BODY, this.stack);
        float entityWidth = horse.getBbWidth();
        float entityHeight = horse.getBbHeight();
        float entityScale = this.calculateScale(entityWidth, entityHeight);
        int scaledWidth = (int)(entityWidth * entityScale);
        int scaledHeight = (int)(entityHeight * entityScale);
        int entityOffset = scaledWidth + 20 - 10;
        super.render(context, x - entityOffset - 70, y, scaledWidth + 60, scaledHeight + 20, z, -1);
        ModelViewerTooltipComponent.drawEntity(context, x - scaledWidth / 2 - 20 - 30, y + scaledHeight + 20, entityScale, currentRotation, (Entity)horse);
    }

    private void renderNautilusArmor(GuiGraphicsExtractor context, int x, int y, int z) throws Exception {
        Nautilus nautilus = (Nautilus)net.minecraft.world.entity.EntityTypes.NAUTILUS.create((Level)Minecraft.getInstance().level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (nautilus == null) {
            return;
        }
        nautilus.setItemSlot(EquipmentSlot.BODY, this.stack);
        float entityWidth = nautilus.getBbWidth();
        float entityHeight = nautilus.getBbHeight();
        float entityScale = this.calculateScale(entityWidth, entityHeight);
        int scaledWidth = (int)(entityWidth * entityScale);
        int scaledHeight = (int)(entityHeight * entityScale);
        int entityOffset = scaledWidth + 20 - 10;
        super.render(context, x - entityOffset - 70, y, scaledWidth + 50, scaledHeight + 20, z, -1);
        ModelViewerTooltipComponent.drawEntity(context, x - scaledWidth / 2 - 20 - 35, y + scaledHeight + 20, entityScale, currentRotation, (Entity)nautilus);
    }

    private void renderWolfArmor(GuiGraphicsExtractor context, int x, int y, int z) throws Exception {
        EntityType entityType = net.minecraft.world.entity.EntityTypes.WOLF;
        Wolf wolf = (Wolf)entityType.create((Level)Minecraft.getInstance().level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (wolf == null) {
            return;
        }
        wolf.setItemSlot(EquipmentSlot.BODY, this.stack);
        float entityWidth = wolf.getBbWidth();
        float entityHeight = wolf.getBbHeight();
        float entityScale = this.calculateScale(entityWidth, entityHeight);
        int scaledWidth = (int)(entityWidth * entityScale);
        int scaledHeight = (int)(entityHeight * entityScale);
        int entityOffset = scaledWidth + 20 - 10;
        super.render(context, x - entityOffset - 70, y, scaledWidth + 50, scaledHeight + 10, z, -1);
        ModelViewerTooltipComponent.drawEntity(context, x - scaledWidth / 2 - 20 - 35, y + scaledHeight + 10, entityScale, currentRotation, (Entity)wolf);
    }

    private void renderBucketEntity(GuiGraphicsExtractor context, int x, int y, int z, MobBucketItem bucketItem) throws Exception {
        EntityType<? extends Mob> entityType = ((MobBucketItemTypeAccessor)bucketItem).get();
        LivingEntity entity = (LivingEntity)entityType.create((Level)Minecraft.getInstance().level, EntitySpawnReason.BUCKET);
        if (entity instanceof Bucketable) {
            Bucketable bucketable = (Bucketable)entity;
            CustomData nbtComponent = (CustomData)this.stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, (Object)CustomData.EMPTY);
            bucketable.loadFromBucketTag(nbtComponent.copyTag());
            if (entity instanceof AbstractSchoolingFish) {
                return;
            }
            if (bucketable instanceof Pufferfish) {
                Pufferfish pufferfishEntity = (Pufferfish)bucketable;
                pufferfishEntity.setPuffState(2);
            }
            float entityWidth = entity.getBbWidth();
            float entityHeight = entity.getBbHeight();
            float entityScale = this.calculateScale(entityWidth, entityHeight);
            int scaledWidth = (int)(entityWidth * entityScale);
            int scaledHeight = (int)(entityHeight * entityScale);
            int entityOffset = scaledWidth + 20 - 10;
            super.render(context, x - entityOffset - 70, y, scaledWidth + 50, scaledHeight + 20, z, -1);
            ModelViewerTooltipComponent.drawEntity(context, x - scaledWidth / 2 - 20 - 35, y + scaledHeight + 20, entityScale, currentRotation, (Entity)((LivingEntity)bucketable));
        }
    }

    private void renderSpawnEggEntity(GuiGraphicsExtractor context, int x, int y, int z, SpawnEggItem spawnEggItem) throws Exception {
        EntityType entityType = SpawnEggItem.getType((ItemStack)this.stack);
        if (entityType == null) {
            return;
        }
        Entity entity = entityType.create((Level)Minecraft.getInstance().level, EntitySpawnReason.COMMAND);
        if (entityType == net.minecraft.world.entity.EntityTypes.VILLAGER || entityType == net.minecraft.world.entity.EntityTypes.ZOMBIE_VILLAGER) {
            ClientLevel world = Minecraft.getInstance().level;
            if (entity != null && world != null) {
                ((VillagerDataHolder)entity).setVillagerData(Villager.createDefaultVillagerData().withLevel(1).withProfession((HolderGetter.Provider)world.registryAccess(), VillagerProfession.FARMER).withType((HolderGetter.Provider)world.registryAccess(), VillagerType.PLAINS));
            }
        }
        if (entity instanceof AbstractSchoolingFish) {
            return;
        }
        if (entity instanceof Pufferfish) {
            Pufferfish pufferfishEntity = (Pufferfish)entity;
            pufferfishEntity.setPuffState(2);
        }
        if (entity instanceof SnowGolem) {
            SnowGolem snowGolemEntity = (SnowGolem)entity;
            snowGolemEntity.setPumpkin(false);
        }
        float entityWidth = entity.getBbWidth();
        float entityHeight = entity.getBbHeight();
        float entityScale = this.calculateScale(entityWidth, entityHeight);
        int scaledWidth = (int)(entityWidth * entityScale);
        int scaledHeight = (int)(entityHeight * entityScale);
        int entityOffset = scaledWidth + 20 - 10;
        super.render(context, x - entityOffset - 70, y, scaledWidth + 50, scaledHeight + 20, z, -1);
        ModelViewerTooltipComponent.drawEntity(context, x - scaledWidth / 2 - 20 - 35, y + scaledHeight + 20, entityScale, currentRotation, entity);
    }

    private float calculateScale(float width, float height) {
        float longerDimension = Math.max(width, height);
        float scale = 22.857143f * longerDimension;
        if (scale > 80.0f) {
            return 80.0f / longerDimension;
        }
        if (scale < 30.0f && longerDimension < 1.0f) {
            return 30.0f / longerDimension;
        }
        return scale / longerDimension;
    }

    public static void drawEntity(GuiGraphicsExtractor context, int x, int y, float scale, float rotationYaw, Entity entity) {
        entity.setYBodyRot(rotationYaw);
        entity.setYRot(rotationYaw);
        entity.setYHeadRot(rotationYaw);
        Quaternionf modelRotation = new Quaternionf().rotateY((float)Math.toRadians(rotationYaw)).rotateX((float)Math.toRadians(180.0));
        entity.setCustomName((Component)Component.literal((String)"enhancedtooltips entity do not touch"));
        entity.setCustomNameVisible(false);
        float entityWidth = entity.getBbWidth();
        float entityHeight = entity.getBbHeight();
        int x1 = (int)((float)x - entityWidth * scale - 20.0f + 5.0f);
        int y1 = (int)((float)y - entityHeight * scale - 20.0f);
        int x2 = (int)((float)x + entityWidth * scale + 20.0f - 5.0f);
        int y2 = (int)((float)y + entityHeight * scale + 20.0f);
        EntityRenderState renderState = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity, 1.0f);
        renderState.lightCoords = 0xF000F0;
        renderState.shadowPieces.clear();
        context.entity(renderState, scale, new Vector3f(), modelRotation, null, x1, y1, x2, y2);
    }
}

