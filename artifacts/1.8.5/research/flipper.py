#!/usr/bin/env python
"""Live config flipper for a running QA game: follows the game log and applies a variants.py variant when the probe logs `PERF flip <variant>`.

usage: flipper.py <sandbox game dir> <stopfile>
Edits files inside the sandbox copy only (refuses the real instance), so Forge's config file watcher / the mod picks the change up while the game runs.
"""
import os, re, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import variants as V
game, stop = os.path.abspath(sys.argv[1]), sys.argv[2]
if os.path.normcase(game).startswith(V.REAL): sys.exit('refusing: real instance')
log = os.path.join(game, 'logs', 'latest.log'); done = 0; pos = 0
print('flipper watching', log, flush=True)
while not os.path.exists(stop):
    try:
        if os.path.exists(log):
            if os.path.getsize(log) < pos: pos = 0
            with open(log, 'rb') as fh:
                fh.seek(pos); chunk = fh.read(); pos = fh.tell()
            for line in chunk.decode('utf8', 'replace').splitlines():
                m = re.search(r'Lads qa log: PERF flip (\S+)', line)
                if m:
                    names = m.group(1).split('+')
                    for n in names:
                        if n in V.V: V.apply(game, n)
                        else: print('unknown variant', n, flush=True)
                    print(time.strftime('%H:%M:%S'), 'flipped', names, flush=True)
    except Exception as e:
        print('flipper error', e, flush=True)
    time.sleep(0.5)
