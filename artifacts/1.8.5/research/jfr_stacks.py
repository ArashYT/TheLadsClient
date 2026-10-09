#!/usr/bin/env python
"""Top innermost-mod-frame methods (and what they call) for one thread and one mod.
usage: jfr_stacks.py <jfr> --thread "Server thread" --mod Data [--top 12] [--depth 6]   (mod = substring of the jar-derived mod name)
"""
import argparse, collections, re, subprocess, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jfr_attrib as A
ap = argparse.ArgumentParser(); ap.add_argument('rec'); ap.add_argument('--jfr', default=A.__dict__.get('JFR', r'C:\Users\Arash\AppData\Roaming\.theladsclient\runtime\java-17\jdk-17.0.20.1+1\bin\jfr.exe'))
ap.add_argument('--thread', default='Server thread'); ap.add_argument('--mod', required=True); ap.add_argument('--top', type=int, default=12); ap.add_argument('--depth', type=int, default=5)
ap.add_argument('--mods', default=os.path.join(os.environ.get('APPDATA', ''), '.theladsclient', 'instances', 'zero-smp-1.20.1', 'minecraft', 'mods'))
a = ap.parse_args()
pkg2mod, ids = A.build_maps(a.mods)
cnt = collections.Counter(); below = collections.defaultdict(collections.Counter); total = 0
cur = None; frames = []
def flush():
    global total
    if not frames or cur is None or a.thread.lower() not in cur.lower(): return
    total += 1
    for i, (c, m) in enumerate(frames):
        mod = A.mod_of_method(m, ids) or A.mod_of_class(c, pkg2mod)
        if mod and a.mod.lower() in mod.lower():
            key = f'{c}.{m}'; cnt[key] += 1
            below[key][' <- '.join(f'{x}.{y}' for x, y in frames[max(0, i - a.depth):i][::-1][:a.depth]) or '(leaf)'] += 1
            break
p = subprocess.Popen([a.jfr, 'print', '--events', A.EVENTS, '--stack-depth', '96', a.rec], stdout=subprocess.PIPE, text=True, encoding='utf8', errors='replace')
for line in p.stdout:
    if line.startswith('jdk.ExecutionSample') or line.startswith('jdk.NativeMethodSample'):
        flush(); frames = []; cur = None
    elif 'sampledThread' in line:
        m = re.search(r'sampledThread = "([^"]*)"', line); cur = m.group(1) if m else '?'
    else:
        m = A.FRAME.match(line)
        if m: frames.append((m.group(1), m.group(2)))
flush()
print(f'thread~{a.thread!r} samples={total}; innermost frame owned by mod~{a.mod!r}: {sum(cnt.values())}')
for k, n in cnt.most_common(a.top):
    print(f'{n:6d} {100*n/max(1,total):5.1f}%  {k}')
    for b, bn in below[k].most_common(2): print(f'          {bn:5d} calls: {b[:230]}')
