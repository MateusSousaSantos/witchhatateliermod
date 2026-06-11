#!/usr/bin/env python3
"""
Accumulate labeled recognition samples into a permanent corpus.

The live log (run/logs/spell_recognition.jsonl) is volatile — it gets wiped
between sessions. This script copies every `intended`-labeled record out of the
log into dataset/spell_corpus.jsonl, deduplicated, so each sample you ever draw
is preserved exactly once. Run it after a labeling session; the corpus only grows.

Each preserved record keeps everything: rawStrokes (for future full-pipeline
replay), the per-template decision trail (for instant offline tuning), and the
intended label. Usage:
  python scripts/build_corpus.py [log.jsonl] [corpus.jsonl] [--drawer NAME]

--drawer NAME stamps every ingested record's `player` with NAME. Use it when
ingesting a website export you know came from one person (the site doesn't stamp
contributor ids): a real per-person id keeps CorpusCrossVal's leave-one-drawer-out
split honest. Without it, web exports collapse into the shared "web_anon" pool.
"""
import argparse
import json
import os

LOG = "run/logs/spell_recognition.jsonl"
CORPUS = "dataset/spell_corpus.jsonl"


def load(path):
    if not os.path.exists(path):
        return []
    return [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]


def signature(rec):
    """Identity of a sample: its label + its exact raw strokes."""
    return rec.get("intended", "") + "|" + json.dumps(rec.get("rawStrokes", []), sort_keys=True)


def normalize_drawer(rec, drawer=None):
    """Ensure every record carries a `player` (drawer id) so CorpusCrossVal can group
    by drawer. Precedence: explicit --drawer override > the record's own `player` >
    a `contributor`/`drawer` field (if the website ever stamps one) > "<source>_anon"
    (e.g. "web_anon", the anonymous website pool). Mutates and returns the record."""
    if drawer:
        rec["player"] = drawer
    elif not rec.get("player"):
        named = rec.get("contributor") or rec.get("drawer")
        src = rec.get("source")
        rec["player"] = named or ((src + "_anon") if src else "<unknown>")
    return rec


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("log", nargs="?", default=LOG)
    ap.add_argument("corpus", nargs="?", default=CORPUS)
    ap.add_argument("--drawer", default=None,
                    help="stamp every ingested record's player with this drawer id")
    args = ap.parse_args()
    log_path = args.log
    corpus_path = args.corpus

    existing = load(corpus_path)
    seen = {signature(r) for r in existing}

    incoming = [r for r in load(log_path) if r.get("intended")]
    new = []
    for r in incoming:
        sig = signature(r)
        if sig not in seen:
            seen.add(sig)
            new.append(normalize_drawer(r, args.drawer))

    os.makedirs(os.path.dirname(corpus_path) or ".", exist_ok=True)
    with open(corpus_path, "a", encoding="utf-8") as f:
        for r in new:
            f.write(json.dumps(r) + "\n")

    total = existing + new
    from collections import Counter
    print("scanned log     :", len(incoming), "labeled record(s)")
    print("added to corpus :", len(new))
    print("corpus total    :", len(total))
    has_decision = sum(1 for r in total if r.get("decision"))
    print("  with decision trail (Tier-1 ready):", has_decision, "/", len(total))
    print("  by label:")
    for k, v in sorted(Counter(r["intended"] for r in total).items()):
        print("    {:<14} {}".format(k, v))
    print("  by drawer:")
    for k, v in sorted(Counter(r.get("player", "<unknown>") for r in total).items()):
        print("    {:<14} {}".format(k, v))


if __name__ == "__main__":
    main()
