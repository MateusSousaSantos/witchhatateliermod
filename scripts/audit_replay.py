#!/usr/bin/env python3
"""
Audit a Tier-2 replay log: per-drawer accuracy, win-stealing templates, unknown causes.

Reads run/logs/corpus_replay.jsonl (or a path argument) and prints the three views the
recall-first loop iterates on:

  1. PER-DRAWER real accuracy + that drawer's confusion cells — shows WHOSE drawing
     style the templates fail on (the coverage signal).
  2. WIN-STEALING template variants — for every misclassified real draw, which
     template variant actually won. Concentrated thieves are ablation candidates
     (verify with `tune_corpus.py <replay-log> --drop spell:variant` before pruning).
  3. UNKNOWN rejection stages — floor vs ambiguity-gate, i.e. whether false rejects
     are a score problem or a near-tie problem.

Drawer identity: CorpusReplay carries the corpus `player` field through, so the replay
log alone is usually enough; the positional corpus join remains as a fallback for old
logs. The strict length assert below fails loudly if the two files ever misalign
(e.g. corpus edited after the replay ran) — re-run `/spell replay-corpus` rather than
trusting a stale log.

Cut classes: labels with no template class in the replayed registry (e.g. `dispersion`
after its roster cut, `bolt` which never shipped) are excluded from real accuracy and
the steal table, and reported in their own should-reject section — a cut draw that
still matches some class is a live misrecognition risk.

  python scripts/audit_replay.py [replay.jsonl] [corpus.jsonl]
  # defaults: run/logs/corpus_replay.jsonl, dataset/spell_corpus.jsonl
"""
import collections
import json
import sys

REPLAY = "run/logs/corpus_replay.jsonl"
CORPUS = "dataset/spell_corpus.jsonl"


def load(path):
    return [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]


def main():
    replay_path = sys.argv[1] if len(sys.argv) > 1 else REPLAY
    corpus_path = sys.argv[2] if len(sys.argv) > 2 else CORPUS
    replay = load(replay_path)
    corpus = load(corpus_path)
    # CorpusReplay processes corpus lines in order (skipping only empty-stroke
    # records), so an equal count means a 1:1 positional join is safe.
    assert len(replay) == len(corpus), (
        f"replay has {len(replay)} records but corpus has {len(corpus)} — "
        "stale replay log? re-run /spell replay-corpus")

    # Template classes that were live during the replay — every record's survivors list
    # covers all templates, so their union is the covered-class set. Labels outside it
    # are cut/uncovered classes and get their own bucket.
    covered = set()
    for rec in replay:
        for s in rec.get("survivors") or []:
            covered.add(s["spell"])

    steal = collections.Counter()                                # winner variant -> wrong-win count
    steal_from = collections.defaultdict(collections.Counter)    # winner variant -> intended labels
    unknown_stage = collections.Counter()
    per_drawer = collections.defaultdict(lambda: [0, 0])         # drawer -> [correct, real total]
    per_drawer_conf = collections.defaultdict(collections.Counter)
    cut_outcome = collections.defaultdict(collections.Counter)   # cut label -> result spells

    for rec, crec in zip(replay, corpus):
        intended = rec.get("intended")
        # CorpusReplay stamps player="replay" (uuid "<replay>") on every output record,
        # so a real drawer id can only come from the joined corpus line (until R0).
        drawer = rec.get("player") if rec.get("player") not in (None, "replay", "<replay>") else None
        drawer = drawer or crec.get("player") or "<?>"
        res = rec["result"]["spell"]
        d = rec.get("decision") or {}
        if not intended or intended == "garbage":
            continue
        if covered and intended not in covered:
            cut_outcome[intended][res] += 1
            continue
        per_drawer[drawer][1] += 1
        if res == intended:
            per_drawer[drawer][0] += 1
            continue
        per_drawer_conf[drawer][f"{intended}->{res}"] += 1
        if res == "unknown":
            unknown_stage[d.get("rejectionStage") or "?"] += 1
        else:
            wv = f"{d.get('winnerSpell', res)}:{d.get('winnerVariant', '?')}"
            steal[wv] += 1
            steal_from[wv][intended] += 1

    print("PER-DRAWER real accuracy:")
    for dr, (c, t) in sorted(per_drawer.items()):
        print(f"  {dr:<12} {c}/{t} ({c / t:.0%})")
        for k, v in per_drawer_conf[dr].most_common(10):
            print(f"      {k}: {v}")

    print("\nWIN-STEALING template variants (wrong wins):")
    for wv, n in steal.most_common(15):
        frm = ", ".join(f"{k}x{v}" for k, v in steal_from[wv].most_common())
        print(f"  {wv:<28} {n:>2}  (from {frm})")

    print("\nUNKNOWN rejection stages (false rejects):",
          dict(unknown_stage) if unknown_stage else "none")

    if cut_outcome:
        print("\nCUT-CLASS draws (no live template — should reject):")
        for label, outcomes in sorted(cut_outcome.items()):
            total = sum(outcomes.values())
            rejected = outcomes.get("unknown", 0)
            leaks = ", ".join(f"{k}x{v}" for k, v in outcomes.most_common() if k != "unknown")
            print(f"  {label:<12} {rejected}/{total} rejected"
                  + (f"  (leaked to: {leaks})" if leaks else ""))


if __name__ == "__main__":
    main()
