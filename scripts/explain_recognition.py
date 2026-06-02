#!/usr/bin/env python3
"""
Explain ONE recognition record's decision trail.

Reads logs/spell_recognition.jsonl and prints, for a chosen record, exactly why
match() returned what it did: the per-template prefilter verdicts, the raw vs
grid-adjusted scores (so you can see which leaders were demoted/removed), and the
meta-gates (worst-pair, consensus, ambiguity). Requires the decision-trail build.

Usage:
  python scripts/explain_recognition.py            # last record
  python scripts/explain_recognition.py 5          # record index 5
  python scripts/explain_recognition.py last fire  # last record whose result is 'fire'
"""
import json
import sys


def load(path):
    return [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]


def pick(rows, args):
    spell = None
    idx = None
    for a in args:
        if a == "last":
            idx = "last"
        elif a.lstrip("-").isdigit():
            idx = int(a)
        else:
            spell = a
    cand = [r for r in rows if spell is None or r["result"]["spell"] == spell]
    if not cand:
        return None
    if isinstance(idx, int):
        return rows[idx] if spell is None else cand[idx]
    return cand[-1]


def f(v):
    return "  nan" if v is None else "{:.3f}".format(v)


def main():
    path = "run/logs/spell_recognition.jsonl"
    args = sys.argv[1:]
    if args and args[0].endswith(".jsonl"):
        path = args.pop(0)
    rows = load(path)
    r = pick(rows, args)
    if r is None:
        print("No matching record.")
        return

    print("intended :", r.get("intended"))
    print("result   :", r["result"]["spell"], "score", f(r["result"]["score"]))
    d = r.get("decision")
    if not d:
        print("\n(no decision trail in this record — it predates the decision-trail build)")
        return

    print("\nGATES")
    print("  winner          : {}:{}".format(d["winnerSpell"], d["winnerVariant"]))
    print("  bestScore       : {}  (already includes worst-pair soft-demote)".format(f(d["bestScore"])))
    if "worstPairFree" in d:
        print("  worst-pair      : winner worst {}  (free {}, weight {} -> demoted into score)".format(
            f(d["bestWorstPair"]), f(d.get("worstPairFree")), f(d.get("worstPairWeight"))))
    elif "maxPairDist" in d:  # legacy hard-gate records
        print("  worst-pair gate : worst {} vs maxPairDist {} -> {}".format(
            f(d["bestWorstPair"]), f(d["maxPairDist"]),
            "REJECT" if d["bestWorstPair"] > d["maxPairDist"] else "pass"))
    print("  runner-up       : {} @ {}  (gap {} vs margin {})".format(
        d["runnerUpSpell"], f(d["bestOfOtherSpell"]), f(d["gap"]), f(d["margin"])))
    print("  consensus       : +{} ({} agree) -> effectiveScore {}".format(
        f(d["consensusBonus"]), d["consensusAgree"], f(d["effectiveScore"])))
    print("  rejectionStage  : {}".format(d["rejectionStage"]))

    temps = d["templates"]
    # sort: passed templates by raw desc, then prefiltered
    passed = sorted([t for t in temps if t["pre"]], key=lambda t: -t["raw"])
    pre = [t for t in temps if not t["pre"]]
    win = (d["winnerSpell"], d["winnerVariant"])

    print("\nTEMPLATES THAT PASSED PREFILTERS (by raw score)")
    print("  {:<22} {:>6} {:>6} {:>6} {:>6} {:>6}".format(
        "spell:variant", "raw", "gridX", "final", "worst", "p90"))
    for t in passed:
        mark = "  <-- WINNER" if (t["spell"], t["variant"]) == win else ""
        demoted = (t.get("gridMult", 1) < 0.999)
        if demoted and not mark:
            mark = "  (grid-demoted {:.2f})".format(t["gridMult"])
        print("  {:<22} {:>6} {:>6} {:>6} {:>6} {:>6}{}".format(
            t["spell"] + ":" + t["variant"], f(t["raw"]), f(t.get("gridMult")),
            f(t["final"]), f(t["worst"]), f(t["p90"]), mark))

    if pre:
        print("\nREMOVED BY PREFILTER (chamfer never ran)")
        from collections import Counter
        by = Counter(t["rejectedBy"] for t in pre)
        for t in pre:
            print("  {:<22} rejectedBy={}".format(t["spell"] + ":" + t["variant"], t["rejectedBy"]))
        print("  ----  totals:", dict(by))


if __name__ == "__main__":
    main()
