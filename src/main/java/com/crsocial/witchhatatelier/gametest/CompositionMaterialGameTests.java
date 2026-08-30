package com.crsocial.witchhatatelier.gametest;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.compiler.ElementType;
import com.crsocial.witchhatatelier.spell.composition.material.Element;
import com.crsocial.witchhatatelier.spell.composition.material.ElementRegistry;
import com.crsocial.witchhatatelier.spell.composition.material.ConvergenceRegistry;
import com.crsocial.witchhatatelier.spell.composition.material.Material;
import com.crsocial.witchhatatelier.spell.composition.material.MaterialModifier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/**
 * Verifies {@code ElementRegistry}/{@code ConvergenceRegistry}: every {@link
 * ElementType} carries a registered palette, and convergence is wired only
 * where {@code docs/sigils_and_signs.md} says it is. Purely additive — the
 * composition engine has no downstream consumer wired into real gameplay
 * yet. Run via {@code gradlew runGameTestServer} or the in-game {@code
 * /test} command.
 */
@GameTestHolder(WitchHatAtelierMod.MODID)
public class CompositionMaterialGameTests {

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void everyElementIsRegistered(GameTestHelper helper) {
        for (ElementType type : ElementType.values()) {
            Element element = ElementRegistry.get(type);
            if (element.type() != type) {
                helper.fail("ElementRegistry.get(" + type + ") returned a palette for " + element.type());
                return;
            }
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void blockyAndBlocklessElementsHaveExpectedBaseMaterials(GameTestHelper helper) {
        if (ElementRegistry.get(ElementType.FIRE).base().asBlock().filter(b -> b == Blocks.FIRE).isEmpty()) {
            helper.fail("Expected Fire's base material to be Blocks.FIRE");
            return;
        }
        if (ElementRegistry.get(ElementType.EARTH).base().asBlock().filter(b -> b == Blocks.STONE).isEmpty()) {
            helper.fail("Expected Earth's base material to be Blocks.STONE");
            return;
        }
        if (ElementRegistry.get(ElementType.LIGHT).base().asBlock().filter(b -> b == Blocks.LIGHT).isEmpty()) {
            helper.fail("Expected Light's base material to be Blocks.LIGHT");
            return;
        }
        // Air and Water are blockless by default - there is no sensible block to place.
        if (ElementRegistry.get(ElementType.AIR).base().asParticle().isEmpty()) {
            helper.fail("Expected Air's base material to be blockless (particle-only)");
            return;
        }
        if (ElementRegistry.get(ElementType.WATER).base().asParticle().isEmpty()) {
            helper.fail("Expected Water's base material to be blockless (particle-only)");
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void onlyFireHasAConvergenceModifierRegistered(GameTestHelper helper) {
        Optional<MaterialModifier> fireModifier = ConvergenceRegistry.find(ElementType.FIRE);
        if (fireModifier.isEmpty()) {
            helper.fail("Expected a convergence MaterialModifier registered for Fire");
            return;
        }

        Element fire = ElementRegistry.get(ElementType.FIRE);
        Material converged = fireModifier.get().apply(fire, fire.base());
        if (converged.asBlock().filter(b -> b == Blocks.MAGMA_BLOCK).isEmpty()) {
            helper.fail("Expected Fire's convergence to resolve to Blocks.MAGMA_BLOCK, got " + converged);
            return;
        }

        for (ElementType type : ElementType.values()) {
            if (type == ElementType.FIRE) continue;
            if (ConvergenceRegistry.find(type).isPresent()) {
                helper.fail(type + " unexpectedly has a convergence modifier registered "
                        + "(only Fire should today)");
                return;
            }
        }
        helper.succeed();
    }
}
