"""Renames class references inside a jar's .class files (constant-pool Utf8 entries: class names, descriptors, signatures).

Used for libraries built against an older Minecraft whose only breakage is a moved class, e.g. catconfig-mc 26.2 on 26.3
(com/mojang/blaze3d/pipeline/RenderPipeline -> com/mojang/renderpearl/api/pipeline/RenderPipeline).
Nested jars (META-INF/jars/*.jar) are copied unchanged.

usage: python patch-jar-classrefs.py <in.jar> <out.jar> <old/Class=new/Class> [...]
"""
import struct, sys, zipfile


def patch_class(data, renames):
    if data[:4] != b'\xca\xfe\xba\xbe':
        return data, 0
    out = bytearray(data[:10])
    n = struct.unpack_from('>H', data, 8)[0]
    p, i, hits = 10, 1, 0
    while i < n:
        t = data[p]
        if t == 1:
            l = struct.unpack_from('>H', data, p + 1)[0]
            s = data[p + 3:p + 3 + l]
            for old, new in renames:
                if old in s:
                    s = s.replace(old, new)
                    hits += 1
            out += bytes([1]) + struct.pack('>H', len(s)) + s
            p += 3 + l
        else:
            size = {3: 5, 4: 5, 5: 9, 6: 9, 7: 3, 8: 3, 16: 3, 19: 3, 20: 3, 9: 5, 10: 5, 11: 5, 12: 5, 17: 5, 18: 5, 15: 4}[t]
            out += data[p:p + size]
            p += size
            if t in (5, 6):
                i += 1
        i += 1
    out += data[p:]
    return bytes(out), hits


def main():
    src, dst, *pairs = sys.argv[1:]
    renames = [tuple(x.encode() for x in pair.split('=', 1)) for pair in pairs]
    total = 0
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            data = zin.read(info)
            if info.filename.endswith('.class'):
                data, hits = patch_class(data, renames)
                if hits:
                    print(f'{info.filename}: {hits} constant(s) renamed')
                total += hits
            zout.writestr(info, data)
    print(f'{total} constant(s) renamed -> {dst}')


if __name__ == '__main__':
    main()
