import json
from pathlib import Path

REPO_ROOT = Path(r'C:\Users\Arash\Desktop\The Lads Client Dev\w170k')
COMMON_JAVA = REPO_ROOT / 'TheLadsCore/common/src/main/java/com/thelads/core/client/killbanner'
BANNERS_JSON = REPO_ROOT / 'TheLadsCore/common/src/main/resources/assets/theladscore/killbanner/banners.json'

with open(BANNERS_JSON, 'r', encoding='utf-8') as f:
    banners = json.load(f)

# Sort with default, reaver, rogue first, then alphabetically
keys = list(banners.keys())
keys.remove('default')
keys.remove('reaver')
keys.remove('rogue')
keys.sort()
ordered = ['default', 'reaver', 'rogue'] + keys

def java_enum_name(bid):
    s = bid.upper().replace('-', '_').replace('.', '_')
    if s[0].isdigit():
        s = 'B_' + s
    return s

entries = []
for bid in ordered:
    b = banners[bid]
    ename = java_enum_name(bid)
    name = b['name'].replace('\\', '\\\\').replace('"', '\\"')
    btype = b['type'].upper()
    if bid == 'reaver' or bid == 'rogue':
        btype = 'ANIMATED_STRIP'
    elif btype == 'DEFAULT':
        btype = 'COMPOSITE'
    elif btype == 'BANNERSWAP':
        btype = 'BANNER_SWAP'
    elif btype == 'PHASEGUARD':
        btype = 'PHASEGUARD'

    radius = float(b['radius'])
    hs_x = float(b['headshotX'])
    hs_y = float(b['headshotY'])
    variants = b['variants']
    var_str = '{' + ', '.join(f'"{v}"' for v in variants) + '}'
    has_frame = str(b.get('hasFrame', True)).lower()
    has_ring = str(b.get('hasRing', False)).lower()
    has_emblem = str(b.get('hasEmblem', True)).lower()
    has_pip = str(b.get('hasPip', True)).lower()
    sound_count = int(b.get('soundCount', 5))
    rad_label = radius + 12.0

    if bid == 'reaver':
        line = '    REAVER("reaver", "Reaver", Type.ANIMATED_STRIP, 128.3f, 99.9f, 43.6f, -5f, 26f, 56f, true, true, false, true, true, 5, 165, 230, new int[] {197, 255, 166}, new String[] {"Base", "Red", "Black", "White"}, new int[][] {{195, 255, 152}, {249, 247, 155}, {250, 229, 132}, {104, 161, 201}})'
    elif bid == 'rogue':
        line = '    ROGUE("rogue", "Rogue", Type.ANIMATED_STRIP, 157.7f, 106.1f, 48.2f, -11.5f, 30f, 66f, false, true, false, true, true, 5, 232, 20, new int[] {8, 255, 166}, new String[] {"Base", "Green", "Red", "Blue"}, new int[][] {{251, 232, 157}, {76, 224, 195}, {28, 203, 166}, {157, 204, 213}})'
    else:
        line = f'    {ename}("{bid}", "{name}", Type.{btype}, 0f, 0f, {radius:.1f}f, {hs_y:.1f}f, 28f, {rad_label:.1f}f, false, {has_frame}, {has_ring}, {has_emblem}, {has_pip}, {sound_count}, 0, 0, null, new String[] {var_str}, null)'
    entries.append(line)

enum_body = ',\n'.join(entries) + ';'

java_code = f'''package com.thelads.core.client.killbanner;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * All Valorant kill banners (91 skins from Kingdom Archives, plus Reaver and Rogue animated 60 fps strips).
 */
public enum KillBannerStyle {{
{enum_body}

    public enum Type {{
        ANIMATED_STRIP,
        COMPOSITE,
        BANNER_SWAP,
        PHASEGUARD
    }}

    /** Frame (60 fps, from the kill) the kill mark lands and the red strobe starts. */
    public static final int MARK_FRAME = 11;
    /** One cell pixel on a 1080p screen, as Valorant draws the banner. */
    public static final float SCREEN_SCALE = 1.15f;

    private static final Map<String, KillBannerStyle> BY_ID = new HashMap<>();

    static {{
        for (KillBannerStyle style : values()) {{
            BY_ID.put(style.id.toLowerCase(Locale.ROOT), style);
        }}
        // Aliases
        BY_ID.put("base", DEFAULT);
    }}

    public final String id;
    public final String displayName;
    public final Type type;
    public final float anchorX, anchorY, ring, markY, markSize, labelY;
    public final boolean heart;
    public final boolean hasFrame, hasRing, hasEmblem, hasPip;
    public final int soundCount;
    public final int bandLow, bandHigh;
    public final int[] accent;
    public final String[] variantNames;
    public final int[][] variants;
    private final KillBannerStrip[] strips = new KillBannerStrip[5];

    KillBannerStyle(String id, String displayName, Type type, float anchorX, float anchorY, float ring, float markY, float markSize, float labelY,
                    boolean heart, boolean hasFrame, boolean hasRing, boolean hasEmblem, boolean hasPip, int soundCount,
                    int bandLow, int bandHigh, int[] accent, String[] variantNames, int[][] variants) {{
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.ring = ring;
        this.markY = markY;
        this.markSize = markSize;
        this.labelY = labelY;
        this.heart = heart;
        this.hasFrame = hasFrame;
        this.hasRing = hasRing;
        this.hasEmblem = hasEmblem;
        this.hasPip = hasPip;
        this.soundCount = soundCount;
        this.bandLow = bandLow;
        this.bandHigh = bandHigh;
        this.accent = accent;
        this.variantNames = variantNames != null ? variantNames : new String[] {{"Default"}};
        this.variants = variants;
    }}

    public static KillBannerStyle fromId(String id) {{
        if (id == null || id.isBlank()) return DEFAULT;
        KillBannerStyle style = BY_ID.get(id.toLowerCase(Locale.ROOT));
        return style != null ? style : DEFAULT;
    }}

    public boolean isAnimated() {{
        return type == Type.ANIMATED_STRIP;
    }}

    public String asset(String name) {{
        return "/assets/theladscore/killbanner/" + id + "/" + name;
    }}

    public String frameAsset() {{
        return asset("frame.png");
    }}

    public String ringAsset() {{
        return asset("ring.png");
    }}

    public String emblemAsset(int variant) {{
        int v = Math.max(0, Math.min(variantNames.length - 1, variant));
        return asset(v == 0 ? "emblem.png" : "emblem_v" + v + ".png");
    }}

    public String pipAsset(int variant) {{
        int v = Math.max(0, Math.min(variantNames.length - 1, variant));
        return asset(v == 0 ? "pip.png" : "pip_v" + v + ".png");
    }}

    public String swapAsset(int kills) {{
        int k = Math.max(1, Math.min(6, kills));
        return asset("k" + k + ".png");
    }}

    /** Frames for 1 to 5 kills (only used when isAnimated() is true). */
    public synchronized KillBannerStrip strip(int kills) {{
        if (!isAnimated()) return null;
        int k = Math.max(1, Math.min(5, kills));
        if (strips[k - 1] == null) {{
            String path = asset("k" + k + ".lkb");
            try (InputStream in = KillBannerStyle.class.getResourceAsStream(path)) {{
                if (in == null) throw new IOException(path + " is missing");
                strips[k - 1] = KillBannerStrip.read(in);
            }} catch (IOException failure) {{
                throw new IllegalStateException("Kill banner frames unavailable: " + path, failure);
            }}
        }}
        return strips[k - 1];
    }}

    /** Recolours the accent in place (used for Reaver and Rogue). */
    public void recolor(byte[] rgba, int variant) {{
        if (variants == null || accent == null) return;
        int[] target = variants[Math.max(0, Math.min(variants.length - 1, variant))];
        float sScale = target[1] / (float) accent[1], vScale = target[2] / (float) accent[2];
        float[] hsv = new float[3];
        int[] rgb = new int[3];
        for (int i = 0; i < rgba.length; i += 4) {{
            if (rgba[i + 3] == 0) continue;
            int r = rgba[i] & 255, g = rgba[i + 1] & 255, b = rgba[i + 2] & 255;
            toHsv(r, g, b, hsv);
            boolean inBand = bandLow < bandHigh ? hsv[0] >= bandLow && hsv[0] <= bandHigh : hsv[0] >= bandLow || hsv[0] <= bandHigh;
            float weight = inBand ? Math.max(0, Math.min(1, (hsv[1] - 50) / 60f)) : 0;
            if (weight == 0) continue;
            toRgb(target[0], Math.min(255, hsv[1] * sScale), Math.min(255, hsv[2] * vScale), rgb);
            rgba[i] = (byte) Math.round(r + (rgb[0] - r) * weight);
            rgba[i + 1] = (byte) Math.round(g + (rgb[1] - g) * weight);
            rgba[i + 2] = (byte) Math.round(b + (rgb[2] - b) * weight);
        }}
    }}

    static void toHsv(int r, int g, int b, float[] out) {{
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = 60f * (g - b) / d;
        else if (max == g) h = 120 + 60f * (b - r) / d;
        else h = 240 + 60f * (r - g) / d;
        if (h < 0) h += 360;
        out[0] = h * 256 / 360;
        out[1] = max == 0 ? 0 : 255f * d / max;
        out[2] = max;
    }}

    static void toRgb(float h, float s, float v, int[] out) {{
        float hue = (h % 256) * 360 / 256 / 60, sat = s / 255;
        int sector = (int) Math.floor(hue) % 6;
        float f = hue - (float) Math.floor(hue);
        float p = v * (1 - sat), q = v * (1 - sat * f), t = v * (1 - sat * (1 - f));
        float r, g, b;
        switch (sector) {{
            case 0 -> {{ r = v; g = t; b = p; }}
            case 1 -> {{ r = q; g = v; b = p; }}
            case 2 -> {{ r = p; g = v; b = t; }}
            case 3 -> {{ r = p; g = q; b = v; }}
            case 4 -> {{ r = t; g = p; b = v; }}
            default -> {{ r = v; g = p; b = q; }}
        }}
        out[0] = Math.round(r);
        out[1] = Math.round(g);
        out[2] = Math.round(b);
    }}
}}
'''

target = COMMON_JAVA / 'KillBannerStyle.java'
target.write_text(java_code, encoding='utf-8')
print(f"Wrote {target} with {len(ordered)} styles.")
