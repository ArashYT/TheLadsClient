#!/usr/bin/env python
"""Allocation attribution from jdk.ObjectAllocationSample (weight = bytes the sample stands for).
usage: jfr_alloc.py <jfr> [--jfr bin] [--out json] [--after-seconds S]
Reports allocation rate (MB/s over the recording), top threads, top mods by innermost mod frame (and on path), top allocated classes.
"""
import argparse, collections, json, os, re, subprocess, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jfr_attrib as A
ap = argparse.ArgumentParser(); ap.add_argument('rec'); ap.add_argument('--jfr', default=r'C:\Users\Arash\AppData\Roaming\.theladsclient\runtime\java-17\jdk-17.0.20.1+1\bin\jfr.exe'); ap.add_argument('--out')
ap.add_argument('--mods', default=os.path.join(os.environ.get('APPDATA', ''), '.theladsclient', 'instances', 'zero-smp-1.20.1', 'minecraft', 'mods'))
a = ap.parse_args()
pkg2mod, ids = A.build_maps(a.mods)
def mb(v):
    m = re.match(r'([\d.,]+)\s*(bytes|kB|MB|GB)', v.strip())
    if not m: return 0.0
    return float(m.group(1).replace(',', '')) * {'bytes': 1 / 1048576, 'kB': 1 / 1024, 'MB': 1.0, 'GB': 1024.0}[m.group(2)]
thr = collections.Counter(); first = collections.Counter(); path = collections.Counter(); cls = collections.Counter(); sect = collections.Counter()
tmin = tmax = None; total = 0.0
p = subprocess.Popen([a.jfr, 'print', '--events', 'jdk.ObjectAllocationSample', '--stack-depth', '40', a.rec], stdout=subprocess.PIPE, text=True, encoding='utf8', errors='replace')
cur = {}; frames = []
def flush():
    global total
    if not cur or 'weight' not in cur: return
    w = mb(cur['weight']); total += w
    t = A.norm_thread(cur.get('eventThread', '?')); thr[t] += w
    cls[cur.get('objectClass', '?')] += w
    f = None; seen = []
    for c, m in frames:
        mod = A.mod_of_method(m, ids) or A.mod_of_class(c, pkg2mod)
        if mod:
            if f is None: f = mod
            if mod not in seen: seen.append(mod)
    first[(t if t in ('main', 'Server thread') else 'other threads', f or '(vanilla/forge/jdk)')] += w
    for m in seen: path[m] += w
for line in p.stdout:
    if line.startswith('jdk.ObjectAllocationSample'):
        flush(); cur = {}; frames = []
    elif re.match(r'^\s+(startTime|objectClass|weight|eventThread) = ', line):
        k, v = re.match(r'^\s+(\w+) = (.*)$', line).groups()
        if k == 'eventThread': v = re.search(r'"([^"]*)"', v).group(1)
        if k == 'objectClass': v = v.split(' ')[0].strip('"')
        if k == 'startTime':
            m = re.search(r'(\d+):(\d+):(\d+\.\d+)', v); t = int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3))
            tmin = t if tmin is None else min(tmin, t); tmax = t if tmax is None else max(tmax, t)
        cur[k] = v
    else:
        m = A.FRAME.match(line)
        if m: frames.append((m.group(1), m.group(2)))
flush()
dur = max(1.0, (tmax or 0) - (tmin or 0))
res = {'total_mb': round(total), 'seconds': round(dur), 'rate_mb_s': round(total / dur),
       'threads': [(k, round(v), round(100 * v / total, 1)) for k, v in thr.most_common(8)],
       'by_mod_first_frame': [(k[0] + ' / ' + k[1], round(v), round(100 * v / total, 1)) for k, v in first.most_common(25)],
       'on_path': [(k, round(v), round(100 * v / total, 1)) for k, v in path.most_common(20)],
       'classes': [(k, round(v), round(100 * v / total, 1)) for k, v in cls.most_common(12)]}
if a.out: json.dump(res, open(a.out, 'w'), indent=1)
print(f"allocated ~{res['total_mb']} MB in {res['seconds']} s = {res['rate_mb_s']} MB/s (sampled estimate)")
for k in ('threads', 'by_mod_first_frame', 'on_path', 'classes'):
    print('--', k); [print(f'  {s:8d} MB {pct:5.1f}%  {n}') for n, s, pct in res[k][:14]]
