#!/usr/bin/env python
"""Summarise one run folder produced by run_case.ps1 (runs/<name>/): startup phases, FPS per phase, spark lines, GC/heap, load noise,
and JFR CPU attribution per phase for the Render and Server threads.

usage: analyze_run.py runs/<name> [--jfr <jfr binary>] [--no-jfr]
writes runs/<name>/summary.json and prints it.
"""
import argparse, collections, csv, datetime, glob, json, os, re, statistics as st, subprocess, sys

JFR = r'C:\Users\Arash\AppData\Roaming\.theladsclient\runtime\java-17\jdk-17.0.20.1+1\bin\jfr.exe'
HERE = os.path.dirname(os.path.abspath(__file__))

def secs(hms):
    h, m, s = hms.split(':'); return int(h) * 3600 + int(m) * 60 + float(s)

def log_times(text):
    out = {}
    def first(key, pat):
        m = re.search(r'^\[(\d+:\d+:\d+)\].*' + pat, text, re.M)
        if m and key not in out: out[key] = secs(m.group(1))
    first('launch', r'ModLauncher running')
    first('mf_bootstrap', r'ModernFix reached bootstrap stage')
    first('modload', r'Forge mod loading, version')
    first('reload', r'Reloading ResourceManager')
    first('game_start', r'Game took')
    first('world_total', r'Total time to load game and open world')
    first('qa_begin', r'Lads auto-world QA BEGIN')
    first('qa_ready', r'Lads auto-world QA READY')
    first('script_end', r'Lads qa script END')
    d = {}
    L = out.get('launch')
    if L is not None:
        for k, v in out.items():
            if k != 'launch': d[k + '_s'] = round(v - L, 1)
    m = re.search(r'Game took ([\d.]+) seconds to start', text)
    if m: d['game_took_reported_s'] = float(m.group(1))
    m = re.search(r'Total time to load game and open world was ([\d.]+) seconds', text)
    if m: d['world_total_reported_s'] = float(m.group(1))
    m = re.search(r'Time from main menu to in-game was ([\d.]+) seconds', text)
    if m: d['menu_to_game_s'] = float(m.group(1))
    return d

def phases(text):
    ev = []
    for m in re.finditer(r'^\[(\d+:\d+:\d+)\].*Lads qa log: PERF phase=(\S+) (begin|end)', text, re.M):
        ev.append((secs(m.group(1)), m.group(2), m.group(3)))
    out = {}
    for t, name, kind in ev:
        out.setdefault(name, {})[kind] = t
    return {n: v for n, v in out.items() if 'begin' in v and 'end' in v}

def fps_series(text):
    rows = []
    for m in re.finditer(r'^\[(\d+:\d+:\d+)\].*Lads qa dump state: .*? fps=(\d+) fps', text, re.M):
        rows.append((secs(m.group(1)), int(m.group(2))))
    return rows

def spark_lines(text):
    keep = []
    for line in text.splitlines():
        if '[CHAT]' in line and re.search(r'TPS|MSPT|CPU|Memory|GC|G1|Tick|tick|Disk', line) and 'spark' not in line.lower()[:0]:
            keep.append(re.sub(r'\u00a7.', '', line.split('[CHAT]', 1)[1]).strip()[:200])
    return keep[:40]

def main():
    ap = argparse.ArgumentParser(); ap.add_argument('run'); ap.add_argument('--jfr', default=JFR); ap.add_argument('--no-jfr', action='store_true')
    a = ap.parse_args()
    run = a.run
    log = os.path.join(run, 'latest.log')
    text = open(log, encoding='utf8', errors='replace').read() if os.path.exists(log) else ''
    res = {'run': os.path.basename(os.path.abspath(run)), 'case': open(os.path.join(run, 'case.txt'), encoding='utf8', errors='replace').read().strip() if os.path.exists(os.path.join(run, 'case.txt')) else ''}
    res['startup'] = log_times(text)
    ph = phases(text); fps = fps_series(text)
    res['phases'] = {}
    for n, v in ph.items():
        xs = [f for t, f in fps if v['begin'] <= t <= v['end']]
        res['phases'][n] = {'seconds': round(v['end'] - v['begin']), 'fps_samples': len(xs),
                            'fps_mean': round(st.mean(xs), 1) if xs else None, 'fps_min': min(xs) if xs else None, 'fps_max': max(xs) if xs else None,
                            'fps_median': st.median(xs) if xs else None}
    res['spark'] = spark_lines(text)
    # machine noise during the game phases
    lm = os.path.join(run, 'load.csv')
    if os.path.exists(lm) and ph:
        t0 = min(v['begin'] for v in ph.values()); t1 = max(v['end'] for v in ph.values())
        cpu, free = [], []
        for r in csv.DictReader(open(lm)):
            try:
                t = secs(r['time'])
                if t0 <= t <= t1: cpu.append(float(r['cpu_pct'])); free.append(float(r['free_gb']))
            except Exception: pass
        if cpu: res['machine_during_script'] = {'cpu_pct_mean': round(st.mean(cpu), 1), 'cpu_pct_max': round(max(cpu), 1), 'free_gb_min': round(min(free), 2)}
    # JFR
    jfrs = sorted(glob.glob(os.path.join(run, 'game-*.jfr')), key=os.path.getsize, reverse=True)
    if jfrs and not a.no_jfr:
        rec = jfrs[0]; res['jfr_file'] = os.path.basename(rec); res['jfr_mb'] = round(os.path.getsize(rec) / 1048576, 1)
        gc = subprocess.run([sys.executable, os.path.join(HERE, 'jfr_gc.py'), rec, '--jfr', a.jfr], capture_output=True, text=True, encoding='utf8', errors='replace')
        res['gc_all'] = gc.stdout.strip().splitlines()
        if ph:
            # GC while the script ran: seconds after the recording start ~ unknown here, so the report shows whole-run numbers plus the script window via jfr_gc --after-seconds in analyze notes
            pass
        def at(pat):
            m = re.search(r'^\[(\d+:\d+:\d+)\].*' + pat, text, re.M)
            return secs(m.group(1)) if m else None
        ph_all = dict(ph)
        t_launch, t_game, t_ready = at(r'ModLauncher running'), at(r'Game took'), at(r'Lads auto-world QA READY')
        if t_launch is not None and t_game is not None: ph_all['startup'] = {'begin': t_launch, 'end': t_game + 1}
        if t_game is not None and t_ready is not None: ph_all['worldload'] = {'begin': t_game, 'end': t_ready}
        pj = os.path.join(run, 'phases.json'); json.dump(ph_all, open(pj, 'w'))
        off = 0.0
        try:
            ji = subprocess.run([a.jfr, 'print', '--events', 'jdk.JVMInformation', rec], capture_output=True, text=True, encoding='utf8', errors='replace').stdout
            mm = re.search(r'jvmStartTime = (\d+):(\d+):(\d+\.\d+)', ji)
            if mm and t_launch is not None:
                jvm = int(mm.group(1)) * 3600 + int(mm.group(2)) * 60 + float(mm.group(3))
                off = round((t_launch - jvm) / 3600.0) * 3600.0
        except Exception:
            pass
        res['jfr_clock_offset_s'] = off
        at = subprocess.run([sys.executable, os.path.join(HERE, 'jfr_attrib.py'), rec, '--jfr', a.jfr, '--out', os.path.join(run, 'jfr_attrib.json'), '--phases', pj, '--offset', str(off)],
                            capture_output=True, text=True, encoding='utf8', errors='replace')
        res['attrib_head'] = at.stdout.strip().splitlines()[:40]
    json.dump(res, open(os.path.join(run, 'summary.json'), 'w'), indent=1)
    print(json.dumps(res, indent=1)[:6000])

if __name__ == '__main__':
    main()
