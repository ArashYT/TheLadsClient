"""Maps the client's kill banner skins to Valorant's own kill banner data (FModel export of
ShooterGame/Content/UI/InGame/KillBanner: Properties JSON + PNG textures), by pixel-identical art: the client's
Kingdom Archives art is the game's textures. Resolves each KillBannerData_<skin> class with its parents, then writes
out/game-map.json: per client skin, the game data per variant (PrimaryColor, slice radius, headshot offset, textures).

python game_map.py --export <KillBanner export dir> [--out out/game-map.json]
"""
import argparse
import collections
import json
import os

import numpy as np
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[1] / 'TheLadsCore/common/src/main/resources/assets/theladscore/killbanner'


def pixels(path):
    return np.asarray(Image.open(path).convert('RGBA')).astype(np.int16)


class Index:
    """Game textures by size; the client's copies are re-encoded, so a match is the same size within a few levels a channel."""

    def __init__(self, export):
        self.by_size = collections.defaultdict(list)
        for p in export.rglob('*.png'):
            im = Image.open(p)
            self.by_size[im.size].append((p.relative_to(export).as_posix(), None))

    def find(self, path, export):
        im = Image.open(path)
        best, best_d = None, 4.0
        for i, (rel, px) in enumerate(self.by_size.get(im.size, [])):
            if px is None:
                px = pixels(export / rel)
                self.by_size[im.size][i] = (rel, px)
            d = float(np.abs(pixels(path) - px).mean())
            if d < best_d:
                best, best_d = rel, d
        return [best] if best else None


def short(v):
    """'Texture2D'/Game/.../Oni_Emblem.Oni_Emblem'' -> 'Oni_Emblem'; other values unchanged."""
    if isinstance(v, dict) and 'ObjectName' in v:
        n = v['ObjectName']
        return n.split("'")[1].rsplit('.', 1)[-1] if "'" in n else n
    return v


def field(k):
    return 'KillWheel-TXT' if k.startswith('KillWheel-TXT') else k.rsplit('_', 2)[0] if k.count('_') >= 2 else k


def load_data(export):
    """Every KillBannerData class: its parent class and its own KillBannerData properties."""
    data = {}
    for f in sorted(export.glob('*/*.json')):
        if not f.stem.lower().startswith('killbannerdata'):
            continue
        objs = json.loads(f.read_text(encoding='utf-8'))
        sup, props = None, {}
        for o in objs:
            if o.get('Type') == 'BlueprintGeneratedClass':
                s = o.get('Super') or o.get('SuperStruct')
                if isinstance(s, dict):
                    n = s.get('ObjectName', '')
                    sup = n.split("'")[1].rsplit('.', 1)[-1] if "'" in n else n
            p = o.get('Properties', {})
            if 'KillBannerData' in p:
                props = p['KillBannerData']
        data[f.stem] = {'super': sup, 'props': props, 'folder': f.parent.name}
    return data


def resolve(data, name, seen=()):
    e = data.get(name)
    if e is None:
        return {}
    sup = e['super']
    if sup and sup.endswith('_C'):
        sup = sup[:-2]
    out = dict(resolve(data, sup, seen + (name,))) if sup and sup in data and sup not in seen else {}
    for k, v in e['props'].items():
        out[field(k)] = v
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--export', required=True)
    ap.add_argument('--out', default=str(HERE / 'out/game-map.json'))
    args = ap.parse_args()
    export = Path(args.export)
    shared = dict(l.split('=', 1) for l in (ASSETS / 'shared.properties').read_text(encoding='utf-8').splitlines()
                  if '=' in l and not l.startswith('#'))
    index = Index(export)
    data = load_data(export)
    resolved = {k: resolve(data, k) for k in data}
    # texture name -> data classes using it as emblem (per variant) / pip / ring / frame
    by_emblem = collections.defaultdict(list)
    for k, r in resolved.items():
        for fld in ('Badge_Default_TXT', 'HeadShot_Badge'):
            t = short(r.get(fld))
            if t:
                by_emblem[t].append(k)
    report, mapping = [], {}
    for skin in sorted(os.listdir(ASSETS)):
        if not (ASSETS / skin).is_dir():
            continue
        files = {}
        for png in sorted((ASSETS / skin).glob('*.png')):
            rel = f'{skin}/{png.name}'
            m = index.find(ASSETS / shared.get(rel, rel), export)
            files[png.name] = m
        folders = collections.Counter(m[0].split('/')[0] for m in files.values() if m)
        # the emblem per variant tells the data class (the variant's KillBannerData)
        variants = {}
        for name, m in files.items():
            if not name.startswith('emblem') or not m:
                continue
            v = 0 if name == 'emblem.png' else int(name[len('emblem_v'):-4])
            tex = Path(m[0]).stem
            classes = [c for c in by_emblem.get(tex, [])]
            variants[v] = classes
        mapping[skin] = {'folders': dict(folders), 'files': {n: (m[0] if m else None) for n, m in files.items()}, 'variants': variants}
        report.append(f'{skin}: {dict(folders)} variants {variants}')
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    Path(args.out).write_text(json.dumps({'mapping': mapping, 'data': {k: {'folder': data[k]['folder'], 'super': data[k]['super'],
        'resolved': {f: short(v) if not isinstance(v, dict) or 'ObjectName' in v else v for f, v in r.items()}} for k, r in resolved.items()}},
        indent=1, default=str), encoding='utf-8')
    print('\n'.join(report))
    print(f'wrote {args.out}: {len(mapping)} skins, {len(data)} data classes')


if __name__ == '__main__':
    main()
