"""Turns measured kill banner motion (killbanner/motion/<skin>.properties) into clean, deliberate animation:

- the emblem rests on the ring centre (the measurer's assumed centre sat ~6 px above the real one, so every emblem was
  stored resting low); only its real moves (the hop as it pops, an ace drop) stay, short excursions after it settles
  (the next kill's effects, a match that slipped) go;
- every channel is smoothed and reduced to keyframes (Ramer-Douglas-Peucker on the smoothed series), then played back
  through a monotone cubic (PCHIP): a move eases in and out, a long spin keeps its speed through its keyframes, and
  sub-threshold wiggles vanish;
- layers fade in once and stay (no flicker), and fade out once; the frame keeps one size; the arrival glow is one bump;
- the pips' settled angles are folded into their spin (one absolute angle, no double count), and a pip set that never
  moved more than the threshold does not move at all.
Banners whose pips were borrowed from the shared template keep those channels as they are.

python clean_motion.py [--dir MOTION_DIR] [--backup DIR] [--plot skin,skin] [--plots DIR]
"""
import argparse
import shutil
from pathlib import Path

import numpy as np

import audit_motion as am

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[1] / 'TheLadsCore/common/src/main/resources/assets/theladscore/killbanner'
CHANNELS = am.CHANNELS


# ---- small numeric helpers -------------------------------------------------------------------------------------------
def median_filter(v, width=5):
    h = width // 2
    return np.array([np.median(v[max(0, i - h):i + h + 1]) for i in range(len(v))])


def gauss(v, sigma):
    if sigma <= 0 or len(v) < 3:
        return np.array(v, float)
    r = int(np.ceil(3 * sigma))
    x = np.arange(-r, r + 1)
    k = np.exp(-x * x / (2 * sigma * sigma))
    k /= k.sum()
    return np.convolve(np.pad(v, r, mode='edge'), k, mode='valid')


def hold(v, valid):
    """Frames that are not valid take the value of the last valid frame (before the first: the first valid one)."""
    v = np.array(v, float)
    idx = np.flatnonzero(valid)
    if len(idx) == 0:
        return None
    out = v.copy()
    out[:idx[0]] = v[idx[0]]
    last = v[idx[0]]
    for i in range(idx[0], len(v)):
        if valid[i]:
            last = v[i]
        else:
            out[i] = last
    return out


def rdp(v, eps):
    """Keyframe indices: the fewest points whose straight segments stay within eps of the series (vertically)."""
    keep = {0, len(v) - 1}
    stack = [(0, len(v) - 1)]
    while stack:
        a, b = stack.pop()
        if b - a < 2:
            continue
        t = np.arange(a, b + 1)
        line = v[a] + (v[b] - v[a]) * (t - a) / (b - a)
        d = np.abs(v[a:b + 1] - line)
        i = int(np.argmax(d))
        if d[i] > eps:
            keep.add(a + i)
            stack += [(a, a + i), (a + i, b)]
    return sorted(keep)


def pchip(xs, ys, n):
    """Monotone cubic through the keyframes (Fritsch-Carlson), flat at both ends; sampled at frames 0..n-1."""
    xs, ys = np.array(xs, float), np.array(ys, float)
    if len(xs) == 1:
        return np.full(n, ys[0])
    h = np.diff(xs)
    delta = np.diff(ys) / h
    d = np.zeros(len(xs))
    for k in range(1, len(xs) - 1):
        if delta[k - 1] * delta[k] > 0:
            w1, w2 = 2 * h[k] + h[k - 1], h[k] + 2 * h[k - 1]
            d[k] = (w1 + w2) / (w1 / delta[k - 1] + w2 / delta[k])
    out = np.empty(n)
    for f in range(n):
        k = min(max(int(np.searchsorted(xs, f, side='right')) - 1, 0), len(xs) - 2)
        t = (f - xs[k]) / h[k]
        if f <= xs[0]:
            out[f] = ys[0]
            continue
        if f >= xs[-1]:
            out[f] = ys[-1]
            continue
        t2, t3 = t * t, t * t * t
        out[f] = ((2 * t3 - 3 * t2 + 1) * ys[k] + (t3 - 2 * t2 + t) * h[k] * d[k]
                  + (-2 * t3 + 3 * t2) * ys[k + 1] + (t3 - t2) * h[k] * d[k + 1])
    return out


def keyframed(v, eps, smooth=1.5, med=5, anchor=None):
    """The series smoothed, reduced to keyframes and played back eased (see the module notes). Keyframes that differ
    from their neighbour toward the anchor frame (the settled one) by less than eps take its value, so a slow drift or a
    small wobble becomes stillness at the settled value instead of a creep."""
    v = np.array(v, float)
    sm = lambda part: gauss(median_filter(part, med), smooth) if len(part) > 4 else part
    # The settled frame and the way out are smoothed apart, so the hold keeps its value instead of leaning into the exit.
    s = sm(v) if anchor is None else np.concatenate([sm(v[:anchor + 1]), sm(v[anchor + 1:])])
    keys = rdp(s, eps)
    if anchor is not None:
        keys = sorted(set(keys) | {anchor})
        vals = s[keys].copy()
        a = keys.index(anchor)
        for j in range(a - 1, -1, -1):
            if abs(vals[j] - vals[j + 1]) < eps:
                vals[j] = vals[j + 1]
        for j in range(a + 1, len(keys)):
            if abs(vals[j] - vals[j - 1]) < eps:
                vals[j] = vals[j - 1]
        return pchip(keys, vals, len(s))
    return pchip(keys, s[keys], len(s))


def ramp(n, start, end, a, b):
    """a until frame start, eased to b at frame end, b after."""
    t = np.clip((np.arange(n) - start) / max(1, end - start), 0, 1)
    return a + (b - a) * t * t * (3 - 2 * t)


def unslip(v, rest, start, end, tol, longest=20):
    """Excursions from rest between frames start and end that come back within longest frames: set to rest."""
    v = np.array(v, float)
    i = start
    while i <= end:
        if abs(v[i] - rest) >= tol:
            j = i
            while j <= end and abs(v[j] - rest) >= tol:
                j += 1
            if j - i < longest or j > end:  # short, or still out at the settled frame: the next kill's effects
                v[i:j] = rest
            i = j
        else:
            i += 1
    return v


def circ_mean(values, period):
    a = np.array(values, float) * 2 * np.pi / period
    return float((np.angle(np.mean(np.exp(1j * a))) * period / (2 * np.pi)) % period)


def wrap(x, period):
    return (x + period / 2) % period - period / 2


def layout(count):
    return sorted(((-(360.0 / count * (i + 1) + (90 if count == 2 else 0))) % 360) for i in range(count))


# ---- one banner -------------------------------------------------------------------------------------------------------
def fade(v, ie, longest_in=20):
    """A layer's opacity as one fade in and one fade out, eased: in from where it starts to show (at most longest_in
    frames before it is full) to where it is full (normalised to its settled level, so a dim settled measure counts as
    full), out from where it starts to drop to where it is gone. No stages, no flicker."""
    v = np.clip(np.array(v, float), 0, None)
    n = len(v)
    intro, out = v[:ie + 1], v[ie + 1:]
    if intro.max() < .25:  # this skin has no such layer showing
        return np.zeros(n)
    tail = intro[max(0, int(len(intro) * .6)):]
    plateau = float(np.median(tail)) if len(tail) else 1.0
    if plateau > .4:
        intro, out = intro / plateau, out / plateau
    sm = gauss(np.clip(intro, 0, 1.2), 1.0)
    full = next((i for i in range(len(sm)) if sm[i] >= .8 and np.median(sm[i:i + 10]) >= .8), len(sm) - 1)
    if np.median(intro[:3]) >= .5:  # on from the start: carried over from the preview's previous banner
        full = min(full, 4)
    low = [i for i in range(full) if sm[i] < .15]
    start = max(low[-1] + 1 if low else 0, full - longest_in)
    res = ramp(n, start - 1, full, 0.0, 1.0) if full > 0 else np.ones(n)
    if len(out):
        so = gauss(np.clip(np.concatenate([[1.0], out]), 0, 1.2), 1.0)[1:]
        drop = next((i for i in range(len(so)) if so[i] < .9), len(so) - 1)
        gone = next((i for i in range(drop, len(so)) if so[i] < .1), len(so) - 1)
        gone = max(gone, drop + 2)
        res[ie + 1:] = ramp(len(out), drop - 1, gone, 1.0, 0.0)
    return np.clip(res, 0, 1)


def clean_banner(b, count, template_pips):
    n = len(b['icon.alpha'])
    ie = b['introEnd']
    mark = b.get('mark', 11)
    out = {}

    # Emblem opacity: in once, out once (its pop is a cut, so no easing).
    ia = np.clip(b['icon.alpha'], 0, 1)
    ia[:ie + 1] = np.maximum.accumulate(ia[:ie + 1])
    if n > ie + 1:
        ia[ie + 1:] = np.minimum.accumulate(np.minimum(ia[ie + 1:], ia[ie]))
    out['icon.alpha'] = ia
    seen = ia >= .5

    # Emblem size: keyframed where it is clearly visible.
    sc = hold(b['icon.scale'], seen)
    if sc is not None:
        rest_sc = float(np.median(sc[min(ie, max(mark + 20, ie - 40)):ie + 1]))
        sc = unslip(sc, rest_sc, min(ie, mark + 20), ie, .025)
        out['icon.scale'] = keyframed(sc, .02, anchor=ie)
    else:
        out['icon.scale'] = np.ones(n)

    # Emblem height: on the ring centre at rest; the hop as it pops stays, later slips go.
    y = np.array(b['icon.y'], float)
    lo = min(ie, max(mark + 12, ie - 40))
    rest = float(np.median(y[lo:ie + 1]))
    y = y - rest
    y = hold(y, ia >= .6)
    if y is None:
        y = np.zeros(n)
    settle = next((i for i in range(3, ie + 1) if np.all(np.abs(y[i:min(ie + 1, i + 8)]) < 2)), ie)
    y = unslip(y, 0.0, settle, ie, 2.0)  # after it settled: slips and the next kill's effects go
    y[settle:] = np.where(np.abs(y[settle:]) < 2, 0, y[settle:])
    y[ie + 1:] = 0  # it leaves where it rests
    ky = keyframed(y, 1.5, anchor=ie)
    out['icon.y'] = np.where(np.abs(ky) < .2, 0, ky)

    # Emblem shade: only the way out darkens it.
    shade = np.ones(n)
    if n > ie + 1:
        tail = np.clip(b['icon.shade'][ie + 1:], 0, 1)
        tail = np.minimum.accumulate(gauss(tail, 1.0))
        shade[ie + 1:] = keyframed(np.concatenate([[1.0], tail]), .04, smooth=0, med=1)[1:]
    out['icon.shade'] = np.clip(shade, 0, 1)

    # Ring and frame: fade in once, out once; the frame keeps one size.
    out['ring.alpha'] = fade(b['ring.alpha'], ie)
    out['ring.scale'] = np.ones(n)
    out['frame.alpha'] = fade(b['frame.alpha'], ie)
    fs = b['frame.scale'][:ie + 1][b['frame.alpha'][:ie + 1] >= .9]
    size = float(np.median(fs)) if len(fs) else 1.0
    out['frame.scale'] = np.full(n, 1.0 if abs(size - 1) < .03 else round(size, 3))

    if template_pips:  # the shared template's pips: already clean
        for c in ('pip.alpha', 'pip.radius', 'pip.flare', 'pip.spin'):
            out[c] = np.array(b[c], float)
        return out, False

    # Pips: fade, slide, one glow, spin.
    pa = fade(b['pip.alpha'], ie, longest_in=10)
    if pa.max() < .5:  # never seen in the preview: they arrive with the kill mark and leave with the emblem
        pa = ramp(n, mark - 2, mark + 4, 0.0, 1.0)
        if n > ie + 1:
            gone = next((i for i in range(ie + 1, n) if out['icon.alpha'][i] < .1), n - 1)
            pa[ie + 1:] = ramp(n, gone - 6, gone, 1.0, 0.0)[ie + 1:]
    full = next((i for i in range(ie + 1) if pa[i] >= .9), ie)
    late = full - (mark + 20)
    if late > 0:  # the preview hid them under its own effects until then: they arrive with the kill mark, as everywhere else
        pa[:ie + 1] = ramp(ie + 1, mark - 2, mark + 4, 0.0, 1.0)
        fl0 = np.array(b['pip.flare'], float)
        b = dict(b)
        b['pip.flare'] = np.concatenate([fl0[late:ie + 1], np.zeros(late), fl0[ie + 1:]])
    out['pip.alpha'] = pa
    on = pa >= .5
    rad = hold(np.clip(b['pip.radius'], .7, 1.4), on)
    out['pip.radius'] = keyframed(rad, .03, anchor=ie) if rad is not None else np.ones(n)
    fl = np.clip(gauss(np.array(b['pip.flare'], float), 1.5), 0, 1)
    if fl.max() > .15:
        p = int(np.argmax(fl))
        fl[:p + 1] = np.maximum.accumulate(fl[:p + 1])
        fl[p:] = np.minimum.accumulate(fl[p:])
        keys = rdp(fl, .05)
        fl = np.clip(pchip(keys, fl[keys], n), 0, 1)
    else:
        fl = np.zeros(n)
    out['pip.flare'] = fl

    period = 360.0 / count
    spin = np.array(b['pip.spin'], float)
    angles = b.get('angles')
    if angles:
        offset = wrap(circ_mean(angles, period) - layout(count)[0] % period, period)
        spin = spin + wrap(offset - spin[ie], period)
    spin = hold(spin, on)
    if spin is None:
        spin = np.zeros(n)
    # Single-frame slips (a blob that was something else): out before smoothing.
    med = median_filter(spin, 7)
    mad = median_filter(np.abs(spin - med), 7) + 1.0
    spin = np.where(np.abs(spin - med) > 4 * mad, med, spin)
    spin[ie + 1:] = spin[ie]  # the pips leave where they rest
    if count == 1:
        out['pip.spin'] = np.full(n, round(float(spin[ie]) % 360, 2))
    else:
        out['pip.spin'] = keyframed(spin, max(6.0, .12 * period), smooth=2.0, med=5, anchor=ie)
    return out, True


def clean_file(path, template):
    data, orbit, box = am.load(path)
    lines = []
    head = [l for l in path.read_text(encoding='utf-8').splitlines() if l.startswith('#')]
    lines += head
    if not any('clean_motion' in l for l in head):
        lines.append('# cleaned by tools/killbanner/clean_motion.py: emblem on the ring centre, smoothed keyframed channels.')
    lines.append(f'orbit={orbit:.3f}')
    if box:
        lines.append(f'headshot.box={box}')
    measured = 0
    for k in sorted(data):
        b = data[k]
        t = template.get(k)
        borrowed = t is not None and len(t['pip.spin']) == len(b['pip.spin']) and np.allclose(t['pip.spin'], b['pip.spin'], atol=.01) \
            and np.allclose(t['pip.radius'], b['pip.radius'], atol=.01)
        cleaned, own = clean_banner(b, k, borrowed)
        measured += own
        lines.append(f'k{k}.introEnd={b["introEnd"]}')
        lines.append(f'k{k}.exit={b["exit"]}')
        lines.append(f'k{k}.mark={b.get("mark", 11)}')
        if 'spray' in b:
            lines.append(f'k{k}.spray={b["spray"]}')
        for c in CHANNELS:
            lines.append(f'k{k}.{c}=' + ','.join(f'{v:.3f}'.rstrip('0').rstrip('.') if abs(v) > 5e-4 else '0' for v in cleaned[c]))
    path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    return measured


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--dir', default=str(ASSETS / 'motion'))
    ap.add_argument('--backup', default=str(HERE / 'out' / 'motion' / 'before-clean'))
    ap.add_argument('--plot', default='')
    ap.add_argument('--plots', default=str(HERE / 'out' / 'motion' / 'audit'))
    args = ap.parse_args()
    src, backup = Path(args.dir), Path(args.backup)
    backup.mkdir(parents=True, exist_ok=True)
    tdata, _, _ = am.load(ASSETS / 'template.properties')
    files = sorted(src.glob('*.properties'))
    for f in files:  # the measured originals are kept once; a re-run cleans those again, not the cleaned ones
        if not (backup / f.name).exists():
            shutil.copy2(f, backup / f.name)
        shutil.copy2(backup / f.name, f)
    total = 0
    for f in files:
        total += clean_file(f, tdata)
    print(f'{len(files)} skins cleaned; {total} banners with their own pips')
    for skin in [s for s in args.plot.split(',') if s]:
        before, _, _ = am.load(backup / f'{skin}.properties')
        after, _, _ = am.load(src / f'{skin}.properties')
        print('plots', am.plot(skin, before, Path(args.plots), '-before'), am.plot(skin, after, Path(args.plots), '-after'))


if __name__ == '__main__':
    main()
