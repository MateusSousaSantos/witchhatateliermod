package com.crsocial.witchhatatelier.spell.composition.material;

import com.crsocial.witchhatatelier.spell.compiler.ElementType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumMap;
import java.util.Map;

/**
 * Code-bootstrapped table of every element's material palette — the concrete
 * content half of the engine (see {@code docs/sigils_and_signs.md}). All five
 * {@link ElementType} values are registered exhaustively; {@link #get} is
 * non-optional because there is never a legitimately-missing entry.
 */
public final class ElementRegistry {

    private static final Map<ElementType, Element> ELEMENTS = new EnumMap<>(ElementType.class);

    static {
        bootstrap();
    }

    private ElementRegistry() {
    }

    private static void bootstrap() {
        register(new Element(ElementType.EARTH,
                new BlockMaterial(Blocks.STONE), new BlockMaterial(Blocks.STONE),
                0xFF7F7F7F, null, 0));
        // Air and Water are blockless by default - there is no sensible "air block"/
        // "water block" to place, so they manifest as particles only.
        register(new Element(ElementType.AIR,
                new ParticleMaterial(ParticleTypes.CLOUD), new ParticleMaterial(ParticleTypes.CLOUD),
                0xFFE8E8E8, null, 0));
        register(new Element(ElementType.WATER,
                new ParticleMaterial(ParticleTypes.SPLASH), new ParticleMaterial(ParticleTypes.SPLASH),
                0xFF3F76E4, null, 0));
        // Fire's converged form is magma - see ConvergenceRegistry, which resolves
        // straight to this block rather than deriving it from `base`.
        register(new Element(ElementType.FIRE,
                new BlockMaterial(Blocks.FIRE), new BlockMaterial(Blocks.MAGMA_BLOCK),
                0xFFFF7800, null, 0));
        register(new Element(ElementType.LIGHT,
                new BlockMaterial(Blocks.LIGHT), new BlockMaterial(Blocks.LIGHT),
                0xFFFFEE88, null, 15));
    }

    private static void register(Element element) {
        ELEMENTS.put(element.type(), element);
    }

    /** The registered palette for {@code type}. Every {@link ElementType} is always present. */
    public static Element get(ElementType type) {
        Element element = ELEMENTS.get(type);
        if (element == null) {
            throw new IllegalStateException("No Element registered for " + type + " - ElementRegistry.bootstrap() is incomplete");
        }
        return element;
    }
}
