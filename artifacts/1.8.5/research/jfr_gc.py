#!/usr/bin/env python
"""GC / heap summary of a JFR recording.

usage: jfr_gc.py <recording.jfr> [--jfr <jfr binary>] [--after-seconds N]   (drops GCs before N s of recording, e.g. startup)

Reports: collections (young/mixed/old/full), STW pause count/avg/p95/max/total, share of wall time in pauses, pauses > 20/50/100 ms,
live set (heap used right after a GC: median and max), allocation-rate estimate (jdk.GCHeapSummary before/after deltas), and the collector name.
"""
import argparse, re, statistics, subprocess, sys, datetime

def ms(v):
    v = v.strip()
    m = re.match(r'([\d.,]+)\s*(ns|us|µs|ms|s|m)\b', v)
    if not m: return None
    n = float(m.group(1).replace(',', ''))
    return n * {'ns': 1e-6, 'us': 1e-3, 'µs': 1e-3, 'ms': 1.0, 's': 1000.0, 'm': 60000.0}[m.group(2)]

def mb(v):
    m = re.match(r'([\d.,]+)\s*(bytes|kB|MB|GB)', v.strip())
    if not m: return None
    n = float(m.group(1).replace(',', ''))
    return n * {'bytes': 1 / 1048576, 'kB': 1 / 1024, 'MB': 1.0, 'GB': 1024.0}[m.group(2)]

def events(jfr, rec, names):
    p = subprocess.run([jfr, 'print', '--events', ','.join(names), rec], capture_output=True, text=True, encoding='utf8', errors='replace')
    cur = None; body = {}
    for line in p.stdout.splitlines():
        m = re.match(r'^(jdk\.\w+) \{', line)
        if m:
            cur = m.group(1); body = {}; continue
        if line.startswith('}') and cur:
            yield cur, body; cur = None; continue
        m = re.match(r'^\s+(\w+) = (.*)$', line)
        if m and cur: body[m.group(1)] = m.group(2)

def t(s):
    try:
        return datetime.datetime.strptime(s.strip(), '%H:%M:%S.%f')
    except Exception:
        return None

def main():
    ap = argparse.ArgumentParser(); ap.add_argument('rec'); ap.add_argument('--jfr', default='jfr'); ap.add_argument('--after-seconds', type=float, default=0.0)
    a = ap.parse_args()
    gcs = []; after = []; t0 = None; tN = None; coll = None; cfg = None
    for name, b in events(a.jfr, a.rec, ['jdk.GarbageCollection', 'jdk.GCHeapSummary', 'jdk.GCConfiguration']):
        st = t(b.get('startTime', ''))
        if st:
            t0 = st if t0 is None or st < t0 else t0
            tN = st if tN is None or st > tN else tN
        if name == 'jdk.GCConfiguration':
            cfg = (b.get('youngCollector'), b.get('oldCollector')); continue
        if name == 'jdk.GarbageCollection':
            gcs.append((st, b.get('name', '?'), b.get('cause', '?'), ms(b.get('sumOfPauses', '0 ms')) or 0.0, ms(b.get('longestPause', '0 ms')) or 0.0))
        elif name == 'jdk.GCHeapSummary' and b.get('when', '').strip('"') == 'After GC':
            after.append((st, mb(b.get('heapUsed', '0 bytes')) or 0.0))
    if t0 is None: print('no GC events'); return
    lo = t0 + datetime.timedelta(seconds=a.after_seconds)
    gcs = [g for g in gcs if g[0] and g[0] >= lo]; after = [x for x in after if x[0] and x[0] >= lo]
    wall = (tN - lo).total_seconds() if tN and tN > lo else 1.0
    pauses = sorted(g[3] for g in gcs)
    print('collectors:', cfg)
    print(f'window: {wall:.0f} s, collections: {len(gcs)}')
    kinds = {}
    for g in gcs: kinds[g[1]] = kinds.get(g[1], 0) + 1
    print('by kind:', kinds)
    if pauses:
        p95 = pauses[int(len(pauses) * 0.95) - 1] if len(pauses) > 1 else pauses[0]
        print(f'pause ms: avg {statistics.mean(pauses):.1f}  p95 {p95:.1f}  max {pauses[-1]:.1f}  total {sum(pauses):.0f} ms = {100 * sum(pauses) / (wall * 1000):.2f}% of wall')
        print('pauses >20 ms: %d  >50 ms: %d  >100 ms: %d' % tuple(sum(1 for p in pauses if p > x) for x in (20, 50, 100)))
        worst = sorted(gcs, key=lambda g: -g[3])[:5]
        print('worst:', [(g[1], g[2], round(g[3], 1)) for g in worst])
    if after:
        used = sorted(x[1] for x in after)
        print(f'heap used after GC (MB): median {statistics.median(used):.0f}  p90 {used[int(len(used) * .9) - 1]:.0f}  max {used[-1]:.0f}  last {after[-1][1]:.0f}')

if __name__ == '__main__':
    main()
