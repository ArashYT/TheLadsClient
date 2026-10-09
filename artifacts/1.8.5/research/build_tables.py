# Builds MOD_TABLE.{csv,json,md} from mods_meta.json, mixins_by_mod.json, mods_modrinth.json and mod_notes.py
import json, csv, re
from mod_notes import N
meta=json.load(open('mods_meta.json')); mix=json.load(open('mixins_by_mod.json'))
mr={x['file']:x for x in json.load(open('mods_modrinth.json'))}
keys=sorted(N,key=len,reverse=True)
rows=[]
for m in meta:
    f=m['file']
    k=next((k for k in keys if f.lower().startswith(k.lower())),None)
    note=N.get(k)
    ml=mix.get(f,[])
    ow=sum(x['ann'].get('Overwrite',0) for x in ml); rd=sum(x['ann'].get('Redirect',0) for x in ml)
    r=mr.get(f,{})
    row=dict(file=f,sizeKB=m['sizeKB'],classes=m.get('classes',0),mixins=len(ml),overwrite=ow,redirect=rd,
             modrinth=r.get('slug',''),latest=(r.get('latest') if r.get('outdated') else ''),
             category=note[0] if note else '?',side=note[1] if note else '?',rt=note[2] if note else '?',mem=note[3] if note else '?',
             action=note[4] if note else '?',note=note[5] if note else '')
    rows.append(row)
json.dump(rows,open('MOD_TABLE.json','w'),indent=1)
with open('MOD_TABLE.csv','w',newline='',encoding='utf8') as fh:
    w=csv.DictWriter(fh,fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)
miss=[r['file'] for r in rows if r['category']=='?']
print('unmatched:',miss)
print(len(rows),'rows')
