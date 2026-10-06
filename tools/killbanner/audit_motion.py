"""Audits the shipped motion files (killbanner/motion/<skin>.properties) for what makes a banner look glitchy: jitter in
the pip spin, radius, alpha and glow, the emblem's resting offset and wobble, and the frame's size steps. Prints the
worst skins and draws channel graphs.

python audit_motion.py [--plot skin,skin] [--out DIR] [--dir MOTION_DIR]
"""
import argparse
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
MOTION = HERE.parents[1] / 'TheLadsCore/common/src/main/resources/assets/theladscore/killbanner/motion'
CHANNELS = ['icon.alpha', 'icon.scale', 'icon.y', 'icon.shade', 'ring.alpha', 'ring.scale', 'frame.alpha', 'frame.scale',
            'pip.alpha', 'pip.radius', 'pip.flare', 'pip.spin']


def load(path):
    """{kills: {'introEnd', 'exit', 'mark', 'angles', channel: np.array}}, orbit, headshot box."""
    data, orbit, box = {}, 1.0, None
    for line in path.read_text(encoding='utf-8').splitlines():
        if '=' not in line or line.startswith('#'):
            continue
        key, value = line.split('=', 1)
        if key == 'orbit':
            orbit = float(value)
            continue
        if key == 'headshot.box':
            box = value
            continue
        k = int(key[1])
        name = key[3:]
        b = data.setdefault(k, {})
        if name in ('introEnd', 'exit', 'mark'):
            b[name] = int(value)
        elif name == 'pip.angles':
            b['angles'] = [float(x) for x in value.split(',')]
        elif name == 'spray':
            b['spray'] = value
        else:
            b[name] = np.array([float(x) for x in value.split(',')])
    return data, orbit, box


def jitter(v):
    """Mean absolute second difference: how much a series zigzags (a smooth move scores near 0)."""
    return float(np.mean(np.abs(np.diff(v, 2)))) if len(v) > 2 else 0.0


def metrics(b, count):
    ie = b['introEnd']
    spin = b['pip.spin']
    d = np.diff(spin)
    period = 360.0 / max(1, count)
    pa = b['pip.alpha']
    lit = np.argmax(pa >= .99) if (pa >= .99).any() else len(pa)
    dips = float(np.max(1 - pa[lit:ie + 1])) if lit <= ie else 0.0
    return {
        'spin_jumps': int(np.sum(np.abs(d) > 6)),
        'spin_back_forth': int(np.sum(np.diff(np.sign(np.round(d, 1))[np.abs(d) > .3]) != 0)) if (np.abs(d) > .3).sum() > 1 else 0,
        'spin_jitter': jitter(spin),
        'spin_rest': float(spin[ie] % period),
        'radius_jitter': jitter(b['pip.radius']),
        'radius_rest': float(b['pip.radius'][ie]),
        'alpha_jitter': jitter(pa),
        'alpha_dips': dips,
        'flare_max': float(b['pip.flare'].max()),
        'flare_jitter': jitter(b['pip.flare']),
        'y_rest': float(b['icon.y'][ie]),
        'y_jitter': jitter(b['icon.y']),
        'y_max': float(np.max(np.abs(b['icon.y'][:ie + 1]))),
        'scale_jitter': jitter(b['icon.scale']),
        'scale_rest': float(b['icon.scale'][ie]),
        'frame_scale_steps': int(np.sum(np.abs(np.diff(b['frame.scale'])) > .02)),
        'frame_alpha_jitter': jitter(b['frame.alpha']),
        'ring_alpha_jitter': jitter(b['ring.alpha']),
    }


def plot(skin, data, out, tag=''):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig, axes = plt.subplots(len(data), 4, figsize=(20, 3.2 * len(data)), squeeze=False)
    for row, k in enumerate(sorted(data)):
        b = data[k]
        ie = b['introEnd']
        groups = [('emblem', ['icon.alpha', 'icon.scale', 'icon.shade']), ('emblem y (cell px)', ['icon.y']),
                  ('ring / frame', ['ring.alpha', 'frame.alpha', 'frame.scale']), ('pips', ['pip.alpha', 'pip.radius', 'pip.flare'])]
        for col, (title, chans) in enumerate(groups):
            ax = axes[row][col]
            for c in chans:
                ax.plot(b[c], label=c, lw=1.2)
            ax.axvline(ie, color='k', lw=.6, ls=':')
            ax.axvline(b.get('mark', 11), color='r', lw=.6, ls=':')
            ax.set_title(f'{skin} k{k} {title}', fontsize=9)
            ax.legend(fontsize=7, loc='best')
        ax2 = axes[row][3].twinx()
        ax2.plot(b['pip.spin'], color='m', lw=1.2, label='pip.spin (deg)')
        ax2.legend(fontsize=7, loc='lower right')
    fig.tight_layout()
    path = out / f'motion-{skin}{tag}.png'
    fig.savefig(path, dpi=80)
    plt.close(fig)
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--plot', default='')
    ap.add_argument('--out', default=str(HERE / 'out' / 'motion' / 'audit'))
    ap.add_argument('--dir', default=str(MOTION))
    ap.add_argument('--tag', default='')
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    rows = []
    for path in sorted(Path(args.dir).glob('*.properties')):
        data, orbit, box = load(path)
        for k, b in sorted(data.items()):
            if 'pip.spin' not in b:
                continue
            rows.append((path.stem, k, metrics(b, k)))
    keys = list(rows[0][2])
    print(f'{len(rows)} banners in {len({r[0] for r in rows})} skins')
    for key in keys:
        vals = np.array([r[2][key] for r in rows], float)
        worst = sorted(rows, key=lambda r: -abs(r[2][key]))[:6]
        print(f'{key:18s} median {np.median(vals):8.3f}  p90 {np.percentile(vals, 90):8.3f}  max {vals.max():8.3f}  worst: '
              + ', '.join(f'{s}-k{k}={m[key]:.2f}' for s, k, m in worst))
    for skin in [s for s in args.plot.split(',') if s]:
        data, _, _ = load(Path(args.dir) / f'{skin}.properties')
        print('plot', plot(skin, data, out, args.tag))


if __name__ == '__main__':
    main()
