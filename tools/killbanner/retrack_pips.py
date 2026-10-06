"""Re-tracks every measured skin's pip spin with pip_track.py's shape-and-spacing tracker and writes the measured motion
files with the new spin (out/motion/retracked/), where the tracker is confident; clean_motion.py then cleans those.

python retrack_pips.py [--workers 3] [--only skin,skin]
"""
import argparse
import json
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np

import audit_motion as am
import pip_track as pt

HERE = Path(__file__).resolve().parent
SRC = HERE / 'out' / 'motion' / 'before-clean'
DST = HERE / 'out' / 'motion' / 'retracked'


def wrap(x, period):
    return (x + period / 2) % period - period / 2


def old_rest(b, count):
    """Where the measurer saw the pips settle (its settled angles, else its spin at the settled frame), layout-relative."""
    period = 360.0 / count
    lay = pt.mv.pip_layout(count)[0]
    if b.get('angles'):
        a = np.array(b['angles'], float) * 2 * np.pi / period
        return float(np.angle(np.mean(np.exp(1j * a))) * period / (2 * np.pi)) - lay
    return float(b['pip.spin'][b['introEnd']])


def retrack(skin):
    summary = json.loads((HERE / 'out' / 'motion' / 'summary.json').read_text(encoding='utf-8'))
    entry = summary.get(skin)
    src = SRC / f'{skin}.properties'
    text = src.read_text(encoding='utf-8')
    data, orbit, _ = am.load(src)
    notes = []
    try:
        frames, fps, banners = pt.kills_of(skin, pt.FOLDER / entry['video'], data)
        tracker = pt.Tracker(skin, entry['scale'], orbit)
        if not tracker.ok:
            raise RuntimeError('no pip art')
        new = {}
        for begin, c, length in banners:
            if c == 1:  # one pip: no spacing to check it against; the measurer's settled angle stands
                continue
            b = data[c]
            ie = b['introEnd']
            period = 360.0 / c
            res = pt.track_banner(tracker, frames, begin, min(length, ie + 91), c, fps / 60)
            if res is None or len(res[0]) < 12:
                notes.append(f'k{c} -')
                continue
            deg, _, peaks = res
            L = len(deg)
            on = np.flatnonzero(b['pip.alpha'][:min(L, ie + 1)] >= .5)
            bg = np.arange(0, max(0, b.get('mark', 11) - 4))
            if len(on) < 6:
                notes.append(f'k{c} pips unseen')
                continue
            s_on = float(np.median(peaks[on]))
            s_bg = float(np.median(peaks[bg])) if len(bg) >= 3 else float(np.percentile(peaks, 15))
            if s_on - s_bg < .08 or s_on < .2:
                notes.append(f'k{c} unsure ({s_on:.2f} vs {s_bg:.2f})')
                continue
            path = np.array([np.median(deg[max(0, i - 3):i + 4]) for i in range(len(deg))])  # frame noise is not travel
            travel = float(np.sum(np.abs(np.diff(path[on[0]:]))))
            if c < 5 and travel > 4 * period:
                notes.append(f'k{c} wild ({travel:.0f} deg)')
                continue
            # The rest is where the measurer saw the pips settle (many settled frames); the tracker supplies the motion,
            # as far as it is sure of the pips (its score stays high; after that come the exit and the next banner).
            strong = peaks >= s_bg + .5 * (s_on - s_bg)
            conf_end, gap = on[0], 0
            for f in range(on[0], L):
                if strong[f]:
                    conf_end, gap = f, 0
                else:
                    gap += 1
                    if gap > 6:
                        break
            rest0 = old_rest(b, c)
            near = lambda f: rest0 + period * np.round((deg[f] - rest0) / period)
            agree = [abs(deg[f] - near(f)) <= 6 for f in range(on[0], conf_end + 1)]
            # The last run of 5+ frames at the settled angle: the pips have arrived there (later frames may be the exit
            # or the next banner's pips).
            run_start, best = None, None
            for j, ok in enumerate(agree + [False]):
                if ok and run_start is None:
                    run_start = j
                elif not ok and run_start is not None:
                    if j - run_start >= 5:
                        best = run_start
                    run_start = None
            if best is None:  # the tracker never settles where the measurer saw the pips: keep the old spin
                notes.append(f'k{c} disagrees ({deg[conf_end] % period:.0f} vs {rest0 % period:.0f})')
                continue
            settle = on[0] + best
            rest = float(near(settle))
            deg = np.array(deg, float)
            deg[settle:] = rest
            settle += 2
            new_ie = max(ie, min(conf_end, settle))  # never shorter than the old intro; longer while the pips still turn
            spin = np.full(new_ie + 1, rest)
            upto = min(L, settle, new_ie + 1)
            spin[:upto] = deg[:upto]
            # The other channels hold their settled values over the longer intro; the way out follows unchanged.
            ext = {}
            for ch in am.CHANNELS:
                v = np.array(b[ch], float)
                intro = np.concatenate([v[:ie + 1], np.full(new_ie - ie, v[ie])])
                ext[ch] = np.concatenate([intro, v[ie + 1:]])
            ext['pip.spin'] = np.concatenate([spin, np.full(len(b['pip.spin']) - (ie + 1), rest)])
            new[c] = (new_ie, ext)
            notes.append(f'k{c} {s_on:.2f}/{s_bg:.2f} rest {rest % period:.0f} (old {old_rest(b, c) % period:.0f}) intro {ie}->{new_ie}')
        lines = []
        for line in text.splitlines():
            key = line.split('=', 1)[0]
            if len(key) > 3 and key[0] == 'k' and key[1].isdigit() and int(key[1]) in new:
                k, name = int(key[1]), key[3:]
                new_ie, ext = new[k]
                if name == 'pip.angles':
                    continue
                if name == 'introEnd':
                    line = f'k{k}.introEnd={new_ie}'
                elif name in ext:
                    line = f'k{k}.{name}=' + ','.join(f'{v:.3f}' for v in ext[name])
            lines.append(line)
        (DST / src.name).write_text('\n'.join(lines) + '\n', encoding='utf-8')
        return skin, f'{len(new)} retracked: ' + '; '.join(notes)
    except Exception as failure:  # keep the old spin for this skin
        (DST / src.name).write_text(text, encoding='utf-8')
        return skin, f'kept ({type(failure).__name__}: {failure})'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--workers', type=int, default=3)
    ap.add_argument('--only', default='')
    args = ap.parse_args()
    DST.mkdir(parents=True, exist_ok=True)
    skins = [s for s in args.only.split(',') if s] or sorted(p.stem for p in SRC.glob('*.properties'))
    with ProcessPoolExecutor(max_workers=args.workers) as pool:
        for skin, note in pool.map(retrack, skins):
            print(f'{skin}: {note}', flush=True)


if __name__ == '__main__':
    main()
