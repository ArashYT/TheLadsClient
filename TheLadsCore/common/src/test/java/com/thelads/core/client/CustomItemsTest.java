package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomItemsTest {
    private static final int SWORD = 276, BOW = 261, POTION = 373, HELMET = 310;

    /** 1.8.9-like ids: names and numbers. */
    private static final CustomItems.Ids IDS = new CustomItems.Ids() {
        @Override public int item(String token) {
            String name = token.startsWith("minecraft:") ? token.substring(10) : token;
            switch (name) {
                case "diamond_sword": case "276": return SWORD;
                case "bow": case "261": return BOW;
                case "potion": case "373": return POTION;
                case "diamond_helmet": return HELMET;
                default: return -1;
            }
        }
        @Override public int enchantment(String token) {
            String name = token.startsWith("minecraft:") ? token.substring(10) : token;
            return name.equals("sharpness") ? 16 : name.equals("fire_aspect") ? 20 : name.equals("flame") ? 50 : -1;
        }
    };

    /** NBT as maps (compounds), lists and boxed leaves. */
    private static final CustomItemsNbt.Access MAPS = new CustomItemsNbt.Access() {
        @Override public Object child(Object node, String key) { return node instanceof Map ? ((Map<?, ?>) node).get(key) : null; }
        @SuppressWarnings("unchecked")
        @Override public Collection<String> keys(Object node) { return node instanceof Map ? ((Map<String, ?>) node).keySet() : null; }
        @Override public int size(Object node) { return node instanceof List ? ((List<?>) node).size() : -1; }
        @Override public Object element(Object node, int index) { return ((List<?>) node).get(index); }
        @Override public Object value(Object node) { return node instanceof String || node instanceof Number ? node : null; }
        @Override public String raw(Object node) { return node instanceof String ? "\"" + node + "\"" : String.valueOf(node); }
    };

    private static Map<String, Object> compound(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    private static CustomItems.Rule rule(String path, String text) {
        return CustomItems.parse("minecraft:optifine/cit/" + path, text, texture -> true, IDS);
    }

    /** Every file from the same pack. */
    private static Map<String, Integer> onePack(Map<String, String> files) {
        Map<String, Integer> packs = new HashMap<>();
        for (String file : files.keySet()) packs.put(file, 0);
        return packs;
    }

    private static CustomItems.Stack stack(int item, int damage, int count) {
        CustomItems.Stack s = new CustomItems.Stack();
        s.item = item;
        s.damage = damage;
        s.maxDamage = item == SWORD ? 1561 : 0;
        s.count = count;
        s.access = MAPS;
        return s;
    }

    @Test
    void texturesResolveBesideThePropertiesLikeOptiFine() {
        CustomItems.Rule bare = rule("swords/fire.properties", "items=diamond_sword\ntexture=fire_sword");
        assertEquals("minecraft:optifine/cit/swords/fire_sword.png", bare.texture);
        assertEquals("minecraft:optifine/cit/swords/a.png", rule("swords/x.properties", "items=276\ntexture=./a.png").texture);
        assertEquals("minecraft:optifine/b.png", rule("swords/x.properties", "items=276\ntexture=~/b").texture);
        assertEquals("minecraft:textures/items/c.png", rule("swords/x.properties", "items=276\ntexture=textures/items/c.png").texture);
        assertEquals("minecraft:optifine/cit/swords/x.png", rule("swords/x.properties", "items=276").texture, "no texture: the png beside it");
        CustomItems.Rule bow = rule("bow.properties", "items=bow\ntexture.bow_pulling_0=p0\ntexture.textures/items/bow_standby.png=s");
        assertNull(bow.texture, "texture.<name> only: no default png");
        assertEquals("minecraft:optifine/cit/p0.png", bow.texture("bow_pulling_0"));
        assertEquals("minecraft:optifine/cit/s.png", bow.texture("bow_standby"));
        assertNull(bow.texture("bow_pulling_1"));
        assertEquals("minecraft:models/item/big.json", rule("x.properties", "items=276\nmodel=item/big\ntexture=t").model);
    }

    @Test
    void missingTexturesAndUnknownItemsDropTheRule() {
        assertNull(CustomItems.parse("minecraft:optifine/cit/x.properties", "items=276", t -> false, IDS), "no png at all");
        assertNull(rule("x.properties", "items=netherite_sword\ntexture=t"), "an item 1.8.9 does not have");
        assertNull(rule("x.properties", "texture=t"), "item rules need items");
        assertNull(rule("x.properties", "type=elytra\nitems=276\ntexture=t"));
        assertNull(rule("x.properties", "items=276\ntexture=t\nnbt.display.Name=regex:(broken"), "a broken condition never widens");
        assertNotNull(rule("x.properties", "type=enchantment\ntexture=glint"), "enchantments may apply to every item");
        assertNull(rule("x.properties", "type=enchantment\ntexture.diamond_sword=glint"), "an effect needs one texture");
        assertNotNull(rule("x.properties", (char) 0xFEFF + "items=276\ntexture=t"), "a UTF-8 BOM before the first key");
    }

    @Test
    void rangesReadOptiFineLists() {
        assertArrayEquals(new int[]{1, 1, 3, 5, 7, Integer.MAX_VALUE}, CustomItems.ranges("1 3-5 7-"));
        assertArrayEquals(new int[]{-25, -25, -10, 10, Integer.MIN_VALUE, 4}, CustomItems.ranges("(-25) (-10)-10 -4"));
        assertNull(CustomItems.ranges("abc"));
        assertTrue(CustomItems.in(CustomItems.ranges("0-5,9"), 9));
        assertFalse(CustomItems.in(CustomItems.ranges("0-5,9"), 6));
    }

    @Test
    void damageMaskPercentAndStackSize() {
        CustomItems.Rule fireResistance = rule("fr.properties", "items=potion\ndamage=3\ndamageMask=15\ntexture=t");
        assertTrue(fireResistance.matches(stack(POTION, 8195, 1)));
        assertTrue(fireResistance.matches(stack(POTION, 16387, 1)), "any splash too");
        assertFalse(fireResistance.matches(stack(POTION, 8194, 1)));

        CustomItems.Rule worn = rule("worn.properties", "items=diamond_sword\ndamage=50-100%\ntexture=t");
        assertFalse(worn.matches(stack(SWORD, 0, 1)));
        assertTrue(worn.matches(stack(SWORD, 1000, 1)));
        assertFalse(worn.matches(stack(POTION, 1000, 1)), "no durability: percent never matches");

        CustomItems.Rule stacked = rule("s.properties", "items=diamond_sword\nstackSize=2-\ntexture=t");
        assertFalse(stacked.matches(stack(SWORD, 0, 1)));
        assertTrue(stacked.matches(stack(SWORD, 0, 64)));
        assertFalse(rule("bad.properties", "items=276\ndamage=lots\ntexture=t").matches(stack(SWORD, 0, 1)), "unreadable list: nothing");
    }

    @Test
    void enchantmentsByNameIdAndLevelsAsMcPatcher() {
        CustomItems.Rule fire = rule("f.properties", "items=276\nenchantments=minecraft:fire_aspect flame\nenchantmentLevels=2-\ntexture=t");
        CustomItems.Stack s = stack(SWORD, 0, 1);
        assertFalse(fire.matches(s));
        s.addEnchantment(20, 1);
        assertFalse(fire.matches(s), "fire aspect I is below 2");
        s.clearEnchantments();
        s.addEnchantment(16, 5);
        s.addEnchantment(20, 2);
        assertTrue(fire.matches(s));

        CustomItems.Rule total = rule("t.properties", "items=276\nenchantmentLevels=6-\ntexture=t");
        assertTrue(total.matches(s), "no ids: levels of all enchantments add up (5 + 2)");
        assertTrue(rule("i.properties", "items=276\nenchantmentIDs=16-17\ntexture=t").matches(s), "numeric id ranges");

        CustomItems.Stack apple = stack(SWORD, 0, 1);
        apple.glint = true;
        assertTrue(rule("g.properties", "items=276\nenchantmentLevels=1\ntexture=t").matches(apple), "glint without enchantments counts as level 1");
    }

    @Test
    void nbtPathsAndValueKinds() {
        Map<String, Object> tag = compound(
            "display", compound("Name", "§6Aspect of the End", "Lore", Arrays.asList("§7Damage: +100", "§9Rare"), "color", 0xff99cc),
            "ExtraAttributes", compound("id", "ASPECT_OF_THE_END", "hot_potato_count", 10, "rarity_upgrades", (byte) 1));
        CustomItems.Stack s = stack(SWORD, 0, 1);
        s.nbt = tag;
        assertTrue(nbt("ExtraAttributes.id", "ASPECT_OF_THE_END", s));
        assertFalse(nbt("ExtraAttributes.id", "ASPECT_OF_THE_VOID", s));
        assertTrue(nbt("display.Name", "ipattern:*aspect of the*", s));
        assertFalse(nbt("display.Name", "pattern:*aspect of the*", s), "pattern is case sensitive");
        assertTrue(nbt("display.Name", "regex:§.Aspect of the (End|Void)", s));
        assertTrue(nbt("display.Name", "iregex:.*ASPECT.*", s));
        assertTrue(nbt("display.Lore.1", "§9Rare", s));
        assertTrue(nbt("display.Lore.*", "pattern:*Damage*", s));
        assertTrue(nbt("display.Lore.count", "2", s));
        assertTrue(nbt("display.color", "#ff99cc", s));
        assertTrue(nbt("ExtraAttributes.hot_potato_count", "range:5-10", s));
        assertFalse(nbt("ExtraAttributes.hot_potato_count", "range:11-", s));
        assertTrue(nbt("ExtraAttributes.rarity_upgrades", "1", s), "a byte compares as its number");
        assertTrue(nbt("ExtraAttributes.*", "ASPECT_OF_THE_END", s), "* is any key");
        assertTrue(nbt("ExtraAttributes.modifier", "exists:false", s));
        assertTrue(nbt("ExtraAttributes.id", "exists:true", s));
        assertTrue(nbt("ExtraAttributes.id", "!ASPECT_OF_THE_VOID", s));
        assertTrue(nbt("ExtraAttributes.missing", "!anything", s), "not: a missing tag is not that value");
        assertTrue(nbt("display.Name", "raw:pattern:\"*End\"", s), "raw: the tag's NBT text");
        s.nbt = null;
        assertFalse(nbt("display.Name", "pattern:*", s), "no NBT: nothing matches");
        assertTrue(nbt("display.Name", "!x", s));
    }

    private static boolean nbt(String path, String value, CustomItems.Stack s) {
        CustomItemsNbt condition = CustomItemsNbt.parse(path, value);
        assertNotNull(condition, path + "=" + value);
        return condition.matches(s.nbt, s.access);
    }

    @Test
    void globMatchesWholeText() {
        assertTrue(CustomItemsNbt.glob("Letter to *", "Letter to Herobrine"));
        assertTrue(CustomItemsNbt.glob("a?c*", "abcdef"));
        assertTrue(CustomItemsNbt.glob("*", ""));
        assertTrue(CustomItemsNbt.glob("*b*b", "abab"));
        assertFalse(CustomItemsNbt.glob("Letter to *", "A Letter to Herobrine"));
        assertFalse(CustomItemsNbt.glob("a?c", "ac"));
    }

    @Test
    void firstRuleByWeightThenFileNameWins() {
        Map<String, String> files = new HashMap<>();
        files.put("minecraft:optifine/cit/b.properties", "items=diamond_sword\ntexture=b");
        files.put("minecraft:optifine/cit/a.properties", "items=diamond_sword\ntexture=a");
        files.put("minecraft:optifine/cit/heavy.properties", "items=diamond_sword\ntexture=h\nweight=5\nnbt.display.Name=Heavy");
        files.put("minecraft:optifine/cit/cit.properties", "useGlint=false");
        files.put("minecraft:optifine/cit/armor.properties", "type=armor\nitems=diamond_helmet\ntexture.diamond_layer_1=d1");
        files.put("minecraft:optifine/cit/glint.properties", "type=enchantment\ntexture=g\nblend=multiply\nspeed=2\nlayer=1");
        CustomItems.Rules rules = CustomItems.load(onePack(files), files::get, t -> true, IDS);
        assertFalse(rules.useGlint);
        assertEquals(5, rules.count, "cit.properties is not a rule");

        CustomItems.Stack s = stack(SWORD, 0, 1);
        assertEquals("minecraft:optifine/cit/a.png", rules.item(s).texture, "equal weight: file name order");
        s.nbt = compound("display", compound("Name", "Heavy"));
        assertEquals("minecraft:optifine/cit/h.png", rules.item(s).texture, "higher weight first");
        assertNull(rules.item(stack(BOW, 0, 1)));
        assertTrue(rules.names(SWORD));
        assertFalse(rules.names(BOW));

        CustomItems.Rule armor = rules.armor(stack(HELMET, 0, 1));
        assertEquals("minecraft:optifine/cit/d1.png", armor.texture("diamond_layer_1"));
        assertNull(armor.texture("diamond_layer_2"));

        s.addEnchantment(16, 1);
        CustomItems.Rule glint = rules.effects(s)[0].rule;
        assertSame(CustomSky.Blend.MULTIPLY, glint.blend);
        assertEquals(2F, glint.speed);
        assertEquals(1, glint.layer);
        assertTrue(rules.itemTextures().contains("minecraft:optifine/cit/h.png"));
        assertFalse(rules.itemTextures().contains("minecraft:optifine/cit/g.png"), "glint textures are not item atlas sprites");
    }

    @Test
    void enchantmentEffectsOnePerLayerAveragedByLevel() {
        Map<String, String> files = new HashMap<>();
        files.put("minecraft:optifine/cit/sharp.properties", "type=enchantment\ntexture=s\nenchantments=sharpness\nlayer=1");
        files.put("minecraft:optifine/cit/sharp_heavy.properties", "type=enchantment\ntexture=sh\nenchantments=sharpness\nlayer=1\nweight=2\nitems=bow");
        files.put("minecraft:optifine/cit/fire.properties", "type=enchantment\ntexture=f\nenchantments=fire_aspect\nblend=alpha");
        files.put("minecraft:optifine/cit/locked.properties", "type=enchantment\ntexture=r\nenchantments=fire_aspect\nlayer=2\nblend=replace");
        CustomItems.Rules rules = CustomItems.load(onePack(files), files::get, t -> true, IDS);
        assertTrue(rules.useGlint);
        assertEquals(CustomItems.AVERAGE, rules.method);

        CustomItems.Stack s = stack(SWORD, 0, 1);
        assertEquals(0, rules.effects(s).length, "nothing enchanted");
        s.addEnchantment(16, 3);
        s.addEnchantment(20, 1);
        CustomItems.Effect[] effects = rules.effects(s);
        assertEquals(3, effects.length);
        assertEquals("minecraft:optifine/cit/f.png", effects[0].rule.texture, "bottom layer first");
        assertEquals("minecraft:optifine/cit/s.png", effects[1].rule.texture, "the bow-only rule is not the sword's");
        assertEquals(0.25F, effects[0].intensity, 1e-6, "fire aspect I of 4 levels");
        assertEquals(0.75F, effects[1].intensity, 1e-6, "sharpness III of 4 levels");
        assertEquals(1F, effects[2].intensity, 1e-6, "replace does not fade");

        CustomItems.Stack bow = stack(BOW, 0, 1);
        bow.addEnchantment(16, 1);
        assertEquals("minecraft:optifine/cit/sh.png", rules.effects(bow)[0].rule.texture, "an item's own rule with the any-item ones");

        files.put("minecraft:optifine/cit/cit.properties", "method=layered\ncap=1\nuseGlint=false");
        CustomItems.Rules capped = CustomItems.load(onePack(files), files::get, t -> true, IDS);
        assertFalse(capped.useGlint);
        CustomItems.Effect[] top = capped.effects(s);
        assertEquals(1, top.length, "cap keeps the topmost layer");
        assertEquals("minecraft:optifine/cit/r.png", top[0].rule.texture);
    }

    @Test
    void atEqualWeightTheHigherPackWinsThenFileName() {
        Map<String, String> files = new HashMap<>();
        files.put("minecraft:optifine/cit/a_sword.properties", "items=276\ntexture=low");
        files.put("minecraft:optifine/cit/z_sword.properties", "items=276\ntexture=high");
        files.put("minecraft:optifine/cit/heavy.properties", "items=276\ntexture=heavy\nweight=1\nstackSize=64");
        Map<String, Integer> packs = new HashMap<>();
        packs.put("minecraft:optifine/cit/a_sword.properties", 0); // the lower pack
        packs.put("minecraft:optifine/cit/z_sword.properties", 1); // applied later: higher in the Selected list
        packs.put("minecraft:optifine/cit/heavy.properties", 0);
        CustomItems.Rules rules = CustomItems.load(packs, files::get, t -> true, IDS);
        assertEquals("minecraft:optifine/cit/high.png", rules.item(stack(SWORD, 0, 1)).texture, "pack order beats file name");
        assertEquals("minecraft:optifine/cit/heavy.png", rules.item(stack(SWORD, 0, 64)).texture, "weight still beats pack order");
    }

    @Test
    void hexIntegersTakeEitherCase() {
        CustomItems.Rule upper = rule("m.properties", "items=potion\ndamage=3\ndamageMask=0X0F\ntexture=t");
        assertTrue(upper.matches(stack(POTION, 8195, 1)));
        assertTrue(rule("l.properties", "items=potion\ndamage=3\ndamageMask=0x0f\ntexture=t").matches(stack(POTION, 16387, 1)));
    }

    @Test
    void aRunawayRegexIsANoMatchAndTurnsItsRuleOff() {
        // (a+)+$ backtracks without end on Java 8 (1.8.9's runtime); newer JDKs cut it short, (.*a){12}$ still runs away on them.
        String catastrophic = "regex:(.*a){12}$";
        CustomItemsNbt condition = CustomItemsNbt.parse("display.Name", catastrophic);
        CustomItemsNbt negated = CustomItemsNbt.parse("display.Name", "!" + catastrophic);
        StringBuilder evil = new StringBuilder();
        for (int i = 0; i < 64; i++) evil.append('a');
        Map<String, Object> runaway = compound("display", compound("Name", evil + "!"));
        Map<String, Object> plain = compound("display", compound("Name", "aaaaaaaaaaaaaaaa"));
        assertTrue(condition.matches(plain, MAPS), "a fine text still matches before");
        long start = System.nanoTime();
        assertFalse(condition.matches(runaway, MAPS), "given up: no match");
        assertFalse(negated.matches(runaway, MAPS), "a runaway is no match even under !");
        assertFalse(CustomItemsNbt.parse("display.Name", "regex:(a+)+$").matches(runaway, MAPS));
        assertTrue((System.nanoTime() - start) / 1_000_000L < 2_000, "bounded, not exponential");
        assertFalse(condition.matches(plain, MAPS), "the rule stays off for this resource load");
        assertTrue(CustomItemsNbt.parse("display.Name", catastrophic).matches(plain, MAPS), "a reload (new condition) tries again");

        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 2000; i++) longName.append('a');
        Map<String, Object> tooLong = compound("display", compound("Name", longName + "b"));
        assertTrue(CustomItemsNbt.parse("display.Name", "regex:a*").matches(tooLong, MAPS), "only the first 1024 characters are read");
    }

    @Test
    void atlasOverflowLeavesOutTheLargestTexturesFirst() {
        long[] areas = {256, 64 * 64, 16 * 16, 128 * 128};
        assertArrayEquals(new boolean[]{false, false, false, false}, CustomItems.dropToFit(areas, 1L << 20), "it all fits");
        assertArrayEquals(new boolean[]{false, false, false, true}, CustomItems.dropToFit(areas, 8192), "the 128x128 goes first");
        assertArrayEquals(new boolean[]{false, true, false, true}, CustomItems.dropToFit(areas, 1024));
        assertArrayEquals(new boolean[]{true, true, true, true}, CustomItems.dropToFit(areas, 0));
        assertNull(CustomItems.dropToFit(areas, -1), "not even without them");
    }
}
