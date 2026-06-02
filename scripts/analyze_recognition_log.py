#!/usr/bin/env python3
"""
Phase 0 analyzer for the spell-recognition log.

Reads logs/spell_recognition.jsonl (one JSON object per recognized sigil) and,
using the `intended` ground-truth label written by `/spell label <value>`,
produces what the remediation plan's Phase 0 is supposed to yield:

  1. accuracy + confusion matrix (where the recognizer is right/wrong today)
  2. VALID-match vs NON-MATCH distributions for each available distance channel
     (mean `dist`, `worst`-pair, `p90`-pair), each with a threshold sweep, so we
     can see whether the Phase-2 worst-pair signal separates where the mean fails
  3. a de-compression recommendation for Phase 1 (DIST_NORM + affine remap)

Records with no `intended` label are skipped. Records labeled `garbage` are the
negative class. Usage:  python scripts/analyze_recognition_log.py [path-to-jsonl]
"""
import json
import sys
import statistics as st
from collections import Counter, defaultdict

GARBAGE = "garbage"
REF = 3 ** 0.5  # current normalizer: score = 1 - d / sqrt(3)


def load(path):
    out = []
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if line:
            out.append(json.loads(line))
    return out


def summ(v):
    if not v:
        return "       (none)"
    return "min {:.3f}  med {:.3f}  mean {:.3f}  max {:.3f}  n={}".format(
        min(v), st.median(v), st.mean(v), max(v), len(v))


def winner(survivors):
    """The survivor the recognizer would pick: smallest mean distance."""
    return min(survivors, key=lambda s: s["dist"])


def best_correct(survivors, spell):
    """Best (smallest mean dist) survivor whose spell == the intended label."""
    cands = [s for s in survivors if s["spell"] == spell]
    return min(cands, key=lambda s: s["dist"]) if cands else None


def sweep(valid, nonmatch, label):
    """Find the threshold on a channel that best separates valid (keep, <=t)
    from non-match (reject, >t)."""
    if not valid or not nonmatch:
        print("    (insufficient data)")
        return
    cuts = sorted(set(round(x, 4) for x in valid + nonmatch))
    best = None
    for t in cuts:
        vk = sum(1 for v in valid if v <= t) / len(valid)
        gr = sum(1 for g in nonmatch if g > t) / len(nonmatch)
        if best is None or (vk + gr) > best[0]:
            best = (vk + gr, t, vk, gr)
    # also report the strictest threshold that still keeps >=90% valid
    keep90 = None
    for t in cuts:
        if sum(1 for v in valid if v <= t) / len(valid) >= 0.90:
            gr = sum(1 for g in nonmatch if g > t) / len(nonmatch)
            keep90 = (t, gr)
            break
    print("    best balance @ {:.3f}: keep {:.0%} valid, reject {:.0%} non-match"
          .format(best[1], best[2], best[3]))
    if keep90:
        print("    @>=90% valid  @ {:.3f}: reject {:.0%} non-match".format(keep90[0], keep90[1]))


def channel_report(real, garbage, key, title):
    print("\n" + "-" * 70)
    print("{}  ({})  — lower = better match".format(title, key))
    # valid: the correct-spell template that would win, its channel value
    valid = [bc[key] for r in real
             if (bc := best_correct(r["survivors"], r["intended"])) is not None]
    # cross: for a real draw, the winning DIFFERENT-spell template's channel value
    cross = []
    for r in real:
        others = [s for s in r["survivors"] if s["spell"] != r["intended"]]
        if others:
            cross.append(min(others, key=lambda s: s["dist"])[key])
    # garbage: the overall winning template's channel value (what would be accepted)
    garb = [winner(r["survivors"])[key] for r in garbage if r["survivors"]]
    nonmatch = cross + garb
    print("  VALID    :", summ(valid))
    print("  CROSS    :", summ(cross))
    print("  GARBAGE  :", summ(garb))
    print("  NON-MATCH:", summ(nonmatch))
    print("  sweep:")
    sweep(valid, nonmatch, key)
    return valid, nonmatch


def tune_soft_demote(real, garbage):
    """Sweep (free, weight, minScore) for the distance-space worst-pair soft-demote:
    effDist = mean + weight*max(0, worst - free); score = 1 - effDist/sqrt(3); accept
    if the winning template (min effDist) clears minScore and matches the intended spell.
    NOTE: approximate — operates on the unfiltered matchVerbose survivors (no prefilter/
    grid), so treat the numbers as priors to confirm in-game, not exact."""
    REF = 3 ** 0.5
    print("\n" + "=" * 70)
    print("PHASE 2 SOFT-DEMOTE TUNER  (effDist = mean + W*max(0, worst-FREE))")

    def run(free, weight, minscore):
        def eff(s):
            return s["dist"] + weight * max(0.0, s["worst"] - free)
        rc = gr = 0
        for r in real:
            w = min(r["survivors"], key=eff)
            if (1 - eff(w) / REF) >= minscore and w["spell"] == r["intended"]:
                rc += 1
        for r in garbage:
            w = min(r["survivors"], key=eff)
            if (1 - eff(w) / REF) < minscore:
                gr += 1
        return rc / len(real), gr / len(garbage)

    best = None
    best90 = None
    for free in [0.15, 0.20, 0.25, 0.30, 0.35]:
        for weight in [0.0, 0.2, 0.4, 0.6, 0.8, 1.0, 1.5]:
            for ms in [round(0.80 + 0.005 * k, 3) for k in range(0, 40)]:
                vk, gr = run(free, weight, ms)
                if best is None or (vk + gr) > best[0]:
                    best = (vk + gr, free, weight, ms, vk, gr)
                if vk >= 0.90 and (best90 is None or gr > best90[0]):
                    best90 = (gr, free, weight, ms, vk)
    _, free, weight, ms, vk, gr = best
    print("  best balance : FREE={} WEIGHT={} minScore={} -> keep {:.0%} valid, reject {:.0%} garbage"
          .format(free, weight, ms, vk, gr))
    if best90:
        gr, free, weight, ms, vk = best90
        print("  @>=90% valid : FREE={} WEIGHT={} minScore={} -> reject {:.0%} garbage (keep {:.0%})"
              .format(free, weight, ms, gr, vk))
    # weight=0 baseline (no demote) at its best minScore, for comparison
    base = [(run(0.25, 0.0, ms)[1], ms) for ms in [round(0.80 + 0.005 * k, 3) for k in range(0, 40)]
            if run(0.25, 0.0, ms)[0] >= 0.90]
    if base:
        b0 = max(base)
        print("  (no-demote baseline @>=90% valid: reject {:.0%} garbage at minScore={})".format(b0[0], b0[1]))


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "run/logs/spell_recognition.jsonl"
    rows = [r for r in load(path) if r.get("intended")]
    if not rows:
        print("No labeled records found in", path)
        return

    real = [r for r in rows if r["intended"] != GARBAGE]
    garbage = [r for r in rows if r["intended"] == GARBAGE]
    has_worst = all("worst" in s for r in rows for s in r["survivors"])

    print("=" * 70)
    print("LABELED:", len(rows), " | real:", len(real), " | garbage:", len(garbage),
          " | worst-pair logged:", has_worst)
    for k, v in sorted(Counter(r["intended"] for r in rows).items()):
        print("   {:<14} {}".format(k, v))

    # ---- accuracy + confusion ---------------------------------------------
    print("\n" + "=" * 70 + "\nACCURACY")
    correct = sum(1 for r in real if r["result"]["spell"] == r["intended"])
    print("  real recognized : {}/{} ({:.0%})".format(
        correct, len(real), correct / len(real) if real else 0))
    print("  real -> unknown : {}/{} (false rejects)".format(
        sum(1 for r in real if r["result"]["spell"] == "unknown"), len(real)))
    if garbage:
        gj = sum(1 for r in garbage if r["result"]["spell"] == "unknown")
        print("  garbage rejected: {}/{} ({:.0%})".format(gj, len(garbage), gj / len(garbage)))
        print("  garbage ACCEPTED: {}/{} (false accepts)".format(len(garbage) - gj, len(garbage)))

    print("\nCONFUSION (intended -> result), real only:")
    conf = defaultdict(Counter)
    for r in real:
        conf[r["intended"]][r["result"]["spell"]] += 1
    for intended in sorted(conf):
        print("  {:<12} ->  ".format(intended)
              + "   ".join("{}:{}".format(res, n) for res, n in conf[intended].most_common()))

    # ---- distributions per channel ----------------------------------------
    print("\n" + "=" * 70 + "\nDISTRIBUTIONS  (winning-template channel values)")
    valid_mean, nonmatch_mean = channel_report(real, garbage, "dist", "MEAN distance")
    if has_worst:
        channel_report(real, garbage, "p90", "P90 matched-pair distance")
        channel_report(real, garbage, "worst", "WORST matched-pair distance")
    else:
        print("\n  (worst-pair / p90 not in this log — recollect with the instrumented build)")

    # ---- Phase 2 soft-demote tuner ----------------------------------------
    if has_worst and garbage:
        tune_soft_demote(real, garbage)

    # ---- Phase 1 de-compression on the mean channel -----------------------
    print("\n" + "=" * 70 + "\nPHASE 1 — DE-COMPRESSION (mean channel)")
    cur = [1 - d / REF for d in (valid_mean + nonmatch_mean)]
    print("  current band (1 - d/sqrt3): {:.3f}..{:.3f} (uses {:.0%} of 0..1)".format(
        min(cur), max(cur), max(cur) - min(cur)))
    if valid_mean and nonmatch_mean:
        mv, mn = st.median(valid_mean), st.median(nonmatch_mean)
        dist_norm = mn / 0.80
        b = 0.70 / (mn - mv) if mn != mv else 0.0
        a = 0.90 + b * mv
        print("  median valid {:.3f} | median non-match {:.3f}".format(mv, mn))
        print("  Option A: DIST_NORM = {:.3f}   (score = max(0, 1 - d/{:.3f}))".format(dist_norm, dist_norm))
        print("  Option B: score = clamp({:.3f} - {:.3f} * d)   (pins medians 0.90/0.20)".format(a, b))


if __name__ == "__main__":
    main()
