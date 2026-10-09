import zipfile, glob, os, re, json, sys
try:
    import tomllib
except ImportError:
    tomllib = None
mods = os.path.join(os.environ['APPDATA'], '.theladsclient','instances','zero-smp-1.20.1','minecraft','mods')
rows=[]
for jar in sorted(glob.glob(os.path.join(mods,'*.jar'))):
    name=os.path.basename(jar)
    info={'file':name,'sizeKB':os.path.getsize(jar)//1024}
    try:
        z=zipfile.ZipFile(jar)
    except Exception as e:
        info['err']=str(e); rows.append(info); continue
    names=z.namelist()
    info['classes']=sum(1 for n in names if n.endswith('.class'))
    info['jarjar']=[n for n in names if n.startswith('META-INF/jarjar/') and n.endswith('.jar')]
    info['fabric_jij']=[n for n in names if n.startswith('META-INF/jars/') and n.endswith('.jar')]
    kind=[]
    if 'META-INF/mods.toml' in names:
        kind.append('forge')
        txt=z.read('META-INF/mods.toml').decode('utf8','replace')
        ids=re.findall(r'^\s*modId\s*=\s*"([^"]+)"',txt,re.M)
        vers=re.findall(r'^\s*version\s*=\s*"([^"]+)"',txt,re.M)
        info['ids']=[i for i in ids if i not in ('forge','minecraft')]
        info['ver_raw']=vers[:3]
        info['dispName']=re.findall(r'^\s*displayName\s*=\s*"([^"]+)"',txt,re.M)[:2]
        # side hints
        info['sides']=sorted(set(re.findall(r'side\s*=\s*"([^"]+)"',txt)))
    if 'fabric.mod.json' in names:
        kind.append('fabric')
        try:
            fj=json.loads(z.read('fabric.mod.json').decode('utf8','replace'),strict=False)
            info['fabric_id']=fj.get('id'); info['fabric_ver']=fj.get('version'); info['fabric_name']=fj.get('name')
            info['env']=fj.get('environment')
            info['mixins']=fj.get('mixins')
        except Exception as e:
            info['fabric_err']=str(e)
    if 'META-INF/MANIFEST.MF' in names:
        mf=z.read('META-INF/MANIFEST.MF').decode('utf8','replace')
    info['kind']=kind
    # mixin configs
    info['mixin_cfgs']=[n for n in names if re.search(r'mixins?\.json$',n) or n.endswith('.mixins.json')]
    rows.append(info)
json.dump(rows,open(sys.argv[1],'w'),indent=1)
for r in rows:
    print(f"{r['file'][:55]:55} {','.join(r.get('kind',[])):12} cls={r.get('classes',0):6} ids={r.get('ids') or r.get('fabric_id')} jj={len(r.get('jarjar',[]))} fjij={len(r.get('fabric_jij',[]))} sides={r.get('sides')} env={r.get('env')}")
