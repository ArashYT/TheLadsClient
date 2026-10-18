"""Median table of ladsbench results. usage: python -I report.py <out dir with *-r<N>.json> [...]
Prints one markdown table per scene metric: config | runs | spawn | hudoff | entities | fly (avg FPS / 1% low / p99 ms) + startup/join."""
import glob, json, os, re, statistics, sys

rows = {}
for d in sys.argv[1:]:
    for f in glob.glob(os.path.join(d, '*.json')):
        m = re.match(r'(.+)-r(\d+)\.json$', os.path.basename(f))
        if not m: continue
        r = json.load(open(f, encoding='utf-8'))
        if r.get('error') or not r.get('scenes'): print(f'skip {f}: {r.get("error")}', file=sys.stderr); continue
        rows.setdefault(m.group(1), []).append(r)

def med(vals):
    vals = [v for v in vals if v is not None]
    return statistics.median(vals) if vals else None

scenes = ['spawn', 'hudoff', 'entities', 'fly']
print('| config | runs | ' + ' | '.join(f'{s} avg / 1% low / p99 ms' for s in scenes) + ' | startup s | join s |')
print('|---|---|' + '---|' * len(scenes) + '---|---|')
for cfg, rs in sorted(rows.items()):
    cells = []
    for s in scenes:
        sc = [next((x for x in r['scenes'] if x['scene'] == s), None) for r in rs]
        sc = [x for x in sc if x]
        if not sc: cells.append('-'); continue
        cells.append(f"{med([x['avgFps'] for x in sc]):.0f} / {med([x['low1Fps'] for x in sc]):.0f} / {med([x['p99Ms'] for x in sc]):.2f}")
    st, jn = med([r['startupMs'] for r in rs]), med([r['joinMs'] for r in rs])
    print(f'| {cfg} | {len(rs)} | ' + ' | '.join(cells) + f' | {st / 1000:.1f} | {jn / 1000:.1f} |')
if '--raw' in sys.argv:
    for cfg, rs in sorted(rows.items()):
        for r in rs:
            print(cfg, r['label'], ' '.join(f"{x['scene']}={x['avgFps']:.0f}/{x['low1Fps']:.0f}" for x in r['scenes']), r['startupMs'], r['joinMs'])
