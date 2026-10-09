#!/usr/bin/env python
"""Top allocating methods (innermost frame owned by a mod) and the classes they allocate, for one thread and mod.
usage: jfr_alloc_drill.py <jfr> --thread main --mod Connector [--top 8] [--depth 3]"""
import argparse, collections, os, re, subprocess, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jfr_attrib as A

ap = argparse.ArgumentParser(); ap.add_argument('rec')
ap.add_argument('--jfr', default=r'C:\Users\Arash\AppData\Roaming\.theladsclient\runtime\java-17\jdk-17.0.20.1+1\bin\jfr.exe')
ap.add_argument('--thread', default='main'); ap.add_argument('--mod', default=''); ap.add_argument('--cls', default=''); ap.add_argument('--top', type=int, default=8); ap.add_argument('--depth', type=int, default=3)
ap.add_argument('--mods', default=os.path.join(os.environ.get('APPDATA', ''), '.theladsclient', 'instances', 'zero-smp-1.20.1', 'minecraft', 'mods'))
a = ap.parse_args()
pkg2mod, ids = A.build_maps(a.mods)


def mb(v):
    m = re.match(r'([\d.,]+)\s*(bytes|kB|MB|GB)', v.strip())
    return float(m.group(1).replace(',', '')) * {'bytes': 1 / 1048576, 'kB': 1 / 1024, 'MB': 1.0, 'GB': 1024.0}[m.group(2)] if m else 0.0


meth = collections.Counter(); cls = collections.defaultdict(collections.Counter); callers = collections.defaultdict(collections.Counter); total = 0.0
cur = {}; frames = []


def flush():
    global total
    if not cur or 'weight' not in cur or a.thread.lower() not in cur.get('eventThread', '').lower():
        return
    w = mb(cur['weight']); total += w
    if a.cls:
        if a.cls in cur.get('objectClass', ''):
            meth[' <- '.join(x + '.' + y for x, y in frames[:a.depth])] += w
        return
    for i, (c, m) in enumerate(frames):
        mod = A.mod_of_method(m, ids) or A.mod_of_class(c, pkg2mod)
        if mod and a.mod.lower() in mod.lower():
            k = c + '.' + m
            meth[k] += w; cls[k][cur.get('objectClass', '?')] += w
            callers[k][' <- '.join(x + '.' + y for x, y in frames[i + 1:i + 1 + a.depth])] += w
            break


p = subprocess.Popen([a.jfr, 'print', '--events', 'jdk.ObjectAllocationSample', '--stack-depth', '40', a.rec], stdout=subprocess.PIPE, text=True, encoding='utf8', errors='replace')
for line in p.stdout:
    if line.startswith('jdk.ObjectAllocationSample'):
        flush(); cur = {}; frames = []
    elif re.match(r'^\s+(objectClass|weight|eventThread) = ', line):
        k, v = re.match(r'^\s+(\w+) = (.*)$', line).groups()
        if k == 'eventThread': v = re.search(r'"([^"]*)"', v).group(1)
        if k == 'objectClass': v = v.split(' ')[0].strip('"')
        cur[k] = v
    else:
        m = A.FRAME.match(line)
        if m: frames.append((m.group(1), m.group(2)))
flush()
print(f"thread~{a.thread!r} total {total:.0f} MB; filter mod~{a.mod!r} cls~{a.cls!r}: {sum(meth.values()):.0f} MB")
for k, w in meth.most_common(a.top):
    print(f"{w:8.0f} MB  {k}")
    print('           classes:', ', '.join(f"{c.split('.')[-1]} {v:.0f}" for c, v in cls[k].most_common(3)))
    print('           called from:', callers[k].most_common(1)[0][0][:260] if callers[k] else '')
