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
  python scripts/build_corpus.py [log.jsonl] [corpus.jsonl]
"""
import json
import os
import sys

LOG = "run/logs/spell_recognition.jsonl"
CORPUS = "dataset/spell_corpus.jsonl"


def load(path):
    if not os.path.exists(path):
        return []
    return [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]


def signature(rec):
    """Identity of a sample: its label + its exact raw strokes."""
    return rec.get("intended", "") + "|" + json.dumps(rec.get("rawStrokes", []), sort_keys=True)


def main():
    log_path = sys.argv[1] if len(sys.argv) > 1 else LOG
    corpus_path = sys.argv[2] if len(sys.argv) > 2 else CORPUS

    existing = load(corpus_path)
    seen = {signature(r) for r in existing}

    incoming = [r for r in load(log_path) if r.get("intended")]
    new = []
    for r in incoming:
        sig = signature(r)
        if sig not in seen:
            seen.add(sig)
            new.append(r)

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


if __name__ == "__main__":
    main()
