#!/usr/bin/env python
"""Mega scenario for the superflat QA world: one game, many measured phases, config flips applied live by flipper.py.
usage: make_mega.py <out.txt>
Phase names are logged as `PERF phase=<n> begin/end`; `PERF flip <variant[+variant]>` asks flipper.py to edit the sandbox config at that moment.
"""
import sys
L = []
def ph(name, secs, every=3, pre=None):
    L.append(f'log PERF phase={name} begin')
    if pre: L.extend(pre)
    for _ in range(max(1, secs // every)):
        L.append(f'wait {every}s'); L.append('dump state')
    L.append(f'log PERF phase={name} end')
def flip(v, settle=8):
    L.append(f'log PERF flip {v}'); L.append(f'wait {settle}s')
L += ['log PERF scenario begin', 'cmd gamemode creative', 'cmd time set day', 'cmd weather clear', 'cmd gamerule doMobSpawning false', 'look 0 10', 'wait 2s', 'dump state']
ph('S', 36)
ph('A', 30)                                            # HUD on, clear, idle: the reference
ph('A2', 21, pre=['key F1'])                           # HUD hidden: total GUI cost
L += ['key F1', 'wait 1s']
# grass A/B (live flips)
flip('grass_r64');  ph('G64', 24)
flip('grass_r32');  ph('G32', 24)
flip('grass_b3');   ph('G32b3', 24)
flip('grass_base'); ph('G160', 24)                     # back to the pack default: checks that flips are reversible/stable
# rain + particle stack
ph('C', 30, pre=['cmd weather rain'])
flip('particular_off'); ph('C_particular_off', 24)
flip('particular_on');  ph('C_particular_on', 24)
# mobs + clear
L.append('cmd weather clear')
for _ in range(60): L.append('cmd execute at @s run summon minecraft:cow ~ ~ ~')
ph('D', 30)
L.append('cmd kill @e[type=minecraft:cow]')
# flight: new chunks, meshing, DH LOD building
L += ['log PERF phase=B begin', 'cmd gamemode spectator', 'cmd tp @s ~ ~12 ~', 'hold forward on', 'hold sprint on']
for i in range(20):
    L += ['wait 3s', f'look {(i * 17) % 360} 8', 'dump state']
L += ['hold forward off', 'hold sprint off', 'cmd gamemode creative', 'cmd spreadplayers 0 0 0 1 false @s', 'wait 8s', 'log PERF phase=B end']
ph('E', 24, pre=['cmd weather thunder'])
L += ['log PERF phase=Z begin', 'cmd weather clear', 'wait 2s', 'cmd spark health', 'wait 3s', 'cmd spark tps', 'wait 3s', 'cmd spark gc', 'wait 3s', 'dump state', 'log PERF phase=Z end', 'log PERF scenario end']
open(sys.argv[1], 'w', encoding='utf8', newline='\n').write('\n'.join(L) + '\n')
print(len(L), 'steps')
