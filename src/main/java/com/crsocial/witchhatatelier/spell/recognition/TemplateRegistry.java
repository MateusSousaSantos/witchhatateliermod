package com.crsocial.witchhatatelier.spell.recognition;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable, server-side singleton holding every loaded {@link Template}.
 * Cleared and rebuilt on every datapack reload by {@link SpellTemplateLoader}.
 */
public final class TemplateRegistry {

    private static final TemplateRegistry INSTANCE = new TemplateRegistry();

    private final List<Template> templates = new ArrayList<>();

    private TemplateRegistry() {}

    public static TemplateRegistry get() {
        return INSTANCE;
    }

    public synchronized void clear() {
        templates.clear();
    }

    public synchronized void register(Template t) {
        templates.add(t);
    }

    /** Returns only content (non-ring) templates, used for spell recognition. */
    public synchronized List<Template> all() {
        return templates.stream().filter(t -> !t.isRing()).toList();
    }

    /** Returns only ring-validator templates (those with {@code isRing=true}). */
    public synchronized List<Template> allRing() {
        return templates.stream().filter(Template::isRing).toList();
    }

    /** Total count of all registered templates (content + ring). */
    public synchronized int size() {
        return templates.size();
    }

    /**
     * True when any registered template for {@code spellName} is flagged
     * {@code directional} — i.e. the glyph is an "arrow" whose drawn heading
     * should steer the spell. Matched case-insensitively against {@code spell_name}.
     */
    public synchronized boolean isDirectional(String spellName) {
        if (spellName == null) return false;
        for (Template t : templates) {
            if (t.directional() && t.spellName().equalsIgnoreCase(spellName)) return true;
        }
        return false;
    }
}
