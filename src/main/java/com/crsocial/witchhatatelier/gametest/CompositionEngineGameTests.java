package com.crsocial.witchhatatelier.gametest;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.compiler.EffectNode;
import com.crsocial.witchhatatelier.spell.compiler.EffectType;
import com.crsocial.witchhatatelier.spell.compiler.ElementType;
import com.crsocial.witchhatatelier.spell.compiler.ExecutionMode;
import com.crsocial.witchhatatelier.spell.compiler.FormNode;
import com.crsocial.witchhatatelier.spell.compiler.FormType;
import com.crsocial.witchhatatelier.spell.compiler.SpellGraph;
import com.crsocial.witchhatatelier.spell.composition.CastContext;
import com.crsocial.witchhatatelier.spell.composition.CompositionEngine;
import com.crsocial.witchhatatelier.spell.composition.ExecutableSpell;
import com.crsocial.witchhatatelier.spell.composition.manifest.BlocksManifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.Manifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.NoneManifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.ParticlesManifestation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector2f;

import java.util.List;
import java.util.Optional;

/**
 * End-to-end coverage of {@link CompositionEngine#compose} — the resolution
 * algorithm in {@code docs/new_spell_engine.md} §6, exercised against
 * hand-built {@link SpellGraph}s (bypassing the recognizer). Run via {@code
 * gradlew runGameTestServer} or the in-game {@code /test} command.
 */
@GameTestHolder(WitchHatAtelierMod.MODID)
public class CompositionEngineGameTests {

    private static FormNode form(FormType type) {
        return new FormNode(type, new Vector2f(0f, 0f), 0f, 0.9f);
    }

    private static EffectNode effect(EffectType type) {
        return new EffectNode(type, new Vector2f(0f, 0f), 0f, 0.9f);
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void bareElementDefaultManifestsWithNoFormsOrEffects(GameTestHelper helper) {
        SpellGraph graph = CompositionTestSupport.graph(ElementType.EARTH, 1, false, List.of(), List.of());
        CastContext ctx = CompositionTestSupport.ctx(helper, new BlockPos(1, 1, 1), CompositionTestSupport.magnitude(1f, 1f, 1f, 1f, 1));

        Optional<ExecutableSpell> result = CompositionEngine.compose(graph, ctx);
        if (result.isEmpty()) {
            helper.fail("Expected a lone element to always manifest (bare element default)");
            return;
        }
        ExecutableSpell spell = result.get();
        if (spell.manifestations().size() != 1 || !(spell.manifestations().getFirst() instanceof BlocksManifestation)) {
            helper.fail("Expected exactly one BlocksManifestation from the bare element default, got " + spell.manifestations());
            return;
        }
        if (spell.mode() != ExecutionMode.CONTINUOUS) {
            helper.fail("Expected a spell with no effects to default to CONTINUOUS, got " + spell.mode());
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void formOnlyManifestsViaColumn(GameTestHelper helper) {
        SpellGraph graph = CompositionTestSupport.graph(ElementType.EARTH, 1, false, List.of(form(FormType.COLUMN)), List.of());
        CastContext ctx = CompositionTestSupport.ctx(helper, new BlockPos(1, 1, 1), CompositionTestSupport.magnitude(1f, 1f, 1f, 1f, 1));

        Optional<ExecutableSpell> result = CompositionEngine.compose(graph, ctx);
        if (result.isEmpty() || !(result.get().manifestations().getFirst() instanceof BlocksManifestation(var positions, var state))) {
            helper.fail("Expected Column to manifest a BlocksManifestation, got " + result);
            return;
        }
        if (positions.size() < 2) {
            helper.fail("Expected the column to place more than one block, got " + positions.size());
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void formPlusCrushUnplacesTheColumn(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        SpellGraph graph = CompositionTestSupport.graph(ElementType.EARTH, 1, false,
                List.of(form(FormType.COLUMN)), List.of(effect(EffectType.CRUSH)));
        CastContext ctx = CompositionTestSupport.ctx(helper, rel, CompositionTestSupport.magnitude(1f, 1f, 1f, 1f, 1));

        Optional<ExecutableSpell> result = CompositionEngine.compose(graph, ctx);
        if (result.isEmpty() || !(result.get().manifestations().getFirst() instanceof NoneManifestation)) {
            helper.fail("Expected Column+Crush to resolve to NoneManifestation, got " + result);
            return;
        }
        if (helper.getLevel().getBlockState(helper.absolutePos(rel)).getBlock() == Blocks.STONE) {
            helper.fail("Expected Crush to have un-placed the column's origin block");
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void carryWithNoFormUsesTheEffectsCarry(GameTestHelper helper) {
        SpellGraph graph = CompositionTestSupport.graph(ElementType.EARTH, 1, false, List.of(), List.of(effect(EffectType.LEVITATION)));
        CastContext ctx = CompositionTestSupport.ctx(helper, new BlockPos(1, 2, 1), CompositionTestSupport.magnitude(1f, 1f, 1f, 1f, 1));

        Optional<ExecutableSpell> result = CompositionEngine.compose(graph, ctx);
        if (result.isEmpty() || !(result.get().manifestations().getFirst() instanceof ParticlesManifestation)) {
            helper.fail("Expected a carrier-capable effect with no form to carry its own manifestation, got " + result);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void fireColumnOverrideScorchesNearbyEntitiesWhereEarthDoesNot(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        Vec3 pigRel = Vec3.atBottomCenterOf(rel).add(0.6, 0, 0);

        SpellGraph fireGraph = CompositionTestSupport.graph(ElementType.FIRE, 1, false, List.of(form(FormType.COLUMN)), List.of());
        CastContext fireCtx = CompositionTestSupport.ctx(helper, rel, CompositionTestSupport.magnitude(1f, 1f, 1f, 0.9f, 1));
        Pig scorched = helper.spawn(EntityType.PIG, pigRel);
        CompositionEngine.compose(fireGraph, fireCtx);
        if (!scorched.isOnFire()) {
            helper.fail("Expected the Fire+Column override to ignite a nearby entity");
            return;
        }

        BlockPos rel2 = new BlockPos(1, 1, 4);
        Vec3 pigRel2 = Vec3.atBottomCenterOf(rel2).add(0.6, 0, 0);
        SpellGraph earthGraph = CompositionTestSupport.graph(ElementType.EARTH, 1, false, List.of(form(FormType.COLUMN)), List.of());
        CastContext earthCtx = CompositionTestSupport.ctx(helper, rel2, CompositionTestSupport.magnitude(1f, 1f, 1f, 0.9f, 1));
        Pig untouched = helper.spawn(EntityType.PIG, pigRel2);
        CompositionEngine.compose(earthGraph, earthCtx);
        if (untouched.isOnFire()) {
            helper.fail("The generic Column default should not ignite anything, only the Fire override");
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void reactiveExtinguishProducesATriggerAndPerEventCostOnly(GameTestHelper helper) {
        SpellGraph graph = CompositionTestSupport.graph(ElementType.FIRE, 1, false, List.of(), List.of(effect(EffectType.EXTINGUISH)));
        CastContext ctx = CompositionTestSupport.ctx(helper, new BlockPos(1, 1, 1), CompositionTestSupport.magnitude(1f, 1f, 1f, 1f, 1));

        Optional<ExecutableSpell> result = CompositionEngine.compose(graph, ctx);
        if (result.isEmpty()) {
            helper.fail("Expected a reactive Extinguish-only spell to still compose");
            return;
        }
        ExecutableSpell spell = result.get();
        if (spell.mode() != ExecutionMode.REACTIVE || spell.trigger().isEmpty()) {
            helper.fail("Expected EXTINGUISH to tag the spell REACTIVE with a Trigger attached, got " + spell.mode() + "/" + spell.trigger());
            return;
        }
        if (spell.cost().perEvent() <= 0f || spell.cost().perTick() != 0f) {
            helper.fail("Expected a reactive spell to cost per-event only, got " + spell.cost());
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void collectionBonusIsExcludedFromCost(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        SpellGraph graph = CompositionTestSupport.graph(ElementType.EARTH, 1, false,
                List.of(form(FormType.COLUMN)), List.of(effect(EffectType.COLLECTION)));
        CastContext ctx = CompositionTestSupport.ctx(helper, rel, CompositionTestSupport.magnitude(1f, 1f, 1f, 1f, 1));

        Optional<ExecutableSpell> withoutNearbyStone = CompositionEngine.compose(graph, ctx);
        if (withoutNearbyStone.isEmpty()) {
            helper.fail("Setup failure: Column+Collection didn't compose");
            return;
        }
        float costWithout = withoutNearbyStone.get().cost().perTick();
        float powerWithout = withoutNearbyStone.get().magnitude().power();

        // Surround a second, identical cast with matching blocks so Collection's free bonus kicks in.
        BlockPos rel2 = new BlockPos(1, 1, 6);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                helper.setBlock(rel2.offset(dx, 0, dz), Blocks.STONE);
            }
        }
        CastContext ctx2 = CompositionTestSupport.ctx(helper, rel2, CompositionTestSupport.magnitude(1f, 1f, 1f, 1f, 1));
        Optional<ExecutableSpell> withNearbyStone = CompositionEngine.compose(graph, ctx2);
        if (withNearbyStone.isEmpty()) {
            helper.fail("Setup failure: second Column+Collection didn't compose");
            return;
        }
        float costWith = withNearbyStone.get().cost().perTick();
        float powerWith = withNearbyStone.get().magnitude().power();

        if (powerWith <= powerWithout) {
            helper.fail("Expected Collection's free bonus to raise magnitude.power() with matching blocks nearby: "
                    + powerWithout + " -> " + powerWith);
            return;
        }
        if (Math.abs(costWith - costWithout) > 1.0e-4f) {
            helper.fail("Expected cost to be unaffected by Collection's free bonus: " + costWithout + " vs " + costWith);
            return;
        }
        helper.succeed();
    }
}
