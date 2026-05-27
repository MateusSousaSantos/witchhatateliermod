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

    /** Manifestation signs acting as base shapes (Column, Dispersion). Multiple coexist. */
    public List<SignNode> carriers() {
        return byRole(SignType.ManifestationRole.CARRIER);
    }

    /** Manifestation signs that ride on a carrier (Bolt → impacts/projectiles). */
    public List<SignNode> riders() {
        return byRole(SignType.ManifestationRole.RIDER);
    }

    private List<SignNode> byRole(SignType.ManifestationRole role) {
        List<SignNode> out = new ArrayList<>();
        for (SignNode n : modifiers) {
            if (n.type().manifestationRole() == role) out.add(n);
        }
        return out;
    }

    /**
     * Human-readable description of the manifestation form, e.g.
     * {@code "Column + Dispersion"}, {@code "Dispersion with Bolt impacts"},
     * {@code "Bolt"}, or {@code "default (no signs)"}.
     */
    public String describeForm() {
        String carriers = distinctNames(carriers());
        String riders = distinctNames(riders());
        if (!carriers.isEmpty()) {
            return riders.isEmpty() ? carriers : carriers + " with " + riders + " impacts";
        }
        if (!riders.isEmpty()) {
            return riders;
        }
        return "default (no signs)";
    }

    private static String distinctNames(List<SignNode> nodes) {
        StringBuilder sb = new StringBuilder();
        List<SignType> seen = new ArrayList<>();
        for (SignNode n : nodes) {
            if (seen.contains(n.type())) continue;
            seen.add(n.type());
            if (!sb.isEmpty()) sb.append(" + ");
            sb.append(titleCase(n.type().name()));
        }
        return sb.toString();
    }

    private static String titleCase(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(Locale.ROOT);
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
        sb.append(String.format(Locale.ROOT, "  form: %s%n", describeForm()));
        sb.append("  signs:");
        if (signsByType().isEmpty()) {
            sb.append(" (none — sigil default behaviour)\n");
        } else {
            sb.append('\n');
            for (SignBundle b : signsByType()) {
                String role = b.type().tier() == SignType.Tier.MANIFESTATION
                        ? b.type().manifestationRole().toString()
                        : b.type().tier().toString();
                sb.append(String.format(Locale.ROOT,
                        "    %s x%d [%s, %s]%n",
                        b.type(), b.count(), role, b.type().stackingMode()));
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
