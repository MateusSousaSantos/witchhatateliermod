package com.crsocial.witchhatatelier.spell.composition.material;

/**
 * Convergence's mechanism: transforms the working material before any {@code
 * Form} shapes it (the composition engine's material stage, step 0 — see
 * {@code docs/new_spell_engine.md} §6). Registered sparsely per {@link
 * com.crsocial.witchhatatelier.spell.compiler.ElementType} in {@link
 * ConvergenceRegistry} — an element with none registered is left unchanged by
 * convergence.
 */
public interface MaterialModifier {

    /**
     * @param element the element being converged (its {@link Element#converged()}
     *                is typically, but not necessarily, what implementations return)
     * @param in      the material convergence is applied to — normally {@code element.base()}
     * @return the working material after convergence
     */
    Material apply(Element element, Material in);
}
