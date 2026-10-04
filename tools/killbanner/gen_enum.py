import json
import re

with open('TheLadsCore/common/src/main/resources/assets/theladscore/killbanner/banners.json', 'r', encoding='utf-8') as f:
    banners = json.load(f)

# Sort with default, reaver, rogue first
keys = list(banners.keys())
keys.remove('default')
keys.remove('reaver')
keys.remove('rogue')
keys.sort()
ordered = ['default', 'reaver', 'rogue'] + keys

enum_constants = []

def java_enum_name(bid):
    # e.g. "orabyonetap-watch" -> "ORA_BY_ONETAP_WATCH"
    # "rgx11zproep9" -> "RGX_11Z_PRO_EP9"
    # "champions2025" -> "CHAMPIONS_2025"
    s = bid.upper().replace('-', '_').replace('.', '_')
    # If starts with digit, prefix with B_
    if s[0].isdigit():
        s = 'B_' + s
    return s

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
        
    radius = b['radius']
    hs_x = b['headshotX']
    hs_y = b['headshotY']
    variants = b['variants']
    var_str = '{' + ', '.join(f'"{v}"' for v in variants) + '}'
    has_frame = str(b.get('hasFrame', True)).lower()
    has_ring = str(b.get('hasRing', False)).lower()
    has_emblem = str(b.get('hasEmblem', True)).lower()
    has_pip = str(b.get('hasPip', True)).lower()
    sound_count = b.get('soundCount', 5)

    if bid == 'reaver':
        line = f'    REAVER("reaver", "Reaver", Type.ANIMATED_STRIP, 128.3f, 99.9f, 43.6f, -5f, 26f, 56f, true, true, false, true, true, 5, new String[] {{"Base", "Red", "Black", "White"}})'
    elif bid == 'rogue':
        line = f'    ROGUE("rogue", "Rogue", Type.ANIMATED_STRIP, 157.7f, 106.1f, 48.2f, -11.5f, 30f, 66f, false, true, false, true, true, 5, new String[] {{"Base", "Green", "Red", "Blue"}})'
    else:
        rad_label = radius + 12.0
        line = f'    {ename}("{bid}", "{name}", Type.{btype}, 0f, 0f, {radius:.1f}f, {hs_y:.1f}f, 28f, {rad_label:.1f}f, false, {has_frame}, {has_ring}, {has_emblem}, {has_pip}, {sound_count}, new String[] {var_str})'
    enum_constants.append(line)

print("Generated", len(enum_constants), "constants.")
print("First 5:")
for c in enum_constants[:5]:
    print(c)
