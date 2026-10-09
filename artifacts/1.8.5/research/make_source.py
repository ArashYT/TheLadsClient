#!/usr/bin/env python
"""Build a variant "source instance" for ZeroSmpQa (--source <dir>) from the REAL Zero SMP instance, read-only.

usage: make_source.py <out dir (under artifacts/verification)> [--variant name]... [--drop jarprefix]... [--add jarpath]... [--no-new]

  - mods/*.jar are HARD-LINKED (no copy), config/ + small pack folders are copied, so ten variants cost a few MB.
  - the three jars another lane just added (c2meF, lazyyyyy, crawlondemand) are left out unless --keep-new (baseline = the pack as shipped).
  - --variant applies variants.py edits to the copy; --drop leaves mods out; --add copies an extra jar into mods (e.g. spark).
  - nothing is ever written inside %APPDATA%.
"""
import argparse, os, shutil, sys, glob, re
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import variants as V

REAL = os.path.join(os.environ['APPDATA'], '.theladsclient', 'instances', 'zero-smp-1.20.1')
NEW = ('c2meF', 'lazyyyyy', 'crawlondemand')
COPY_DIRS = ('config', 'defaultconfigs', 'configureddefaults', 'resourcepacks', 'shaderpacks', 'moonlight-global-datapacks', 'xaero', 'schematics')
COPY_FILES = ('options.txt', 'thelads_config.json')

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out'); ap.add_argument('--variant', action='append', default=[]); ap.add_argument('--drop', action='append', default=[])
    ap.add_argument('--add', action='append', default=[]); ap.add_argument('--keep-new', action='store_true')
    a = ap.parse_args()
    out = os.path.abspath(a.out)
    if os.path.normcase(out).startswith(os.path.normcase(REAL)) or os.path.normcase(REAL).startswith(os.path.normcase(out)):
        sys.exit('refusing: out overlaps the real instance')
    if 'artifacts' + os.sep + 'verification' not in out:
        sys.exit('out must be under artifacts\\verification')
    game = os.path.join(out, 'minecraft'); mods = os.path.join(game, 'mods')
    if os.path.exists(out): shutil.rmtree(out)
    os.makedirs(mods)
    src_mods = os.path.join(REAL, 'minecraft', 'mods')
    for j in sorted(glob.glob(os.path.join(src_mods, '*.jar'))):
        n = os.path.basename(j)
        if not a.keep_new and n.startswith(NEW): continue
        if any(n.lower().startswith(d.lower()) for d in a.drop): continue
        os.link(j, os.path.join(mods, n))           # hard link: same volume, no copy
    for d in COPY_DIRS:
        s = os.path.join(REAL, 'minecraft', d)
        if os.path.isdir(s): shutil.copytree(s, os.path.join(game, d))
    for f in COPY_FILES:
        s = os.path.join(REAL, 'minecraft', f)
        if os.path.isfile(s): shutil.copy2(s, os.path.join(game, f))
    shutil.copy2(os.path.join(REAL, 'instance.json'), os.path.join(out, 'instance.json'))
    for j in a.add: shutil.copy2(j, mods)
    for v in a.variant: V.apply(game, v)
    n = len(glob.glob(os.path.join(mods, '*.jar')))
    print(f'source {out}: {n} jars, variants={a.variant}, dropped={a.drop}, added={[os.path.basename(x) for x in a.add]}')

if __name__ == '__main__':
    main()
