"""Writes KillBannerStyle's constants from banners.json (download_all_assets.py's manifest).

Only the constant list is replaced; the rest of KillBannerStyle.java is hand-written. Saved configs store the Style,
Custom Banner and Custom Sound choices by the constants' order, so the existing order is kept and new skins are added
at the end.
"""
import json
import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
TARGET = REPO_ROOT / 'TheLadsCore/common/src/main/java/com/thelads/core/client/killbanner/KillBannerStyle.java'
BANNERS_JSON = Path(__file__).resolve().parent / 'banners.json'

# Measured from gameplay footage (60 fps strips, recoloured variants): not from the manifest.
STRIPS = {
    'reaver': '    REAVER("reaver", "Reaver", Type.ANIMATED_STRIP, 128.3f, 99.9f, 43.6f, -5f, 26f, 56f, true, true, false, true, true, 5, 165, 230, new int[] {197, 255, 166}, new String[] {"Base", "Red", "Black", "White"}, new int[][] {{195, 255, 152}, {249, 247, 155}, {250, 229, 132}, {104, 161, 201}})',
    'rogue': '    ROGUE("rogue", "Rogue", Type.ANIMATED_STRIP, 157.7f, 106.1f, 48.2f, -11.5f, 30f, 66f, false, true, false, true, true, 5, 232, 20, new int[] {8, 255, 166}, new String[] {"Base", "Green", "Red", "Blue"}, new int[][] {{251, 232, 157}, {76, 224, 195}, {28, 203, 166}, {157, 204, 213}})',
}
TYPES = {'DEFAULT': 'COMPOSITE', 'BANNERSWAP': 'BANNER_SWAP', 'PHASEGUARD': 'PHASEGUARD'}


def constant(bid, b):
    if bid in STRIPS:
        return STRIPS[bid]
    name = b['name'].replace('\\', '\\\\').replace('"', '\\"')
    variants = ', '.join(f'"{v}"' for v in b['variants'])
    flags = ', '.join(str(b.get(k, d)).lower() for k, d in (('hasFrame', True), ('hasRing', False), ('hasEmblem', True), ('hasPip', True)))
    radius = float(b['radius'])
    # markSize 38 art pixels: the strips' ~28 cell pixels at KillBannerStyle.ART_SCALE.
    return (f'    {bid.upper().replace("-", "_")}("{bid}", "{name}", Type.{TYPES[b["type"].upper()]}, 0f, 0f, {radius:.1f}f, '
            f'{float(b["headshotY"]):.1f}f, 38f, {radius + 12:.1f}f, false, {flags}, {int(b.get("soundCount", 5))}, 0, 0, null, '
            f'new String[] {{{variants}}}, null)')


java = TARGET.read_bytes().decode('utf-8')
banners = json.loads(BANNERS_JSON.read_text(encoding='utf-8'))
head, rest = java.split('public enum KillBannerStyle {', 1)
body, tail = rest.split('\n    public enum Type {', 1)
existing = re.findall(r'^    [A-Z0-9_]+\("([^"]+)"', body, re.M)
order = existing + sorted(set(banners) - set(existing))
nl = '\r\n' if '\r\n' in java else '\n'
constants = (',' + nl).join(constant(bid, banners[bid]) for bid in order if bid in banners or bid in STRIPS) + ';' + nl
TARGET.write_text(head + 'public enum KillBannerStyle {' + nl + constants + nl + '    public enum Type {' + tail, encoding='utf-8', newline='')
print(f'Wrote {len(order)} styles to {TARGET}')
