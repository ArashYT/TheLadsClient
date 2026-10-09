import json, sys, urllib.request, urllib.parse
UA="TheLadsClient-research/1.0 (local modpack performance research)"
def get(url):
    req=urllib.request.Request(url,headers={'User-Agent':UA})
    with urllib.request.urlopen(req,timeout=30) as r:
        return json.load(r)
def project(slug):
    try: return get(f"https://api.modrinth.com/v2/project/{slug}")
    except Exception as e: return None
def search(q):
    f=urllib.parse.quote('[["versions:1.20.1"],["project_type:mod"]]')
    d=get(f"https://api.modrinth.com/v2/search?query={urllib.parse.quote(q)}&facets={f}&limit=5")
    return d['hits']
def versions(slug,loaders=None):
    p=f"https://api.modrinth.com/v2/project/{slug}/version?game_versions={urllib.parse.quote('[\"1.20.1\"]')}"
    if loaders: p+=f"&loaders={urllib.parse.quote(json.dumps(loaders))}"
    try: return get(p)
    except Exception as e: return []
out=[]
for slug in sys.argv[1:]:
  try:
      p=project(slug)
      if not p:
          hits=search(slug)
          print(f"## {slug}: NOT A SLUG. search -> "+", ".join(f"{h['slug']}({h['downloads']})" for h in hits[:4]))
          continue
      vs=versions(slug)
      per={}
      for v in vs:
          for l in v['loaders']:
              per.setdefault(l,[]).append((v['date_published'][:10],v['version_number'],v['version_type']))
      summ={l:sorted(x,reverse=True)[0] for l,x in per.items()}
      print(f"## {p['slug']} | {p['title']} | dl={p['downloads']} | updated={p['updated'][:10]} | client={p['client_side']} server={p['server_side']} | loaders1.20.1={ {k:f'{a[1]} @{a[0]} {a[2]}' for k,a in summ.items()} }")
      print(f"   {p['description'][:200]}")
      out.append({'slug':p['slug'],'title':p['title'],'downloads':p['downloads'],'updated':p['updated'][:10],'client':p['client_side'],'server':p['server_side'],'v1201':{k:list(a) for k,a in summ.items()},'desc':p['description']})
  except Exception as e:
    print('## ERR',slug,e)
json.dump(out,open(sys.argv[0].replace('mr.py','mr_out.json'),'w'),indent=1)
