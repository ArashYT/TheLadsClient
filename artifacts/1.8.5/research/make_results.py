#!/usr/bin/env python
"""Render RESULTS.md from runs/<name>/{summary.json,jfr_attrib.json,alloc.json,phases.json}.
usage: make_results.py [run ...]   (default: every run folder that has a summary.json)"""
import glob, json, os, re, statistics as st, sys

HERE = os.path.dirname(os.path.abspath(__file__))
names = sys.argv[1:] or sorted(os.path.basename(os.path.dirname(p)) for p in glob.glob(os.path.join(HERE, 'runs', '*', 'summary.json')))
out = ['# Measured runs (sandbox, shared QA harness)', '',
       'All runs: Zero SMP pack copy (128 baseline jars + spark), Forge 47.4.10 + Connector, 944x501 tiled window, GUI scale 2, vsync off, master volume 0, '
       'render distance 12 / simulation 8 / uncapped FPS unless stated, JFR `profile` settings on, machine shared with the other lanes (see "machine" column).',
       'FPS is the 1-second average Minecraft prints (`dump state`), sampled every 2-4 s. CPU shares come from JFR samples (10 ms Java, 20 ms native), only the render thread ("main") unless stated.', '']

def load(run, f):
    p = os.path.join(HERE, 'runs', run, f)
    return json.load(open(p, encoding='utf8')) if os.path.exists(p) else None

NOTES = {'base1': 'The live-flip helper was not running in this run (a variable-name clash in run_case.ps1, fixed afterwards): phases G64/G32/G32b3/G160 therefore have the SAME configuration as A and are a noise/drift estimate (230-278 fps for identical state).',
         'smoke': 'Quick scenario in the owner Tectonic world copy (New World, spawn on a 297-block peak), render distance 4, 120 fps cap (the exe was built before the perf override existed). Used for the worldgen/DH-generation profile.',
         'flip1': 'Alternating base/variant pairs. Live config flips (grass radius, Particular) did NOT change the JFR grass share -> those Forge TOML values are not hot-reloaded; restart-class runs (gr64, gr32) cover them.'}
for run in names:
    s = load(run, 'summary.json')
    if not s: continue
    a = load(run, 'jfr_attrib.json'); al = load(run, 'alloc.json')
    out.append(f'## {run}')
    if run in NOTES: out.append('> ' + NOTES[run]); out.append('')
    out.append('`' + s.get('case', '').replace('\n', ' | ').replace('﻿', '') + '`')
    out.append('')
    su = s.get('startup', {})
    out.append(f"* startup (cold Connector cache, sandbox copy): JVM->ModernFix bootstrap {su.get('mf_bootstrap_s')} s, mod construction at {su.get('modload_s')} s, resource reload at {su.get('reload_s')} s, "
               f"`Game took` **{su.get('game_took_reported_s')} s**, world open total {su.get('world_total_reported_s')} s (menu->in-game {su.get('menu_to_game_s')} s)")
    m = s.get('machine_during_script')
    if m: out.append(f"* machine during the script: CPU mean {m['cpu_pct_mean']} % (max {m['cpu_pct_max']} %), free RAM min {m['free_gb_min']} GB")
    gc = s.get('gc_all') or []
    if gc: out.append('* GC: ' + ' | '.join(x for x in gc if x.startswith(('window', 'by kind', 'pause', 'pauses', 'heap'))))
    if al: out.append(f"* allocation: {al['rate_mb_s']} MB/s over {al['seconds']} s; render thread {next((p for n, v, p in al['threads'] if n == 'main'), '?')} %; "
                      + ', '.join(f"{n.split(' / ')[1]} {p}%" for n, v, p in al['by_mod_first_frame'][:6]))
    out.append('')
    ph = s.get('phases', {})
    if ph:
        out.append('| phase | s | fps mean | median | min | max | render-thread CPU-s | grass % | xaero % | Lads % | particles % | ParticleRain % | entities % |')
        out.append('|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|')
        bp = (a or {}).get('by_phase', {})
        for k, v in ph.items():
            r = bp.get(k, {}).get('main')
            sec = {n: pc for n, _, pc in r['section']} if r else {}
            fm = {n: pc for n, _, pc in r['first_mod_frame']} if r else {}
            out.append(f"| {k} | {v['seconds']} | {v['fps_mean']} | {v['fps_median']} | {v['fps_min']} | {v['fps_max']} | {r['cpu_sec'] if r else ''} | {sec.get('grass', '')} | {sec.get('xaero', '')} | {sec.get('lads-core', '')} | {sec.get('particles', '')} | {fm.get('particlerain', '')} | {sec.get('entities', '')} |")
        out.append('')
    if a and 'by_phase' in a:
        for key in ('startup', 'worldload'):
            r = a['by_phase'].get(key, {}).get('*ALL*')
            if r:
                out.append(f"* **{key}** CPU {r['cpu_sec']} s over all threads, by innermost mod frame: " + ', '.join(f"{n} {sec}s" for n, sec, pc in r['first_mod_frame'][:12]))
        out.append('')
open(os.path.join(HERE, 'RESULTS.md'), 'w', encoding='utf8').write('\n'.join(out) + '\n')
print('RESULTS.md', len(out), 'lines for', names)
