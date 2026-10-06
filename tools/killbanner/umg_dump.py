"""Reads FModel JSON exports of Valorant's kill banner widgets and prints every widget animation: its bindings, tracks,
sections, keyframes (time in frames and seconds, value, interpolation, tangents) and easing. Also lists the widget
tree with slots/render transforms and the Blueprint functions (with bytecode if serialized).

python umg_dump.py <export.json> [--out summary.txt]
"""
import argparse
import json
from pathlib import Path


def by_index(objects):
    return {i: o for i, o in enumerate(objects)}


def ref_index(ref):
    """FModel object refs look like {'ObjectName': ..., 'ObjectPath': '/Game/.../File.47'} -> 47."""
    if not isinstance(ref, dict):
        return None
    path = ref.get("ObjectPath", "")
    try:
        return int(path.rsplit(".", 1)[1])
    except (IndexError, ValueError):
        return None


def tick_res(ms):
    tr = ms.get("Properties", {}).get("TickResolution", {})
    return tr.get("Numerator", 24000) / max(1, tr.get("Denominator", 1)) if tr else 24000.0


def frame_time(v):
    if isinstance(v, dict):
        return v.get("Value", v.get("FrameNumber", {}).get("Value", 0)) if "Value" in v or "FrameNumber" in v else 0
    return v


def dump_curve(name, curve, res, out, indent="        "):
    if not isinstance(curve, dict):
        return
    keys = curve.get("Keys") or []
    times = curve.get("Times") or []
    values = curve.get("Values") or []
    default = curve.get("DefaultValue")
    pre, post = curve.get("PreInfinityExtrap"), curve.get("PostInfinityExtrap")
    if keys:
        out.append(f"{indent}{name}: {len(keys)} keys" + (f" (default {default})" if default is not None else ""))
        for k in keys:
            t = frame_time(k.get("Time", {}))
            tf = t / res * 60
            v = k.get("Value", {})
            val = v.get("Value", v) if isinstance(v, dict) else v
            interp = v.get("InterpMode", "") if isinstance(v, dict) else ""
            tang = v.get("TangentMode", "") if isinstance(v, dict) else ""
            tn = v.get("Tangent", {}) if isinstance(v, dict) else {}
            extra = ""
            if tn:
                extra = f" tan in/out {tn.get('ArriveTangent', 0):.3f}/{tn.get('LeaveTangent', 0):.3f} w {tn.get('ArriveTangentWeight', 0):.2f}/{tn.get('LeaveTangentWeight', 0):.2f} {tn.get('TangentWeightMode', '')}"
            out.append(f"{indent}   t={t:>7} ({tf:7.2f} f60, {t / res:6.3f}s) v={val} {interp} {tang}{extra}")
    elif times or values:
        out.append(f"{indent}{name}: {len(times)} keys (times/values form)")
        for t, v in zip(times, values):
            tt = frame_time(t)
            out.append(f"{indent}   t={tt:>7} ({tt / res * 60:7.2f} f60) v={v}")
    else:
        out.append(f"{indent}{name}: no keys, default {default}")


def dump_section(sec, res, out):
    p = sec.get("Properties", {})
    rng = p.get("SectionRange", {}).get("Value", {})
    lo, hi = frame_time(rng.get("LowerBound", {}).get("Value", {})), frame_time(rng.get("UpperBound", {}).get("Value", {}))
    easing = p.get("Easing", {})
    ease = ""
    if easing:
        ei, eo = easing.get("EaseIn", {}), easing.get("EaseOut", {})
        ease = f" easeIn {easing.get('AutoEaseInDuration', '')}/{ei.get('ObjectName', '') if isinstance(ei, dict) else ei} easeOut {easing.get('AutoEaseOutDuration', '')}/{eo.get('ObjectName', '') if isinstance(eo, dict) else eo}"
    out.append(f"      section {sec.get('Name')} [{lo}..{hi}] = [{lo / res * 60:.1f}..{hi / res * 60:.1f} f60]{ease} row {p.get('RowIndex', 0)}")
    for key, val in p.items():
        if key in ("SectionRange", "Easing", "Signature", "RowIndex", "OverlapPriority", "EvalOptions"):
            continue
        if isinstance(val, dict) and ("Keys" in val or "Times" in val or "DefaultValue" in val):
            dump_curve(key, val, res, out)
        elif isinstance(val, dict) and all(isinstance(v, dict) for v in val.values()) and any("Keys" in v for v in val.values() if isinstance(v, dict)):
            for sub, curve in val.items():
                dump_curve(f"{key}.{sub}", curve, res, out)
        elif key == "EventChannel":
            for k in val.get("KeyTimes", []) or []:
                out.append(f"        event at {frame_time(k)} ({frame_time(k) / res * 60:.1f} f60)")
            for e in val.get("KeyValues", []) or []:
                out.append(f"        event: {str(e)[:200]}")
        else:
            out.append(f"        {key}: {str(val)[:160]}")


def dump(path, out):
    objs = json.loads(Path(path).read_text(encoding="utf-8"))
    idx = by_index(objs)
    out.append(f"##### {Path(path).name}: {len(objs)} objects")
    # widget tree
    for o in objs:
        if o.get("Type") in ("Image", "Overlay", "CanvasPanel", "SizeBox", "ScaleBox", "TextBlock", "Spacer", "Border") or o.get("Type", "").endswith("_C") and "Default__" not in o.get("Name", ""):
            p = o.get("Properties", {})
            keep = {k: p[k] for k in p if k in ("RenderTransform", "RenderOpacity", "Visibility", "ColorAndOpacity", "SizeOverride", "FadeInDuration", "FadeOutDuration", "DissolveTexture", "AnimationStates", "ColorTint", "RenderTransformPivot")}
            brush = p.get("Brush", {})
            if brush:
                keep["Brush"] = {k: brush[k] for k in brush if k in ("ImageSize", "ResourceObject", "TintColor", "DrawAs")}
            slot = p.get("Slot")
            if slot:
                si = ref_index(slot)
                sp = idx.get(si, {}).get("Properties", {}) if si is not None else {}
                keep["slot"] = {k: sp[k] for k in sp if k in ("LayoutData", "HorizontalAlignment", "VerticalAlignment", "Padding")}
            out.append(f"  widget {o.get('Type')} {o.get('Name')}: {json.dumps(keep)[:700]}")
    # functions
    for o in objs:
        if o.get("Type") == "Function":
            script = o.get("ScriptBytecode") or o.get("Script")
            out.append(f"  function {o.get('Name')}" + (f" (bytecode {len(script)} statements)" if script else ""))
    # animations
    for o in objs:
        if o.get("Type") != "WidgetAnimation":
            continue
        p = o.get("Properties", {})
        ms = idx.get(ref_index(p.get("MovieScene")), {})
        mp = ms.get("Properties", {})
        res = tick_res(ms)
        rng = mp.get("PlaybackRange", {}).get("Value", {})
        lo, hi = frame_time(rng.get("LowerBound", {}).get("Value", {})), frame_time(rng.get("UpperBound", {}).get("Value", {}))
        out.append(f"\n  ANIMATION {p.get('DisplayLabel') or o.get('Name')}: range [{lo}..{hi}] ticks = [{lo / res * 60:.1f}..{hi / res * 60:.1f}] frames at 60 ({hi / res:.3f}s), tick res {res}, display rate {mp.get('DisplayRate', {}).get('Numerator')}")
        for b in p.get("AnimationBindings", []) or []:
            out.append(f"    binding: widget {b.get('WidgetName')} slot {b.get('SlotWidgetName')} guid {b.get('AnimationGuid')}")
        for ob in mp.get("ObjectBindings", []) or []:
            out.append(f"    object binding {ob.get('BindingName')} ({ob.get('ObjectGuid')})")
            for tref in ob.get("Tracks", []) or []:
                tr = idx.get(ref_index(tref), {})
                tp = tr.get("Properties", {})
                pb = tp.get("PropertyBinding", {})
                out.append(f"    track {tr.get('Type')} {tr.get('Name')} -> {pb.get('PropertyPath') or pb.get('PropertyName') or ''}")
                for sref in tp.get("Sections", []) or []:
                    sec = idx.get(ref_index(sref), {})
                    dump_section(sec, res, out)
        for tref in mp.get("Tracks", []) or mp.get("MasterTracks", []) or []:
            tr = idx.get(ref_index(tref), {})
            tp = tr.get("Properties", {})
            out.append(f"    master track {tr.get('Type')} {tr.get('Name')}")
            for sref in tp.get("Sections", []) or []:
                sec = idx.get(ref_index(sref), {})
                dump_section(sec, res, out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("files", nargs="+")
    ap.add_argument("--out", default="")
    args = ap.parse_args()
    out = []
    for f in args.files:
        dump(f, out)
    text = "\n".join(out)
    if args.out:
        Path(args.out).write_text(text, encoding="utf-8")
        print(f"wrote {args.out} ({len(out)} lines)")
    else:
        print(text)


if __name__ == "__main__":
    main()
