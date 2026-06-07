#!/usr/bin/env python3
"""
Promote labeled corpus draws into spell_templates variants (coverage expansion).

The existing tuning loop goes corpus -> decision params (tune_corpus.py). It has no
path for the OTHER lever: when a sigil's failures are thin TEMPLATE COVERAGE (too few
variants, or authored variants that don't match how people actually draw it), the fix
is to add real, well-formed draws as new template variants. This script does that
straight from the corpus you already collected — no redrawing.

It works because SpellTemplateLoader pushes every variant through the SAME
PointCloudPreprocessor.process as a live candidate (scale -> centroid -> resample), so
a variant's absolute position/scale is irrelevant; only its shape matters. A corpus
record's `rawStrokes` (un-preprocessed ink, grouped per stroke) is exactly that shape,
so it drops straight into a variant's `points` (flattened, stroke index -> stroke_id).

Selection is conservative by default: only samples the recognizer ALREADY classified
as their own label are promoted, so a borderline/garbage-like draw never becomes a
template (which would widen the accept region and let false matches in). Pass
--include-misrecognized to also add the failures (covers hard cases, higher risk).

  python scripts/promote_to_templates.py fire                 # add all clean fire draws
  python scripts/promote_to_templates.py crush --count 6      # cap how many are added
  python scripts/promote_to_templates.py fire --dry-run       # preview, write nothing
  python scripts/promote_to_templates.py crush --include-misrecognized

IMPORTANT — validation is IN-GAME, not offline. tune_corpus.py replays the LOGGED
chamfer geometry, which does NOT contain these new templates, so it cannot measure
their effect. After promoting, relaunch (or /reload) and verify with F9 / the
gametests / actual casting that recall improved and garbage rejection held. (A faithful
offline check would need the not-yet-built Tier-2 harness that re-runs the real Java
pipeline on rawStrokes — the corpus preserves rawStrokes for exactly that day.)
"""
import argparse
import json
import os

CORPUS = "dataset/spell_corpus.jsonl"
TEMPLATE_DIR = "src/main/resources/data/witchhatateliermod/spell_templates"


def load_corpus(path):
    return [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]


def strokes_to_points(raw_strokes):
    """Flatten corpus rawStrokes [[{x,y},...],...] -> [{x,y,stroke_id},...]."""
    pts = []
    for sid, stroke in enumerate(raw_strokes):
        for p in stroke:
            pts.append({"x": round(float(p["x"]), 3),
                        "y": round(float(p["y"]), 3),
                        "stroke_id": sid})
    return pts


def stroke_signature(raw_strokes):
    """Stable key for dedup: rounded, flattened coordinates + stroke layout."""
    return tuple((sid, round(float(p["x"]), 3), round(float(p["y"]), 3))
                 for sid, s in enumerate(raw_strokes) for p in s)


def main():
    ap = argparse.ArgumentParser(description="Promote corpus draws into spell_templates variants.")
    ap.add_argument("label", help="intended label to promote (e.g. fire, crush)")
    ap.add_argument("--corpus", default=CORPUS)
    ap.add_argument("--count", type=int, default=None, help="max variants to add (default: all eligible)")
    ap.add_argument("--include-misrecognized", action="store_true",
                    help="also promote samples the recognizer got wrong (covers hard cases, riskier)")
    ap.add_argument("--dry-run", action="store_true", help="report what would change; write nothing")
    args = ap.parse_args()

    if not os.path.exists(args.corpus):
        print("No corpus at", args.corpus, "- run build_corpus.py first.")
        return

    template_path = os.path.join(TEMPLATE_DIR, args.label + ".json")
    if not os.path.exists(template_path):
        print("No template file at", template_path,
              "- this script extends an existing spell; create the file first.")
        return

    recs = [r for r in load_corpus(args.corpus) if r.get("intended") == args.label]
    if not recs:
        print(f"No corpus samples labeled '{args.label}'.")
        return

    tpl = json.load(open(template_path, encoding="utf-8"))
    variants = tpl.setdefault("variants", [])

    # Dedup against anything already present (authored or previously promoted), by the
    # variant's own point signature so we never double-add the same draw.
    existing_sigs = set()
    for v in variants:
        pts = v.get("points", [])
        existing_sigs.add(tuple((p.get("stroke_id", 0), round(float(p["x"]), 3), round(float(p["y"]), 3))
                                for p in pts))

    added, skipped_dup, skipped_mis = 0, 0, 0
    next_idx = 1
    used = {v.get("name") for v in variants}
    for r in recs:
        result_spell = (r.get("result") or {}).get("spell")
        if not args.include_misrecognized and result_spell != args.label:
            skipped_mis += 1
            continue
        raw = r.get("rawStrokes")
        if not raw:
            continue
        if stroke_signature(raw) in existing_sigs:
            skipped_dup += 1
            continue
        if args.count is not None and added >= args.count:
            break
        while f"corpus_{next_idx}" in used:
            next_idx += 1
        name = f"corpus_{next_idx}"
        used.add(name)
        existing_sigs.add(stroke_signature(raw))
        variants.append({"name": name, "points": strokes_to_points(raw)})
        added += 1

    print(f"label '{args.label}': {len(recs)} corpus samples")
    print(f"  eligible (clean) skipped as misrecognized : {skipped_mis}"
          + (" (included)" if args.include_misrecognized else ""))
    print(f"  skipped as duplicate of existing variant   : {skipped_dup}")
    print(f"  variants: {len(variants) - added} existing -> {len(variants)} (+{added})")

    if args.dry_run:
        print("  [dry-run] no file written.")
        return
    if added == 0:
        print("  nothing to add; file unchanged.")
        return
    with open(template_path, "w", encoding="utf-8") as f:
        json.dump(tpl, f, indent=2)
        f.write("\n")
    print(f"  wrote {template_path}")
    print("  NEXT: relaunch or /reload, then validate in-game (F9 / gametests) — the")
    print("        offline tuner cannot see new templates.")


if __name__ == "__main__":
    main()
