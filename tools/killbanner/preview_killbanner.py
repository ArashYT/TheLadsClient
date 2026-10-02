#!/usr/bin/env python3
"""Renders the kill banner the way Lads Core draws it (KillBannerPlayer, KillBannerArt) next to gameplay footage,
to tune the overlay timing and colours. Developer tool.

python preview_killbanner.py --style reaver --video reaver.mp4 --kill-frame 55 --out sheet.png [--variant 0]"""
import argparse
import io
import math
import struct
import sys
import zlib
from pathlib import Path

import cv2
import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_killbanner import OUT  # noqa: E402

# Mirrors com.thelads.core.client.killbanner.KillBannerStyle.
STYLES = {
    'reaver': dict(anchor=(128.3, 99.9), ring=43.6, mark=11, mark_pos=(0.0, -5.0), mark_size=26.0, heart=True,
                   accent=(197, 255, 166), band=(165, 230),
                   variants=[(195, 255, 152), (249, 247, 155), (250, 229, 132), (104, 161, 201)]),
    'rogue': dict(anchor=(157.7, 106.1), ring=48.2, mark=11, mark_pos=(0.0, -11.5), mark_size=30.0, heart=False,
                  accent=(8, 255, 166), band=(232, 20),
                  variants=[(251, 232, 157), (76, 224, 195), (28, 203, 166), (157, 204, 213)]),
}
# Red strobe on the icon, per frame from the kill mark's arrival (both skins, measured from gameplay).
TINT = [0.85, 0.27, 0.01, 0.06, 0.38, 1.0, 0.79, 0.25, 0.01, 0.06, 0.40, 1.0, 0.79, 0.27, 0.02, 0.05, 0.39, 0.97, 0.66,
        0.22]
RED = np.array([226, 18, 44], float)
MARK_RED = np.array([196, 22, 38], float)
SCREEN_SCALE = 1.15  # one cell pixel on a 1080p screen, as in Valorant (Rogue ring 48.2 -> 55.3 px)


def read_strip(path):
    data = path.read_bytes()
    assert data[:4] == b'LKB2'
    w, h, fps, frames, intro_end, exit_frames, packed = struct.unpack('>6HI', data[4:20])
    raw = zlib.decompress(data[20:20 + packed])
    out, current = [], np.zeros(h * w * 4, np.uint8)
    for i in range(frames):
        current = (current + np.frombuffer(raw, np.uint8, h * w * 4, i * h * w * 4)).astype(np.uint8)
        out.append(current.reshape(h, w, 4).copy())
    layers, pos = [], 20 + packed
    while pos < len(data):
        n = struct.unpack('>I', data[pos:pos + 4])[0]
        layers.append(np.asarray(Image.open(io.BytesIO(data[pos + 4:pos + 4 + n])).convert('RGBA')))
        pos += 4 + n
    return dict(frames=out, intro_end=intro_end, exit_frames=exit_frames, layers=layers)


def recolor(frame, style, variant):
    """Accent pixels (the style's hue band, saturated) take the variant's hue; their saturation and value scale."""
    s = STYLES[style]
    rgb = np.ascontiguousarray(frame[:, :, :3])
    hsv = cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV_FULL).astype(float)
    lo, hi = s['band']
    h = hsv[:, :, 0]
    in_band = (h >= lo) & (h <= hi) if lo < hi else (h >= lo) | (h <= hi)
    weight = np.clip((hsv[:, :, 1] - 50) / 60, 0, 1) * in_band
    base, target = s['accent'], s['variants'][variant]
    new = hsv.copy()
    new[:, :, 0] = target[0]
    new[:, :, 1] = np.clip(hsv[:, :, 1] * target[1] / base[1], 0, 255)
    new[:, :, 2] = np.clip(hsv[:, :, 2] * target[2] / base[2], 0, 255)
    out_rgb = cv2.cvtColor(new.astype(np.uint8), cv2.COLOR_HSV2RGB_FULL).astype(float)
    mixed = rgb * (1 - weight[:, :, None]) + out_rgb * weight[:, :, None]
    out = frame.copy()
    out[:, :, :3] = np.clip(mixed, 0, 255).astype(np.uint8)
    return out


def over(dst, src, x, y, scale=1.0, color=(255, 255, 255), alpha=1.0):
    """Alpha-blends src (RGBA, straight) onto dst (float RGB) at (x, y) top-left, scaled and multiplied by color."""
    if alpha <= 0:
        return
    img = Image.fromarray(src, 'RGBA')
    if scale != 1.0:
        size = (max(1, round(img.width * scale)), max(1, round(img.height * scale)))
        img = img.convert('RGBa').resize(size, Image.LANCZOS).convert('RGBA')
    a = np.asarray(img).astype(float)
    x0, y0 = round(x), round(y)
    h, w = a.shape[:2]
    H, W = dst.shape[:2]
    sx0, sy0, sx1, sy1 = max(0, -x0), max(0, -y0), min(w, W - x0), min(h, H - y0)
    if sx1 <= sx0 or sy1 <= sy0:
        return
    patch = a[sy0:sy1, sx0:sx1]
    k = patch[:, :, 3:4] / 255 * alpha
    col = patch[:, :, :3] * (np.array(color, float) / 255)
    region = dst[y0 + sy0:y0 + sy1, x0 + sx0:x0 + sx1]
    region[:] = region * (1 - k) + col * k


def smooth(e0, e1, x):
    t = min(1, max(0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def compose(style, strip, f, variant=0, headshot=False, canvas=None):
    """Draws frame f (60 fps, since the kill) of one kill's banner in cell space onto canvas (float RGB, cell size)."""
    s = STYLES[style]
    art = {n: np.asarray(Image.open(OUT / p).convert('RGBA')) for n, p in
           [('mark', 'mark.png'), ('mark_thin', 'mark_thin.png'), ('shadow', 'shadow.png'), ('label', 'headshot.png'), ('tint', f'{style}/tint.png')]
           + ([('heart', 'reaver/heart.png')] if s['heart'] else [])}
    ax, ay = s['anchor']
    m = s['mark']
    frames, intro_end = strip['frames'], strip['intro_end']
    frame = frames[min(f, intro_end)]
    # Backdrop: a soft dark disc with the ring.
    shadow_alpha = 0.5 * smooth(m - 8, m, f)
    sh = art['shadow']
    sh_scale = (s['ring'] * 1.85 * 2) / sh.shape[0]
    over(canvas, sh, ax - sh.shape[1] * sh_scale / 2, ay - sh.shape[0] * sh_scale / 2, sh_scale, (0, 0, 0), shadow_alpha)
    over(canvas, recolor(frame, style, variant), 0, 0)
    t = f - m
    if t >= 0:
        if s['heart']:
            over(canvas, art['heart'], 0, 0)
        if t < len(TINT):
            over(canvas, art['tint'], 0, 0, 1.0, RED, TINT[t])
        level = TINT[t] if t < len(TINT) else 0
        white = smooth(0.05, 0.45, level) if t > 1 else 1.0
        color = MARK_RED * (1 - white) + np.array([255, 255, 255]) * white
        size = s['mark_size'] * (1 + 1.4 * math.exp(-t / 1.5))
        k = size / art['mark'].shape[0]
        thin = 1 - smooth(0, 3, t)
        x, y = ax + s['mark_pos'][0] - size / 2, ay + s['mark_pos'][1] - size / 2
        over(canvas, art['mark_thin'], x, y, k, color, 0.75 * thin)
        over(canvas, art['mark'], x, y, k, color, 1 - thin)
    if headshot:
        lb = art['label']
        k = (s['ring'] * 1.45) / lb.shape[1]
        over(canvas, lb, ax - lb.shape[1] * k / 2, ay + s['ring'] + 9, k, (255, 255, 255), smooth(m - 4, m + 4, f))
    return canvas


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--style', required=True)
    parser.add_argument('--video', required=True)
    parser.add_argument('--kill-frame', type=int, required=True, help='video frame of the kill (strip frame 0)')
    parser.add_argument('--kill', type=int, default=1)
    parser.add_argument('--variant', type=int, default=0)
    parser.add_argument('--frames', default='0:40:2')
    parser.add_argument('--center', default='959,858', help='banner ring centre in the 1080p video')
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    strip = read_strip(OUT / args.style / f'k{args.kill}.lkb')
    a, b, step = (int(v) for v in args.frames.split(':'))
    wanted = {args.kill_frame + f for f in range(a, b, step)} | {args.kill_frame - 6}
    video, frames, i = cv2.VideoCapture(args.video), {}, 0
    while i <= max(wanted):
        ok, fr = video.read()
        if not ok:
            break
        if i in wanted:
            frames[i] = cv2.cvtColor(fr, cv2.COLOR_BGR2RGB)
        i += 1
    cx, cy = (float(v) for v in args.center.split(','))
    s = STYLES[args.style]
    h, w = strip['frames'][0].shape[:2]
    # The cell placed so its ring centre lands on the video's, at Valorant's 1080p scale.
    left, top = cx - s['anchor'][0] * SCREEN_SCALE, cy - s['anchor'][1] * SCREEN_SCALE
    bg = frames[args.kill_frame - 6].astype(float)
    rows = []
    for f in range(a, b, step):
        cell = np.asarray(Image.fromarray(bg[int(top):int(top) + round(h * SCREEN_SCALE),
                                             int(left):int(left) + round(w * SCREEN_SCALE)].astype(np.uint8))
                          .resize((w, h), Image.LANCZOS)).astype(float)
        mine = compose(args.style, strip, f, args.variant, False, cell)
        real = np.asarray(Image.fromarray(frames[args.kill_frame + f][int(top):int(top) + round(h * SCREEN_SCALE),
                                          int(left):int(left) + round(w * SCREEN_SCALE)]).resize((w, h), Image.LANCZOS))
        pair = np.vstack([real.astype(np.uint8), np.clip(mine, 0, 255).astype(np.uint8)])
        cv2.putText(pair, str(f), (3, 14), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (255, 255, 0), 1)
        rows.append(pair)
    cols = 8
    blank = np.zeros_like(rows[0])
    grid = [np.hstack(rows[r:r + cols] + [blank] * (cols - len(rows[r:r + cols]))) for r in range(0, len(rows), cols)]
    Image.fromarray(np.vstack(grid)).save(args.out)


if __name__ == '__main__':
    main()
