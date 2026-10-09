import json, re
m = json.load(open('mixins_by_mod.json')); h = json.load(open('mixin_hotspots.json'))
mr = {x['file']: x for x in json.load(open('mods_modrinth.json'))}
def nice(f):
    r = mr.get(f)
    if r and r.get('title'): return re.sub(r'[^\x00-\x7f]', '', re.sub(r'\s*\[.*?\]\s*', '', r['title'])).strip()
    return re.sub(r'[-_ +\[(].*$', '', f.replace('.jar', ''))
rows = []
for e in h:
    t = e['target'].strip('L;')
    mods = []
    for f, v in sorted(e['mods'].items(), key=lambda kv: -kv[1].get('n', 0)):
        mods.append(f"{nice(f)}({v.get('n', 0)}{'/OW' + str(v['Overwrite']) if v.get('Overwrite') else ''}{'/RD' + str(v['Redirect']) if v.get('Redirect') else ''})")
    rows.append((len(e['mods']), t, mods))
rows.sort(key=lambda r: (-r[0], r[1]))
open('mixin_hotspots.md', 'w', encoding='utf8').write(
    "# Mixin hot spots (vanilla classes mixed into by >= 3 mod jars)\n\nMixin classes per mod in brackets; OW = @Overwrite, RD = @Redirect. Counted statically from the 128 baseline jars (the three jars another lane added later are not included). Targets of Fabric-intermediary mods (Immersive Winds, Lads Core, Continuity) are not resolved.\n\n| # mods | target class | mods (mixin classes) |\n|---:|---|---|\n"
    + "\n".join(f"| {n} | `{t}` | {', '.join(mm)} |" for n, t, mm in rows) + "\n")
print(len(rows))
