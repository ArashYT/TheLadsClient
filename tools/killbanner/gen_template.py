#!/usr/bin/env python3
"""Measures Rogue's kill banner strips frame by frame into the motion template every still skin plays
(KillBannerPlayer.layers): how the icon, ring, frame and pips appear, settle and leave, in each of Rogue's measured
60 fps frames. Developer tool; writes
  TheLadsCore/common/src/main/resources/assets/theladscore/killbanner/template.properties
with, per kill count, the intro and exit frame counts and one value a frame for each layer channel.

python gen_template.py            measure and write
python gen_template.py --check    also draw Rogue's own layers with the template next to its real frames
                                  (template-check-kN.png under tools/killbanner/out) and print the mean difference a frame

Channels (one float a frame; frames 0..introEnd play and introEnd holds, then the exit frames play the way out):
  icon.alpha/scale/y/shade   the emblem: opacity, size about the ring centre, cell pixels below its settled place,
                             brightness (1 = as drawn, less = darkened on the way out)
  ring.alpha/scale           the ring (and whatever fill its art has)
  frame.alpha/scale          the outer frame
  pip.alpha/radius/flare/spin
                             the kill pips: opacity, orbit radius (1 = settled), arrival glow (0..1), degrees turned
                             clockwise (the ace's pips go round)
  spray                      "start,count": the frame droplets first fly and about how many (0,0: none)

What the frames show (so nobody looks for it again): the icon pops in at its place, jumps up about 20 px and drops back
(f2-9); the pips fade in 17% out from their place and glow (f1-14), then slide in as the ring fades in (f28-50); the frame
fades in (f8-15); the ring fades in, in place (f24-38); the blood and droplets are Rogue's own; the way out is the frame,
then the icon shrinking and darkening, then the ring, the pips last.
"""
import argparse
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_killbanner import OUT, icon_mask, read_strip, ring  # noqa: E402

TEMPLATE = OUT / 'template.properties'
CHANNELS = ['icon.alpha', 'icon.scale', 'icon.y', 'icon.shade', 'ring.alpha', 'ring.scale', 'frame.alpha', 'frame.scale',
            'pip.alpha', 'pip.radius', 'pip.flare', 'pip.spin']


def red_mask(frame):
    """Rogue's red: pips, their glow, the blood and the droplets (and the icon's eyes, masked off by callers)."""
    a = frame[:, :, 3].astype(int)
    rgb = frame[:, :, :3].astype(int)
    mx, mn = rgb.max(2), rgb.min(2)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1), 0)
    return (a > 20) & (sat > .45) & (rgb[:, :, 0] > rgb[:, :, 1] * 1.6) & (rgb[:, :, 0] > rgb[:, :, 2] * 1.6)


def pink_mask(frame):
    """The pips and their glow: Rogue's pip red has some blue in it (B about 0.13-0.17 R); the blood and droplets none."""
    rgb = frame[:, :, :3].astype(int)
    return red_mask(frame) & (rgb[:, :, 2] > rgb[:, :, 0] * .06)


def blood_mask(frame):
    rgb = frame[:, :, :3].astype(int)
    return red_mask(frame) & (rgb[:, :, 2] <= rgb[:, :, 0] * .03)


class Geometry:
    """Rogue's settled one-kill frame: where everything sits."""

    def __init__(self, settled):
        self.cx, self.cy, self.r = ring(settled)
        self.icon = icon_mask(settled, self.cx, self.cy, self.r)
        h, w = settled.shape[:2]
        self.yy, self.xx = np.mgrid[0:h, 0:w]
        ys, xs = np.nonzero(self.icon)
        self.icon_box = (xs.min(), xs.max(), ys.min(), ys.max())
        # The icon's alpha and brightness with its red eyes out, the template the frames are matched against.
        eyes = red_mask(settled) & self.icon
        self.icon_alpha = np.where(self.icon & ~eyes, settled[:, :, 3], 0).astype(float)
        self.icon_bright = np.where(self.icon & ~eyes, settled[:, :, :3].max(2), 0).astype(float)
        self.scaled = {}

    def dist(self, dy=0):
        return np.hypot(self.xx - self.cx, self.yy - (self.cy + dy))

    def angle(self, dy=0):
        """Degrees clockwise from the top about the ring centre."""
        return np.degrees(np.arctan2(self.xx - self.cx, -(self.yy - (self.cy + dy)))) % 360

    def icon_at(self, scale):
        """The settled icon's alpha and brightness scaled about the ring centre (cached)."""
        key = round(scale, 3)
        if key not in self.scaled:
            self.scaled[key] = tuple(zoom_about(a, scale, self.cx, self.cy) for a in (self.icon_alpha, self.icon_bright))
        return self.scaled[key]


def zoom_about(image, scale, cx, cy):
    """image scaled by scale about (cx, cy), same size (bilinear, zero outside)."""
    h, w = image.shape
    matrix = np.array([[1 / scale, 0], [0, 1 / scale]])
    offset = np.array([cy - cy / scale, cx - cx / scale])
    return ndimage.affine_transform(image, matrix, offset=offset, output_shape=(h, w), order=1, mode='constant', cval=0)


def shift(image, dy):
    out = np.zeros_like(image)
    if dy >= 0:
        out[dy:] = image[:image.shape[0] - dy]
    else:
        out[:dy] = image[-dy:]
    return out


def ncc(a, b):
    return float((a * b).sum() / (math.sqrt((a * a).sum() * (b * b).sum()) + 1e-9))


def measure_icon(g, frame, red, ring_dy):
    """(alpha, scale, y, shade): the settled icon fitted to the frame (scale about the ring centre, y cell px down)."""
    x0, x1, y0, y1 = g.icon_box
    win = (slice(max(0, y0 - 48), y1 + 20), slice(max(0, x0 - 10), x1 + 11))
    alpha = np.where(red, 0, frame[:, :, 3]).astype(float)
    if alpha[win].max() < 30:
        return 0, 1, ring_dy, 1
    bright = np.where(red, 0, frame[:, :, :3].max(2)).astype(float)
    best = (-1, 1, 0)
    for pass_ in range(2):
        if pass_ == 0:
            scales, dys = np.arange(.3, 1.3, .05), range(-30, 13, 3)
        else:
            s0, d0 = best[1], best[2]
            scales, dys = np.arange(s0 - .05, s0 + .051, .01), range(d0 - 3, d0 + 4)
        for s in scales:
            template = g.icon_at(float(s))[0]
            for dy in dys:
                score = ncc(alpha[win], shift(template, dy)[win])
                if score > best[0]:
                    best = (score, float(s), int(dy))
    score, s, dy = best
    t_alpha, t_bright = (shift(a, dy) for a in g.icon_at(s))
    mask = t_alpha > 60
    if score < .3 or mask.sum() < 30:
        return 0, 1, ring_dy, 1
    a = float(np.clip(alpha[mask].sum() / t_alpha[mask].sum(), 0, 1.05))
    lit = mask & (t_bright > 100)
    shade = float(np.clip((bright[lit] * alpha[lit]).sum() / ((t_bright[lit] * alpha[lit]).sum() + 1e-9), 0, 1.1))
    return a, s, dy, shade


def profile(values, dist, lo, hi, step=1.0):
    """Mean of values in annuli [lo, hi) of width step about the centre (values already masked to what counts)."""
    bins = np.arange(lo, hi + step, step)
    idx = np.clip(((dist - lo) / step).astype(int), 0, len(bins) - 2)
    inside = (dist >= lo) & (dist < hi)
    total = np.bincount(idx[inside], weights=values[inside], minlength=len(bins) - 1)
    count = np.bincount(idx[inside], minlength=len(bins) - 1)
    return total / np.maximum(count, 1), bins[:-1] + step / 2


def intensity(frame, red):
    """How much light a pixel gives on the dark game background: alpha times brightness, the red left out."""
    return np.where(red, 0, frame[:, :, 3].astype(float) * frame[:, :, :3].max(2) / 255)


def measure_ring(g, frame, red, ring_dy, icon_scale, icon_dy, settled_peak=None):
    """(alpha, scale, peak_value): the ring's ridge in the radial intensity profile, near its settled radius."""
    dist = g.dist(ring_dy)
    icon = shift(g.icon_at(icon_scale)[0] > 20, icon_dy)
    icon = ndimage.binary_dilation(icon, iterations=2)
    values = np.where(icon, 0, intensity(frame, red))
    prof, radii = profile(values, dist, 42, 55, .5)
    i = int(np.argmax(prof))
    peak = float(prof[i])
    if settled_peak is None:
        settled_peak = peak
    return float(np.clip(peak / settled_peak, 0, 1.05)), float(radii[i] / g.r), settled_peak


def measure_frame(g, frame, red, ring_dy, settled=None, ring_alpha=1.0):
    """(alpha, scale, settled profile): the outer frame's radial intensity profile matched to the settled one."""
    dist = g.dist()
    ring_band = (np.abs(g.dist(ring_dy) - g.r) < 4) & (ring_alpha > .05)
    values = np.where(ring_band, 0, intensity(frame, red))
    prof, radii = profile(values, dist, g.r + 6, 110, 1.0)
    if settled is None:
        return 1.0, 1.0, prof
    if prof.sum() < .02 * settled.sum():
        return 0.0, 1.0, settled
    best = (-1, 1.0, settled)
    for s in np.arange(.8, 1.4, .01):
        # The settled profile stretched by s: the value at radius rho comes from rho / s.
        src = (radii / s - radii[0]) / (radii[1] - radii[0])
        stretched = np.interp(src, np.arange(len(settled)), settled, left=0, right=0)
        score = ncc(prof, stretched)
        if score > best[0]:
            best = (score, float(s), stretched)
    score, s, stretched = best
    alpha = float(np.clip(prof.sum() / (stretched.sum() + 1e-9), 0, 1.05))
    return alpha, s, settled


def pip_angles(g, settled):
    """Each settled pip's angle, degrees clockwise from the top."""
    red = pink_mask(settled) & ~g.icon
    dist = g.dist()
    band = red & (dist > g.r + 4) & (dist < g.r + 32)
    labels, n = ndimage.label(band)
    sizes = ndimage.sum(band, labels, range(1, n + 1))
    centres = ndimage.center_of_mass(band, labels, range(1, n + 1))
    return sorted(float(np.degrees(np.arctan2(c[1] - g.cx, -(c[0] - g.cy))) % 360) for c, size in zip(centres, sizes) if size > 40)


def pip_sectors(g, angles, ring_dy):
    """Boolean sector masks round each pip's settled angle, in the orbit band about the current ring centre."""
    half = min(40, 180 / max(1, len(angles)) - 8) if len(angles) > 1 else 25
    dist = g.dist(ring_dy)
    ang = g.angle(ring_dy)
    band = (dist > g.r + 2) & (dist < g.r + 45)
    sectors = []
    for theta in angles:
        diff = np.abs((ang - theta + 180) % 360 - 180)
        sectors.append(band & (diff < half))
    return sectors


def measure_pips(g, frame, pink, ring_dy, angles, settled=None, spin_allowed=True):
    """(alpha, radius, flare, spin), settled stats. Averaged over the pips; spin by circular correlation."""
    red = pink & ~shift(g.icon, ring_dy)
    dist = g.dist(ring_dy)
    ang = g.angle(ring_dy)
    alpha = np.where(red, frame[:, :, 3], 0).astype(float)
    sectors = pip_sectors(g, angles, ring_dy)
    band = (dist > g.r + 4) & (dist < g.r + 45)
    # Angular profile of red alpha round the orbit, 2 degree bins.
    bins = ((ang[band] / 2).astype(int)) % 180
    angular = np.bincount(bins, weights=alpha[band], minlength=180)
    stats = []
    for sector in sectors:
        a = alpha[sector]
        if a.max() <= 0:
            stats.append((0, 0, 0))
            continue
        top = float(np.percentile(a[a > 0], 97))
        mass = float(a.sum())
        core_px = a > 200
        radius = float(dist[sector][core_px].mean()) if core_px.sum() >= 20 else 0
        stats.append((top, mass, radius))
    if settled is None:
        return (1, 1, 0, 0), (stats, angular)
    s_stats, s_angular = settled
    alphas, masses, radii = [], [], []
    for (top, mass, radius), (s_top, s_mass, s_radius) in zip(stats, s_stats):
        if s_top <= 0:
            continue
        alphas.append(top / s_top)
        masses.append(mass / s_mass)
        if radius > 0 and s_radius > 0:
            radii.append(radius / s_radius)
    if not alphas or max(alphas) <= 0:
        return (0, 0, 0, 0), settled
    a = float(np.clip(np.mean(alphas), 0, 1.05))
    flare = float(max(0, np.mean(masses) - 1))
    radius = float(np.mean(radii)) if radii else 0  # 0: not measurable in this frame (filled from the neighbours)
    spin = 0
    if spin_allowed:  # the shift of the angular profile that best matches the settled one (clockwise, degrees, modulo the pips' spacing)
        period = 360 // max(1, len(angles))
        best = (-1, 0)
        for k in range(period // 2):
            score = ncc(np.roll(s_angular, k), angular)
            if score > best[0]:
                best = (score, k * 2)
        spin = best[1] if best[0] > .3 else 0
    return (a, radius, flare, spin), settled


def measure_spray(g, frame):
    """Red droplets beyond the frame: how many, and how many pixels."""
    far = blood_mask(frame) & (g.dist() > 96)
    labels, n = ndimage.label(far)
    return n, int(far.sum())


def smooth(values):
    v = np.array(values, float)
    out = v.copy()
    for i in range(1, len(v) - 1):
        if v[i] == 0 or v[i - 1] == 0 or v[i + 1] == 0:
            continue
        out[i] = .25 * v[i - 1] + .5 * v[i] + .25 * v[i + 1]
    return out


def tidy(channel, values):
    """Measurement noise out: settled alphas are 1, settled scales 1."""
    v = np.array(values, float)
    if channel.endswith('.alpha') or channel == 'icon.shade':
        v = np.where(v > .96, 1, v)
    if channel.endswith('.scale') or channel == 'pip.radius':
        v = np.where(np.abs(v - 1) < .015, 1, v)
    if channel == 'pip.spin':
        v = np.where(np.abs(v) < 3, 0, v)
    return np.round(v, 3)


def measure(kills, strips, g):
    strip = strips[kills]
    frames, intro_end, icon_y = strip['frames'], strip['intro_end'], strip['icon_y']
    settled = frames[intro_end]
    angles = pip_angles(g, settled)
    red_s = red_mask(settled)
    _, _, ring_peak = measure_ring(g, settled, red_s, 0, 1, 0)
    _, _, frame_prof = measure_frame(g, settled, red_s, 0)
    _, pip_stats = measure_pips(g, settled, pink_mask(settled), 0, angles)
    rows = {c: [] for c in CHANNELS}
    spray = []
    for f, frame in enumerate(frames):
        dy = icon_y[f] if f < len(icon_y) else 0
        red = red_mask(frame)
        ia, isc, iy, ish = measure_icon(g, frame, red, dy)
        ra, rsc, _ = measure_ring(g, frame, red, dy, isc, iy, ring_peak)
        fa, fsc, _ = measure_frame(g, frame, red, dy, frame_prof, ra)
        (ka, kr, kf, ksp), _ = measure_pips(g, frame, pink_mask(frame), dy, angles, pip_stats, kills >= 4 and f >= 24)
        for c, v in zip(CHANNELS, (ia, isc, iy, ish, ra, rsc, fa, fsc, ka, kr, kf, ksp)):
            rows[c].append(v)
        spray.append(measure_spray(g, frame))
    # A layer that is on stays on until the way out (the measurement dips where the blood, the droplets or the pips'
    # glow cross it).
    for c in ('ring.alpha', 'frame.alpha', 'pip.alpha'):
        a = rows[c]
        on = next((i for i, x in enumerate(a) if x >= .85 and min(a[i:intro_end + 1]) >= .85), None)
        if on is not None:
            for i in range(on, intro_end + 1):
                a[i] = 1.0
    # The ring does not change size; its ridge drifts in the measurement while it fades out.
    for i in range(intro_end + 1, len(rows['ring.scale'])):
        rows['ring.scale'][i] = 1.0
    # Nothing is measurable for a layer that is not there (or a pip too dim for its bright core to show): its scale,
    # radius and shade keep their neighbours' values.
    for c, ref in (('icon.scale', 'icon.alpha'), ('icon.shade', 'icon.alpha'), ('icon.y', 'icon.alpha'), ('ring.scale', 'ring.alpha'),
                   ('frame.scale', 'frame.alpha'), ('pip.radius', 'pip.alpha')):
        v, a = rows[c], rows[ref]
        present = [i for i, x in enumerate(a) if x > .1 and (c != 'pip.radius' or v[i] > 0)]
        first = present[0] if present else None
        for i in range(len(v)):
            if i not in present:
                v[i] = v[first] if first is not None and i < first else (v[i - 1] if i > 0 else 1)
    # The pips' turn comes modulo their spacing: unwrapped so it runs on (the ace's pips go round and more).
    period = 360 / max(1, len(angles))
    spin, previous = rows['pip.spin'], 0.0
    for i in range(len(spin)):
        step = (spin[i] - previous + period / 2) % period - period / 2
        spin[i] = previous + step
        previous = spin[i]
    # The pips' arrival glow: how much more light than the settled pips give, in the first second; the peak is 1, and
    # once it has died away it stays away (what the measurement picks up later is the blood's pink edges).
    flare = np.array(rows['pip.flare'])
    flare[60:] = 0
    peak = flare.max()
    if peak > .08:
        flare = np.clip(flare / peak, 0, 1)
        gone = next((i for i in range(int(np.argmax(flare)), len(flare)) if flare[i] < .05), len(flare))
        flare[gone:] = 0
        rows['pip.flare'] = list(flare)
    else:
        rows['pip.flare'] = [0.0] * len(flare)
    out = {c: tidy(c, smooth(rows[c]) if c in ('icon.shade', 'pip.radius', 'pip.flare', 'frame.scale') else rows[c]) for c in CHANNELS}
    counts = [n for n, _ in spray]
    start = next((f for f, (n, px) in enumerate(spray) if px > 25), None)
    count = int(np.percentile(counts, 95))
    out['spray'] = (max(0, start - 8), count) if start is not None and count >= 8 else (0, 0)
    return intro_end, strip['exit_frames'], out


def write_template(results):
    lines = ['# Every still skin plays Rogue\'s measured motion (tools/killbanner/gen_template.py): per kill count, frames 0..introEnd',
             '# play and introEnd holds, then exit frames play the way out; one value a frame for each channel.']
    for kills, (intro_end, exit_frames, rows) in sorted(results.items()):
        lines.append(f'k{kills}.introEnd={intro_end}')
        lines.append(f'k{kills}.exit={exit_frames}')
        lines.append(f'k{kills}.spray={rows["spray"][0]},{rows["spray"][1]}')
        for c in CHANNELS:
            lines.append(f'k{kills}.{c}=' + ','.join(f'{v:g}' for v in rows[c]))
    TEMPLATE.write_text('\n'.join(lines) + '\n', encoding='utf-8')


def layers_from(settled, g, angles):
    """Rogue's own still layers cut from its settled frame: icon, ring (with its fill), frame, one pip."""
    dist = g.dist()
    red = pink_mask(settled) & ~g.icon
    icon = settled.copy()
    icon[~g.icon] = 0
    ring_l = settled.copy()
    ring_l[~((dist < g.r + 5) & ~g.icon & ~red)] = 0
    frame_l = settled.copy()
    frame_l[~((dist >= g.r + 5) & ~red)] = 0
    pip = settled.copy()
    sector = pip_sectors(g, angles[:1], 0)[0] if angles else np.zeros_like(g.icon)
    pip[~(red & sector)] = 0
    return icon, ring_l, frame_l, pip, angles[0] if angles else 0


def draw_layer(canvas, layer, scale, alpha, dy, cx, cy, shade=1.0, angle=0.0):
    """layer (RGBA, full cell) scaled about (cx, cy), moved dy down, turned angle degrees clockwise, onto canvas (float RGBA)."""
    if alpha <= 0:
        return
    matrix = np.array([[1 / scale, 0], [0, 1 / scale]])
    offset = np.array([cy - cy / scale, cx - cx / scale])
    a = ndimage.affine_transform(layer[:, :, 3].astype(float), matrix, offset=offset, order=1, mode='constant', cval=0)
    rgb = np.stack([ndimage.affine_transform(layer[:, :, i].astype(float), matrix, offset=offset, order=1, mode='constant', cval=0)
                    for i in range(3)], -1)
    if angle:
        a = ndimage.rotate(a, -angle, reshape=False, order=1)
        rgb = np.stack([ndimage.rotate(rgb[:, :, i], -angle, reshape=False, order=1) for i in range(3)], -1)
    a = shift(a, int(round(dy))) / 255 * alpha
    rgb = shift(rgb, int(round(dy))) * shade
    k = a[:, :, None]
    canvas[:, :, :3] = canvas[:, :, :3] * (1 - k) + rgb * k
    canvas[:, :, 3] = canvas[:, :, 3] * (1 - a) + a


def check(results, strips, g, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    for kills, (intro_end, exit_frames, rows) in sorted(results.items()):
        strip = strips[kills]
        frames = strip['frames']
        angles = pip_angles(g, frames[intro_end])
        icon, ring_l, frame_l, pip, pip0 = layers_from(frames[intro_end], g, angles)
        idx = list(range(0, min(intro_end + 1, 48))) + list(range(48, intro_end + 1, 8)) + list(range(intro_end + 1, len(frames)))
        h, w = frames[0].shape[:2]
        cols = 12
        rowsn = (len(idx) + cols - 1) // cols
        sheet = Image.new('RGBA', (cols * w, rowsn * h * 2), (40, 40, 40, 255))
        draw = ImageDraw.Draw(sheet)
        diffs = []
        for j, f in enumerate(idx):
            canvas = np.zeros((h, w, 4), float)
            v = {c: rows[c][f] for c in CHANNELS}
            dy = v['icon.y']
            draw_layer(canvas, frame_l, v['frame.scale'], v['frame.alpha'], 0, g.cx, g.cy)
            draw_layer(canvas, ring_l, v['ring.scale'], v['ring.alpha'], dy, g.cx, g.cy)
            draw_layer(canvas, icon, v['icon.scale'], v['icon.alpha'], dy, g.cx, g.cy, v['icon.shade'])
            for theta in angles:
                draw_layer(canvas, pip, v['pip.radius'], v['pip.alpha'], dy, g.cx, g.cy, 1, theta - pip0 + v['pip.spin'])
            mine = canvas.copy()
            mine[:, :, 3] *= 255
            mine = np.clip(mine + .5, 0, 255).astype(np.uint8)
            real = frames[f]
            keep = ~red_mask(real)
            diff = np.abs(mine[:, :, 3].astype(int) - real[:, :, 3].astype(int))[keep].mean()
            diffs.append(diff)
            x, y = (j % cols) * w, (j // cols) * h * 2
            sheet.alpha_composite(Image.fromarray(real, 'RGBA'), (x, y))
            sheet.alpha_composite(Image.fromarray(mine, 'RGBA'), (x, y + h))
            draw.text((x + 3, y + 3), f'{f}', fill=(255, 255, 0, 255))
            draw.text((x + 3, y + h + 3), f'{diff:.1f}', fill=(0, 255, 255, 255))
        sheet.save(out_dir / f'template-check-k{kills}.png')
        print(f'k{kills}: mean alpha difference {np.mean(diffs):.2f}/255 over {len(idx)} frames (max {max(diffs):.1f} at frame {idx[int(np.argmax(diffs))]})')


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--out', default=str(Path(__file__).resolve().parent / 'out'))
    args = parser.parse_args()
    strips = {k: read_strip(OUT / 'rogue' / f'k{k}.lkb') for k in range(1, 6)}
    g = Geometry(strips[1]['frames'][strips[1]['intro_end']])
    print(f'rogue: ring ({g.cx:.1f}, {g.cy:.1f}) r {g.r:.1f}, icon box {g.icon_box}')
    results = {}
    for kills in range(1, 6):
        results[kills] = measure(kills, strips, g)
        intro_end, exit_frames, rows = results[kills]
        print(f'  k{kills}: hold at {intro_end}, {exit_frames} exit frames, spray {rows["spray"]}')
    write_template(results)
    print(f'wrote {TEMPLATE.name} ({TEMPLATE.stat().st_size // 1024} KB)')
    if args.check:
        check(results, strips, g, Path(args.out))


if __name__ == '__main__':
    main()
