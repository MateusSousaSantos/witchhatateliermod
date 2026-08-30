package com.crsocial.witchhatatelier.gametest;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.compiler.ElementType;
import com.crsocial.witchhatatelier.spell.compiler.ExecutionMode;
import com.crsocial.witchhatatelier.spell.composition.CastContext;
import com.crsocial.witchhatatelier.spell.composition.ExecutableSpell;
import com.crsocial.witchhatatelier.spell.composition.Trigger;
import com.crsocial.witchhatatelier.spell.composition.effect.CollectionEffect;
import com.crsocial.witchhatatelier.spell.composition.effect.CrushEffect;
import com.crsocial.witchhatatelier.spell.composition.effect.ExtinguishEffect;
import com.crsocial.witchhatatelier.spell.composition.effect.LevitationEffect;
import com.crsocial.witchhatatelier.spell.composition.effect.PullEffect;
import com.crsocial.witchhatatelier.spell.composition.form.ColumnForm;
import com.crsocial.witchhatatelier.spell.composition.manifest.BlocksManifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.Manifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.NoneManifestation;
import com.crsocial.witchhatatelier.spell.composition.material.Element;
import com.crsocial.witchhatatelier.spell.composition.material.ElementRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Verifies each default {@code Effect} implementation modifies/carries
 * correctly in isolation, against a hand-built {@link CastContext} (bypassing
 * {@code CompositionEngine} and the recognizer). Run via {@code gradlew
 * runGameTestServer} or the in-game {@code /test} command.
 */
@GameTestHolder(WitchHatAtelierMod.MODID)
public class CompositionEffectGameTests {

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void levitationLiftsNearbyEntities(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 2, 1);
        CastContext ctx = CompositionTestSupport.ctx(helper, rel, CompositionTestSupport.magnitude(1f, 1f, 1f, 1f, 1));
        Pig pig = helper.spawn(EntityType.PIG, rel);
        pig.setDeltaMovement(Vec3.ZERO);

        Element earth = ElementRegistry.get(ElementType.EARTH);
        new LevitationEffect().carry(earth.base(), ctx);

        if (pig.getDeltaMovement().y <= 0) {
            helper.fail("Expected Levitation to give the nearby pig upward velocity, got " + pig.getDeltaMovement());
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void crushBreaksEveryBlockAColumnPlaced(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        CastContext ctx = CompositionTestSupport.ctx(helper, rel, CompositionTestSupport.magnitude(1.5f, 1f, 1f, 1f, 1));
        Element earth = ElementRegistry.get(ElementType.EARTH);

        Manifestation column = new ColumnForm().manifest(earth.base(), ctx);
        if (!(column instanceof BlocksManifestation(var positions, var state))) {
            helper.fail("Setup failure: ColumnForm didn't produce a BlocksManifestation");
            return;
        }

        Manifestation result = new CrushEffect().modify(column, earth.base(), ctx);
        if (!(result instanceof NoneManifestation)) {
            helper.fail("Expected Crush to reduce a BlocksManifestation to NoneManifestation, got " + result);
            return;
        }
        for (BlockPos pos : positions) {
            if (!helper.getLevel().getBlockState(pos).isAir()) {
                helper.fail("Crush left a block standing at " + pos);
                return;
            }
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void pullDrawsNearbyEntitiesTowardOrigin(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 2, 1);
        BlockPos pigPos = new BlockPos(4, 2, 1);
        CastContext ctx = CompositionTestSupport.ctx(helper, origin, CompositionTestSupport.magnitude(1f, 2f, 1f, 1f, 1));
        Pig pig = helper.spawn(EntityType.PIG, pigPos);
        pig.setDeltaMovement(Vec3.ZERO);

        Element earth = ElementRegistry.get(ElementType.EARTH);
        new PullEffect().carry(earth.base(), ctx);

        if (pig.getDeltaMovement().x >= 0) {
            helper.fail("Expected Pull to move the pig back toward the origin (negative X), got " + pig.getDeltaMovement());
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void collectionBonusScalesWithNearbyMatchingBlocksOnly(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        for (int dx = -1; dx <= 1; dx++) {
            helper.setBlock(origin.offset(dx, 0, 0), Blocks.STONE);
        }
        Vec3 originVec = Vec3.atCenterOf(helper.absolutePos(origin));

        Element earth = ElementRegistry.get(ElementType.EARTH);
        float stoneBonus = CollectionEffect.freeBonus(helper.getLevel(), originVec, earth.base());
        if (stoneBonus <= 0f) {
            helper.fail("Expected a positive free bonus with matching blocks nearby, got " + stoneBonus);
            return;
        }

        Element air = ElementRegistry.get(ElementType.AIR);
        float airBonus = CollectionEffect.freeBonus(helper.getLevel(), originVec, air.base());
        if (airBonus != 0f) {
            helper.fail("Expected zero bonus for a blockless material, got " + airBonus);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void extinguishTriggerDetectsAndRemovesNearbyFire(GameTestHelper helper) {
        BlockPos origin = new BlockPos(1, 1, 1);
        helper.setBlock(origin.offset(1, 0, 0), Blocks.FIRE);
        CastContext ctx = CompositionTestSupport.ctx(helper, origin, CompositionTestSupport.magnitude(1f, 1f, 1f, 1f, 1));

        ExtinguishEffect effect = new ExtinguishEffect();
        Optional<Trigger> trigger = effect.trigger();
        if (trigger.isEmpty()) {
            helper.fail("Expected ExtinguishEffect to declare a Trigger");
            return;
        }

        Element earth = ElementRegistry.get(ElementType.EARTH);
        ExecutableSpell spell = new ExecutableSpell(ElementType.FIRE, earth.base(), List.of(), ctx.magnitude(),
                ctx.origin(), ctx.direction(), ExecutionMode.REACTIVE, ExecutableSpell.Cost.NONE, trigger);

        if (!trigger.get().test(helper.getLevel(), spell)) {
            helper.fail("Expected the Extinguish trigger to detect the nearby fire");
            return;
        }

        effect.onTrigger(helper.getLevel(), ctx.origin());
        if (helper.getLevel().getBlockState(helper.absolutePos(origin.offset(1, 0, 0))).is(Blocks.FIRE)) {
            helper.fail("Expected onTrigger to remove the fire block");
            return;
        }
        helper.succeed();
    }
}
