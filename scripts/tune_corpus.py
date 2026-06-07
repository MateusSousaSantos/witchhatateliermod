#!/usr/bin/env python3
"""
Tier-1 offline tuner: replay the recognizer's DECISION over the corpus.

Because the decision trail logs every template's chamfer geometry (dist, worst,
gridSim) and prefilter verdict, we can recompute match()'s final answer under ANY
decision-stage params WITHOUT re-running the chamfer or redrawing. This sweeps
those params over the whole corpus in milliseconds.

Faithfully mirrors PDollarPlusRecognizer.matchInternal for the decision stage:
  effDist  = mean + WEIGHT * max(0, worst - FREE)
  rawScore = clamp((distZero - effDist) / (distZero - distFull), 0, 1)   # Phase-1 ramp
  score    = rawScore * gridMult   (only if rawScore > gridCheckThr and gridSim known)
  winner   = max score; reject if < minScore; consensus rescue; ambiguity margin.

The Phase-1 two-pin score map (distFull→1, distZero→0) replaced the old fixed
1−d/sqrt(3). Records logged before Phase-1 carry no pins; for them the replay falls
back to distFull=0, distZero=sqrt(3) (i.e. exactly 1−d/sqrt(3)), so the FIDELITY
check stays faithful to how they were actually scored. The SWEEP/EVAL instead apply
the NEW pins (--distFull/--distZero, default 0.054/0.085) to the logged geometry, so
you can re-derive minScore for the de-compressed scale without redrawing.

Only tunes DECISION params (minScore, worstPairFree/weight, ambiguityMargin,
consensus, gridMinSimilarity). Preprocessing/prefilter params are baked into the
logged geometry — changing those needs the Tier-2 Java harness.

Usage:
  python scripts/tune_corpus.py                       # fidelity check + sweep
  python scripts/tune_corpus.py --free .25 --weight .4 --minScore .9   # eval one config
"""
import argparse
import json
import os
import sys
from collections import Counter, defaultdict

REF = 3 ** 0.5
CORPUS = "dataset/spell_corpus.jsonl"
GARBAGE = "garbage"

# Phase-1 score de-compression pins (mirror Config.RECOGNITION_DIST_AT_*_SCORE).
# Distances at/below DIST_FULL score 1.0; at/above DIST_ZERO score 0.0.
DIST_FULL_DEFAULT = 0.054
DIST_ZERO_DEFAULT = 0.085


def load(path):
    return [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]


def score_raw(eff, dist_full, dist_zero):
    """Phase-1 two-pin linear ramp: clamp((distZero - eff)/(distZero - distFull), 0, 1)."""
    denom = dist_zero - dist_full
    if denom <= 1e-6:
        return 1.0 if eff <= dist_full else 0.0
    return max(0.0, min(1.0, (dist_zero - eff) / denom))


def gmult(grid_sim, grid_min):
    if grid_min <= 0 or grid_sim is None or grid_sim >= grid_min:
        return 1.0
    return max(0.0, grid_sim / grid_min)


def replay(rec, P):
    """Reconstruct match()'s predicted spell for one record under params P."""
    d = rec.get("decision")
    if not d:
        return None
    best = None            # (spell, score, worst)
    best_per_spell = {}
    survivors = []         # (spell, score)
    drop = P.get("drop")   # ablation: exclude templates by 'spell' or 'spell:variant'
    for t in d["templates"]:
        if drop and (t["spell"] in drop or t["spell"] + ":" + t.get("variant", "") in drop):
            continue       # ablated template — invisible to the matcher
        if not t.get("pre"):
            continue       # prefiltered out — excluded from winner selection
        eff = t["dist"] + P["weight"] * max(0.0, t["worst"] - P["free"])
        raw = score_raw(eff, P["distFull"], P["distZero"])
        s = raw
        if raw > P["gridCheckThr"]:
            s *= gmult(t.get("gridSim"), P["gridMinSim"])
        survivors.append((t["spell"], s))
        if t["spell"] not in best_per_spell or s > best_per_spell[t["spell"]]:
            best_per_spell[t["spell"]] = s
        if best is None or s > best[1]:
            best = (t["spell"], s, t["worst"])
    if best is None:
        return "unknown"
    winner, best_score, _ = best
    if best_score < P["minScore"]:
        return "unknown"
    other = max([v for k, v in best_per_spell.items() if k != winner], default=0.0)
    eff_score, gap = best_score, best_score - other
    if gap < P["margin"]:
        survivors.sort(key=lambda x: -x[1])
        topn = min(P["consensusTopN"], len(survivors))
        agree = sum(1 for i in range(topn) if survivors[i][0] == winner)
        eff_score = min(1.0, best_score + P["consensusBonus"] * agree)
        gap = eff_score - other
    return "unknown" if gap < P["margin"] else winner


def params_from_record(rec):
    thr = rec["thresholds"]
    d = rec["decision"]
    return {
        "minScore": thr["minScore"],
        "margin": thr["ambiguityMargin"],
        "consensusBonus": thr["consensusBonus"],
        "consensusTopN": thr["consensusTopN"],
        "gridCheckThr": thr["gridCheckScoreThreshold"],
        "gridMinSim": thr["gridMinSimilarity"],
        "free": d.get("worstPairFree", 0.0),
        "weight": d.get("worstPairWeight", 0.0),
        # Pre-Phase-1 records lack pins → distFull=0, distZero=√3 == old 1−d/√3.
        "distFull": thr.get("distAtFullScore", 0.0),
        "distZero": thr.get("distAtZeroScore", REF),
    }


def evaluate(records, P):
    """Returns (valid_correct, n_real, garbage_rejected, n_garbage, confusion)."""
    rc = gr = nr = ng = 0
    conf = defaultdict(Counter)
    for rec in records:
        pred = replay(rec, P)
        intended = rec["intended"]
        if intended == GARBAGE:
            ng += 1
            if pred == "unknown":
                gr += 1
        else:
            nr += 1
            conf[intended][pred] += 1
            if pred == intended:
                rc += 1
    return rc, nr, gr, ng, conf


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("corpus", nargs="?", default=CORPUS)
    ap.add_argument("--free", type=float)
    ap.add_argument("--weight", type=float)
    ap.add_argument("--minScore", type=float)
    ap.add_argument("--margin", type=float)
    ap.add_argument("--gridMinSim", type=float)
    ap.add_argument("--distFull", type=float, help="Phase-1 lower pin (score=1 at/below)")
    ap.add_argument("--distZero", type=float, help="Phase-1 upper pin (score=0 at/above)")
    ap.add_argument("--drop", action="append", default=[],
                    help="ablate a template from the matcher: 'pull:variant_7' (one variant) "
                         "or 'pull' (whole spell). Repeatable. Faithful because removing a "
                         "template never changes any other template's logged geometry.")
    args = ap.parse_args()

    if not os.path.exists(args.corpus):
        print("No corpus at", args.corpus, "- run build_corpus.py first.")
        return
    allrecs = [r for r in load(args.corpus) if r.get("intended")]
    recs = [r for r in allrecs if r.get("decision")]
    print("corpus:", len(allrecs), "labeled |", len(recs), "with decision trail (usable)")
    if not recs:
        print("No decision-trail records — recollect on the decision-trail build.")
        return
    real = [r for r in recs if r["intended"] != GARBAGE]
    garb = [r for r in recs if r["intended"] == GARBAGE]
    print("  real:", len(real), " garbage:", len(garb))

    # 1. Fidelity check: replay each record under ITS OWN logged params; the
    #    prediction must match the logged result, else the replay is not faithful.
    ok = 0
    for r in recs:
        if replay(r, params_from_record(r)) == r["result"]["spell"]:
            ok += 1
    print("\nFIDELITY: replay reproduces logged result for {}/{} records ({:.0%})".format(
        ok, len(recs), ok / len(recs)))
    if ok < len(recs):
        print("  (mismatches usually mean params drifted between draws, or grid re-applied)")

    # baseline params for sweeps: take from the most recent record, allow CLI override.
    # The score pins always default to the NEW Phase-1 values (not the record's), so the
    # sweep re-derives thresholds for the de-compressed scale over the logged geometry.
    base = params_from_record(recs[-1])
    base["distFull"] = args.distFull if args.distFull is not None else DIST_FULL_DEFAULT
    base["distZero"] = args.distZero if args.distZero is not None else DIST_ZERO_DEFAULT
    base["drop"] = set(args.drop)
    for k, v in (("free", args.free), ("weight", args.weight), ("minScore", args.minScore),
                 ("margin", args.margin), ("gridMinSim", args.gridMinSim)):
        if v is not None:
            base[k] = v

    # 2. If the user pinned any param (or ablated a template), evaluate that single config.
    if args.drop or any(x is not None for x in (args.free, args.weight, args.minScore, args.margin,
                                   args.gridMinSim, args.distFull, args.distZero)):
        rc, nr, gr, ng, conf = evaluate(recs, base)
        print("\nEVAL  free={free} weight={weight} minScore={minScore} margin={margin} "
              "gridMinSim={gridMinSim} distFull={distFull} distZero={distZero}".format(**base))
        if base["drop"]:
            print("  dropped templates: " + ", ".join(sorted(base["drop"])))
        print("  real correct : {}/{} ({:.0%})".format(rc, nr, rc / nr if nr else 0))
        print("  garbage rejct: {}/{} ({:.0%})".format(gr, ng, gr / ng if ng else 0))
        print("  confusion:")
        for i in sorted(conf):
            print("    {:<12} -> ".format(i) + "  ".join("{}:{}".format(k, n) for k, n in conf[i].most_common()))
        return

    # 3. Sweep free / weight / minScore; hold the rest at base. The minScore range is
    #    wide (0.30–1.0) because the Phase-1 ramp moves the valid/garbage crossover well
    #    below the old ~0.90 band.
    print("\nSWEEP (margin={:.3f} gridMinSim={:.2f} consensusBonus={:.3f} topN={} "
          "distFull={:.3f} distZero={:.3f})".format(
              base["margin"], base["gridMinSim"], base["consensusBonus"], base["consensusTopN"],
              base["distFull"], base["distZero"]))
    best = best90 = None
    for free in [0.15, 0.20, 0.25, 0.30, 0.35]:
        for weight in [0.0, 0.2, 0.4, 0.6, 0.8, 1.0, 1.5]:
            for ms in [round(0.30 + 0.01 * k, 3) for k in range(0, 71)]:
                P = dict(base, free=free, weight=weight, minScore=ms)
                rc, nr, gr, ng, _ = evaluate(recs, P)
                vk = rc / nr if nr else 0
                gj = gr / ng if ng else 0
                if best is None or (vk + gj) > best[0]:
                    best = (vk + gj, free, weight, ms, vk, gj)
                if vk >= 0.90 and (best90 is None or gj > best90[0]):
                    best90 = (gj, free, weight, ms, vk)
    _, free, weight, ms, vk, gj = best
    print("  best balance : free={} weight={} minScore={} -> {:.0%} valid, {:.0%} garbage rejected"
          .format(free, weight, ms, vk, gj))
    if best90:
        gj, free, weight, ms, vk = best90
        print("  @>=90% valid : free={} weight={} minScore={} -> {:.0%} garbage rejected ({:.0%} valid)"
              .format(free, weight, ms, gj, vk))
    print("\nApply with: set worstPairFreeAllowance / worstPairWeight / recognitionMinScore in the config.")


if __name__ == "__main__":
    main()
