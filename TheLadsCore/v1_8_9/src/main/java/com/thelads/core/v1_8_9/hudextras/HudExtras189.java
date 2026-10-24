package com.thelads.core.v1_8_9.hudextras;

import com.thelads.core.client.hud.DamageNumbers;
import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.client.hud.JumpReset;
import com.thelads.core.client.hud.MediaSession;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.RaisedModule;
import com.thelads.core.modules.TitleTweakerModule;
import com.thelads.core.v1_8_9.hudextras.mixin.PlayerControllerAccessor189;
import java.lang.reflect.Field;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/**
 * The HUD widgets and vanilla HUD tweaks of parity 1.9.19 on 1.8.9 (common HudExtras): once a client tick it fills what the switched-on
 * widgets show; after the world it draws Damage Indicator's floating numbers; Forge's overlay events move and scale Raised's parts. The
 * other hooks are the mixins in theladscore.v1_8_9.hudextras.mixins.json (package hudextras.mixin). Minecraft 1.8.9 has no totems and
 * no subtitles, and draws no effect icons on the HUD.
 */
public final class HudExtras189 {
    static final String[] MODULES = {HudExtras.DAMAGE_INDICATOR, HudExtras.JUMP_RESET, HudExtras.MEDIA, HudExtras.HORSE, HudExtras.PLAYERS,
        HudExtras.BLOCK_INFO, HudExtras.CUSTOM_TEXT, HudExtras.CUSTOM_SHAPE, HudExtras.CUSTOM_IMAGE, HudExtras.HEAD, HudExtras.ABOVE,
        HudExtras.ENTITIES, HudExtras.SHADER, HudExtras.LORE, TitleTweakerModule.NAME};
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private int lastHurt, targetEntity = -1, shaderTicks;
    private IBlockState blockState, aboveState;
    private ItemStack loreStack;
    private boolean loreRead, numbersShown;
    /** A Raised part's move is pushed (Forge draws the parts one after another, never nested). */
    private boolean pushed;
    private static Field shaderName;
    private static boolean shaderLooked;

    public static void register() {
        ModuleSupport.registerBuiltIn(MODULES);
        ModuleSupport.registerBuiltInLimited(com.thelads.core.modules.VanillaHudModule.NAME,
            "Minecraft 1.8.9 draws no effect icons on the HUD, so Hide Effect Icons and their offsets have nothing to act on.");
        ModuleSupport.registerBuiltInLimited(com.thelads.core.modules.ToastControlModule.NAME,
            "Minecraft 1.8.9's pop-ups are achievements (Advancement) and the \"Open your inventory\" hint (Tutorial); it has no recipe toasts.");
        ModuleSupport.registerUnavailable(HudExtras.TOTEMS, "Minecraft 1.8.9 has no totems of undying; they were added in Minecraft 1.11.");
        ModuleSupport.registerUnavailable(com.thelads.core.modules.SubtitlesModule.NAME, "Minecraft 1.8.9 has no subtitles; they were added in Minecraft 1.9.");
        if (!MediaSession.SUPPORTED) ModuleSupport.registerUnavailable(HudExtras.MEDIA, "Reads Windows' media controls; this computer is not on Windows.");
        MinecraftForge.EVENT_BUS.register(new HudExtras189());
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        HudExtras.tick();
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        WorldClient world = mc.theWorld;
        if (player == null || world == null) {
            HudExtras.targetId = -1;
            HudExtras.horseShown = HudExtras.blockShown = false;
            JumpReset.reset();
            DamageNumbers.clear();
            return;
        }
        long now = System.currentTimeMillis();
        int hurt = player.hurtTime;
        if (HudExtras.jumpReset) JumpReset.tick(hurt > lastHurt, player.onGround, now);
        lastHurt = hurt;
        if (HudExtras.target) target(mc, world, now);
        if (HudExtras.numbers) {
            numbersShown = true;
            for (Entity entity : world.loadedEntityList) {
                if (entity == player || !(entity instanceof EntityLivingBase) || entity.getDistanceSqToEntity(player) > 32 * 32) continue;
                DamageNumbers.observe(entity.getEntityId(), ((EntityLivingBase) entity).getHealth(), entity.posX, entity.posY + entity.height + 0.3,
                    entity.posZ, now);
            }
            DamageNumbers.endTick();
        } else if (numbersShown) {
            numbersShown = false;
            DamageNumbers.clear();
        }
        if (HudExtras.horse) horse(mc, player);
        if (HudExtras.players) {
            HudExtras.playersTotal = mc.getNetHandler() == null ? 1 : mc.getNetHandler().getPlayerInfoMap().size();
            HudExtras.playersNearby = Math.max(0, world.playerEntities.size() - 1);
        }
        if (HudExtras.block) block(mc, world, player);
        if (HudExtras.above) above(world, player);
        if (HudExtras.entities) HudExtras.entityCount = world.loadedEntityList.size();
        if (HudExtras.shader && shaderTicks-- <= 0) {
            shaderTicks = 20;
            HudExtras.shaderName = shaderPack();
        }
        if (HudExtras.lore) lore(player.getHeldItem());
    }

    /** Damage Indicator: the living entity under the crosshair, kept 3 s after it leaves it while it is still loaded. */
    private void target(Minecraft mc, WorldClient world, long now) {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && hit.entityHit instanceof EntityLivingBase
            && hit.entityHit.isEntityAlive()) {
            if (hit.entityHit.getEntityId() != targetEntity) {
                targetEntity = hit.entityHit.getEntityId();
                HudExtras.targetName = hit.entityHit.getName();
            }
            HudExtras.targetId = targetEntity;
            HudExtras.targetUntil = now + 3000;
        }
        if (HudExtras.targetId == -1) return;
        Entity entity = world.getEntityByID(HudExtras.targetId);
        if (entity instanceof EntityLivingBase) {
            HudExtras.targetHealth = ((EntityLivingBase) entity).getHealth();
            HudExtras.targetMax = ((EntityLivingBase) entity).getMaxHealth();
        } else {
            HudExtras.targetId = targetEntity = -1;
        }
    }

    private void horse(Minecraft mc, EntityPlayerSP player) {
        MovingObjectPosition hit = mc.objectMouseOver;
        Entity looked = hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY ? hit.entityHit : null;
        EntityHorse horse = player.ridingEntity instanceof EntityHorse ? (EntityHorse) player.ridingEntity
            : looked instanceof EntityHorse ? (EntityHorse) looked : null;
        HudExtras.horseShown = horse != null;
        if (horse == null) return;
        HudExtras.horseJump = HudExtras.jumpHeight(horse.getHorseJumpStrength());
        HudExtras.horseSpeed = HudExtras.blocksPerSecond(horse.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue());
        HudExtras.horseHealth = horse.getHealth();
        HudExtras.horseMax = horse.getMaxHealth();
    }

    private void block(Minecraft mc, WorldClient world, EntityPlayerSP player) {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            HudExtras.blockShown = false;
            return;
        }
        BlockPos pos = hit.getBlockPos();
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (state != blockState) { // a name and tool once per block state, not per tick
            blockState = state;
            HudExtras.blockName = block.getLocalizedName();
            // Forge's harvest levels: 0 wood or gold, 1 stone, 2 iron, 3 diamond.
            HudExtras.blockTool = HudExtras.toolName(block.getHarvestTool(state), Math.max(0, block.getHarvestLevel(state)));
        }
        HudExtras.blockHarvest = block.getMaterial().isToolNotRequired() ? -1 : ForgeHooks.canHarvestBlock(block, player, world, pos) ? 1 : 0;
        BlockPos face = pos.offset(hit.sideHit); // the light on the face you look at
        HudExtras.blockLight = world.getLightFor(EnumSkyBlock.BLOCK, face);
        HudExtras.skyLight = world.getLightFor(EnumSkyBlock.SKY, face);
        PlayerControllerAccessor189 controller = (PlayerControllerAccessor189) mc.playerController;
        HudExtras.blockProgress = controller != null && controller.lads$hitting() && pos.equals(controller.lads$block()) ? controller.lads$damage() : 0;
        HudExtras.blockShown = true;
    }

    private void above(WorldClient world, EntityPlayerSP player) {
        int x = (int) Math.floor(player.posX), z = (int) Math.floor(player.posZ), from = (int) Math.floor(player.posY + player.height);
        for (int gap = 0; gap < 32; gap++) {
            IBlockState state = world.getBlockState(cursor.set(x, from + gap, z));
            if (state.getBlock().getMaterial() == net.minecraft.block.material.Material.air) continue;
            if (state != aboveState) {
                aboveState = state;
                HudExtras.aboveName = state.getBlock().getLocalizedName();
            }
            HudExtras.aboveGap = gap;
            return;
        }
        aboveState = null;
        HudExtras.aboveName = "";
    }

    /** OptiFine's shader pack by reflection (its Shaders class), so the Core runs without it; "" when off or absent. */
    private static String shaderPack() {
        try {
            if (!shaderLooked) {
                shaderLooked = true;
                Class<?> shaders;
                try {
                    shaders = Class.forName("shadersmod.client.Shaders");
                } catch (ClassNotFoundException newer) {
                    shaders = Class.forName("net.optifine.shaders.Shaders");
                }
                shaderName = shaders.getField("currentshadername");
            }
            Object name = shaderName == null ? null : shaderName.get(null);
            String pack = name == null ? "" : name.toString();
            if (!com.thelads.core.v1_8_9.feature.OptiFineShaderLink189.isShaderPack(pack)) return "";
            return pack.endsWith(".zip") ? pack.substring(0, pack.length() - 4) : pack;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) {
            shaderName = null;
            return "";
        }
    }

    /** Held Item Lore: enchantments then lore lines, rebuilt only when the held item changes. */
    private void lore(ItemStack stack) {
        if (loreRead && ItemStack.areItemStacksEqual(stack, loreStack)) return;
        loreRead = true;
        loreStack = stack == null ? null : stack.copy();
        StringBuilder out = new StringBuilder();
        NBTTagList enchantments = stack == null ? null : stack.getEnchantmentTagList();
        for (int i = 0; enchantments != null && i < enchantments.tagCount(); i++) {
            NBTTagCompound tag = enchantments.getCompoundTagAt(i);
            Enchantment enchantment = Enchantment.getEnchantmentById(tag.getShort("id"));
            if (enchantment == null) continue;
            if (out.length() > 0) out.append('\n');
            out.append(enchantment.getTranslatedName(tag.getShort("lvl")));
        }
        NBTTagCompound root = stack == null ? null : stack.getTagCompound();
        NBTTagList lore = root == null ? null : root.getCompoundTag("display").getTagList("Lore", 8);
        for (int i = 0; lore != null && i < lore.tagCount(); i++) {
            if (out.length() > 0) out.append('\n');
            out.append(lore.getStringTagAt(i));
        }
        HudExtras.loreText = out.toString();
    }

    /** After the world: Damage Indicator's floating numbers, billboards like name tags, hidden behind blocks as name tags are. */
    @SubscribeEvent
    public void numbers(RenderWorldLastEvent event) {
        if (!numbersShown) return;
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager view = mc.getRenderManager();
        FontRenderer font = mc.fontRendererObj;
        long now = System.currentTimeMillis();
        int life = HudExtras.numberMillis;
        float pitch = (mc.gameSettings.thirdPersonView == 2 ? -1 : 1) * view.playerViewX;
        for (int slot = 0; slot < DamageNumbers.slots(); slot++) {
            String text = DamageNumbers.text(slot, now, life);
            if (text == null) continue;
            GlStateManager.pushMatrix();
            GlStateManager.translate(DamageNumbers.x(slot) - view.viewerPosX, DamageNumbers.y(slot, now, life) - view.viewerPosY,
                DamageNumbers.z(slot) - view.viewerPosZ);
            GL11.glNormal3f(0, 1, 0);
            GlStateManager.rotate(-view.playerViewY, 0, 1, 0);
            GlStateManager.rotate(pitch, 1, 0, 0);
            GlStateManager.scale(-0.025f, -0.025f, 0.025f);
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            font.drawString(text, -font.getStringWidth(text) / 2, 0, DamageNumbers.alpha(slot, now, life) << 24 | 0xFF5555, true);
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.popMatrix();
        }
    }

    // Raised's own move and scale of each part, after Raised189's lift (HIGHEST) so it scales where the part is drawn.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void move(RenderGameOverlayEvent.Pre event) {
        int piece = piece(event.type);
        RaisedModule raised = HudExtras.RAISED;
        if (piece < 0 || raised == null || !raised.moved[piece]) return;
        int w = event.resolution.getScaledWidth(), h = event.resolution.getScaledHeight();
        float px = RaisedModule.pivotX(piece, w), py = RaisedModule.pivotY(piece, h), scale = raised.scale[piece];
        GlStateManager.pushMatrix();
        GlStateManager.translate(px + raised.dx[piece], py + raised.dy[piece], 0);
        GlStateManager.scale(scale, scale, 1);
        GlStateManager.translate(-px, -py, 0);
        pushed = true;
    }

    // A cancelled part never reaches Post: its matrix ends here instead.
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void cancelled(RenderGameOverlayEvent.Pre event) {
        if (event.isCanceled() && pushed && piece(event.type) >= 0) end();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void restore(RenderGameOverlayEvent.Post event) {
        if (pushed && piece(event.type) >= 0) end();
    }

    private void end() {
        GlStateManager.popMatrix();
        pushed = false;
    }

    private static int piece(ElementType type) {
        switch (type) {
            case HOTBAR: return RaisedModule.HOTBAR;
            case HEALTH: return RaisedModule.HEALTH;
            case FOOD: return RaisedModule.FOOD;
            case ARMOR: return RaisedModule.ARMOR;
            case AIR: return RaisedModule.AIR;
            case HEALTHMOUNT: return RaisedModule.MOUNT;
            default: return -1;
        }
    }
}
