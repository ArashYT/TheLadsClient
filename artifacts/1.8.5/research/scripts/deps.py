import zipfile, glob, os, re, json, sys
mods = os.path.join(os.environ['APPDATA'], '.theladsclient','instances','zero-smp-1.20.1','minecraft','mods')
def blocks(txt):
    # returns list of (header, body) for [[mods]] and [[dependencies.x]]
    parts=re.split(r'^\s*\[\[(.+?)\]\]\s*$',txt,flags=re.M)
    out=[]
    for i in range(1,len(parts),2):
        out.append((parts[i].strip(),parts[i+1]))
    return out
graph={}   # file -> {'ids':[...], 'deps':{ownerid:[(dep,mandatory,side)]}}
provides={}
for jar in sorted(glob.glob(os.path.join(mods,'*.jar'))):
    name=os.path.basename(jar)
    z=zipfile.ZipFile(jar)
    names=z.namelist()
    ids=[];deps=[]
    if 'META-INF/mods.toml' in names:
        txt=z.read('META-INF/mods.toml').decode('utf8','replace')
        for h,b in blocks(txt):
            if h=='mods':
                m=re.search(r'^\s*modId\s*=\s*"([^"]+)"',b,re.M)
                if m: ids.append(m.group(1))
            elif h.startswith('dependencies.'):
                owner=h.split('.',1)[1]
                m=re.search(r'^\s*modId\s*=\s*"([^"]+)"',b,re.M)
                mand=re.search(r'^\s*mandatory\s*=\s*(true|false)',b,re.M)
                if not mand:
                    ty=re.search(r'^\s*type\s*=\s*"([^"]+)"',b,re.M)
                    mand_v = (ty.group(1)=='required') if ty else False
                else: mand_v = mand.group(1)=='true'
                side=re.search(r'^\s*side\s*=\s*"([^"]+)"',b,re.M)
                if m: deps.append((owner,m.group(1),mand_v,side.group(1) if side else ''))
    if 'fabric.mod.json' in names:
        try:
            fj=json.loads(z.read('fabric.mod.json').decode('utf8','replace'),strict=False)
            ids.append(fj.get('id'))
            for k,v in (fj.get('depends') or {}).items():
                deps.append((fj.get('id'),k,True,''))
        except Exception as e: pass
    graph[name]={'ids':ids,'deps':deps}
json.dump(graph,open(sys.argv[1],'w'),indent=1)
# reverse: which files depend (mandatory) on a given modid
core={'forge','minecraft','neoforge','java','fabricloader','fabric-api','fabric','fabric-resource-loader-v0'}
idfile={}
for f,g in graph.items():
    for i in g['ids']: idfile.setdefault(i,[]).append(f)
rev={}
for f,g in graph.items():
    for o,d,m,s in g['deps']:
        if d in core: continue
        rev.setdefault(d,set()).add((f,m))
for lib in sys.argv[2:]:
    print('==',lib,'provided by',idfile.get(lib))
    for f,m in sorted(rev.get(lib,[])):
        print('   needs it:',f,'(mandatory)' if m else '(optional)')
