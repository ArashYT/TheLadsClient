#!/usr/bin/env python3
"""Measures every still skin's kill banner motion from its preview videos (videos.json from map_videos.py), a few
videos at a time, keeping for each skin the video whose banner art matches best. Writes motion/<skin>.properties, the
check sheets and a summary. Developer tool.

python measure_all.py [--workers 4] [--per-skin 4] [--only skin,skin] [--out DIR]
"""
import argparse
import contextlib
import json
import sys
import time
import traceback
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import measure_video as mv  # noqa: E402

FOLDER = Path('G:/Val skins Previews')


def one(skin, video, out):
    """Measures one (skin, video) into a scratch properties file; returns (skin, video, summary or error)."""
    scratch = Path(out) / 'try' / skin / Path(video).stem.replace(' ', '_')
    scratch.mkdir(parents=True, exist_ok=True)
    mv.MOTION = scratch  # the properties file goes to the scratch folder; the winner is copied later
    try:
        t = time.time()
        with open(scratch / 'measure.log', 'w', encoding='utf-8') as log, contextlib.redirect_stdout(log):
            result = mv.measure(skin, FOLDER / video, check_dir=str(scratch), quiet=False)
        if result:
            result['seconds'] = round(time.time() - t, 1)
            result['scratch'] = str(scratch)
        return skin, video, result
    except Exception as failure:  # one bad video must not stop the batch
        return skin, video, {'error': ''.join(traceback.format_exception_only(type(failure), failure)).strip()}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--workers', type=int, default=4)
    parser.add_argument('--per-skin', type=int, default=4)
    parser.add_argument('--only', default='')
    parser.add_argument('--resume', action='store_true', help='skip videos already in results.json')
    parser.add_argument('--out', default=str(HERE / 'out' / 'motion'))
    args = parser.parse_args()
    videos = json.loads((HERE / 'videos.json').read_text(encoding='utf-8'))
    only = [x for x in args.only.split(',') if x]
    tasks = []
    for skin, cands in videos.items():
        if only and skin not in only:
            continue
        for video in cands[:args.per_skin]:
            tasks.append((skin, video))
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    results = {}
    done_before = set()
    if args.resume and (out / 'results.json').exists():  # keep what an earlier run measured
        results = json.loads((out / 'results.json').read_text(encoding='utf-8'))
        done_before = {(skin, video) for skin, tries in results.items() for video, res in tries if res and 'error' not in res}  # failures get another go
        results = {skin: [t for t in tries if t[1] and 'error' not in t[1]] for skin, tries in results.items()}
        tasks = [t for t in tasks if t not in done_before]
    print(f'{len(tasks)} measurements for {len({t[0] for t in tasks})} skins, {args.workers} at a time ({len(done_before)} kept from before)')
    done = 0
    with ProcessPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(one, skin, video, str(out)): (skin, video) for skin, video in tasks}
        for future in as_completed(futures):
            skin, video, result = future.result()
            results.setdefault(skin, []).append((video, result))
            done += 1
            if result and 'error' in result:
                print(f'[{done}/{len(tasks)}] {skin} <- {video}: ERROR {result["error"][:120]}')
            elif result:
                print(f'[{done}/{len(tasks)}] {skin} <- {video}: kills {len(result["kills"])}, measured {result["measured"]}, emblem {result["emblem"]:.3f}, scale {result["scale"]:.3f}, orbit {result["orbit"]:.2f}, {result["seconds"]} s')
            else:
                print(f'[{done}/{len(tasks)}] {skin} <- {video}: no banner found')
            (out / 'results.json').write_text(json.dumps(results, indent=1, default=str), encoding='utf-8')
    # The winner a skin: most kill counts measured, then the best emblem match.
    summary = {}
    checks = out / 'checks'
    checks.mkdir(exist_ok=True)
    for skin, tries in results.items():
        good = [(v, r) for v, r in tries if r and 'error' not in r]
        if not good:
            summary[skin] = None
            continue
        good = [vr for vr in good if vr[1]['emblem'] >= .2]  # an emblem the matcher barely told from the background: no trust in the motion
        if not good:
            summary[skin] = None
            continue
        video, best = max(good, key=lambda vr: (len(vr[1]['measured']), vr[1]['emblem']))
        src = Path(best['scratch']) / f'{skin}.properties'
        dst = mv.ASSETS / 'motion' / f'{skin}.properties'
        dst.parent.mkdir(exist_ok=True)
        dst.write_bytes(src.read_bytes())
        for sheet in Path(best['scratch']).glob('check-*.png'):
            (checks / sheet.name).write_bytes(sheet.read_bytes())
        summary[skin] = dict(video=video, measured=best['measured'], emblem=round(best['emblem'], 3), scale=round(best['scale'], 3),
                             orbit=round(best['orbit'], 3), marks=best['marks'], intro=best['intro'], exit=best['exit'], kills=best['kills'])
    (out / 'summary.json').write_text(json.dumps(summary, indent=1), encoding='utf-8')
    missing = [s for s, v in summary.items() if v is None]
    print(f'{len(summary) - len(missing)} skins measured, none for: {missing}')
    scales = [v['scale'] for v in summary.values() if v]
    if scales:
        import statistics
        print(f'art scale median {statistics.median(scales):.3f} screen px an art px = {statistics.median(scales) / mv.CELL:.3f} cell px (ART_SCALE now {mv.ART_NOMINAL})')


if __name__ == '__main__':
    main()
