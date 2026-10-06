#!/usr/bin/env python3
"""Measures a still skin's kill banner motion from its Valorant skin preview video (the five kills at the start of
Riot's level previews), frame by frame, into the same channels as template.properties (gen_template.py), one file a skin:
  TheLadsCore/common/src/main/resources/assets/theladscore/killbanner/motion/<skin>.properties

python measure_video.py <skin> <video.mp4> [--crops cache.npy] [--check] [--out DIR] [--variant N]

How: the banner sits at (W/2, 0.794 H), 1.15 screen px a cell px at 1080p. Every kill lands its mark with the same red
strobe on the emblem (KillBannerPlayer.STROBE, from frame 11 after the kill), so a red flash at the banner centre dates each
kill. The skin's Kingdom Archives layers (emblem, ring, frame, pip) are then matched to each frame: the emblem by edge
correlation over scale and offset, the ring by its ridge in the radial profile, the frame by edge correlation, the pips by
their accent colour in the orbit band (angles give the turn, radii the slide, area the arrival glow). A banner cut short
by the next kill keeps its measured frames and continues with the longest banner's (the ace's) from there.
"""
import argparse
import json
import math
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[1] / 'TheLadsCore/common/src/main/resources/assets/theladscore/killbanner'
MOTION = ASSETS / 'motion'
BANNERS = json.loads((HERE / 'banners.json').read_text(encoding='utf-8'))
SHARED = dict(l.split('=', 1) for l in (ASSETS / 'shared.properties').read_text(encoding='utf-8').splitlines() if '=' in l and not l.startswith('#'))
ACCENT = dict(l.split('=', 1) for l in (ASSETS / 'accent.properties').read_text(encoding='utf-8').splitlines() if '=' in l and not l.startswith('#'))
def load_template():
    """template.properties (Rogue's measured motion) per kill count: introEnd, exit and each channel's frames."""
    out = {}
    for line in (ASSETS / 'template.properties').read_text(encoding='utf-8').splitlines():
        if '=' not in line or line.startswith('#') or not line.startswith('k'):
            continue
        key, value = line.split('=', 1)
        count = int(key[1])
        name = key[3:]
        b = out.setdefault(count, {})
        if name in ('introEnd', 'exit', 'mark'):
            b[name] = int(value)
        elif name == 'spray' or name == 'pip.angles':
            continue
        else:
            b[name] = [float(x) for x in value.split(',')]
    return out


TEMPLATE = load_template()
CHANNELS = ['icon.alpha', 'icon.scale', 'icon.y', 'icon.shade', 'ring.alpha', 'ring.scale', 'frame.alpha', 'frame.scale',
            'pip.alpha', 'pip.radius', 'pip.flare', 'pip.spin']
# The crop: 1080p geometry, the ring centre at (CX, CY).
CW, CH, CX, CY = 520, 320, 260, 160
CELL = 1.15          # screen px a cell px at 1080p
ART_NOMINAL = .73    # cell px an art px (KillBannerStyle.ART_SCALE), the starting point for the scale fit
MARK_FRAME = 11


def art(skin, name):
    key = f'{skin}/{name}'
    path = ASSETS / SHARED.get(key, key)
    return np.asarray(Image.open(path).convert('RGBA')) if path.exists() else None


def scaled(rgba, s):
    """RGBA art scaled by s (premultiplied resampling, so edges stay clean)."""
    im = Image.fromarray(rgba, 'RGBA')
    size = (max(1, round(im.width * s)), max(1, round(im.height * s)))
    return np.asarray(im.convert('RGBa').resize(size, Image.LANCZOS).convert('RGBA'))


def edges(gray):
    """Gradient vectors with doubled angles (magnitude, 2 x direction), two channels: correlating these matches edges by
    their direction as well as their strength, so a random background scores about 0 (edge strengths alone score high
    anywhere)."""
    gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    mag = cv2.magnitude(gx, gy)
    ang = np.arctan2(gy, gx)
    return np.dstack([mag * np.cos(2 * ang), mag * np.sin(2 * ang)]).astype(np.float32)


def gray_of(rgb):
    return cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY).astype(np.float32)


class _Lazy:
    """Per-frame images made on first use and kept for a while (the measurement walks the frames in order)."""

    def __init__(self, make, keep=300):
        self.make, self.keep, self.cache = make, keep, {}

    def __getitem__(self, f):
        v = self.cache.get(f)
        if v is None:
            v = self.make(f)
            self.cache[f] = v
            if len(self.cache) > self.keep:
                for k in sorted(self.cache)[:len(self.cache) - self.keep]:
                    del self.cache[k]
        return v


class Layer:
    """One piece of art ready to match at a scale: its edge template and mask, centred at the banner centre."""

    def __init__(self, rgba, s):
        self.rgba = scaled(rgba, s)
        a = self.rgba[:, :, 3].astype(np.float32) / 255
        g = gray_of(self.rgba[:, :, :3]) * a
        self.edge = edges(g) * a[:, :, None]
        self.mask = (cv2.dilate((a > .2).astype(np.uint8), np.ones((3, 3), np.uint8)) * 255).astype(np.uint8)
        self.h, self.w = a.shape
        self.alpha = a


def match(frame_edge, layer, cx, cy, dy_range=(0, 0), dx_range=(0, 0)):
    """Best masked edge correlation of the layer centred near (cx, cy): (score, dx, dy)."""
    x0 = int(round(cx - layer.w / 2 + dx_range[0])), int(round(cy - layer.h / 2 + dy_range[0]))
    x1 = int(round(cx - layer.w / 2 + dx_range[1])), int(round(cy - layer.h / 2 + dy_range[1]))
    xa, ya = max(0, x0[0]), max(0, x0[1])
    xb, yb = min(frame_edge.shape[1], x1[0] + layer.w), min(frame_edge.shape[0], x1[1] + layer.h)
    if xb - xa < layer.w or yb - ya < layer.h:
        return 0.0, 0, 0
    window = frame_edge[ya:yb, xa:xb]
    res = cv2.matchTemplate(window, layer.edge, cv2.TM_CCORR_NORMED, mask=layer.mask)
    res = np.clip(np.nan_to_num(res, nan=0, posinf=0, neginf=0), -1, 1)
    j, i = np.unravel_index(int(np.argmax(res)), res.shape)
    return float(res[j, i]), int(xa + i - (cx - layer.w / 2)), int(ya + j - (cy - layer.h / 2))


STROBE_DISC = np.hypot(np.mgrid[0:CH, 0:CW][1] - CX, np.mgrid[0:CH, 0:CW][0] - CY) < 20


def red_dominant(rgb):
    """Fraction of the banner centre that is strongly red: the mark's strobe tints the emblem red (a red crosshatch
    on a dark one), whatever the skin's colours."""
    r, g, b = rgb[:, :, 0].astype(int), rgb[:, :, 1].astype(int), rgb[:, :, 2].astype(int)
    return float(((r > 1.6 * np.maximum(g, b)) & (r > 100) & STROBE_DISC).sum() / STROBE_DISC.sum())


def headshot_box(rgb):
    """The HEADSHOT label's box under the banner (old previews: a coloured box, 105 x 27 px at 1080p with its top about
    82 px under the ring centre, with HEADSHOT in white): (colour as RGB ints, width, height, top) or None. Found by its
    white letters, then the box colour round them (the box often touches other colour, so its own outline misleads)."""
    y0, y1, x0, x1 = CY + 74, CY + 122, CX - 80, CX + 80
    patch = rgb[y0:y1, x0:x1]
    white = patch.min(2) > 185
    n, labels, stats, _ = cv2.connectedComponentsWithStats(white.astype(np.uint8), 8)
    cands = [stats[i] for i in range(1, n) if 2 <= stats[i][2] <= 100 and 5 <= stats[i][3] <= 16]
    if not cands:
        return None
    ys = [int(c[1]) for c in cands]
    row = max(set(ys), key=lambda y: sum(abs(y2 - y) <= 3 for y2 in ys))  # the row most letters share
    letters = [c for c in cands if abs(int(c[1]) - row) <= 3]
    if len(letters) < 5 and not any(l[2] >= 50 for l in letters):  # separate letters, or the word run together
        return None
    xs0, xs1 = min(l[0] for l in letters), max(l[0] + l[2] for l in letters)
    ys0, ys1 = min(l[1] for l in letters), max(l[1] + l[3] for l in letters)
    tw, th = xs1 - xs0, ys1 - ys0
    if not (50 <= tw <= 100 and 6 <= th <= 16 and abs((xs0 + xs1) / 2 - (CX - x0)) < 10):
        return None
    samples = [patch[ys0:ys1, max(0, xs0 - 6):max(0, xs0 - 2)].reshape(-1, 3), patch[ys0:ys1, xs1 + 2:xs1 + 6].reshape(-1, 3),
               patch[max(0, ys0 - 6):max(0, ys0 - 2), xs0:xs1].reshape(-1, 3), patch[ys1 + 2:ys1 + 6, xs0:xs1].reshape(-1, 3)]
    px = np.concatenate([x for x in samples if len(x)])
    px = px[px.min(1) < 170]  # not the letters
    if len(px) < 60:
        return None
    hsv = cv2.cvtColor(px.reshape(1, -1, 3).astype(np.uint8), cv2.COLOR_RGB2HSV)[0]
    if np.median(hsv[:, 1]) < 50:
        return None  # grey scenery behind white text is no box
    c = np.median(px, axis=0)
    if np.mean(np.abs(px - c).max(1) < 40) < .7:
        return None  # one colour all round the letters, or it is not a box
    return tuple(int(v) for v in c), 105, 27, 82


def headshot_label(frames, f0, f1, step=2):
    """The HEADSHOT label through a banner's frames f0..f1: (colour hex, first frame seen, last frame seen, width,
    height, top) or None when the kill was no headshot."""
    seen = []
    for f in range(f0, min(f1, len(frames)), step):
        box = headshot_box(frames[f])
        if box:
            seen.append((f, box))
    if len(seen) < 3:
        return None
    colours = np.array([b[0] for _, b in seen])
    c = np.median(colours, axis=0).astype(int)
    return (f'{c[0]:02X}{c[1]:02X}{c[2]:02X}', seen[0][0] - f0, seen[-1][0] - f0,
            int(np.median([b[1] for _, b in seen])), int(np.median([b[2] for _, b in seen])), int(np.median([b[3] for _, b in seen])))


def strobe_signal(frames):
    """The banner centre's red-dominant fraction per frame."""
    return np.array([red_dominant(f) for f in frames])


def flashes(sig, scale):
    """Each frame's rise over the least red around it (3 frames either way): a flash, not red that stays."""
    w = max(1, int(round(3 * scale)))
    sig = np.asarray(sig, float)
    return np.array([sig[i] - sig[max(0, i - w):i + w + 1].min() for i in range(len(sig))])


def strobe_score(hp, i, scale):
    """How surely the mark's strobe starts at frame i: the weakest of its three strong flashes (KillBannerPlayer.STROBE
    peaks 5, 11 and 17 frames after the mark lands; the first, as it lands, is often faint since the emblem is still
    small), plus half the first flash to settle a tie between neighbouring starts. 0 when the video ends first."""
    least = 1e9
    for d in (5.5, 11.5, 17.5):
        a, b = i + int(round((d - 1.5) * scale)), i + int(round((d + 1.5) * scale))
        if b >= len(hp):
            return 0.0
        least = min(least, hp[a:b + 1].max())
    first = hp[i]
    return float(least + .5 * min(first, least))


def find_kills(strobe, fps, least=.05):
    """Kill frames (where each mark's strobe starts, minus MARK_FRAME), at the video's frame rate."""
    scale = fps / 60
    hp = flashes(strobe, scale)
    score = np.array([strobe_score(hp, i, scale) for i in range(len(hp))])
    thresh = least  # a red weapon pulsing on show scores a little too; the emblem check after sorts those out
    kills, i, gap = [], 0, int(26 * scale)
    while i < len(score):
        if score[i] > thresh:
            start = i + int(np.argmax(score[i:i + gap]))
            kills.append(max(0, int(round(start - MARK_FRAME * scale))))
            i = start + gap  # the strobe spans 20 frames
        else:
            i += 1
    return kills


def hsv_accent_mask(rgb, accent):
    """Pixels in the accent colour (the pips and their glow); white accents by brightness."""
    hsv = cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV)
    ar, ag, ab = (accent >> 16) & 255, (accent >> 8) & 255, accent & 255
    ahsv = cv2.cvtColor(np.array([[[ar, ag, ab]]], np.uint8), cv2.COLOR_RGB2HSV)[0, 0]
    h, s, v = hsv[:, :, 0].astype(int), hsv[:, :, 1].astype(int), hsv[:, :, 2].astype(int)
    if ahsv[1] < 60:  # white or grey accent
        return (v > 190) & (s < 70)
    dh = np.abs(h - int(ahsv[0]))
    dh = np.minimum(dh, 180 - dh)
    return (dh < 15) & (s > 45) & (v > 100)


def pip_layout(count):
    """The adapters' pip angles, as seen on screen (degrees clockwise from the top)."""
    return sorted(((-(360.0 / count * (i + 1) + (90 if count == 2 else 0))) % 360) for i in range(count))


class Measurer:
    def __init__(self, skin, variant, frames, fps):
        self.skin, self.variant, self.frames, self.fps = skin, variant, frames, fps
        b = BANNERS[skin]
        self.pip_orbit_art = float(b['radius'])
        suffix = '' if variant == 0 else f'_v{variant}'
        self.emblem_art = art(skin, f'emblem{suffix}.png') if b['hasEmblem'] else None
        # A Banner Swap skin has a picture a kill count instead of an emblem: the count in play picks it (count()).
        self.swap_art = [art(skin, f'k{n}.png') for n in range(1, 6)] if b['type'] == 'BannerSwap' else None
        self.count = 1
        if self.swap_art is not None:
            self.emblem_art = self.swap_art[0]
        self.pip_art = art(skin, f'pip{suffix}.png') if b['hasPip'] else None
        self.ring_art = art(skin, 'ring.png') if b['hasRing'] else None
        self.frame_art = art(skin, 'frame.png') if b['hasFrame'] else None
        colours = ACCENT.get(skin, 'FFFFFF').split(',')
        self.accent = int(colours[min(variant, len(colours) - 1)], 16)
        self.gray = _Lazy(lambda f: gray_of(frames[f]))
        self.edge = _Lazy(lambda f: edges(self.gray[f]))
        self.ring_r_art = None
        if self.ring_art is not None:
            a = self.ring_art[:, :, 3] > 40
            bright = a & (self.ring_art[:, :, :3].min(2) > 150)
            h, w = a.shape
            yy, xx = np.mgrid[0:h, 0:w]
            d = np.hypot(xx - (w - 1) / 2, yy - (h - 1) / 2)
            self.ring_r_art = float(np.median(d[bright])) if bright.any() else (float(d[a].max()) if a.any() else None)

    def use(self, count):
        """The kill count whose banner is being measured (a Banner Swap skin's emblem is that count's picture)."""
        self.count = max(1, min(5, count))
        if self.swap_art is not None:
            self.emblem_art = self.swap_art[self.count - 1]

    def colour_mask(self, rgb):
        """Pixels the colour of the pips: as located in the video when that worked, else the accent of the art."""
        if self.pip_hsv is None:
            return hsv_accent_mask(rgb, self.accent)
        h0, s0, v0 = self.pip_hsv
        hsv = cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV)
        h, sat, v = hsv[:, :, 0].astype(int), hsv[:, :, 1].astype(int), hsv[:, :, 2].astype(int)
        if s0 < 60:  # white or pale pips: by brightness
            return (v > max(150, .7 * v0)) & (sat < max(80, s0 + 40))
        dh = np.abs(h - int(h0))
        dh = np.minimum(dh, 180 - dh)
        return (dh < 14) & (sat > max(60, .45 * s0)) & (v > max(90, .5 * v0))

    # ---- scale -------------------------------------------------------------------------------------------------
    def fit_scale(self, settled_frames):
        """Screen px an art px: the emblem (and frame) matched over scales on settled frames."""
        best = (-1, ART_NOMINAL * CELL)
        for s in np.arange(.60, 1.3 if self.swap_art is not None else 1.0, .01):  # swap pictures draw at 1.25 x
            layers = [Layer(a, s) for a in (self.emblem_art, self.frame_art) if a is not None]
            score = 0
            for f in settled_frames:
                for layer in layers:
                    score += match(self.edge[f], layer, CX, CY, (-3, 3), (-3, 3))[0]
            if score > best[0]:
                best = (score, float(s))
        return best[1]

    # ---- per-frame channels ----------------------------------------------------------------------------------------
    _emblem_cache = {}

    def emblem(self, f, s, prev_scale=1.0):
        """(score, scale, dy, brightness) of the emblem in frame f, searched over scale and vertical offset."""
        if self.emblem_art is None:
            return 0, 1, 0, 1
        key = (id(self), f, round(s, 3), self.count if self.swap_art is not None else 0)
        if key in Measurer._emblem_cache:
            return Measurer._emblem_cache[key]
        result = self._emblem(f, s)
        Measurer._emblem_cache[key] = result
        return result

    def _emblem(self, f, s):
        best = (-1, 1.0, 0, 0)
        for rel in np.arange(.5, 1.16, .05):
            layer = self._emblem_layer(s * rel)
            score, dx, dy = match(self.edge[f], layer, CX, CY, (-50, 20), (-8, 8))
            if score > best[0]:
                best = (score, float(rel), dy, dx)
        score, rel, dy, dx = best
        for rel2 in np.arange(rel - .04, rel + .041, .01):
            layer = self._emblem_layer(s * rel2)
            sc, dx2, dy2 = match(self.edge[f], layer, CX, CY, (dy - 3, dy + 3), (dx - 2, dx + 2))
            if sc > score:
                score, rel, dy, dx = sc, float(rel2), dy2, dx2
        layer = self._emblem_layer(s * rel)
        y0, x0 = int(round(CY - layer.h / 2 + dy)), int(round(CX - layer.w / 2 + dx))
        region = self.gray[f][max(0, y0):y0 + layer.h, max(0, x0):x0 + layer.w]
        a = layer.alpha[:region.shape[0], :region.shape[1]]
        bright = float((region * a).sum() / (a.sum() + 1e-6)) if region.size else 0
        return score, rel, dy, bright

    _layers = {}

    def _emblem_layer(self, s):
        key = ('e', self.skin, self.variant, round(s, 3), self.count if self.swap_art is not None else 0)
        if key not in Measurer._layers:
            Measurer._layers[key] = Layer(self.emblem_art, s)
        return Measurer._layers[key]

    def pip_area(self, s):
        """Screen pixels a settled pip covers (its art's opaque pixels at the scale)."""
        if self.pip_art is None:
            return 1.0
        return float((self.pip_art[:, :, 3] > 128).sum() * s * s)

    def frame_layer(self, s):
        key = ('f', self.skin, round(s, 3))
        if key not in Measurer._layers:
            Measurer._layers[key] = Layer(self.frame_art, s)
        return Measurer._layers[key]

    def frame(self, f, s):
        if self.frame_art is None:
            return 0, 1
        best = (-1, 1.0)
        for rel in (.9, .95, 1.0, 1.05, 1.1, 1.15, 1.2):
            sc = match(self.edge[f], self.frame_layer(s * rel), CX, CY, (-2, 2), (-2, 2))[0]
            if sc > best[0]:
                best = (sc, rel)
        return best

    def ring(self, f, s, exclude_angles=()):
        """Ridge strength of the ring at its radius (brightness on the circle minus either side)."""
        if self.ring_r_art is None:
            return 0, 1
        g = self.gray[f]
        best = (-1e9, 1.0)
        for rel in (.9, .95, 1.0, 1.05, 1.1):
            r = self.ring_r_art * s * rel
            on = self._circle_mean(g, r, exclude_angles)
            off = .5 * (self._circle_mean(g, r - 5, exclude_angles) + self._circle_mean(g, r + 5, exclude_angles))
            ridge = on - off
            if ridge > best[0]:
                best = (ridge, rel)
        return best

    @staticmethod
    def _circle_mean(g, r, exclude_angles):
        angles = np.arange(0, 360, 2)
        if exclude_angles:
            keep = np.ones(len(angles), bool)
            for a in exclude_angles:
                d = np.abs((angles - a + 180) % 360 - 180)
                keep &= d > 14
            angles = angles[keep]
        rad = np.radians(angles)
        xs = np.clip(np.round(CX + r * np.sin(rad)).astype(int), 0, CW - 1)
        ys = np.clip(np.round(CY - r * np.cos(rad)).astype(int), 0, CH - 1)
        return float(g[ys, xs].mean())

    pip_hsv = None  # (h, s, v) medians of the pips as the video shows them, once located

    def pip_edge(self, s, angle):
        key = ('p', self.skin, self.variant, round(s, 3), int(round(angle)) % 360)
        if key not in Measurer._layers:
            layer = Layer(self.pip_art, s)
            if angle:
                im = Image.fromarray(layer.rgba, 'RGBA').rotate(-angle, resample=Image.BICUBIC, expand=True)
                layer = Layer(np.asarray(im), 1.0)
            Measurer._layers[key] = layer
        return Measurer._layers[key]

    def locate_pips(self, frames, count, s, dy=0):
        """Where this many pips sit when settled: (orbit factor, angles clockwise from the top) by matching the pip's
        art round the orbit in the given frames, and the pips' colour there. None when nothing matches."""
        if self.pip_art is None or not frames:
            return None
        period = 360.0 / count
        layout = pip_layout(count)
        best = (-1e9, None, None)
        for rel in np.arange(.75, 1.5, .025):
            orbit = self.pip_orbit_art * s * rel
            for off in np.arange(0, period, 3.0):
                total = 0
                for f in frames:
                    for theta in layout:
                        a = theta + off
                        px, py = CX + orbit * math.sin(math.radians(a)), CY + dy - orbit * math.cos(math.radians(a))
                        total += match(self.edge[f], self.pip_edge(s, a), px, py, (-3, 3), (-3, 3))[0]
                if total > best[0]:
                    best = (total, float(rel), float(off))
        score, rel, off = best
        per = score / (len(frames) * count)
        if per < .2:
            return None
        angles = [(t + off) % 360 for t in layout]
        # The pips' colour: the pixels under the pip's shape at its places.
        hs, ss, vs = [], [], []
        orbit = self.pip_orbit_art * s * rel
        for f in frames[:: max(1, len(frames) // 4)]:
            hsv = cv2.cvtColor(self.frames[f], cv2.COLOR_RGB2HSV)
            for a in angles:
                layer = self.pip_edge(s, a)
                px, py = CX + orbit * math.sin(math.radians(a)), CY + dy - orbit * math.cos(math.radians(a))
                x0, y0 = int(round(px - layer.w / 2)), int(round(py - layer.h / 2))
                sub = hsv[max(0, y0):y0 + layer.h, max(0, x0):x0 + layer.w]
                a_mask = layer.alpha[:sub.shape[0], :sub.shape[1]] > .6
                if a_mask.sum() < 4:
                    continue
                hs.extend(sub[:, :, 0][a_mask].tolist()); ss.extend(sub[:, :, 1][a_mask].tolist()); vs.extend(sub[:, :, 2][a_mask].tolist())
        if vs:
            self.pip_hsv = (float(np.median(hs)), float(np.median(ss)), float(np.median(vs)))
        return rel, angles, per

    def pips(self, f, s, count, dy=0):
        """Accent blobs in the orbit band: (blobs as (angle, radius, area, brightness)) about the centre moved by dy."""
        rgb = self.frames[f]
        mask = hsv_accent_mask(rgb, self.accent)
        yy, xx = np.mgrid[0:CH, 0:CW]
        orbit = self.pip_orbit_art * s
        d = np.hypot(xx - CX, yy - (CY + dy))
        band = mask & (d > orbit * .55) & (d < orbit * 1.7)
        n, labels, stats, centroids = cv2.connectedComponentsWithStats(band.astype(np.uint8), 8)
        blobs = []
        gray = self.gray[f]
        least = .3 * self.pip_area(s)
        for i in range(1, n):
            area = stats[i, cv2.CC_STAT_AREA]
            if area < max(6, least):
                continue
            cx, cy = centroids[i]
            ang = math.degrees(math.atan2(cx - CX, -(cy - (CY + dy)))) % 360
            rad = math.hypot(cx - CX, cy - (CY + dy))
            shape = match(self.edge[f], self.pip_edge(s, ang), cx, cy, (-4, 4), (-4, 4))[0] if self.pip_art is not None else 1.0
            if self.shape_log is not None:
                self.shape_log.append((f, round(ang), float(area), round(shape, 3)))
            if shape < self.shape_least:
                continue
            bright = float(gray[labels == i].mean())
            blobs.append((ang, rad / orbit, float(area), bright))
        return blobs

    shape_least = .45  # pip-art edge match a blob must reach: true pips score .5 to .9, a skin's effects under .4
    shape_log = None  # a list to collect every blob's (frame, angle, area, shape score) into, for calibration

    @staticmethod
    def pip_fit(blobs, count, period_hint=None):
        """(spin degrees clockwise, mean radius, mean area, mean brightness, matched) of the pip set from the blobs."""
        if not blobs:
            return None
        layout = pip_layout(count)
        period = 360.0 / count
        best = None
        for offset in np.arange(0, period, 1.0):
            matched = []
            for theta in layout:
                target = (theta + offset) % 360
                cands = [b for b in blobs if abs((b[0] - target + 180) % 360 - 180) < 10]
                if cands:
                    matched.append(max(cands, key=lambda b: b[2]))
            if best is None or len(matched) > len(best[1]) or (len(matched) == len(best[1]) and matched and
                                                              sum(b[2] for b in matched) > sum(b[2] for b in best[1])):
                best = (offset, matched)
        offset, matched = best
        if not matched:
            return None
        # Refine the offset and radius from the clean blobs (a blob merged with the skin's effects is the wrong shape).
        areas = [b[2] for b in matched]
        typical = period_hint if period_hint else float(np.median(areas))
        clean = [b for b in matched if .55 * typical <= b[2] <= 1.6 * typical] or matched
        diffs = []
        for b in clean:
            nearest = min(layout, key=lambda t: abs((b[0] - t - offset + 180) % 360 - 180))
            diffs.append((b[0] - nearest - offset + 180) % 360 - 180)
        offset = (offset + float(np.mean(diffs))) % period
        return offset, float(np.mean([b[1] for b in clean])), float(np.mean(areas)), \
            float(np.mean([b[3] for b in matched])), len(matched)


def smooth3(v):
    v = np.array(v, float)
    out = v.copy()
    for i in range(1, len(v) - 1):
        out[i] = .25 * v[i - 1] + .5 * v[i] + .25 * v[i + 1]
    return out


def unwrap(values, period):
    out, prev = [], 0.0
    for v in values:
        if v is None:
            out.append(prev)
            continue
        step = (v - prev + period / 2) % period - period / 2
        prev = prev + step
        out.append(prev)
    return out


def measure_banner(m, f0, f_end, count, s, settled):
    """Channels for one banner from its kill frame f0 to f_end (exclusive), at the video's frame rate (raw, per frame)."""
    rows = {c: [] for c in CHANNELS}
    emblem_settled_score, emblem_bg, emblem_settled_bright = settled['emblem_score'], settled['emblem_bg'], settled['emblem_bright']
    frame_settled, frame_bg = settled['frame_score'], settled['frame_bg']
    ring_settled = settled['ring']
    pip_area_settled, pip_bright_settled, orbit = settled['pip_area'], settled['pip_bright'], settled['pip_r']
    layout = pip_layout(count)
    period = 360.0 / count
    locked = None  # (spin, radius) of the last good pip fit, once the set has been seen
    raw_spin, raw_area = [], []
    scores = []
    for f in range(f0, f_end):
        score, rel, dy, bright = m.emblem(f, s)
        scores.append(score)
        alpha = float(np.clip((score - emblem_bg) / (emblem_settled_score - emblem_bg + 1e-6), 0, 1))
        rows['icon.alpha'].append(alpha)
        rows['icon.scale'].append(rel)
        rows['icon.y'].append(dy / CELL)
        rows['icon.shade'].append(float(np.clip(bright / (emblem_settled_bright + 1e-6), 0, 1)))
        fs, frel = m.frame(f, s)
        rows['frame.alpha'].append(float(np.clip((fs - frame_bg) / (frame_settled - frame_bg + 1e-6), 0, 1)))
        rows['frame.scale'].append(frel)
        blobs = m.pips(f, s, count, dy if alpha > .3 else 0)
        # Pip-sized blobs only; once the set is locked, only blobs near where it was.
        blobs = [b for b in blobs if .4 * pip_area_settled <= b[2] <= 3.0 * pip_area_settled and b[3] >= 100]
        if locked is not None:
            spin0, r0 = locked
            keep = []
            for b in blobs:
                near = any(abs((b[0] - (t + spin0) + 180) % 360 - 180) < 16 for t in layout)
                keep.append(b) if near and abs(b[1] - r0) < .16 * orbit + .05 else None
            blobs = keep
        fit = Measurer.pip_fit(blobs, count, pip_area_settled)
        if fit and fit[4] >= max(1, count - 1 if count > 2 else count):
            spin, radius, area, pbright, matched = fit
            if count == 1:
                spin = 0.0
            if locked is not None:
                spin = locked[0] + ((spin - locked[0] + period / 2) % period - period / 2)
            locked = (spin, radius)
            raw_spin.append(spin)
            raw_area.append(area)
            rows['pip.radius'].append(radius / orbit)
            rows['pip.alpha'].append(float(np.clip(pbright / (pip_bright_settled + 1e-6), 0, 1)))
            pip_angles = [(t + spin) % 360 for t in layout]
        else:
            raw_spin.append(None)
            raw_area.append(None)
            rows['pip.radius'].append(None)
            rows['pip.alpha'].append(0.0 if locked is None else None)
            pip_angles = [(t + locked[0]) % 360 for t in layout] if locked else ()
        rr, rrel = m.ring(f, s, pip_angles)
        rows['ring.alpha'].append(float(np.clip(rr / (ring_settled + 1e-6), 0, 1)) if ring_settled > 0 else 0.0)
        rows['ring.scale'].append(1.0)
    rows['pip.spin'] = raw_spin
    rows['pip.flare'] = [max(0.0, a / pip_area_settled - 1) if a else None for a in raw_area]
    rows['_score'] = scores
    return rows


def settled_reference(m, kills, spans, counts, s, fps, bg_frames):
    """What each layer measures when settled (the longest banner, half to three quarters of the way through) and with none."""
    longest = max(range(len(kills)), key=lambda i: (spans[i][1] or 0) - kills[i])
    m.use(counts[longest])
    f0 = kills[longest]
    f1 = spans[longest][1] or (kills[longest + 1] if longest + 1 < len(kills) else len(m.frames))
    span = f1 - f0
    settled_frames = list(range(f0 + int(span * .5), f0 + int(span * .78), max(1, int(span * .28 / 12))))
    emb = [m.emblem(f, s)[0] for f in settled_frames]
    emb_b = [m.emblem(f, s)[3] for f in settled_frames]
    emb_bg = [m.emblem(f, s)[0] for f in bg_frames]
    fr = [m.frame(f, s)[0] for f in settled_frames]
    fr_bg = [m.frame(f, s)[0] for f in bg_frames]
    count = counts[longest]
    located = settled_pips(m, settled_frames, count, s)
    if located:
        pip_r, pip_angles, pip_area, pip_bright = located
    else:
        pip_r, pip_angles, pip_area, pip_bright = 1.0, (), m.pip_area(s), 200.0
    ring = float(np.median([m.ring(f, s, pip_angles)[0] for f in settled_frames]))
    ring_bg = float(np.median([m.ring(f, s)[0] for f in bg_frames]))
    return dict(emblem_score=float(np.median(emb)), emblem_bg=float(np.median(emb_bg)), emblem_bright=float(np.median(emb_b)),
                frame_score=float(np.median(fr)), frame_bg=float(np.median(fr_bg)), ring=max(ring - ring_bg, 1e-3), ring_bg=ring_bg,
                pip_area=pip_area, pip_bright=pip_bright, pip_r=pip_r, settled_frames=settled_frames, longest=longest,
                pip_angles=list(pip_angles))


def presence_span(m, f0, limit, s, bg, settled):
    """(first, last) frames the emblem is there from kill f0, up to limit (exclusive)."""
    level = bg + .45 * (settled - bg)
    first = last = None
    gone = 0
    for f in range(f0, limit):
        on = m.emblem(f, s)[0] >= level
        if on:
            if first is None:
                first = f
            last = f
            gone = 0
        elif first is not None:
            gone += 1
            if gone > 6:
                break
    return first, last


def settled_pips(m, frames, count, s):
    """The pips as they sit in these (settled) frames: (orbit factor, angles clockwise from the top, area, brightness),
    the count largest pip-sized accent blobs on the orbit in each frame, agreed across the frames. None if not seen."""
    least = .35 * m.pip_area(s)
    fits = []
    for f in frames:
        blobs = [b for b in m.pips(f, s, count) if least <= b[2] <= 6 * least and .7 <= b[1] <= 1.5 and b[3] >= 100]
        fit = Measurer.pip_fit(blobs, count)
        if fit and fit[4] >= max(1, count - 1):
            fits.append(fit)
    if not fits:
        return None
    period = 360.0 / count
    offs = np.array([x[0] for x in fits])
    # The circular median of the offsets (modulo the pips' spacing).
    ref = offs[0]
    diffs = (offs - ref + period / 2) % period - period / 2
    offset = (ref + float(np.median(diffs))) % period
    angles = [(t + offset) % 360 for t in pip_layout(count)]
    return float(np.median([x[1] for x in fits])), angles, float(np.median([x[2] for x in fits])), float(np.median([x[3] for x in fits]))


def count_pips(m, f0, f1, s, orbit=None):
    """How many pips a banner has: the accent blobs on the orbit while it is settled, sized like a pip."""
    votes = []
    lo, hi = (.8 * orbit, 1.25 * orbit) if orbit else (.75, 1.5)
    least = .35 * m.pip_area(s)
    for f in range(f0 + int((f1 - f0) * .55), max(f0 + int((f1 - f0) * .55) + 1, f1), max(1, (f1 - f0) // 12)):
        blobs = [b for b in m.pips(f, s, 5) if least <= b[2] <= 8 * least and lo < b[1] < hi and b[3] >= 100]
        votes.append(min(6, len(blobs)))
    return int(np.median(votes)) if votes else 0


def to60(rows, fps):
    rows = {c: v for c, v in rows.items() if not c.startswith('_')}
    if abs(fps - 60) < 1:
        return rows
    out = {}
    for c, v in rows.items():
        v = fill_holes(v, 1.0 if c == 'pip.radius' else 0.0)
        v = np.array(v, float)
        n60 = int(round(len(v) * 60 / fps))
        src = np.arange(len(v)) * 60 / fps
        out[c] = list(np.interp(np.arange(n60), src, v))
    return out


def tidy(c, v):
    v = np.array(v, float)
    if c in ('icon.alpha', 'ring.alpha', 'frame.alpha', 'pip.alpha', 'icon.shade'):
        v = np.where(v > .9, 1, v)
    if c.endswith('.scale') or c == 'pip.radius':
        v = np.where(np.abs(v - 1) < .03, 1, v)
    return np.round(v, 3)


def fill_holes(v, default):
    """None entries take the value before them (the first, the value after; nothing at all: the default)."""
    v = list(v)
    last = next((x for x in v if x is not None), default)
    for i in range(len(v)):
        if v[i] is None:
            v[i] = last
        else:
            last = v[i]
    return v


def median5(v):
    v = np.array(v, float)
    out = v.copy()
    for i in range(len(v)):
        out[i] = np.median(v[max(0, i - 2):i + 3])
    return out


def finish(rows, cut_from=None, continuation=None):
    """Cleans the raw series into the template's channels: strobe dips and the skin's own effects held over, layers that
    are on stay on, the motion smoothed; a banner cut by the next kill carries on with a complete banner's frames."""
    n = len(rows['icon.alpha'])
    out = {}
    alpha = median5(rows['icon.alpha'])
    on = next((i for i in range(n) if alpha[i] >= .45), None)
    # The emblem is present from its first strong frame until its final fade (the last run of falling values).
    last_on = max((i for i in range(n) if alpha[i] >= .6), default=n - 1)
    if on is not None:
        for i in range(on, last_on + 1):
            alpha[i] = 1.0
    out['icon.alpha'] = alpha
    good = [i for i in range(n) if rows['icon.alpha'][i] >= .75]  # frames the match was clean (no strobe, no effect over it)
    scale = np.array(fill_holes([rows['icon.scale'][i] if i in set(good) else None for i in range(n)], 1.0), float)
    y = np.array(fill_holes([rows['icon.y'][i] if i in set(good) else None for i in range(n)], 0.0), float)
    out['icon.scale'] = median5(scale)
    out['icon.y'] = median5(y)
    shade = np.ones(n)
    for i in range(max(0, last_on - 30), n):  # the way out darkens; nothing else does
        shade[i] = rows['icon.shade'][i] if rows['icon.shade'][i] is not None else 1.0
    out['icon.shade'] = np.minimum(1, median5(shade))
    y_settled = float(np.median(out['icon.y'][min(n - 1, 30):min(n, 60)])) if n > 35 else 0.0
    moving = np.abs(out['icon.y'] - y_settled) > 3.5
    ring = np.array(rows['ring.alpha'], float)
    ring[moving] = 0
    ring[:max(0, on or 0)] = 0
    out['ring.alpha'] = median5(ring)
    out['ring.scale'] = np.ones(n)
    frame = median5(np.array(rows['frame.alpha'], float))
    frame[:max(0, on or 0)] = 0
    out['frame.alpha'] = frame
    out['frame.scale'] = median5(np.array(fill_holes([rows['frame.scale'][i] if frame[i] > .5 else None for i in range(n)], 1.0), float))
    pa = list(rows['pip.alpha'])
    first_pip = next((i for i in range(n) if pa[i] is not None and pa[i] > 0), None)
    for i in range(last_on + 1, n):  # the way out: a pip not found is a pip gone
        if pa[i] is None:
            pa[i] = 0.0
    out['pip.alpha'] = np.array(fill_holes(pa, 0.0), float)
    out['pip.radius'] = median5(np.array(fill_holes(rows['pip.radius'], 1.0), float))
    spin = np.array(fill_holes(rows['pip.spin'], 0.0), float)
    out['pip.spin'] = median5(spin)
    flare = np.array(fill_holes(rows['pip.flare'], 0.0), float)
    if first_pip is not None:
        flare[:first_pip] = 0
        flare[first_pip + 20:] = 0  # the arrival glow; what the accent colour shows later is the skin's own effects
    else:
        flare[:] = 0
    out['pip.flare'] = median5(flare)
    # Layers that are on stay on until the way out.
    exit_start = last_on + 1
    for c in ('frame.alpha', 'ring.alpha', 'pip.alpha'):
        a = out[c]
        lit = next((i for i in range(n) if a[i] >= .85), None)
        if lit is not None:
            for i in range(lit, min(n, exit_start)):
                a[i] = 1.0
        out[c] = a
    if continuation is not None and cut_from is not None:
        m = len(continuation['icon.alpha'])
        for c in CHANNELS:
            v = list(out[c][:cut_from])
            if c.startswith('pip.'):
                hold = v[-1] if v else (1.0 if c == 'pip.radius' else 0.0)
                if c == 'pip.alpha' and hold > .5:
                    hold = 1.0
                v += [hold] * max(0, m - cut_from)
            else:
                v += list(continuation[c][cut_from:m])
            out[c] = np.array(v, float)
    peak = out['pip.flare'].max()
    out['pip.flare'] = np.clip(out['pip.flare'] / peak, 0, 1) if peak > .25 else np.zeros(len(out['pip.flare']))
    out['icon.alpha'] = np.where(out['icon.alpha'] < .15, 0, out['icon.alpha'])
    # A banner after a cut-short one: the old emblem's shrunken tail in the first frames is not this banner's.
    sc = out['icon.scale']
    for j in range(1, min(10, len(sc))):
        if sc[j] >= .85 and min(sc[:j]) <= .75:
            out['icon.alpha'][:j] = 0
            out['ring.alpha'][:j] = 0
            out['pip.alpha'][:j] = 0
            break
    return {c: tidy(c, out[c]) for c in CHANNELS}


def motion_bounds(done):
    """(introEnd, exit): the last frame anything still moves before the settled hold, and the way out's length."""
    n = len(done['icon.alpha'])
    alpha = done['icon.alpha']
    present = [i for i in range(n) if alpha[i] >= .5]
    last_on = present[-1] if present else n - 1
    # The way out starts after the last frame everything is still settled (the emblem at its size and brightness, the
    # frame there): some skins shrink the emblem first, some drop the frame first.
    mid = present[len(present) // 2] if present else 0
    scale_ref = float(np.median(done['icon.scale'][max(0, mid - 10):mid + 10]))
    leave = last_on
    for i in range(last_on, max(0, last_on - 90), -1):
        if alpha[i] >= .9 and done['icon.shade'][i] >= .95 and done['frame.alpha'][i] >= .5 and abs(done['icon.scale'][i] - scale_ref) < .04:
            leave = i + 1
            break
    gone = leave
    quiet = 0
    for i in range(leave, n):
        if done['icon.alpha'][i] > .1 or done['ring.alpha'][i] > .35 or done['pip.alpha'][i] > .35:
            gone = i + 1
            quiet = 0
        else:
            quiet += 1
            if quiet >= 4:
                break
    exit_len = max(4, min(60, gone - leave + 2))
    settled = max(0, leave - 10)
    ref = {c: float(np.median(done[c][max(0, leave - 24):max(1, leave - 4)])) for c in CHANNELS}
    tol = {'pip.spin': 3.0, 'icon.y': 1.5, 'pip.radius': .03, 'icon.scale': .03, 'frame.scale': .03, 'ring.scale': .03, 'icon.shade': .08}
    intro_end, mover = 1, ''
    for i in range(1, settled):
        for c in CHANNELS:
            if abs(done[c][i] - ref[c]) > tol.get(c, .05) and abs(done[c][i - 1] - ref[c]) > tol.get(c, .05):
                intro_end, mover = i, c
    intro_end = min(intro_end + 3, leave - 1)
    motion_bounds.mover = mover
    return intro_end, exit_len, leave


def write_properties(skin, banners, path, headshot=None):
    lines = [f'# {skin}: its kill banner motion measured from its Valorant preview video (tools/killbanner/measure_video.py);',
             '# per kill count, frames 0..introEnd play and introEnd holds, then exit frames play the way out.',
             f'orbit={next(iter(banners.values()))["orbit"]:.3f}']
    if headshot:
        lines.append(f'headshot.box={headshot[0]}')  # the HEADSHOT label's box colour, as the preview showed it
    for kills in range(1, 6):
        b = banners.get(kills)
        if b is None:
            continue
        if kills == 1 or 'orbit' not in lines[-1]:
            pass
        lines.append(f'k{kills}.introEnd={b["introEnd"]}')
        lines.append(f'k{kills}.exit={b["exit"]}')
        lines.append(f'k{kills}.mark={b["mark"]}')
        if b.get('angles'):
            lines.append(f'k{kills}.pip.angles=' + ','.join(f'{a:.1f}' for a in b['angles']))
        lines.append(f'k{kills}.spray={b["spray"][0]},{b["spray"][1]}')
        for c in CHANNELS:
            lines.append(f'k{kills}.{c}=' + ','.join(f'{v:g}' for v in b['rows'][c]))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('\n'.join(lines) + '\n', encoding='utf-8')


def render(m, s, rows, count, f):
    """The banner drawn from the art with the measured channels at frame f, on a dark card (as the check sheet)."""
    card = Image.new('RGBA', (CW, CH), (40, 40, 40, 255))
    g = {c: rows[c][f] if f < len(rows[c]) else rows[c][-1] for c in CHANNELS}
    def put(rgba, scale, alpha, x, y, angle=0, shade=1.0):
        if alpha <= 0 or rgba is None:
            return
        im = Image.fromarray(scaled(rgba, scale), 'RGBA')
        if angle:
            im = im.rotate(-angle, resample=Image.BICUBIC, expand=True)
        if shade < 1 or alpha < 1:
            arr = np.asarray(im).astype(np.float32)
            arr[:, :, :3] *= shade
            arr[:, :, 3] *= alpha
            im = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA')
        card.alpha_composite(im, (round(x - im.width / 2), round(y - im.height / 2)))
    put(m.frame_art, s * g['frame.scale'], g['frame.alpha'], CX, CY)
    put(m.ring_art, s * g['ring.scale'], g['ring.alpha'], CX, CY + g['icon.y'] * CELL)
    m.use(count)
    put(m.emblem_art, s * g['icon.scale'], g['icon.alpha'], CX, CY + g['icon.y'] * CELL, shade=g['icon.shade'])
    orbit = m.pip_orbit_art * s * g['pip.radius'] * rows.get('orbit', 1.0)
    for theta in (rows.get('angles') or pip_layout(count)):
        a = theta + g['pip.spin']
        px, py = CX + orbit * math.sin(math.radians(a)), CY + g['icon.y'] * CELL - orbit * math.cos(math.radians(a))
        put(m.pip_art, s, g['pip.alpha'], px, py, angle=a)
    return card


def check_sheet(m, s, banners, kills, counts, path):
    """One sheet a kill count: the video's banner above the art drawn with the measured channels, at the same frames."""
    for i, f0 in enumerate(kills):
        count = counts[i]
        b = banners.get(count)
        if b is None or b.get('from') != i:
            continue
        n = len(b['rows']['icon.alpha'])
        intro_end, leave = b['introEnd'], b['leave']
        idx = list(range(0, min(intro_end + 1, 40), 2)) + list(range(40, intro_end + 1, 10)) + list(range(intro_end + 1, n, 3))
        tiles = []
        for f in idx:
            video_frame = f if f <= intro_end else leave + (f - intro_end - 1)
            src = f0 + int(round(video_frame * m.fps / 60))
            video = Image.fromarray(m.frames[min(src, len(m.frames) - 1)], 'RGB').convert('RGBA')
            mine = render(m, s, dict(b['rows'], orbit=b['orbit'], angles=b.get('angles')), count, f)
            pair = Image.new('RGBA', (CW, CH * 2))
            pair.paste(video, (0, 0))
            pair.paste(mine, (0, CH))
            d = ImageDraw.Draw(pair)
            d.text((4, 4), f'f{f}' + ('' if f <= intro_end else f' (video {video_frame})'), fill=(255, 255, 0, 255))
            tiles.append(pair.resize((CW * 6 // 10, CH * 2 * 6 // 10)))
        if not tiles:
            continue
        tw, th = tiles[0].size
        cols = 10
        rows_n = (len(tiles) + cols - 1) // cols
        sheet = Image.new('RGB', (cols * tw, rows_n * th), (0, 0, 0))
        for j, t in enumerate(tiles):
            sheet.paste(t, ((j % cols) * tw, (j // cols) * th))
        out = path.with_name(path.stem + f'-k{count}' + path.suffix)
        sheet.save(out)


def measure(skin, video, frames=None, fps=60.0, variant=0, check_dir=None, quiet=False):
    """Measures one skin from one video: writes motion/<skin>.properties and returns a summary dict, or None."""
    say = (lambda *a: None) if quiet else print
    loaded = frames is None
    if loaded:
        frames, fps = load_video(video)
    strobe = strobe_signal(frames)
    kills = find_kills(strobe, fps)
    say(f'{skin}: {len(frames)} frames at {fps:g} fps; strobes at {kills}')
    if not kills:
        return None
    if loaded and len(kills) < 5:  # the strobes it stopped at may not all be kills: the whole video then
        frames, fps = load_video(video, stop=False)
        kills = find_kills(strobe_signal(frames), fps)
        say(f'  the whole video: {len(frames)} frames; strobes at {kills}')
    r60 = fps / 60
    # Only the frames round the kills are needed (a whole video's grey and edge images would take gigabytes).
    lo = max(0, kills[0] - int(80 * r60))
    hi = min(len(frames), kills[-1] + int(480 * r60))
    frames = frames[lo:hi]
    kills = [k - lo for k in kills]
    m = Measurer(skin, variant, frames, fps)
    # The first banner: the first strobe the emblem follows (a long showcase flashes red elsewhere too), at the nominal scale.
    s0 = ART_NOMINAL * CELL
    probe_at = lambda k, sc: [m.emblem(f, sc)[0] for f in range(k + int(18 * r60), k + int(42 * r60), max(1, int(4 * r60)))]
    bg0 = float(np.median([m.emblem(f, s0)[0] for f in range(0, min(len(frames), 60), 6)]))
    first = next((k for k in kills if float(np.median(probe_at(k, s0))) - bg0 >= .12), None)
    if first is None:
        best = max(float(np.median(probe_at(k, s0))) for k in kills)
        say(f'  the emblem is not found after any strobe (best score {best:.3f} vs {bg0:.3f} without)')
        return None
    # The art scale from that banner's first second (every preview's first banner is up that long).
    s = m.fit_scale(list(range(first + int(18 * r60), first + int(42 * r60), max(1, int(6 * r60)))))
    bg_frames = list(range(max(0, first - 40), max(1, first - 5), 3)) or [0]
    emblem_bg = float(np.median([m.emblem(f, s)[0] for f in bg_frames]))
    probe = lambda k: probe_at(k, s)
    emblem_on = float(np.median(probe(first)))
    if emblem_on - emblem_bg < .12:
        say(f'  the emblem is not found after the first strobe (score {emblem_on:.3f} vs {emblem_bg:.3f} without)')
        return None
    level = emblem_bg + .45 * (emblem_on - emblem_bg)
    say(f'  emblem after each strobe (share of frames at least {level:.3f}): {[(k, round(float(np.mean([x >= level for x in probe(k)])), 2)) for k in kills]}')
    kept = []
    for k in kills:
        near = bool(kept) and k - kept[-1] <= 60 * r60  # right after a kill the emblem is mid-animation or tinted
        if np.mean([x >= level for x in probe(k)]) >= (.3 if near else .6):
            kept.append(k)
    kills = kept
    say(f'  art scale {s:.3f} screen px an art px ({s / CELL:.3f} cell px; nominal {ART_NOMINAL}); kills {kills}')
    if not kills:
        return None
    frame_bg = float(np.median([m.frame(f, s)[0] for f in bg_frames]))
    frame_on = float(np.median([m.frame(f, s)[0] for f in range(first + int(18 * r60), first + int(42 * r60), max(1, int(4 * r60)))]))
    last = kills[-1]
    # Each banner starts where its emblem pops (the last rise of the emblem's presence before its mark lands; a banner
    # the previous kill cut short shows the old emblem shrinking first) and ends when emblem, ring and pips are all gone.
    strobes = [k + int(MARK_FRAME * r60) for k in kills]
    pops = []
    for i, st in enumerate(strobes):
        m.use(i + 1)
        lo = max(0, st - int(70 * r60), strobes[i - 1] + int(10 * r60) if i else 0)
        hi = st - int(4 * r60)  # the pop comes before the mark; the strobe itself upsets the match
        fits = [m.emblem(f, s) for f in range(lo, hi + 1)]
        series = median5([(fit[0] - emblem_bg) / (emblem_on - emblem_bg + 1e-6) for fit in fits])
        scales = [fit[1] for fit in fits]
        frame_series = median5([(m.frame(f, s)[0] - frame_bg) / (frame_on - frame_bg + 1e-6) for f in range(lo, hi + 1)])
        drops = [j for j in range(1, len(series)) if frame_series[j - 1] >= .5 and frame_series[j] < .3 and series[j] >= .3
                 and st - int(26 * r60) <= lo + j <= st - int(6 * r60)]
        pop = None
        if drops:
            d = drops[-1]
            for j in range(d + 1, len(series)):
                if series[j] >= .5 and scales[j] >= .85 and min(scales[max(0, j - 4):j] or [1]) <= .8:
                    pop = lo + j
                    break
        if pop is None:  # from nothing: the presence rising after a real absence (a dip under the skin's effects is none)
            edges = [lo + j for j in range(4, len(series)) if series[j] >= .4 and all(x < .4 for x in series[j - 4:j])]
            pop = edges[-1] if edges else st - int(9 * r60)
        pops.append(pop)
    kills = [max(0, pop - int(2 * r60)) for pop in pops]
    marks = [st - k for st, k in zip(strobes, kills)]
    # A mark far from where the strobe says it lands means the pop was mistaken: the usual 11 frames then.
    for i in range(len(kills)):
        if not 6 * r60 <= marks[i] <= 45 * r60:
            kills[i] = max(0, strobes[i] - int(MARK_FRAME * r60))
            marks[i] = strobes[i] - kills[i]
    nexts = kills[1:] + [len(frames)]
    spans = []
    for i, f0 in enumerate(kills):
        m.use(i + 1)
        a, b = presence_span(m, f0, min(nexts[i], f0 + int(420 * r60)), s, emblem_bg, emblem_on)
        spans.append((a, b))
    ends = [min(nexts[i], (sp[1] + int(70 * r60)) if sp[1] is not None else nexts[i]) for i, sp in enumerate(spans)]
    say(f'  banners (kill, emblem from, emblem to, mark frame) {[(k, sp[0], sp[1], mk) for k, sp, mk in zip(kills, spans, marks)]}')
    headshot = None
    for i, f0 in enumerate(kills):
        label = headshot_label(frames, f0 + int(8 * r60), ends[i])
        if label:
            say(f'  HEADSHOT label on banner {i + 1}: box #{label[0]} {label[3]}x{label[4]} px, top {label[5]:+d} px, frames {label[1]}..{label[2]}')
            headshot = headshot or label
    # The pips' colour and orbit from the last (longest) banner, assumed the ace, before counting blobs anywhere.
    guess = [1, 2, 3, 4, 5][:len(kills)] if len(kills) <= 5 else [1, 2, 3, 4, 5] + [5] * (len(kills) - 5)
    pre = settled_reference(m, kills, spans, guess, s, fps, bg_frames)
    counts = [count_pips(m, kills[i], (spans[i][1] or ends[i]) - int(10 * r60), s, pre['pip_r']) for i in range(len(kills))]
    seen = counts[:]
    # Kill counts from the kills' spacing (the pips, as blobs, mislead while a skin's own effects play): a preview's kills
    # run 1 to 5 in a row; one long after the last (its banner gone, 5 seconds) starts at 1 again, as in the game.
    counts = []
    for i, k in enumerate(kills):
        counts.append(1 if i == 0 or k - kills[i - 1] > 300 * r60 else min(5, counts[-1] + 1))
    say(f'  pips per banner {counts} (blobs saw {seen})')
    if not any(1 <= c <= 5 for c in counts):
        return None
    settled = settled_reference(m, kills, spans, counts, s, fps, bg_frames)
    say(f'  pip orbit {settled["pip_r"]:.3f} x the Kingdom Archives radius, settled angles {[round(a) for a in settled["pip_angles"]]}')
    say(f'  settled: emblem {settled["emblem_score"]:.3f} (bg {settled["emblem_bg"]:.3f}), frame {settled["frame_score"]:.3f} (bg {settled["frame_bg"]:.3f}), ring ridge {settled["ring"]:.1f}, pip area {settled["pip_area"]:.0f} r {settled["pip_r"]:.3f}')
    banners = {}
    measured = {}
    # Each count's banner from the kill that showed it longest (a sixth kill's ace outlasts a fifth's the next kill cut).
    longest_of = {}
    for i, f0 in enumerate(kills):
        if 1 <= counts[i] <= 5 and spans[i][1] is not None:
            length = spans[i][1] - f0
            if counts[i] not in longest_of or length > longest_of[counts[i]][0]:
                longest_of[counts[i]] = (length, i)
    for count, (_, i) in sorted(longest_of.items()):
        f0 = kills[i]
        m.use(count)
        rows = measure_banner(m, f0, ends[i], count, s, settled)
        rows['_mark'] = int(round(marks[i] / r60))
        late = list(range(f0 + int((spans[i][1] - f0) * .6), spans[i][1] - 2, max(1, int((spans[i][1] - f0) * .4 / 8))))
        own = settled_pips(m, late, count, s)
        rows['_angles'] = own[1] if own and len(own[1]) == count else None
        measured[count] = (i, rows)
    if not measured:
        return None
    # The longest banner that is not the ace carries on the ones the next kill cut short (the ace's pips turn).
    candidates = [c for c in measured if c != 5 and len(measured[c][1]['icon.alpha']) >= 120] or list(measured)
    ref_count = max(candidates, key=lambda c: len(measured[c][1]['icon.alpha']))
    ref_done = finish(to60(measured[ref_count][1], fps))
    ref_intro, ref_exit, ref_leave = motion_bounds(ref_done)
    ref_rows = {c: list(ref_done[c][:ref_leave + ref_exit]) for c in CHANNELS}
    orbit = settled['pip_r']
    for count, (i, rows) in measured.items():
        mark = rows['_mark']
        angles = rows['_angles']
        rows60 = to60(rows, fps)
        n = len(rows60['icon.alpha'])
        cut = i + 1 < len(kills) and kills[i + 1] <= (spans[i][1] or 0) + 2
        done = finish(rows60, cut_from=n - 8 if cut and count != ref_count else None, continuation=ref_rows if cut and count != ref_count else None)
        intro_end, exit_len, leave = motion_bounds(done)
        # Pips the video never showed clearly (the skin's own effects in their colour, or none seen): Rogue's pip
        # motion from the shared template on this skin's own emblem, ring and frame motion.
        borrowed = not any(x is not None for x in rows['pip.spin']) and count in TEMPLATE
        settled_end = intro_end
        if borrowed:
            intro_end = max(intro_end, TEMPLATE[count]['introEnd'])
        # The template is the intro (0..introEnd) followed straight by the way out (leave..): the hold between is the Duration option's.
        trimmed = {}
        for c in CHANNELS:
            v = list(done[c])
            v = v[:settled_end + 1]
            while len(v) < intro_end + 1:
                v.append(v[-1] if v else 0)
            v += list(done[c][leave:leave + exit_len])
            while len(v) < intro_end + 1 + exit_len:
                v.append(v[-1] if v else 0)
            trimmed[c] = v
        done = trimmed
        if borrowed:
            t = TEMPLATE[count]
            ti, te = t['introEnd'], t['exit']
            for c in ('pip.alpha', 'pip.radius', 'pip.flare', 'pip.spin'):
                tv = t[c]
                intro = [tv[min(f, ti)] for f in range(intro_end + 1)]
                tail = tv[ti + 1:ti + 1 + te]
                way_out = list(np.interp(np.linspace(0, len(tail) - 1, exit_len), np.arange(len(tail)), tail)) if tail and exit_len else [intro[-1]] * exit_len
                done[c] = intro + way_out
            angles = None
        banners[count] = dict(introEnd=intro_end, exit=exit_len, leave=leave, mark=mark, angles=angles, spray=(26, 34) if count > 1 else (0, 0), rows=done, orbit=orbit, **{'from': i})
        say(f'  k{count}: frames {n} ({"cut by the next kill" if cut else "to the end"}), mark {mark}, introEnd {intro_end} (last mover {motion_bounds.mover}), leaves at {leave}, exit {exit_len}, spin end {done["pip.spin"][intro_end]:.0f}, pips at {[round(a) for a in angles] if angles else "the usual places"}{" (pip motion borrowed from the template)" if borrowed else ""}')
    out = MOTION / f'{skin}.properties'
    write_properties(skin, banners, out, headshot)
    say(f'  wrote {out}')
    if check_dir:
        Path(check_dir).mkdir(parents=True, exist_ok=True)
        check_sheet(m, s, banners, kills, counts, Path(check_dir) / f'check-{skin}.png')
    return dict(skin=skin, video=str(video), fps=fps, scale=s, kills=[k + lo for k in kills], counts=counts, orbit=orbit, marks=marks,
                headshot=headshot,
                emblem=settled['emblem_score'] - settled['emblem_bg'], measured=sorted(banners), spans=spans,
                intro={k: b['introEnd'] for k, b in banners.items()}, exit={k: b['exit'] for k, b in banners.items()})


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('skin')
    parser.add_argument('video')
    parser.add_argument('--crops', help='cached crops .npy (520x320 RGB at 1080p geometry) instead of decoding the video')
    parser.add_argument('--fps', type=float, default=60)
    parser.add_argument('--variant', type=int, default=0)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--out', default=str(HERE / 'out'))
    args = parser.parse_args()
    frames = list(np.load(args.crops)) if args.crops else None
    result = measure(args.skin, args.video, frames, args.fps, args.variant, args.out if args.check else None)
    if result is None:
        sys.exit('no usable kill banner found')


def load_video(path, stop=True):
    """The banner crops of a preview video (1080p geometry) and its frame rate; reading stops once five banners' worth of
    frames are in (a preview with the kills at its end is read to the end) unless {@code stop} is off."""
    import av
    frames = []
    sig, kills, enough = [], [], None
    with av.open(str(path)) as c:
        st = c.streams.video[0]
        fps = float(st.average_rate)
        W, H = st.width, st.height
        k = H / 1080
        cx, cy = W / 2, .794 * H
        hw, hh = (CW / 2) * k, (CH / 2) * k
        x0, y0, x1, y1 = round(cx - hw), round(cy - hh), round(cx + hw), round(cy + hh)
        for i, fr in enumerate(c.decode(video=0)):
            arr = fr.to_ndarray(format='rgb24')[max(0, y0):y1, max(0, x0):x1]
            if arr.shape[0] != CH or arr.shape[1] != CW:
                arr = cv2.resize(arr, (CW, CH), interpolation=cv2.INTER_AREA)
            frames.append(np.ascontiguousarray(arr))
            sig.append(red_dominant(arr))
            scale = fps / 60
            start = i - int(round(23 * scale))  # the strobe whose last flash (and its surroundings) just arrived
            if start >= 0 and (not kills or start - kills[-1] >= 26 * scale):
                hp = flashes(sig[max(0, start - 4):], scale)
                if strobe_score(hp, min(start, 4), scale) > .05:
                    kills.append(start)
                    if len(kills) == 5:
                        enough = i + int(500 * scale)
            if stop and enough is not None and i >= enough:
                break
    return frames, fps


if __name__ == '__main__':
    main()
