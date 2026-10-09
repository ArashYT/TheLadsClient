#!/usr/bin/env python
"""Alternating A/B scenario for ONE game (drift-proof): long settle, then base/variant/base/variant ... pairs, live config flips by flipper.py.
usage: make_mega2.py <out.txt>
Phases: names ending in _b are the base state, _v the variant; the report compares neighbours.
"""
import sys
L = []
def ph(name, secs=20, every=2, pre=None):
    L.append(f'log PERF phase={name} begin')
    if pre: L.extend(pre)
    for _ in range(max(1, secs // every)):
        L.append(f'wait {every}s'); L.append('dump state')
    L.append(f'log PERF phase={name} end')
def flip(v, settle=6):
    L.append(f'log PERF flip {v}'); L.append(f'wait {settle}s')
L += ['log PERF scenario begin', 'cmd gamemode creative', 'cmd time set day', 'cmd weather clear', 'cmd gamerule doMobSpawning false', 'look 0 10', 'wait 2s', 'dump state']
ph('S', 90, every=5)
# 1. HUD on/off x3
for i in range(3):
    ph(f'hud_b{i}', 20); ph(f'hud_v{i}', 20, pre=['key F1']); L += ['key F1', 'wait 2s']
# 2. grass radius 160 -> 64 and -> 32 (live)
for i, v in enumerate(['grass_r64', 'grass_r64', 'grass_r32', 'grass_r32']):
    flip('grass_base'); ph(f'grass_b{i}', 20)
    flip(v); ph(f'grass_v{i}_{v[6:]}', 20)
flip('grass_base')
# 3. rain particle stack, base vs particular off (live)
L.append('cmd weather rain'); L.append('wait 10s')
for i in range(3):
    flip('particular_on'); ph(f'rain_b{i}', 20)
    flip('particular_off'); ph(f'rain_v{i}', 20)
flip('particular_on')
# 4. one rain phase with the HUD hidden, for the particle share without GUI
ph('rain_nohud', 20, pre=['key F1']); L += ['key F1', 'wait 2s']
# 5. mobs, flight, thunder (JFR attribution only)
L.append('cmd weather clear')
for _ in range(60): L.append('cmd execute at @s run summon minecraft:cow ~ ~ ~')
ph('D', 30)
L.append('cmd kill @e[type=minecraft:cow]')
L += ['log PERF phase=B begin', 'cmd gamemode spectator', 'cmd tp @s ~ ~12 ~', 'hold forward on', 'hold sprint on']
for i in range(20):
    L += ['wait 3s', f'look {(i * 17) % 360} 8', 'dump state']
L += ['hold forward off', 'hold sprint off', 'cmd gamemode creative', 'cmd spreadplayers 0 0 0 1 false @s', 'wait 8s', 'log PERF phase=B end']
ph('E', 24, pre=['cmd weather thunder'])
L += ['log PERF phase=Z begin', 'cmd weather clear', 'wait 2s', 'cmd spark health', 'wait 3s', 'cmd spark tps', 'wait 3s', 'cmd spark gc', 'wait 3s', 'dump state', 'log PERF phase=Z end', 'log PERF scenario end']
open(sys.argv[1], 'w', encoding='utf8', newline='\n').write('\n'.join(L) + '\n')
print(len(L), 'steps')
