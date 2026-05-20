package com.crsocial.witchhatatelier.spell.recognition;

import java.util.ArrayList;
import java.util.Collections;
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

    public synchronized List<Template> all() {
        return Collections.unmodifiableList(new ArrayList<>(templates));
    }

    public synchronized int size() {
        return templates.size();
    }
}
