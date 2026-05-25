package com.crsocial.witchhatatelier.spell.compiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Structural representation of one ring's spell, produced by
 * {@link SpellGraphBuilder}. A graph that escapes the builder is guaranteed
 * structurally valid (one sigil, at most one sign per conflicting tier); the
 * meaning engine assumes this and does not re-check.
 *
 * @param root      the enclosing ring
 * @param core      the central sigil (exactly one)
 * @param modifiers one node per sign occurrence
 * @param inner     nested inner-ring graph; empty until ring nesting (Phase 6)
 * @param symmetry  sign-placement symmetry report
 * @param size      inscription size report
 */
public record SpellGraph(RingNode root,
                         SigilNode core,
                         List<SignNode> modifiers,
                         Optional<SpellGraph> inner,
                         SymmetryReport symmetry,
                         SizeReport size) {

    /** Occurrences grouped per sign type, for the meaning engine's stacking math. */
    public List<SignBundle> signsByType() {
        Map<SignType, List<SignNode>> grouped = new LinkedHashMap<>();
        for (SignNode n : modifiers) {
            grouped.computeIfAbsent(n.type(), k -> new ArrayList<>()).add(n);
        }
        List<SignBundle> out = new ArrayList<>(grouped.size());
        for (Map.Entry<SignType, List<SignNode>> e : grouped.entrySet()) {
            out.add(new SignBundle(e.getKey(), e.getValue().size(), List.copyOf(e.getValue())));
        }
        return out;
    }

    /** Multi-line structured dump for server logging (Phase 1 verification). */
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SpellGraph{\n");
        sb.append(String.format(Locale.ROOT,
                "  ring: strokes=%s arc=%.0f° radius=%.1f%n",
                root.strokeIds(), root.arcCoverageDeg(), root.radius()));
        sb.append(String.format(Locale.ROOT,
                "  sigil: %s quality=%.2f centre=(%.1f,%.1f)%n",
                core.type(), core.quality(), core.centre().x, core.centre().y));
        sb.append("  signs:");
        if (signsByType().isEmpty()) {
            sb.append(" (none — sigil default behaviour)\n");
        } else {
            sb.append('\n');
            for (SignBundle b : signsByType()) {
                sb.append(String.format(Locale.ROOT,
                        "    %s x%d [%s, %s]%n",
                        b.type(), b.count(), b.type().tier(), b.type().stackingMode()));
            }
        }
        sb.append(String.format(Locale.ROOT,
                "  symmetry: radial=%.2f net=(%.1f,%.1f) stable=%b%n",
                symmetry.radialScore(), symmetry.netDirection().x, symmetry.netDirection().y,
                symmetry.stable()));
        sb.append(String.format(Locale.ROOT,
                "  size: bboxArea=%.0f normalized=%.2f%n",
                size.bboxArea(), size.normalizedBboxArea()));
        sb.append(String.format(Locale.ROOT, "  inner: %s%n", inner.isPresent() ? "present" : "none"));
        sb.append('}');
        return sb.toString();
    }
}
