package com.crsocial.witchhatatelier.spell.compiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Structural representation of one ring's spell, produced by {@link
 * SpellGraphBuilder}. A graph that escapes the builder is guaranteed
 * structurally valid (exactly one element) — the terminal artifact of the
 * pipeline today; nothing downstream resolves it into a world effect yet.
 *
 * @param root       the enclosing ring
 * @param core       the central element (representative of the ring's single element)
 * @param sigilStack how many identical elements were drawn ({@code >= 1})
 * @param convergence whether a Convergence glyph was drawn
 * @param forms      one node per form occurrence
 * @param effects    one node per effect occurrence
 * @param inner      nested inner-ring graph; empty until ring nesting is built
 * @param symmetry   glyph-placement symmetry report (forms + effects combined)
 * @param size       inscription size report
 */
public record SpellGraph(RingNode root,
                         ElementNode core,
                         int sigilStack,
                         boolean convergence,
                         List<FormNode> forms,
                         List<EffectNode> effects,
                         Optional<SpellGraph> inner,
                         SymmetryReport symmetry,
                         SizeReport size) {

    /** Occurrences grouped per form type, for the composition engine's stacking math. */
    public List<FormBundle> formsByType() {
        Map<FormType, List<FormNode>> grouped = new LinkedHashMap<>();
        for (FormNode n : forms) {
            grouped.computeIfAbsent(n.type(), k -> new ArrayList<>()).add(n);
        }
        List<FormBundle> out = new ArrayList<>(grouped.size());
        for (Map.Entry<FormType, List<FormNode>> e : grouped.entrySet()) {
            out.add(new FormBundle(e.getKey(), e.getValue().size(), List.copyOf(e.getValue())));
        }
        return out;
    }

    /** Occurrences grouped per effect type, for the composition engine's stacking math. */
    public List<EffectBundle> effectsByType() {
        Map<EffectType, List<EffectNode>> grouped = new LinkedHashMap<>();
        for (EffectNode n : effects) {
            grouped.computeIfAbsent(n.type(), k -> new ArrayList<>()).add(n);
        }
        List<EffectBundle> out = new ArrayList<>(grouped.size());
        for (Map.Entry<EffectType, List<EffectNode>> e : grouped.entrySet()) {
            out.add(new EffectBundle(e.getKey(), e.getValue().size(), List.copyOf(e.getValue())));
        }
        return out;
    }

    /**
     * Human-readable description of what was drawn, e.g. {@code "Column +
     * Levitation"}, {@code "Convergence + Column"}, or {@code "default (no
     * forms/effects)"}.
     */
    public String describeForm() {
        List<String> names = new ArrayList<>();
        if (convergence) names.add("Convergence");
        names.addAll(distinctNames(forms.stream().map(FormNode::type).map(Enum::name).toList()));
        names.addAll(distinctNames(effects.stream().map(EffectNode::type).map(Enum::name).toList()));
        return names.isEmpty() ? "default (no forms/effects)" : String.join(" + ", names);
    }

    private static List<String> distinctNames(List<String> enumNames) {
        List<String> seen = new ArrayList<>();
        for (String n : enumNames) {
            String title = titleCase(n);
            if (!seen.contains(title)) seen.add(title);
        }
        return seen;
    }

    private static String titleCase(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Multi-line structured dump for server logging. */
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SpellGraph{\n");
        sb.append(String.format(Locale.ROOT,
                "  ring: strokes=%s arc=%.0f° radius=%.1f%n",
                root.strokeIds(), root.arcCoverageDeg(), root.radius()));
        sb.append(String.format(Locale.ROOT,
                "  element: %s x%d quality=%.2f centre=(%.1f,%.1f)%n",
                core.type(), sigilStack, core.quality(), core.centre().x, core.centre().y));
        sb.append(String.format(Locale.ROOT, "  convergence: %b%n", convergence));
        sb.append(String.format(Locale.ROOT, "  composition: %s%n", describeForm()));
        sb.append("  forms:");
        if (formsByType().isEmpty()) {
            sb.append(" (none)\n");
        } else {
            sb.append('\n');
            for (FormBundle b : formsByType()) {
                sb.append(String.format(Locale.ROOT, "    %s x%d [%s]%n", b.type(), b.count(), b.type().role()));
            }
        }
        sb.append("  effects:");
        if (effectsByType().isEmpty()) {
            sb.append(" (none)\n");
        } else {
            sb.append('\n');
            for (EffectBundle b : effectsByType()) {
                sb.append(String.format(Locale.ROOT,
                        "    %s x%d [canCarry=%b, mode=%s]%n",
                        b.type(), b.count(), b.type().canCarry(), b.type().modeTag()));
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
