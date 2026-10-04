#!/usr/bin/env python3
"""Builds the Kill Banner assets for Lads Core (developer tool; nothing here ships).

Reaver: the 60 fps flipbook sheets in source/reaver (256x176 cells, 8 a row, 11 rows a sheet).
Rogue: the transparent QuickTime exports "Rouge N Kill(s).mov" (1080p, 60 fps, with sound), passed as --rogue DIR
(the "Transparent Background" download linked from youtube.com/watch?v=Hf3G6JpvIl0).

Writes, under TheLadsCore/common/src/main/resources/assets/theladscore:
  killbanner/<style>/k<N>.lkb   one kill count's frames (format below)
  killbanner/<style>/tint.png   the icon's shading, tinted red at run time for the kill strobe
  killbanner/reaver/heart.png   the emblem's cavity behind the kill mark
  killbanner/mark.png, mark_thin.png, shadow.png, headshot.png
  sounds/killbanner/rogue-kill-<N>.ogg
and prints the geometry that KillBannerStyle holds.

.lkb, big-endian: "LKB2", u16 width, height, fps, frames, introEnd, exitFrames, u32 deflated length, then one zlib stream
of `frames` RGBA frames, each the byte-wise difference (mod 256) from the frame before (the first from transparent).
Frames [0, introEnd] play and introEnd holds; the next exitFrames frames play the way out. With exitFrames 0 two PNGs
follow (u32 length each): the settled icon and the settled banner without it, for a drawn way out.
"LKB3" is the same with `frames` signed bytes after exitFrames: each frame's icon offset (cell pixels down from its
settled place), which the overlays drawn on the icon follow.

python build_killbanner.py --refit   re-fits the icon of the shipped Rogue strips to their ring (follow_ring) without
the source videos."""
import argparse
import io
import math
import struct
import subprocess
import zlib
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'TheLadsCore/common/src/main/resources/assets/theladscore'
OUT = ASSETS / 'killbanner'
SOURCE = Path(__file__).resolve().parent / 'source'
# Rogue's exports are ~4.5x the in-game size; this puts both styles at the same cell scale (1.17x at 1080p).
ROGUE_CROP = (229, 89, 1633, 954)
ROGUE_SCALE = 0.222


def png(a):
    out = io.BytesIO()
    Image.fromarray(a, 'RGBA').save(out, 'PNG', optimize=True)
    return out.getvalue()


def reaver_frames():
    w, h = 256, 176
    kills = {}
    for kill in range(1, 6):
        cells = []
        for sheet in sorted((SOURCE / 'reaver').glob(f'reaver_kill_{kill}_*.png')):
            image = np.asarray(Image.open(sheet).convert('RGBA'))
            for row in range(11):
                for col in range(8):
                    cells.append(image[row * h:(row + 1) * h, col * w:(col + 1) * w])
        last = max(i for i, c in enumerate(cells) if c[:, :, 3].any())
        kills[kill] = cells[:last + 1]
    return kills


def rogue_frames(folder):
    import av
    kills = {}
    for kill in range(1, 6):
        path = next(Path(folder).glob(f'Rouge {kill} Kill*.mov'))
        frames = []
        with av.open(str(path)) as video:
            for frame in video.decode(video=0):
                image = Image.fromarray(frame.to_ndarray(format='rgba'), 'RGBA').crop(ROGUE_CROP)
                size = (round(image.width * ROGUE_SCALE), round(image.height * ROGUE_SCALE))
                # Premultiplied resampling: no dark fringes around the white rings.
                frames.append(np.asarray(image.convert('RGBa').resize(size, Image.LANCZOS).convert('RGBA')))
        last = max(i for i, f in enumerate(frames) if f[:, :, 3].max() > 2)
        kills[kill] = frames[:last + 1]
    return kills


def phases(frames, has_exit):
    """(introEnd, exitStart): the longest still run is the hold; without a way out, the last frame that moves."""
    still = [np.abs(frames[i].astype(np.int16) - frames[i - 1]).max() <= 2 for i in range(1, len(frames))]
    if not has_exit:
        moving = [i + 1 for i, s in enumerate(still) if not s]
        return (moving[-1] if moving else 0), len(frames)
    best, start = (0, 0), None
    for i, s in enumerate(still + [False]):
        if s:
            start = i if start is None else start
        elif start is not None:
            if i - start > best[1] - best[0]:
                best = (start, i)
            start = None
    return best[0], best[1] + 1


def ring(settled):
    """Centre and radius of the white ring around the icon: a circle fitted to the widest white component."""
    a = settled[:, :, 3]
    rgb = settled[:, :, :3].astype(int)
    labels, count = ndimage.label((a > 100) & (rgb.min(axis=2) > 170))
    widest, points = 0, None
    for i in range(1, count + 1):
        ys, xs = np.nonzero(labels == i)
        if xs.max() - xs.min() > widest:
            widest, points = xs.max() - xs.min(), (xs.astype(float), ys.astype(float))
    xs, ys = points
    # Algebraic least-squares circle: x^2 + y^2 + D x + E y + F = 0.
    d, e, f = np.linalg.lstsq(np.column_stack([xs, ys, np.ones_like(xs)]), -(xs ** 2 + ys ** 2), rcond=None)[0]
    cx, cy = -d / 2, -e / 2
    return cx, cy, math.sqrt(cx * cx + cy * cy - f)


def icon_mask(settled, cx, cy, radius):
    """The icon: the biggest opaque component inside the ring, with the parts that thin gaps split off it."""
    a = settled[:, :, 3] > 40
    yy, xx = np.mgrid[0:a.shape[0], 0:a.shape[1]]
    labels, count = ndimage.label(a & ((xx - cx) ** 2 + (yy - cy) ** 2 < (radius * 0.80) ** 2))
    sizes = ndimage.sum(np.ones_like(labels), labels, range(1, count + 1))
    main = labels == int(np.argmax(sizes)) + 1
    near = ndimage.binary_dilation(main, iterations=3)
    for i in range(1, count + 1):
        part = labels == i
        if (part & near).any():
            main |= part
    return main


def tint_sprite(settled, mask):
    rgb = settled[:, :, :3].astype(float)
    shade = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
    out = np.zeros_like(settled)
    # Lifted shading: mid greys still turn red, as the game's flash does.
    out[:, :, :3] = np.clip(70 + shade * 0.75, 0, 255).astype(np.uint8)[:, :, None]
    out[:, :, 3] = np.where(mask, settled[:, :, 3], 0)
    return out


def heart_sprite(settled, mask):
    """The emblem's see-through centre, dark red behind the kill mark."""
    holes = ndimage.binary_fill_holes(mask) & ~mask & (settled[:, :, 3] < 128)
    labels, count = ndimage.label(holes)
    ys, xs = np.nonzero(mask)
    cx, cy = (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2
    # The cavity nearest the icon's centre; the emblem's other gaps stay see-through.
    best = min(range(1, count + 1), key=lambda i: np.hypot(*(np.argwhere(labels == i).mean(axis=0) - (cy, cx))))
    keep = ndimage.binary_dilation(labels == best, iterations=1)
    out = np.zeros(settled.shape, np.uint8)
    out[keep] = (58, 4, 14, 235)
    return out


def mark_sprite(size=192, stroke=0.075):
    """Kill mark: three strokes each way on the diagonals, their ends standing proud of the lattice."""
    s = size * 4
    image = Image.new('L', (s, s), 0)
    draw = ImageDraw.Draw(image)
    c, gap, reach, width = s / 2, s * 0.17, s * 0.30, s * stroke
    for k in (-1, 0, 1):
        for sign in (1, -1):
            dx, dy = math.cos(math.pi / 4), sign * math.sin(math.pi / 4)
            px, py = -dy * k * gap, dx * k * gap
            draw.line([(c + px - dx * reach, c + py - dy * reach), (c + px + dx * reach, c + py + dy * reach)],
                      fill=255, width=round(width))
    out = np.zeros((size, size, 4), np.uint8)
    out[:, :, :3] = 255
    out[:, :, 3] = np.asarray(image.resize((size, size), Image.LANCZOS))
    return out


def shadow_sprite(size=128):
    yy, xx = np.mgrid[0:size, 0:size]
    r = np.hypot(xx - (size - 1) / 2, yy - (size - 1) / 2) / (size / 2)
    out = np.zeros((size, size, 4), np.uint8)
    out[:, :, 3] = np.round(np.exp(-(r / 0.5) ** 2) * (r < 1) * 255).astype(np.uint8)
    return out


def headshot_sprite():
    font = ImageFont.truetype('C:/Windows/Fonts/bahnschrift.ttf', 64)
    font.set_variation_by_name('SemiBold')
    text, spacing = 'HEADSHOT', 15
    widths = [font.getlength(ch) for ch in text]
    glyphs = Image.new('L', (int(sum(widths) + spacing * (len(text) - 1)) + 24, 88), 0)
    draw = ImageDraw.Draw(glyphs)
    x = 12
    for ch, cw in zip(text, widths):
        draw.text((x, 6), ch, font=font, fill=255)
        x += cw + spacing
    alpha = np.asarray(glyphs).astype(float)
    glow = np.asarray(glyphs.filter(ImageFilter.GaussianBlur(5))).astype(float)
    out = np.zeros(alpha.shape + (4,), np.uint8)
    out[:, :, :3] = 255
    out[:, :, 3] = np.clip(alpha + glow * 0.35, 0, 255).astype(np.uint8)
    ys, xs = np.nonzero(out[:, :, 3] > 4)
    return out[max(0, ys.min() - 2):ys.max() + 3, max(0, xs.min() - 2):xs.max() + 3]


def glow(frame, sigma=2.0, strength=1.5):
    """Reaver's purple as the game shows it: the flipbook's pale lavender edges saturated, and the bloom around the
    purple as a blurred halo under the frame."""
    rgb = np.ascontiguousarray(frame[:, :, :3])
    hsv = np.asarray(Image.fromarray(rgb).convert('HSV')).astype(float)
    purple = (hsv[:, :, 0] > 170) & (hsv[:, :, 0] < 215) & (frame[:, :, 3] > 60)
    lavender = purple & (hsv[:, :, 1] > 25) & (hsv[:, :, 1] < 110)
    hsv[:, :, 1] = np.where(lavender, np.minimum(255, hsv[:, :, 1] * 2.2), hsv[:, :, 1])
    frame = frame.copy()
    frame[:, :, :3] = np.asarray(Image.fromarray(hsv.astype(np.uint8), 'HSV').convert('RGB'))
    accent = purple & (hsv[:, :, 1] > 25)
    if not accent.any():
        return frame
    a = np.clip(ndimage.gaussian_filter(accent.astype(float), sigma) * strength, 0, 1)[:, :, None]
    color = np.array([118, 40, 206], float) / 255
    base = frame.astype(float) / 255
    out_a = base[:, :, 3:4] + a * (1 - base[:, :, 3:4])
    out_rgb = (base[:, :, :3] * base[:, :, 3:4] + color * a * (1 - base[:, :, 3:4])) / np.maximum(out_a, 1e-6)
    return np.clip(np.dstack([out_rgb, out_a]) * 255 + 0.5, 0, 255).astype(np.uint8)


def read_strip(path):
    """A .lkb back into frames: dict(frames, intro_end, exit_frames, icon_y, layers)."""
    data = path.read_bytes()
    assert data[:4] in (b'LKB2', b'LKB3')
    w, h, fps, frames, intro_end, exit_frames = struct.unpack('>6H', data[4:16])
    pos, icon_y = 16, [0] * frames
    if data[:4] == b'LKB3':
        icon_y, pos = list(struct.unpack(f'>{frames}b', data[pos:pos + frames])), pos + frames
    packed = struct.unpack('>I', data[pos:pos + 4])[0]
    raw = zlib.decompress(data[pos + 4:pos + 4 + packed])
    out, current = [], np.zeros(h * w * 4, np.uint8)
    for i in range(frames):
        current = (current + np.frombuffer(raw, np.uint8, h * w * 4, i * h * w * 4)).astype(np.uint8)
        out.append(current.reshape(h, w, 4).copy())
    layers, pos = [], pos + 4 + packed
    while pos < len(data):
        n = struct.unpack('>I', data[pos:pos + 4])[0]
        layers.append(np.asarray(Image.open(io.BytesIO(data[pos + 4:pos + 4 + n])).convert('RGBA')))
        pos += 4 + n
    return dict(frames=out, intro_end=intro_end, exit_frames=exit_frames, icon_y=icon_y, layers=layers)


def follow_ring(frames, intro_end, cx, cy, radius):
    """Rogue's 5-kill export drops its ring in from above (frames 5-23) while the icon stays in the middle; every other
    kill count keeps the icon in its ring. Moves the icon with the ring in the intro frames (the ring it covered is
    redrawn from the opposite side of the ring, which is symmetric); returns the frames and each frame's icon offset (cell pixels down, 0 where the
    ring is settled or not yet seen)."""
    settled = frames[intro_end]
    icon = ndimage.binary_dilation(icon_mask(settled, cx, cy, radius), iterations=2)
    xs = np.nonzero(icon)[1]
    band = np.zeros(icon.shape, bool)
    band[:, xs.min() - 4:xs.max() + 5] = True  # the icon's columns, wherever it is in them
    alpha = settled[:, :, 3].astype(float)
    ring_alpha = np.where(band, 0, alpha)
    icon_alpha = np.where(icon, alpha, 0)
    yy, xx = np.mgrid[0:icon.shape[0], 0:icon.shape[1]]

    def shift(a, dy):
        out = np.zeros_like(a)
        if dy >= 0:
            out[dy:] = a[:a.shape[0] - dy]
        else:
            out[:dy] = a[-dy:]
        return out

    def fit(a, template, offsets):
        """(score, offset): where the template sits in a, by normalised correlation."""
        def score(d):
            b = shift(template, d)
            return (a * b).sum() / (math.sqrt((a * a).sum() * (b * b).sum()) + 1e-9)
        return max((score(d), d) for d in offsets)

    out, icon_y = list(frames), [0] * len(frames)
    for f in range(intro_end):
        frame = frames[f]
        a = frame[:, :, 3].astype(float)
        if not np.where(band, 0, a).any():
            continue
        score, dy = fit(np.where(band, 0, a), ring_alpha, range(-40, 9))
        if dy == 0 or score < 0.15:
            continue
        icon_y[f] = dy
        own = fit(np.where(band, a, 0), icon_alpha, range(-40, 9))[1]
        if own == dy:
            continue
        head = shift(icon, own) & band
        # The ring under the icon's old place: this frame's ring turned half a turn about the ring's centre.
        sx, sy = np.rint(2 * cx - xx).astype(int), np.rint(2 * (cy + dy) - yy).astype(int)
        inside = (sx >= 0) & (sx < xx.shape[1]) & (sy >= 0) & (sy < yy.shape[0])
        sx, sy = np.clip(sx, 0, xx.shape[1] - 1), np.clip(sy, 0, yy.shape[0] - 1)
        under = np.where((inside & ~head[sy, sx])[:, :, None], frame[sy, sx], 0)
        rest = np.where(head[:, :, None], under, frame).astype(float) / 255
        moved = shift(np.where(head[:, :, None], frame, 0).astype(np.uint8), dy - own).astype(float) / 255
        out_a = moved[:, :, 3:4] + rest[:, :, 3:4] * (1 - moved[:, :, 3:4])
        rgb = (moved[:, :, :3] * moved[:, :, 3:4] + rest[:, :, :3] * rest[:, :, 3:4] * (1 - moved[:, :, 3:4])) \
            / np.maximum(out_a, 1e-6)
        out[f] = np.clip(np.dstack([rgb, out_a]) * 255 + 0.5, 0, 255).astype(np.uint8)
    return out, icon_y


def write_strip(path, frames, intro_end, exit_frames, layers=(), icon_y=None):
    previous = np.zeros_like(frames[0])
    deltas = bytearray()
    for f in frames:
        deltas += (f.astype(np.int16) - previous).astype(np.uint8).tobytes()
        previous = f
    packed = zlib.compress(bytes(deltas), 9)
    h, w = frames[0].shape[:2]
    data = io.BytesIO()
    moving = icon_y is not None and any(icon_y)
    data.write(b'LKB3' if moving else b'LKB2')
    data.write(struct.pack('>6H', w, h, 60, len(frames), intro_end, exit_frames))
    if moving:
        data.write(struct.pack(f'>{len(frames)}b', *icon_y))
    data.write(struct.pack('>I', len(packed)))
    data.write(packed)
    for layer in layers:
        blob = png(layer)
        data.write(struct.pack('>I', len(blob)))
        data.write(blob)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data.getvalue())
    return data.tell()


def save(path, a):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(a, 'RGBA').save(path, 'PNG', optimize=True)


def build_style(name, kills, has_exit):
    if name == 'reaver':
        kills = {k: [glow(f) for f in frames] for k, frames in kills.items()}
    settled = kills[1][phases(kills[1], has_exit)[0]]
    cx, cy, radius = ring(settled)
    mask = icon_mask(settled, cx, cy, radius)
    save(OUT / name / 'tint.png', tint_sprite(settled, mask))
    if name == 'reaver':
        save(OUT / name / 'heart.png', heart_sprite(settled, mask))
    ys, xs = np.nonzero(mask)
    print(f'{name}: cell {settled.shape[1]}x{settled.shape[0]}, ring ({cx:.1f}, {cy:.1f}) r {radius:.1f}, '
          f'icon box x {xs.min()}-{xs.max()} y {ys.min()}-{ys.max()}')
    total = 0
    for kill, frames in kills.items():
        intro_end, exit_start = phases(frames, has_exit)
        layers, icon_y = (), None
        if has_exit:
            # The hold is the settled frame held, so the frames between it and the way out are not stored.
            frames = frames[:intro_end + 1] + frames[exit_start:]
            exit_frames = len(frames) - intro_end - 1
            frames, icon_y = follow_ring(frames, intro_end, cx, cy, radius)
        else:
            done = frames[intro_end]
            m = icon_mask(done, cx, cy, radius)
            icon_layer, rest = done.copy(), done.copy()
            icon_layer[~m, 3] = 0
            rest[m, 3] = 0
            layers = (icon_layer, rest)
            frames, exit_frames = frames[:intro_end + 1], 0
        size = write_strip(OUT / name / f'k{kill}.lkb', frames, intro_end, exit_frames, layers, icon_y)
        total += size
        print(f'  k{kill}: {len(frames)} frames, hold at {intro_end}, {exit_frames} exit frames, {size // 1024} KB')
    print(f'  {name}: {total // 1024} KB')


def rogue_sounds(folder):
    import imageio_ffmpeg
    for kill in range(1, 6):
        source = next(Path(folder).glob(f'Rouge {kill} Kill*.mov'))
        target = ASSETS / 'sounds/killbanner' / f'rogue-kill-{kill}.ogg'
        # Mono for Minecraft's sound engine; the export's sound starts with its first frame, as the banner does.
        subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-y', '-loglevel', 'error', '-i', str(source), '-map', '0:a',
                        '-ac', '1', '-ar', '48000', '-c:a', 'libvorbis', '-q:a', '5', str(target)], check=True)
        print('  sound', target.name, target.stat().st_size // 1024, 'KB')


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--rogue', help='folder with "Rouge N Kill(s).mov"; without it Rogue is left as built')
    parser.add_argument('--refit', action='store_true', help='only re-fit the shipped Rogue strips (follow_ring)')
    args = parser.parse_args()
    if args.refit:
        strips = {k: read_strip(OUT / 'rogue' / f'k{k}.lkb') for k in range(1, 6)}
        cx, cy, radius = ring(strips[1]['frames'][strips[1]['intro_end']])
        for kill, strip in strips.items():
            frames, icon_y = follow_ring(strip['frames'], strip['intro_end'], cx, cy, radius)
            if any(icon_y) and icon_y != strip['icon_y']:
                write_strip(OUT / 'rogue' / f'k{kill}.lkb', frames, strip['intro_end'], strip['exit_frames'], (), icon_y)
            print(f'  rogue k{kill}: icon offsets {[(f, d) for f, d in enumerate(icon_y) if d]}')
        return
    save(OUT / 'mark.png', mark_sprite())
    # The game strokes the mark at a fixed width while it shrinks in, so it arrives thin-lined.
    save(OUT / 'mark_thin.png', mark_sprite(stroke=0.03))
    save(OUT / 'shadow.png', shadow_sprite())
    save(OUT / 'headshot.png', headshot_sprite())
    build_style('reaver', reaver_frames(), has_exit=False)
    if args.rogue:
        build_style('rogue', rogue_frames(args.rogue), has_exit=True)
        rogue_sounds(args.rogue)


if __name__ == '__main__':
    main()
