"""Writes the data the Kingdom Archives skins' drawn animation needs (KillBannerPlayer.layers):
  killbanner/accent.properties  each skin's accent colour per variant (KillBannerStyle.accent), the colour its burst,
                                pip flares, glint and spray glow in
  killbanner/glow.png           the soft white dot those are drawn with, tinted at run time

The accent is the saturated colour of the skin's pip in that variant, else of its emblem, frame or ring, else white;
Banner Swap skins take theirs from the five-kill art. Run after download_all_assets.py and dedupe_assets.py.
"""
import colorsys
import json
from pathlib import Path

import numpy as np
from PIL import Image

ASSETS = Path(__file__).resolve().parents[2] / 'TheLadsCore/common/src/main/resources/assets/theladscore/killbanner'
BANNERS = json.loads((Path(__file__).resolve().parent / 'banners.json').read_text(encoding='utf-8'))
STRIPS = {'reaver', 'rogue'}
SHARED = dict(line.split('=', 1) for line in (ASSETS / 'shared.properties').read_text(encoding='utf-8').splitlines()
              if '=' in line and not line.startswith('#'))


def art(skin, name):
    path = ASSETS / SHARED.get(f'{skin}/{name}', f'{skin}/{name}')
    return np.asarray(Image.open(path).convert('RGBA')).reshape(-1, 4).astype(float) if path.exists() else None


def accent(pixels):
    """The mean colour of the opaque saturated pixels, at full brightness; None when there are too few."""
    if pixels is None:
        return None
    rgb, a = pixels[:, :3] / 255, pixels[:, 3] / 255
    mx, mn = rgb.max(axis=1), rgb.min(axis=1)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    w = a * (sat > .35) * (mx > .35) * sat
    if w.sum() < 12:
        return None
    mean = (rgb * w[:, None]).sum(axis=0) / w.sum()
    h, s, _ = colorsys.rgb_to_hsv(*mean)
    return tuple(round(c * 255) for c in colorsys.hsv_to_rgb(h, min(1, s * 1.15), 1))


def glow(size=64):
    yy, xx = np.mgrid[0:size, 0:size]
    r = np.hypot(xx - (size - 1) / 2, yy - (size - 1) / 2) / (size / 2)
    out = np.full((size, size, 4), 255, np.uint8)
    out[:, :, 3] = np.round(np.clip(np.exp(-(r / .42) ** 2) * (1 - r), 0, 1) * 255).astype(np.uint8)
    return out


def main():
    Image.fromarray(glow(), 'RGBA').save(ASSETS / 'glow.png', optimize=True)
    lines = []
    for skin in sorted(BANNERS):
        if skin in STRIPS:
            continue
        b = BANNERS[skin]
        colours = []
        if b['type'] == 'BannerSwap':
            colours.append(accent(art(skin, 'k5.png')) or (255, 255, 255))
        else:
            for v in range(len(b['variants'])):
                suffix = '' if v == 0 else f'_v{v}'
                colours.append(accent(art(skin, f'pip{suffix}.png')) or accent(art(skin, f'emblem{suffix}.png'))
                               or accent(art(skin, 'frame.png')) or accent(art(skin, 'ring.png')) or (255, 255, 255))
        lines.append(f'{skin}=' + ','.join('%02X%02X%02X' % c for c in colours))
    (ASSETS / 'accent.properties').write_text(
        '# Kill banner accent colour per variant (tools/killbanner/gen_accents.py)\n' + '\n'.join(lines) + '\n', encoding='utf-8')
    print(f'{len(lines)} skins')


if __name__ == '__main__':
    main()
