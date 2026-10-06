"""Tracks a kill banner's pips in a preview video by their shape, all round the ring at once: each frame's edges are
unwrapped into polar coordinates about the real banner centre (a turn of the pip set becomes a shift along the angle
axis), the skin's own pip art, unwrapped the same way at its orbit, is correlated along every angle and a range of radii,
and the N pips' scores are combined at N evenly spaced angles (a comb), so effects in the pips' colour that are not N
pip shapes evenly spaced do not count. The set's angle is then followed through the banner with a smooth path
(Viterbi: highest scores, smallest turns between frames).

Output per kill count: spin (degrees clockwise, the adapters' convention: the pips sit at pip_layout(N) + spin), radius
(relative to the settled orbit), visibility (0..1) and the settled orbit (multiple of the skin's pip radius).

python pip_track.py skin [--counts 3,4] [--check DIR]    (a diagnostic run)
"""
import argparse
import json
import math
from pathlib import Path

import cv2
import numpy as np
from PIL import Image

import audit_motion as am
import measure_video as mv

HERE = Path(__file__).resolve().parent
FOLDER = Path('G:/Val skins Previews')
ROWS = 720                 # polar rows: half a degree each
RMAX = 150                 # polar columns: one pixel of radius each
DY = 6                     # the real banner centre sits ~6 px below 0.794 H at 1080p


def mag(gray):
    gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    return cv2.magnitude(gx, gy)


def polar(img, centre):
    """Rows: angle clockwise from 3 o'clock (OpenCV), half a degree each; columns: radius in px."""
    return cv2.warpPolar(img, (RMAX, ROWS), centre, RMAX, cv2.WARP_POLAR_LINEAR + cv2.INTER_LINEAR)


def row_of(theta_top_cw):
    """Our angle (clockwise from 12 o'clock) to an OpenCV polar row (clockwise from 3 o'clock)."""
    return int(round(((theta_top_cw - 90) % 360) * ROWS / 360)) % ROWS


def likelihood(rgb, accent_hsv):
    """How much each pixel looks like the pips' colour (0..1): their hue, saturated and bright; white pips by brightness."""
    hsv = cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV).astype(np.float32)
    h, sat, v = hsv[:, :, 0], hsv[:, :, 1], hsv[:, :, 2]
    h0, s0, v0 = accent_hsv
    if s0 < 60:
        return np.clip((v - 175) / 60, 0, 1) * np.clip((80 - sat) / 50, 0, 1)
    dh = np.abs(h - h0)
    dh = np.minimum(dh, 180 - dh)
    return np.exp(-(dh / 10) ** 2) * np.clip((sat - 50) / 80, 0, 1) * np.clip((v - 80) / 90, 0, 1)


def pip_template(pip_rgba, s, r0, centre, colour=False):
    """The pip at the top of the ring (radius r0, pointing out), as edge magnitude unwrapped: (patch, mask, row, col)."""
    canvas = Image.new('RGBA', (mv.CW, mv.CH), (0, 0, 0, 0))
    im = Image.fromarray(mv.scaled(pip_rgba, s), 'RGBA')
    canvas.alpha_composite(im, (round(centre[0] - im.width / 2), round(centre[1] - r0 - im.height / 2)))
    a = np.asarray(canvas).astype(np.float32)
    if colour:  # the pip's silhouette, softened: the colour image is matched against where the pip is
        m = cv2.GaussianBlur((a[:, :, 3] / 255).astype(np.float32), (0, 0), 1.2)
    else:
        gray = cv2.cvtColor(a[:, :, :3].astype(np.uint8), cv2.COLOR_RGB2GRAY).astype(np.float32) * a[:, :, 3] / 255
        m = mag(gray)
    pm = polar(m, centre)
    alpha = polar((a[:, :, 3] > 40).astype(np.float32), centre)
    ys, xs = np.nonzero(alpha > .2)
    if len(ys) == 0:
        return None
    r0w, r1w = max(0, xs.min() - 3), min(RMAX, xs.max() + 4)
    top = row_of(0)
    rows = (ys - top + ROWS // 2) % ROWS - ROWS // 2  # rows about the top, signed
    h0, h1 = rows.min() - 3, rows.max() + 4
    idx = [(top + k) % ROWS for k in range(h0, h1)]
    patch = pm[idx, r0w:r1w]
    mask = cv2.dilate((alpha[idx, r0w:r1w] > .2).astype(np.uint8), np.ones((9, 9) if colour else (3, 3), np.uint8))
    return patch.astype(np.float32), (mask * 255).astype(np.uint8), h0, r0w


class Tracker:
    def __init__(self, skin, s, orbit, colour=True):
        b = mv.BANNERS[skin]
        self.pip = mv.art(skin, 'pip.png')
        self.r0 = float(b['radius']) * s * orbit
        self.s = s
        self.centre = (mv.CX, mv.CY + DY)
        self.colour = colour
        acc = int(mv.ACCENT.get(skin, 'FFFFFF').split(',')[0], 16)
        rgb = np.array([[[(acc >> 16) & 255, (acc >> 8) & 255, acc & 255]]], np.uint8)
        self.accent_hsv = tuple(float(x) for x in cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV)[0, 0])
        t = pip_template(self.pip, s, self.r0, self.centre, colour) if self.pip is not None else None
        self.ok = t is not None
        if self.ok:
            self.patch, self.mask, self.h0, self.c0 = t
            self.ph, self.pw = self.patch.shape

    def scores(self, frame_rgb, radial=(-22, 30)):
        """Correlation of the pip shape at every angle (rows, half degrees) and radius shift: (ROWS, shifts) array."""
        if self.colour:
            lk = cv2.GaussianBlur(likelihood(frame_rgb, self.accent_hsv), (0, 0), 1.0)
            p = polar(lk, self.centre)
        else:
            gray = cv2.cvtColor(frame_rgb, cv2.COLOR_RGB2GRAY).astype(np.float32)
            p = polar(mag(gray), self.centre)
        pad = self.ph
        wrapped = np.vstack([p[-pad:], p, p[:pad]])
        c_lo, c_hi = max(0, self.c0 + radial[0]), min(RMAX, self.c0 + self.pw + radial[1])
        if c_hi - c_lo < self.pw:
            return None, c_lo
        method = cv2.TM_CCOEFF_NORMED if self.colour else cv2.TM_CCORR_NORMED
        res = cv2.matchTemplate(wrapped[:, c_lo:c_hi], self.patch, method, mask=self.mask)
        res = np.clip(np.nan_to_num(res, nan=0, posinf=0, neginf=0), 0, 1)
        # res row j: the template's first row at wrapped row j, i.e. polar row j - pad; the pip's top-centre row is
        # template row -h0 (the pip sits at the top-centre of its patch when h0 is the first row offset).
        start = pad + self.h0  # wrapped row where a pip centred at polar row 0 starts... normalise below
        out = np.zeros((ROWS, res.shape[1]), np.float32)
        for r in range(ROWS):
            j = r + pad + self.h0
            if 0 <= j < res.shape[0]:
                out[r] = res[j]
        return out, c_lo


def comb(scores, count):
    """Mean score of N pips evenly spaced, for each set angle (rows within one period) and radius shift."""
    period = ROWS // count
    acc = np.zeros((period, scores.shape[1]), np.float32)
    for i in range(count):
        acc += np.roll(scores, -i * period, axis=0)[:period]
    return acc / count


def viterbi(profiles, max_step, smooth=.0012):
    """The smoothest high-scoring path through per-frame angle profiles (circular, one period long)."""
    n, p = profiles.shape
    steps = np.arange(-max_step, max_step + 1)
    penalty = smooth * np.abs(steps).astype(np.float32)  # per half degree turned: ties go to the smaller turn
    cost = profiles[0].copy()
    back = np.zeros((n, p), np.int32)
    for t in range(1, n):
        best = np.full(p, -1e9, np.float32)
        arg = np.zeros(p, np.int32)
        for k, d in enumerate(steps):
            cand = np.roll(cost, d) - penalty[k]   # from state (x - d) to x
            better = cand > best
            best[better] = cand[better]
            arg[better] = d
        cost = best + profiles[t]
        back[t] = arg
    path = np.zeros(n, np.int64)
    path[-1] = int(np.argmax(cost))
    for t in range(n - 1, 0, -1):
        path[t - 1] = (path[t] - back[t][path[t]]) % p
    # unwrap: each step is the move the path took
    moves = np.array([back[t][path[t]] for t in range(1, n)])
    unwrapped = np.concatenate([[path[0]], path[0] + np.cumsum(moves)])
    return path, unwrapped


def track_banner(tracker, frames, begin, length, count, r60):
    """Per 60 fps frame of one banner: (spin degrees clockwise from the layout, radius ratio, visibility, raw score)."""
    period = ROWS // count
    profiles, radii, peaks = [], [], []
    for f in range(length):
        vf = begin + int(round(f * r60))
        if vf < 0 or vf >= len(frames):
            break
        sc, c_lo = tracker.scores(frames[vf])
        if sc is None:
            break
        cm = comb(sc, count)
        best_r = cm.max(axis=0)
        profiles.append(cm.max(axis=1))
        radii.append(c_lo + int(np.argmax(best_r)) - tracker.c0)
        peaks.append(float(cm.max()))
    if not profiles:
        return None
    prof = np.array(profiles)
    max_step = max(4, min(period // 2 - 1, int(30 * ROWS / 360)))  # up to 30 degrees a frame
    path, unwrapped = viterbi(prof, max_step)
    lay = mv.pip_layout(count)[0]
    # path rows are OpenCV polar rows of one pip of the set (mod the period): back to our angle, relative to the layout
    deg = (np.array(unwrapped) * 360.0 / ROWS) + 90 - lay
    # one representative near 0 for the first frame, the rest follows by continuity
    deg = deg - (360.0 / count) * np.round(deg[0] / (360.0 / count))
    peaks = np.array(peaks)
    radius = 1 + np.array(radii, float) / tracker.r0
    return deg, radius, peaks


def kills_of(skin, video, data):
    frames, fps = mv.load_video(video)
    starts = mv.find_kills(mv.strobe_signal(frames), fps)
    if len(starts) < 5:
        frames, fps = mv.load_video(video, stop=False)
        starts = mv.find_kills(mv.strobe_signal(frames), fps)
    r60 = fps / 60
    counts = []
    for i, k in enumerate(starts):
        counts.append(1 if i == 0 or k - starts[i - 1] > 300 * r60 else min(5, counts[-1] + 1))
    banners = []
    for i, k in enumerate(starts):
        c = counts[i]
        if c not in data or c in [b[1] for b in banners]:
            continue
        begin = k + int(round(mv.MARK_FRAME * r60)) - int(round(data[c].get('mark', 11) * r60))
        nxt = starts[i + 1] if i + 1 < len(starts) else len(frames)
        length = int(min(nxt - begin, len(frames) - begin) / r60)
        banners.append((begin, c, length))
    return frames, fps, banners


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('skin')
    ap.add_argument('--counts', default='')
    args = ap.parse_args()
    summary = json.loads((HERE / 'out' / 'motion' / 'summary.json').read_text(encoding='utf-8'))
    entry = summary[args.skin]
    data, orbit, _ = am.load(HERE / 'out' / 'motion' / 'before-clean' / f'{args.skin}.properties')
    frames, fps, banners = kills_of(args.skin, FOLDER / entry['video'], data)
    s = entry['scale']
    tracker = Tracker(args.skin, s, orbit)
    want = [int(c) for c in args.counts.split(',') if c]
    for begin, c, length in banners:
        if want and c not in want:
            continue
        res = track_banner(tracker, frames, begin, min(length, data[c]['introEnd'] + 40), c, fps / 60)
        if res is None:
            continue
        deg, radius, peaks = res
        picks = [f for f in (0, 6, 9, 12, 14, 16, 18, 20, 22, 26, 30, 36, 45, 55, 60, 70, 90) if f < len(deg)]
        per = 360.0 / c
        print(f'k{c}: spin ' + ' '.join(f'f{f}:{deg[f]:.0f}' for f in picks))
        print(f'     mod  ' + ' '.join(f'f{f}:{((deg[f] + per / 2) % per) - per / 2:.0f}' for f in picks))
        print(f'     score ' + ' '.join(f'f{f}:{peaks[f]:.2f}' for f in picks))
        print(f'     radius ' + ' '.join(f'f{f}:{radius[f]:.2f}' for f in picks))


if __name__ == '__main__':
    main()
