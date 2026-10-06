"""Side-by-side previews: each skin's Valorant preview banner (left) next to ours played from its shipped motion file
(right, drawn the way the client draws it: frame, ring and pips on the ring centre, the emblem moving alone), kill by
kill. Writes an MP4 (60 fps) and a contact sheet per skin.

python preview_motion.py [--only skin,skin] [--out DIR] [--workers N]
"""
import argparse
import os
import json
import math
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw

import audit_motion as am
import measure_video as mv

# The sheet's frames (KB_PICKS=9,12,...) and cell size (KB_CELL=520,320), for close looks at a moment.
PICKS = [int(x) for x in os.environ.get('KB_PICKS', '0,3,6,9,12,16,20,26,32,40,50,60,75,90,110,140,170,200').split(',') if x.strip()]
CELL_W, CELL_H = [int(x) for x in os.environ.get('KB_CELL', '260,160').split(',')]
FX_DIR = mv.ASSETS / 'fx'

HERE = Path(__file__).resolve().parent
FOLDER = Path('G:/Val skins Previews')
MOTION = mv.ASSETS / 'motion'
ART_SCALE = .76                  # KillBannerStyle.ART_SCALE: cell px an art px
S = ART_SCALE * mv.CELL          # screen px an art px at 1080p
DY = 6                           # the real banner centre sits ~6 px below 0.794 H at 1080p (centre_check.py)


def crops_unused(path, ranges):
    """Banner crops (1080p geometry, centred on the real banner centre) for every frame index in the ranges."""
    import av
    want = set()
    for a, b in ranges:
        want.update(range(max(0, a), b))
    last = max(want) if want else -1
    out = {}
    with av.open(str(path)) as c:
        st = c.streams.video[0]
        fps = float(st.average_rate)
        W, H = st.width, st.height
        k = H / 1080
        cx, cy = W / 2, .794 * H + DY * k
        hw, hh = (mv.CW / 2) * k, (mv.CH / 2) * k
        x0, y0, x1, y1 = round(cx - hw), round(cy - hh), round(cx + hw), round(cy + hh)
        for i, fr in enumerate(c.decode(video=0)):
            if i > last:
                break
            if i in want:
                arr = fr.to_ndarray(format='rgb24')[max(0, y0):y1, max(0, x0):x1]
                if arr.shape[0] != mv.CH or arr.shape[1] != mv.CW:
                    arr = cv2.resize(arr, (mv.CW, mv.CH), interpolation=cv2.INTER_AREA)
                out[i] = arr
    return out, fps


class Art:
    def __init__(self, skin):
        b = mv.BANNERS[skin]
        self.swap = b['type'] == 'BannerSwap'
        self.radius = float(b['radius'])
        self.emblem = mv.art(skin, 'emblem.png') if b['hasEmblem'] and not self.swap else None
        self.swaps = [mv.art(skin, f'k{n}.png') for n in range(1, 6)] if self.swap else None
        self.ring = mv.art(skin, 'ring.png') if b['hasRing'] else None
        self.frame = mv.art(skin, 'frame.png') if b['hasFrame'] else None
        self.pip = mv.art(skin, 'pip.png') if b['hasPip'] else None
        self.pip_up = mv.art(skin, 'pip_up.png') if b['hasPip'] else None
        colours = mv.ACCENT.get(skin, 'FFFFFF').split(',')
        c = int(colours[0], 16)
        self.accent = ((c >> 16) & 255, (c >> 8) & 255, c & 255)
        self.cache = {}

    def img(self, rgba, scale, angle=0):
        key = (id(rgba), round(scale, 3), round(angle) % 360)
        if key not in self.cache:
            im = Image.fromarray(mv.scaled(rgba, scale), 'RGBA')
            if angle:
                im = im.rotate(-angle, resample=Image.BICUBIC, expand=True)
            self.cache[key] = im
            if len(self.cache) > 4000:
                self.cache.clear()
        return self.cache[key]


def props(path):
    out = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        if '=' in line and not line.startswith('#'):
            k, v = line.split('=', 1)
            out[k.strip()] = v.strip()
    return out


FX = {}
for k, v in props(mv.ASSETS / 'fx.properties').items():
    name, key = k.rsplit('.', 1)
    FX.setdefault(name, {})[key] = v
FX_SKINS = props(mv.ASSETS / 'fx-skins.properties')
FX_ATLAS = {}
FLAME_W, FLAME_H, FLAME_Y, TIER_SIZE, LARGE_SIZE, X_W, X_H, X_OFFSET = 199, 224, -30, 256, 300, 80, 250, 100
FX_DELAY = .05   # the game's sprite timer shows a flipbook's first frame 3 frames (50 ms) after the FX event (measured on Oni's preview)


def fx_tier(skin, count):
    if count < 2:
        return None
    tier = 2 if count >= 5 else 1 if count >= 4 else 0
    names = ['baset1_fx', 'baset2_fx', 'baset3_fx']
    own = FX_SKINS.get(skin)
    if own:
        first = own.split('|')[0].strip()
        if first:
            names = [n.strip() for n in first.split(',')]
    return names[min(tier, len(names) - 1)]


def fx_cell(card, art, name, seconds, cx, cy, w, h, angle=0, flip=False):
    """One flipbook frame as the client draws it: the atlas cell stretched into a box (art px) centred at cx, cy from the
    ring centre, turned clockwise by angle, mirrored when flip, tinted the skin's colour."""
    book = FX.get(name)
    if book is None or seconds < 0:
        return
    frames = [int(x) for x in book['frames'].split(',')]
    i = int((seconds - FX_DELAY) * float(book['fps']))
    if i < 0:
        return
    if i >= len(frames) or frames[i] < 0:
        return
    if name not in FX_ATLAS:
        FX_ATLAS[name] = Image.open(FX_DIR / f'{name}.png').convert('RGBA')
    atlas = FX_ATLAS[name]
    cw, ch = [int(x) for x in book['cell'].split(',')]
    cols = int(book['cols'])
    c = frames[i]
    cell = atlas.crop(((c % cols) * cw, (c // cols) * ch, (c % cols) * cw + cw, (c // cols) * ch + ch))
    a = np.asarray(cell).astype(np.float32)
    a[:, :, :3] *= np.array(art.accent, dtype=np.float32) / 255
    cell = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA')
    cell = cell.resize((max(1, round(w * S)), max(1, round(h * S))), Image.BILINEAR)
    if flip:
        cell = cell.transpose(Image.FLIP_LEFT_RIGHT)
    if angle:
        cell = cell.rotate(-angle, resample=Image.BICUBIC, expand=True)
    put(card, cell, mv.CX + cx * S, mv.CY + cy * S)


def draw_fx(card, art, skin, count, f, mark):
    seconds = (f - mark) / 60
    if seconds < 0:
        return
    if count >= 5:
        for i in range(4):
            sx, sy = (-1 if i in (0, 3) else 1), (-1 if i < 2 else 1)
            angle = (-45, 45, 135, -135)[i]
            fx_cell(card, art, 'fb_x_sparks', seconds, sx * X_OFFSET, sy * X_OFFSET, X_W, X_H, angle)
    fx_cell(card, art, 'fb_heroflame', seconds, 0, FLAME_Y, FLAME_W, FLAME_H)
    tier = fx_tier(skin, count)
    if tier:
        fx_cell(card, art, tier, seconds, -TIER_SIZE / 2, 0, TIER_SIZE, TIER_SIZE, flip=True)
        fx_cell(card, art, tier, seconds, TIER_SIZE / 2, 0, TIER_SIZE, TIER_SIZE)
    if count >= 5:
        fx_cell(card, art, 'fb_large_sparks', seconds, 0, 0, LARGE_SIZE, LARGE_SIZE)


def channel(b, c, f):
    """A channel at banner frame f (60 fps): the intro, then it holds settled (the preview's own hold)."""
    v = b[c]
    ie = b['introEnd']
    return float(v[min(int(f), ie)])


def put(card, im, x, y, alpha=1.0, shade=1.0):
    if alpha <= .004:
        return
    if alpha < 1 or shade < 1:
        a = np.asarray(im).astype(np.float32)
        a[:, :, :3] *= shade
        a[:, :, 3] *= alpha
        im = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA')
    card.alpha_composite(im, (round(x - im.width / 2), round(y - im.height / 2)))


def draw(art, b, count, orbit, f, skin=None):
    card = Image.new('RGBA', (mv.CW, mv.CH), (38, 38, 42, 255))
    g = {c: channel(b, c, f) for c in am.CHANNELS}
    cx, cy = mv.CX, mv.CY
    if skin and 'mark' in b:
        draw_fx(card, art, skin, count, f, b['mark'])
    if art.frame is not None:
        put(card, art.img(art.frame, S * g['frame.scale']), cx, cy, g['frame.alpha'])
    if art.ring is not None:
        put(card, art.img(art.ring, S * g['ring.scale']), cx, cy, g['ring.alpha'])
    ey = cy + g['icon.y'] * mv.CELL
    if art.swap:
        put(card, art.img(art.swaps[count - 1], S * 1.25 * g['icon.scale']), cx, ey, g['icon.alpha'], g['icon.shade'])
    elif art.emblem is not None:
        put(card, art.img(art.emblem, S * g['icon.scale']), cx, ey, g['icon.alpha'], g['icon.shade'])
    if art.pip is not None and g['pip.alpha'] > 0:
        # the game template keeps pip.radius in art px outward; measured files kept a multiple of the radius
        r = art.radius * S * orbit + g['pip.radius'] * S if b.get('radius_px') else art.radius * S * orbit * g['pip.radius']
        lit = tuple(int(255 * .75 + c * .25) for c in art.accent)
        for theta in mv.pip_layout(count):
            a = theta + g['pip.spin']
            px, py = cx + r * math.sin(math.radians(a)), cy - r * math.cos(math.radians(a))
            if art.pip_up is not None and g['pip.up'] > 0:
                put(card, art.img(art.pip_up, S * g['pip.scale'], a), px, py, g['pip.alpha'] * g['pip.up'])
            if g['pip.flare'] > .004:
                hover = art.img(art.pip, S * g['pip.scale'], a)
                if art.pip_up is not None:
                    t = np.asarray(hover).astype(np.float32)
                    t[:, :, :3] *= np.array(lit, dtype=np.float32) / 255
                    hover = Image.fromarray(np.clip(t, 0, 255).astype(np.uint8), 'RGBA')
                put(card, hover, px, py, g['pip.alpha'] * (g['pip.flare'] if art.pip_up is not None else 1))
    return np.asarray(card.convert('RGB'))


def label(img, text):
    img = img.copy()
    cv2.putText(img, text, (6, 18), cv2.FONT_HERSHEY_SIMPLEX, .5, (255, 255, 0), 1, cv2.LINE_AA)
    return img


def preview(skin, out):
    summary = json.loads((HERE / 'out' / 'motion' / 'summary.json').read_text(encoding='utf-8'))
    entry = summary.get(skin)
    path = MOTION / f'{skin}.properties'
    if not path.exists():
        path = mv.ASSETS / 'template.properties'  # the game's motion, shared by every skin
    if not entry:
        return skin, 'no measurement'
    data, orbit, _ = am.load(path)
    video = FOLDER / entry['video']
    # The kills from the video's own strobes (the summary's kill frames carry a stale offset): a banner starts its
    # mark frames before its strobe; kill counts by spacing, as the measurer counts them.
    frames, fps = mv.load_video(video)
    starts = mv.find_kills(mv.strobe_signal(frames), fps)
    if len(starts) < len(entry['measured']):
        frames, fps = mv.load_video(video, stop=False)
        starts = mv.find_kills(mv.strobe_signal(frames), fps)
    r60 = fps / 60
    counts = []
    for i, k in enumerate(starts):
        counts.append(1 if i == 0 or k - starts[i - 1] > 300 * r60 else min(5, counts[-1] + 1))
    jobs = []
    for i, k in enumerate(starts):
        c = counts[i]
        if c not in data or c in [j[1] for j in jobs]:
            continue
        strobe = k + int(round(mv.MARK_FRAME * r60))
        begin = strobe - int(round(data[c].get('mark', 11) * r60))
        nxt = starts[i + 1] + int(round(mv.MARK_FRAME * r60)) - int(round(11 * r60)) if i + 1 < len(starts) else begin + int(400 * r60)
        length = max(40, min(int((nxt - begin) / r60), data[c]['introEnd'] + 50, 360))
        jobs.append((begin, c, length))
    art = Art(skin)
    out.mkdir(parents=True, exist_ok=True)
    writer = cv2.VideoWriter(str(out / f'preview-{skin}.mp4'), cv2.VideoWriter_fourcc(*'mp4v'), 60, (mv.CW * 2, mv.CH))
    sheet_rows = []
    picks_all = PICKS
    for begin, c, length in jobs:
        picks = [p for p in picks_all if p < length]
        row_v, row_o = [], []
        for f in range(length):
            vf = begin + int(round(f * r60))
            if vf < 0 or vf >= len(frames):
                break
            v = frames[vf]
            v = np.vstack([v[DY:], np.repeat(v[-1:], DY, axis=0)])  # centred on the real banner centre
            o = draw(art, data[c], c, orbit, f, skin)
            pair = np.hstack([label(v, f'{skin} k{c} f{f} video'), label(o, 'ours')])
            writer.write(cv2.cvtColor(pair, cv2.COLOR_RGB2BGR))
            if f in picks:
                row_v.append(cv2.resize(label(v, f'k{c} f{f}'), (CELL_W, CELL_H), interpolation=cv2.INTER_AREA))
                row_o.append(cv2.resize(o, (CELL_W, CELL_H), interpolation=cv2.INTER_AREA))
        if row_v:
            while len(row_v) < len(picks_all):
                row_v.append(np.zeros((CELL_H, CELL_W, 3), np.uint8))
                row_o.append(np.zeros((CELL_H, CELL_W, 3), np.uint8))
            sheet_rows.append(np.hstack(row_v))
            sheet_rows.append(np.hstack(row_o))
    writer.release()
    if sheet_rows:
        cv2.imwrite(str(out / f'sheet-{skin}.png'), cv2.cvtColor(np.vstack(sheet_rows), cv2.COLOR_RGB2BGR))
    return skin, f'{len(jobs)} banners'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', default='')
    ap.add_argument('--out', default=r'C:\Users\Arash\Desktop\The Lads Client Dev\Lads Client\artifacts\1.7.4\analysis\previews')
    ap.add_argument('--workers', type=int, default=1)
    args = ap.parse_args()
    out = Path(args.out)
    skins = [s for s in args.only.split(',') if s] or sorted(p.stem for p in MOTION.glob('*.properties'))
    if args.workers <= 1:
        for s in skins:
            print(*preview(s, out), flush=True)
        return
    with ProcessPoolExecutor(max_workers=args.workers) as pool:
        for skin, note in pool.map(preview, skins, [out] * len(skins)):
            print(skin, note, flush=True)


if __name__ == '__main__':
    main()
