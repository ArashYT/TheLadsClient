package com.thelads.core.v1_8_9.cit;

import com.thelads.core.client.CustomItems;
import com.thelads.core.client.CustomItemsNbt;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.CustomItemTexturesModule;
import com.thelads.core.v1_8_9.cit.mixin.AbstractResourcePackCitAccessor189;
import com.thelads.core.v1_8_9.cit.mixin.RenderItemCitInvoker189;
import com.thelads.core.v1_8_9.feature.Options189;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockPart;
import net.minecraft.client.renderer.block.model.BlockPartFace;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelRotation;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.RegistrySimple;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

/**
 * Custom Item Textures on 1.8.9 without OptiFine (Ionium): the resource packs' OptiFine CIT rules (client.CustomItems).
 * Reload: on the block/item atlas' stitch (TextureStitchEvent.Pre, inside every model reload) the rules are read from every selected
 * pack's optifine/cit and mcpatcher/cit folders and their item textures join the atlas; ModelBakeEvent then names each item model.
 * Items: ItemModelMesher.getItemModel and the bow/rod models of renderItemModelForEntity (theladscore.v1_8_9.cit.mixins.json) hand
 * their model to {@link #model}: the first matching rule's textures replace the model's layers by name (texture.bow_pulling_0,
 * texture.potion_overlay, else texture), baked like a vanilla generated item and kept per rule and model. Each stack remembers its
 * lookup (ItemStackCitMixin189) until it changes or a second passes, so a frame does no matching and allocates nothing; items no rule
 * names cost one id lookup. Armor: LayerArmorBaseMixin asks {@link #armor} for the layer texture. Enchantment effects replace the
 * glint where vanilla draws it ({@link #glint}, {@link #armorGlint}); cit.properties' useGlint=false hides the rest. With OptiFine nothing is loaded:
 * OptiFine's own Custom Items setting draws these packs.
 */
public final class Cit189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");
    /** A stack whose item, damage, size and tag object are unchanged is looked up again after this (tags edited in place). */
    private static final long RECHECK_NANOS = 1_000_000_000L;
    private static final ItemModelGenerator GENERATOR = new ItemModelGenerator();
    private static final FaceBakery BAKERY = new FaceBakery();
    /** Vanilla 1.8.9's generated-item display (apple.json), for a flat texture replacing a block or entity-rendered item. */
    private static final String FLAT_DISPLAY = "{\"parent\":\"builtin/generated\",\"display\":{\"thirdperson\":{\"rotation\":[-90,0,0],\"translation\":[0,1,-3],"
        + "\"scale\":[0.55,0.55,0.55]},\"firstperson\":{\"rotation\":[0,-135,25],\"translation\":[0,4,2],\"scale\":[1.7,1.7,1.7]}}}";
    private static final ThreadLocal<CustomItems.Stack> STACK = new ThreadLocal<CustomItems.Stack>() {
        @Override protected CustomItems.Stack initialValue() { return new CustomItems.Stack(); }
    };

    private static Module module;
    private static volatile CustomItems.Rules rules = CustomItems.Rules.EMPTY;
    /** Item ids some item or armor rule names. */
    private static volatile boolean[] named = new boolean[0];
    private static volatile int epoch;
    // main thread, rebuilt per reload
    private static Map<IBakedModel, ModelResourceLocation> modelNames = new IdentityHashMap<IBakedModel, ModelResourceLocation>();
    private static final Map<IBakedModel, String[]> LAYERS = new IdentityHashMap<IBakedModel, String[]>();
    private static final Map<CustomItems.Rule, Map<IBakedModel, IBakedModel>> BAKED = new IdentityHashMap<CustomItems.Rule, Map<IBakedModel, IBakedModel>>();
    /** Armor and enchantment textures (bound directly, not atlas sprites) by path. */
    private static final Map<String, ResourceLocation> ARMOR = new HashMap<String, ResourceLocation>();
    private static final float[] COLOR = new float[4];
    private static ItemCameraTransforms flat;

    private Cit189() {}

    /** Added to ItemStack (ItemStackCitMixin189): the stack's last lookup. */
    public interface Cached {
        Match lads$cit();
        void lads$cit(Match match);
    }

    /** One stack's lookup: what it was made from, the rules found and the model last handed out. */
    public static final class Match {
        int epoch;
        Item item;
        int damage, count;
        NBTTagCompound tag;
        long checked;
        CustomItems.Rule itemRule, armorRule;
        CustomItems.Effect[] effects = CustomItems.Rules.NO_EFFECTS;
        IBakedModel original, model;
    }

    public static void register() {
        if (!CitMixinPlugin189.ON) {
            ModuleSupport.registerBuiltInLimited(CustomItemTexturesModule.NAME, "With OptiFine, OptiFine's own Custom Items setting draws "
                + "the packs' item textures; this module draws them when Lads runs 1.8.9 without OptiFine (Ionium).");
            return;
        }
        ModuleSupport.registerBuiltIn(CustomItemTexturesModule.NAME);
        module = Options189.module(CustomItemTexturesModule.NAME);
        MinecraftForge.EVENT_BUS.register(new Cit189());
    }

    // ---- reload ------------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void stitch(TextureStitchEvent.Pre event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.map != mc.getTextureMapBlocks()) return;
        long start = System.nanoTime();
        CustomItems.Rules loaded;
        try {
            loaded = load(mc);
        } catch (RuntimeException broken) {
            LOGGER.error("Custom Item Textures: could not read the resource packs' CIT rules", broken);
            loaded = CustomItems.Rules.EMPTY;
        }
        int sprites = 0;
        for (String texture : loaded.itemTextures()) {
            String name = spriteName(texture);
            if (event.map.setTextureEntry(name, new CitSprite(name, texture))) sprites++;
        }
        install(loaded);
        if (!loaded.isEmpty() || sprites > 0) {
            LOGGER.info("Custom Item Textures: {} rules, {} item textures ({} ms)", loaded.count, sprites, (System.nanoTime() - start) / 1_000_000L);
        }
    }

    @SubscribeEvent
    public void baked(ModelBakeEvent event) {
        Map<IBakedModel, ModelResourceLocation> names = new IdentityHashMap<IBakedModel, ModelResourceLocation>();
        if (event.modelRegistry instanceof RegistrySimple) {
            @SuppressWarnings("unchecked")
            RegistrySimple<ModelResourceLocation, IBakedModel> registry = (RegistrySimple<ModelResourceLocation, IBakedModel>) event.modelRegistry;
            for (ModelResourceLocation key : registry.getKeys()) {
                IBakedModel model = registry.getObject(key);
                if (model != null && "inventory".equals(key.getVariant())) names.put(model, key);
            }
        }
        modelNames = names;
        install(rules);
    }

    private static void install(CustomItems.Rules loaded) {
        boolean[] ids = new boolean[0];
        if (!loaded.isEmpty()) {
            ids = new boolean[32000];
            for (int id = 0; id < ids.length; id++) ids[id] = loaded.names(id);
        }
        LAYERS.clear();
        BAKED.clear();
        ARMOR.clear();
        named = ids;
        rules = loaded;
        epoch++;
    }

    private static CustomItems.Rules load(Minecraft mc) {
        final IResourceManager resources = mc.getResourceManager();
        ResourcePackRepository repository = mc.getResourcePackRepository();
        List<IResourcePack> packs = new ArrayList<IResourcePack>();
        for (ResourcePackRepository.Entry entry : repository.getRepositoryEntries()) packs.add(entry.getResourcePack());
        if (repository.getResourcePackInstance() != null) packs.add(repository.getResourcePackInstance());
        final Set<String> files = new TreeSet<String>();
        for (IResourcePack pack : packs) list(pack, files);
        if (files.isEmpty()) return CustomItems.Rules.EMPTY;
        List<String> properties = new ArrayList<String>();
        for (String file : files) if (file.endsWith(".properties")) properties.add(file);
        return CustomItems.load(properties, name -> read(resources, name), name -> files.contains(name) || exists(resources, name), IDS);
    }

    /** Every file under the pack's CIT folders, as "minecraft:path". */
    private static void list(IResourcePack pack, Set<String> files) {
        if (!(pack instanceof AbstractResourcePack)) return;
        File file = ((AbstractResourcePackCitAccessor189) pack).lads$citPackFile();
        if (file == null) return;
        if (file.isDirectory()) {
            File assets = new File(file, "assets/minecraft");
            for (String folder : CustomItems.FOLDERS) walk(new File(assets, folder), folder, files);
            return;
        }
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith("assets/minecraft/")) continue;
                String path = name.substring("assets/minecraft/".length());
                for (String folder : CustomItems.FOLDERS) if (path.startsWith(folder)) files.add("minecraft:" + path);
            }
        } catch (IOException unreadable) {
            LOGGER.warn("Custom Item Textures: cannot list {}", file.getName());
        }
    }

    private static void walk(File dir, String path, Set<String> files) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) walk(child, path + child.getName() + "/", files);
            else files.add("minecraft:" + path + child.getName());
        }
    }

    /** A properties file's text (UTF-8, or Latin-1 when it is not valid UTF-8), or null when no pack has it. */
    private static String read(IResourceManager resources, String name) {
        try (InputStream in = resources.getResource(new ResourceLocation(name)).getInputStream()) {
            byte[] bytes = IOUtils.toByteArray(in);
            try {
                return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException notUtf8) {
                return new String(bytes, StandardCharsets.ISO_8859_1);
            }
        } catch (IOException | RuntimeException missing) {
            return null;
        }
    }

    private static boolean exists(IResourceManager resources, String name) {
        try (InputStream in = resources.getResource(new ResourceLocation(name)).getInputStream()) {
            return in != null;
        } catch (IOException | RuntimeException missing) {
            return false;
        }
    }

    private static final CustomItems.Ids IDS = new CustomItems.Ids() {
        @Override public int item(String token) {
            Item item;
            try {
                item = Item.getByNameOrId(token);
            } catch (RuntimeException badName) {
                return -1;
            }
            return item == null ? -1 : Item.getIdFromItem(item);
        }

        @Override public int enchantment(String token) {
            Enchantment enchantment = Enchantment.getEnchantmentByLocation(token);
            return enchantment == null ? -1 : enchantment.effectId;
        }
    };

    /** "minecraft:optifine/cit/a.png" is the atlas sprite "minecraft:optifine/cit/a". */
    private static String spriteName(String texture) {
        return texture.endsWith(".png") ? texture.substring(0, texture.length() - 4) : texture;
    }

    /** A CIT texture in the block/item atlas, read from its own path (not textures/), animated by its .mcmeta like any item. */
    static final class CitSprite extends TextureAtlasSprite {
        private final ResourceLocation file;

        CitSprite(String name, String file) {
            super(name);
            this.file = new ResourceLocation(file);
        }

        @Override
        public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) { return true; }

        @Override
        public boolean load(IResourceManager manager, ResourceLocation location) {
            try {
                IResource resource = manager.getResource(file);
                BufferedImage[] images = new BufferedImage[5]; // as vanilla: one slot per mip level (at most 4), filled by generateMipmaps
                images[0] = TextureUtil.readBufferedImage(resource.getInputStream());
                AnimationMetadataSection animation = resource.getMetadata("animation");
                loadSprite(images, animation);
                return false; // loaded: stitch it
            } catch (Exception broken) {
                LOGGER.warn("Custom Item Textures: cannot load {} ({})", file, broken.toString());
                return true;
            }
        }
    }

    // ---- lookup (render thread) ---------------------------------------------------------------------------------------------------

    /** ItemModelMesher.getItemModel and renderItemModelForEntity: the stack's CIT model, else {@code original}. */
    public static IBakedModel model(ItemStack stack, IBakedModel original) {
        if (original == null) return original;
        Match match = match(stack, false);
        if (match == null || match.itemRule == null) return original;
        if (match.original != original) {
            match.model = bake(match.itemRule, original);
            match.original = original;
        }
        return match.model;
    }

    /**
     * LayerArmorBase.getArmorResource: the CIT texture for this armor layer, or null. {@code texture} is vanilla's path when known
     * (RenderTweaks189), else the layer is named from the armor material as Forge does.
     */
    public static ResourceLocation armor(ItemStack stack, int slot, String type, ResourceLocation texture) {
        Match match = match(stack, false);
        if (match == null || match.armorRule == null) return null;
        String name;
        if (texture != null) {
            String path = texture.getResourcePath();
            name = path.substring(path.lastIndexOf('/') + 1);
            if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);
        } else if (stack.getItem() instanceof ItemArmor) {
            String material = ((ItemArmor) stack.getItem()).getArmorMaterial().getName();
            name = material.substring(material.indexOf(':') + 1) + "_layer_" + (slot == 2 ? 2 : 1) + (type == null ? "" : "_" + type);
        } else {
            return null;
        }
        String replacement = match.armorRule.texture(name);
        return replacement == null ? null : location(replacement);
    }

    /**
     * The stack's lookup, redone when it changed; null when CIT is off, no rule can apply (an item or armor rule naming its item; for
     * {@code glint}, any enchantment rule) or this is not the render thread.
     */
    private static Match match(ItemStack stack, boolean glint) {
        CustomItems.Rules r = rules;
        if (stack == null || r.isEmpty() || module == null || !module.isEnabled()) return null;
        Item item = stack.getItem();
        if (item == null) return null;
        int id = Item.getIdFromItem(item);
        boolean[] ids = named;
        if (id < 0 || (glint ? !r.hasEnchantments() : id >= ids.length || !ids[id])) return null;
        if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) return null;
        Cached cached = (Cached) (Object) stack;
        Match match = cached.lads$cit();
        NBTTagCompound tag = stack.getTagCompound();
        long now = System.nanoTime();
        int damage = stack.getItemDamage();
        if (match != null && match.epoch == epoch && match.item == item && match.damage == damage && match.count == stack.stackSize
            && match.tag == tag && now - match.checked < RECHECK_NANOS) {
            return match;
        }
        if (match == null) cached.lads$cit(match = new Match());
        CustomItems.Stack s = STACK.get();
        s.item = id;
        s.damage = damage;
        s.maxDamage = stack.getMaxDamage();
        s.count = stack.stackSize;
        s.nbt = tag;
        s.access = NBT;
        s.glint = stack.hasEffect();
        s.clearEnchantments();
        NBTTagList enchantments = tag == null ? null : tag.getTagList(item == Items.enchanted_book ? "StoredEnchantments" : "ench", 10);
        if (enchantments != null) {
            for (int i = 0; i < enchantments.tagCount(); i++) {
                NBTTagCompound enchantment = enchantments.getCompoundTagAt(i);
                s.addEnchantment(enchantment.getShort("id"), enchantment.getShort("lvl"));
            }
        }
        CustomItems.Rule itemRule = r.item(s), armorRule = r.armor(s);
        match.effects = s.glint && r.hasEnchantments() ? r.effects(s) : CustomItems.Rules.NO_EFFECTS;
        s.nbt = null; // the scratch must not keep a tag alive
        if (itemRule != match.itemRule) match.original = match.model = null;
        match.itemRule = itemRule;
        match.armorRule = armorRule;
        match.epoch = epoch;
        match.item = item;
        match.damage = damage;
        match.count = stack.stackSize;
        match.tag = tag;
        match.checked = now;
        return match;
    }

    // ---- enchantment effects (type=enchantment) --------------------------------------------------------------------------------------

    /**
     * RenderItem.renderItem, where vanilla draws the glint (renderEffect): the stack's CIT enchantment effects over the model, each its
     * texture scrolled by speed, turned by rotation and blended by blend at its intensity, with vanilla's glint state around them.
     * False when vanilla's glint should draw instead (no effect matches and cit.properties keeps useGlint).
     */
    public static boolean glint(RenderItem renderer, ItemStack stack, IBakedModel model) {
        Match match = match(stack, true);
        if (match == null) return false;
        CustomItems.Effect[] effects = match.effects;
        if (effects.length == 0) return !rules.useGlint;
        long now = Minecraft.getSystemTime();
        if (rules.method == CustomItems.CYCLE) rules.intensities(effects, now);
        TextureManager textures = Minecraft.getMinecraft().getTextureManager();
        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(GL11.GL_EQUAL);
        GlStateManager.disableLighting();
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        for (CustomItems.Effect effect : effects) {
            if (effect.intensity <= 0) continue;
            CustomItems.Rule rule = effect.rule;
            textures.bindTexture(location(rule.texture));
            rule.blend.color(effect.intensity, COLOR);
            GlStateManager.blendFunc(rule.blend.source, rule.blend.destination);
            GlStateManager.pushMatrix();
            GlStateManager.scale(8F, 8F, 8F); // vanilla's glint scale over the item's atlas UVs
            GlStateManager.translate(scroll(rule.speed, now) / 8F, 0F, 0F);
            GlStateManager.rotate(rule.rotation, 0F, 0F, 1F);
            ((RenderItemCitInvoker189) renderer).lads$renderModel(model, argb(COLOR));
            GlStateManager.popMatrix();
        }
        // vanilla's renderEffect leaves this state
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableLighting();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        textures.bindTexture(TextureMap.locationBlocksTexture);
        return true;
    }

    /** LayerArmorBase.renderLayer, where vanilla draws the armor glint (renderGlint): as {@link #glint}, over the armor model. */
    public static boolean armorGlint(ItemStack stack, EntityLivingBase entity, ModelBase model, float limbSwing, float limbSwingAmount,
                                     float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        Match match = match(stack, true);
        if (match == null) return false;
        CustomItems.Effect[] effects = match.effects;
        if (effects.length == 0) return !rules.useGlint;
        long now = Minecraft.getSystemTime();
        if (rules.method == CustomItems.CYCLE) rules.intensities(effects, now);
        TextureManager textures = Minecraft.getMinecraft().getTextureManager();
        GlStateManager.enableBlend();
        GlStateManager.depthFunc(GL11.GL_EQUAL);
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        for (CustomItems.Effect effect : effects) {
            if (effect.intensity <= 0) continue;
            CustomItems.Rule rule = effect.rule;
            textures.bindTexture(location(rule.texture));
            rule.blend.color(effect.intensity, COLOR);
            GlStateManager.color(COLOR[0], COLOR[1], COLOR[2], COLOR[3]);
            GlStateManager.blendFunc(rule.blend.source, rule.blend.destination);
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.loadIdentity();
            GlStateManager.scale(1 / 3F, 1 / 3F, 1 / 3F); // vanilla's armor glint scale
            GlStateManager.translate(scroll(rule.speed, now), 0F, 0F);
            GlStateManager.rotate(rule.rotation, 0F, 0F, 1F);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            model.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        }
        // vanilla's renderGlint leaves this state (with the blend function and colour reset)
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.disableBlend();
        return true;
    }

    /** How far a texture scrolling at {@code speed} has moved (0-1, wrapping): speed 1 crosses it every 3 seconds, as vanilla's glint. */
    private static float scroll(float speed, long millis) {
        if (speed == 0) return 0;
        double t = millis * (double) speed / 3000.0;
        return (float) (t - Math.floor(t));
    }

    private static ResourceLocation location(String texture) {
        ResourceLocation location = ARMOR.get(texture);
        if (location == null) ARMOR.put(texture, location = new ResourceLocation(texture));
        return location;
    }

    private static int argb(float[] rgba) {
        return (int) (rgba[3] * 255F + 0.5F) << 24 | (int) (rgba[0] * 255F + 0.5F) << 16 | (int) (rgba[1] * 255F + 0.5F) << 8
            | (int) (rgba[2] * 255F + 0.5F);
    }

    /** The rule's model for this original (kept per rule and model): its layers with the rule's textures, else the original. */
    private static IBakedModel bake(CustomItems.Rule rule, IBakedModel original) {
        Map<IBakedModel, IBakedModel> forRule = BAKED.get(rule);
        if (forRule == null) BAKED.put(rule, forRule = new IdentityHashMap<IBakedModel, IBakedModel>());
        IBakedModel model = forRule.get(original);
        if (model == null) {
            try {
                model = generate(rule, original);
            } catch (RuntimeException broken) {
                LOGGER.warn("Custom Item Textures: cannot build {}", rule.path, broken);
                model = original;
            }
            forRule.put(original, model);
        }
        return model;
    }

    private static IBakedModel generate(CustomItems.Rule rule, IBakedModel original) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        String[] layers = layers(original);
        Map<String, String> textures = new HashMap<String, String>();
        boolean changed = false;
        int count = Math.min(Math.max(layers.length, 1), ItemModelGenerator.LAYERS.size());
        for (int i = 0; i < count; i++) {
            String vanilla = i < layers.length ? layers[i] : null;
            String replacement = rule.texture(vanilla == null ? null : vanilla.substring(vanilla.lastIndexOf('/') + 1));
            String sprite = vanilla;
            if (replacement != null && atlas.getAtlasSprite(spriteName(replacement)) != atlas.getMissingSprite()) {
                sprite = spriteName(replacement);
                changed = true;
            }
            if (sprite == null) return original;
            textures.put(ItemModelGenerator.LAYERS.get(i), sprite);
        }
        if (!changed) return original;
        ModelBlock generated = GENERATOR.makeItemModel(atlas,
            new ModelBlock(null, Collections.<BlockPart>emptyList(), textures, false, false, ItemCameraTransforms.DEFAULT));
        if (generated == null) return original;
        List<BakedQuad> quads = new ArrayList<BakedQuad>();
        for (BlockPart part : generated.getElements()) {
            for (Map.Entry<EnumFacing, BlockPartFace> face : part.mapFaces.entrySet()) {
                TextureAtlasSprite sprite = atlas.getAtlasSprite(new ResourceLocation(generated.resolveTextureName(face.getValue().texture)).toString());
                quads.add(BAKERY.makeBakedQuad(part.positionFrom, part.positionTo, face.getValue(), sprite, face.getKey(), ModelRotation.X0_Y0,
                    part.partRotation, false, part.shade));
            }
        }
        List<List<BakedQuad>> faces = new ArrayList<List<BakedQuad>>();
        for (int i = 0; i < EnumFacing.values().length; i++) faces.add(Collections.<BakedQuad>emptyList());
        // A flat texture keeps a flat item's display (a sword's hold); over a block or a skull it takes a plain item's.
        ItemCameraTransforms transforms = original.isGui3d() || original.isBuiltInRenderer() ? flat() : original.getItemCameraTransforms();
        return new SimpleBakedModel(quads, faces, false, false, atlas.getAtlasSprite(textures.get("layer0")), transforms);
    }

    private static ItemCameraTransforms flat() {
        if (flat == null) flat = ModelBlock.deserialize(FLAT_DISPLAY).getAllTransforms();
        return flat;
    }

    /** The original model's layer textures ("minecraft:items/bow_standby"), from its item model file; none for built-in models. */
    private static String[] layers(IBakedModel original) {
        String[] layers = LAYERS.get(original);
        if (layers == null) {
            ModelResourceLocation location = modelNames.get(original);
            layers = location == null ? new String[0] : readLayers(location);
            LAYERS.put(original, layers);
        }
        return layers;
    }

    private static String[] readLayers(ModelResourceLocation location) {
        IResourceManager resources = Minecraft.getMinecraft().getResourceManager();
        Map<String, String> textures = new HashMap<String, String>();
        ResourceLocation at = new ResourceLocation(location.getResourceDomain(), "item/" + location.getResourcePath());
        for (int depth = 0; at != null && depth < 8 && !at.getResourcePath().startsWith("builtin/"); depth++) {
            ModelBlock block;
            ResourceLocation file = new ResourceLocation(at.getResourceDomain(), "models/" + at.getResourcePath() + ".json");
            try (Reader reader = new InputStreamReader(resources.getResource(file).getInputStream(), StandardCharsets.UTF_8)) {
                block = ModelBlock.deserialize(reader);
            } catch (IOException | RuntimeException missing) {
                break;
            }
            for (Map.Entry<String, String> e : block.textures.entrySet()) if (!textures.containsKey(e.getKey())) textures.put(e.getKey(), e.getValue());
            at = block.getParentLocation();
        }
        List<String> layers = new ArrayList<String>();
        for (String layer : ItemModelGenerator.LAYERS) {
            String texture = textures.get(layer);
            for (int i = 0; texture != null && texture.startsWith("#") && i < 8; i++) texture = textures.get(texture.substring(1));
            if (texture == null || texture.startsWith("#")) break;
            layers.add(new ResourceLocation(texture).toString());
        }
        return layers.toArray(new String[0]);
    }

    /** 1.8.9's NBT for the rules' nbt conditions. */
    static final CustomItemsNbt.Access NBT = new CustomItemsNbt.Access() {
        @Override public Object child(Object node, String key) {
            return node instanceof NBTTagCompound ? ((NBTTagCompound) node).getTag(key) : null;
        }

        @Override public Collection<String> keys(Object node) {
            return node instanceof NBTTagCompound ? ((NBTTagCompound) node).getKeySet() : null;
        }

        @Override public int size(Object node) {
            return node instanceof NBTTagList ? ((NBTTagList) node).tagCount() : -1;
        }

        @Override public Object element(Object node, int index) {
            return ((NBTTagList) node).get(index);
        }

        @Override public Object value(Object node) {
            if (node instanceof NBTTagString) return ((NBTTagString) node).getString();
            if (node instanceof NBTTagInt) return ((NBTTagInt) node).getInt();
            if (node instanceof NBTTagByte) return ((NBTTagByte) node).getByte();
            if (node instanceof NBTTagShort) return ((NBTTagShort) node).getShort();
            if (node instanceof NBTTagLong) return ((NBTTagLong) node).getLong();
            if (node instanceof NBTTagFloat) return ((NBTTagFloat) node).getFloat();
            if (node instanceof NBTTagDouble) return ((NBTTagDouble) node).getDouble();
            return null;
        }

        @Override public String raw(Object node) {
            return node instanceof NBTBase ? node.toString() : String.valueOf(node);
        }
    };
}
