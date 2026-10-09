import zipfile, glob, os, struct, json, sys, re, collections
mods = os.path.join(os.environ['APPDATA'], '.theladsclient','instances','zero-smp-1.20.1','minecraft','mods')
def parse(data):
    p=8
    n=struct.unpack('>H',data[p:p+2])[0]; p+=2
    cp=[None]*n
    i=1
    while i<n:
        t=data[p]; p+=1
        if t==1:
            l=struct.unpack('>H',data[p:p+2])[0]; p+=2
            cp[i]=('u',data[p:p+l].decode('utf8','replace')); p+=l
        elif t in (3,4): cp[i]=('n',); p+=4
        elif t in (5,6): cp[i]=('n',); p+=8; i+=1
        elif t in (7,8,16,19,20): cp[i]=('r',struct.unpack('>H',data[p:p+2])[0]); p+=2
        elif t in (9,10,11,12,17,18): cp[i]=('x',); p+=4
        elif t==15: cp[i]=('x',); p+=3
        else: raise ValueError('tag %d'%t)
        i+=1
    def utf(ix):
        e=cp[ix]
        return e[1] if e and e[0]=='u' else None
    def cls(ix):
        e=cp[ix]
        return utf(e[1]) if e and e[0]=='r' else None
    p+=6  # access, this, super
    ic=struct.unpack('>H',data[p:p+2])[0]; p+=2+2*ic
    def skip_members(p):
        cnt=struct.unpack('>H',data[p:p+2])[0]; p+=2
        res=[]
        for _ in range(cnt):
            p+=6
            ac=struct.unpack('>H',data[p:p+2])[0]; p+=2
            attrs=[]
            for _ in range(ac):
                ni=struct.unpack('>H',data[p:p+2])[0]; ln=struct.unpack('>I',data[p+2:p+6])[0]
                attrs.append((utf(ni),p+6,ln)); p+=6+ln
            res.append(attrs)
        return p,res
    p,fields=skip_members(p)
    p,methods=skip_members(p)
    ac=struct.unpack('>H',data[p:p+2])[0]; p+=2
    cattrs=[]
    for _ in range(ac):
        ni=struct.unpack('>H',data[p:p+2])[0]; ln=struct.unpack('>I',data[p+2:p+6])[0]
        cattrs.append((utf(ni),p+6,ln)); p+=6+ln
    def ev(q):
        tag=chr(data[q]); q+=1
        if tag in 'BCDFIJSZs': return q+2,None
        if tag=='e': return q+4,None
        if tag=='c':
            v=utf(struct.unpack('>H',data[q:q+2])[0]); return q+2,('c',v)
        if tag=='@':
            q,_=ann(q); return q,None
        if tag=='[':
            k=struct.unpack('>H',data[q:q+2])[0]; q+=2; out=[]
            for _ in range(k):
                q,v=ev(q); out.append(v)
            return q,('a',out)
        raise ValueError(tag)
    def ann(q):
        ti=struct.unpack('>H',data[q:q+2])[0]; q+=2
        np_=struct.unpack('>H',data[q:q+2])[0]; q+=2
        vals={}
        for _ in range(np_):
            nm=utf(struct.unpack('>H',data[q:q+2])[0]); q+=2
            # need string const for 's'
            tag=chr(data[q])
            if tag=='s':
                vals.setdefault(nm,[]).append(utf(struct.unpack('>H',data[q+1:q+3])[0])); q+=3; continue
            q,v=ev(q)
            if v:
                if v[0]=='c': vals.setdefault(nm,[]).append(v[1])
                elif v[0]=='a':
                    for x in v[1]:
                        if x and x[0]=='c': vals.setdefault(nm,[]).append(x[1])
        return q,(utf(ti),vals)
    def ann_s(q):
        # handle arrays of strings within ann for 'targets'
        return ann(q)
    res={'targets':[], 'methods':collections.Counter()}
    # re-implement ann with string-array support
    def ev2(q):
        tag=chr(data[q]); q+=1
        if tag=='s': return q+2,[utf(struct.unpack('>H',data[q:q+2])[0])]
        if tag in 'BCDFIJSZ': return q+2,[]
        if tag=='e': return q+4,[]
        if tag=='c': return q+2,[utf(struct.unpack('>H',data[q:q+2])[0])]
        if tag=='@':
            q,_=ann2(q); return q,[]
        if tag=='[':
            k=struct.unpack('>H',data[q:q+2])[0]; q+=2; out=[]
            for _ in range(k):
                q,v=ev2(q); out+=v
            return q,out
        raise ValueError(tag)
    def ann2(q):
        ti=struct.unpack('>H',data[q:q+2])[0]; q+=2
        np_=struct.unpack('>H',data[q:q+2])[0]; q+=2
        vals={}
        for _ in range(np_):
            nm=utf(struct.unpack('>H',data[q:q+2])[0]); q+=2
            q,v=ev2(q); vals[nm]=v
        return q,(utf(ti),vals)
    def annlist(start):
        q=start
        k=struct.unpack('>H',data[q:q+2])[0]; q+=2
        out=[]
        for _ in range(k):
            q,a=ann2(q); out.append(a)
        return out
    for name,st,ln in cattrs:
        if name in ('RuntimeInvisibleAnnotations','RuntimeVisibleAnnotations'):
            for a in annlist(st):
                if a[0]=='Lorg/spongepowered/asm/mixin/Mixin;':
                    res['targets']=[t.replace('/','.') for t in a[1].get('value',[])+a[1].get('targets',[]) if t]
    if res['targets']:
        for attrs in methods:
            for name,st,ln in attrs:
                if name in ('RuntimeInvisibleAnnotations','RuntimeVisibleAnnotations'):
                    for a in annlist(st):
                        t=a[0]
                        if t and ('mixin' in t.lower() or 'mixinextras' in t.lower()):
                            res['methods'][t.rsplit('/',1)[-1].rstrip(';')]+=1
    return res
allm={}
for jar in sorted(glob.glob(os.path.join(mods,'*.jar'))):
    name=os.path.basename(jar)
    try: z=zipfile.ZipFile(jar)
    except: continue
    lst=[]
    jars=[(name,z)]
    for n in z.namelist():
        if n.endswith('.class') and not n.startswith('META-INF'):
            try: d=z.read(n)
            except: continue
            if b'org/spongepowered/asm/mixin/Mixin' not in d: continue
            try: r=parse(d)
            except Exception as e: continue
            if r['targets']:
                lst.append({'cls':n[:-6].replace('/','.'),'targets':r['targets'],'ann':dict(r['methods'])})
    allm[name]=lst
json.dump(allm,open(sys.argv[1],'w'),indent=0)
tot=collections.Counter()
for f,l in allm.items():
    tot[f]=len(l)
for f,c in tot.most_common(40): print(f"{c:5} {f}")
print(sum(tot.values()),'mixin classes total')
