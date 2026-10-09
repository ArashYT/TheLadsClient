import hashlib, glob, os, json, urllib.request, sys
UA="TheLadsClient-research/1.0 (local modpack performance research)"
mods = os.path.join(os.environ['APPDATA'], '.theladsclient','instances','zero-smp-1.20.1','minecraft','mods')
h={}
for j in sorted(glob.glob(os.path.join(mods,'*.jar'))):
    h[hashlib.sha1(open(j,'rb').read()).hexdigest()]=os.path.basename(j)
def post(url,body):
    req=urllib.request.Request(url,data=json.dumps(body).encode(),headers={'User-Agent':UA,'Content-Type':'application/json'})
    return json.load(urllib.request.urlopen(req,timeout=60))
cur=post("https://api.modrinth.com/v2/version_files",{"hashes":list(h),"algorithm":"sha1"})
upd=post("https://api.modrinth.com/v2/version_files/update",{"hashes":list(h),"algorithm":"sha1","loaders":["forge"],"game_versions":["1.20.1"]})
# project info
pids=sorted({v['project_id'] for v in cur.values()})
projs={}
for i in range(0,len(pids),50):
    chunk=pids[i:i+50]
    ids=json.dumps(chunk)
    req=urllib.request.Request("https://api.modrinth.com/v2/projects?ids="+urllib.request.quote(ids),headers={'User-Agent':UA})
    for p in json.load(urllib.request.urlopen(req,timeout=60)): projs[p['id']]=p
rows=[]
for sha,f in h.items():
    c=cur.get(sha); u=upd.get(sha)
    if not c:
        rows.append({'file':f,'matched':False}); continue
    p=projs.get(c['project_id'],{})
    rows.append({'file':f,'matched':True,'slug':p.get('slug'),'title':p.get('title'),'cur':c['version_number'],'curDate':c['date_published'][:10],
                 'latest':u['version_number'] if u else None,'latestDate':u['date_published'][:10] if u else None,'outdated': bool(u and u['id']!=c['id']),
                 'client':p.get('client_side'),'server':p.get('server_side'),'downloads':p.get('downloads')})
json.dump(rows,open(sys.argv[1],'w'),indent=1)
print("matched",sum(r['matched'] for r in rows),"of",len(rows))
for r in rows:
    if not r['matched']: print("NO MATCH:",r['file'])
print()
for r in rows:
    if r.get('outdated'): print(f"OUTDATED {r['slug']:32} {r['cur']:28} {r['curDate']} -> {r['latest']} {r['latestDate']}")
