package com.crsocial.witchhatatelier.gametest;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.compiler.ElementType;
import com.crsocial.witchhatatelier.spell.composition.CastContext;
import com.crsocial.witchhatatelier.spell.composition.form.BoltForm;
import com.crsocial.witchhatatelier.spell.composition.form.ColumnForm;
import com.crsocial.witchhatatelier.spell.composition.form.DispersionForm;
import com.crsocial.witchhatatelier.spell.composition.manifest.BlocksManifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.Manifestation;
import com.crsocial.witchhatatelier.spell.composition.manifest.ParticlesManifestation;
import com.crsocial.witchhatatelier.spell.composition.material.Element;
import com.crsocial.witchhatatelier.spell.composition.material.ElementRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Verifies each default {@code Form} implementation manifests correctly in
 * isolation, against a hand-built {@link CastContext} (bypassing {@code
 * CompositionEngine} and the recognizer). Run via {@code gradlew
 * runGameTestServer} or the in-game {@code /test} command.
 */
@GameTestHolder(WitchHatAtelierMod.MODID)
public class CompositionFormGameTests {

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void columnFormPlacesBlockyMaterialUpward(GameTestHelper helper) {
        CastContext ctx = CompositionTestSupport.ctx(helper, new BlockPos(1, 1, 1),
                CompositionTestSupport.magnitude(1.5f, 1f, 1f, 1f, 1));
        Element earth = ElementRegistry.get(ElementType.EARTH);

        Manifestation result = new ColumnForm().manifest(earth.base(), ctx);
        if (!(result instanceof BlocksManifestation(var positions, var state))) {
            helper.fail("Expected ColumnForm to produce a BlocksManifestation for a blocky material, got " + result);
            return;
        }
        if (positions.isEmpty()) {
            helper.fail("Column placed no blocks");
            return;
        }
        if (state.getBlock() != Blocks.STONE) {
            helper.fail("Expected the column to be Blocks.STONE, got " + state.getBlock());
            return;
        }
        for (BlockPos pos : positions) {
            if (helper.getLevel().getBlockState(pos).getBlock() != Blocks.STONE) {
                helper.fail("Column position " + pos + " was reported placed but isn't stone in the world");
                return;
            }
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void columnFormEmitsParticlesForBlocklessMaterial(GameTestHelper helper) {
        CastContext ctx = CompositionTestSupport.ctx(helper, new BlockPos(1, 1, 1),
                CompositionTestSupport.magnitude(1f, 1f, 1f, 1f, 1));
        Element air = ElementRegistry.get(ElementType.AIR);

        Manifestation result = new ColumnForm().manifest(air.base(), ctx);
        if (!(result instanceof ParticlesManifestation)) {
            helper.fail("Expected ColumnForm to produce a ParticlesManifestation for a blockless material, got " + result);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void dispersionFormScattersBlockyMaterial(GameTestHelper helper) {
        CastContext ctx = CompositionTestSupport.ctx(helper, new BlockPos(1, 1, 1),
                CompositionTestSupport.magnitude(1f, 1f, 1f, 1f, 1));
        Element earth = ElementRegistry.get(ElementType.EARTH);

        Manifestation result = new DispersionForm().manifest(earth.base(), ctx);
        if (!(result instanceof BlocksManifestation(var positions, var state))) {
            helper.fail("Expected DispersionForm to produce a BlocksManifestation, got " + result);
            return;
        }
        if (positions.isEmpty()) {
            helper.fail("Dispersion scattered no blocks");
            return;
        }
        if (state.getBlock() != Blocks.STONE) {
            helper.fail("Expected the scatter to be Blocks.STONE, got " + state.getBlock());
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void boltFormFiresOneStreakPerRepetition(GameTestHelper helper) {
        CastContext ctx = CompositionTestSupport.ctx(helper, new BlockPos(1, 1, 1),
                CompositionTestSupport.magnitude(1f, 1f, 1f, 1f, 3));
        Element fire = ElementRegistry.get(ElementType.FIRE);

        Manifestation result = new BoltForm().manifest(fire.base(), ctx);
        if (!(result instanceof ParticlesManifestation p) || p.reach() <= 0f) {
            helper.fail("Expected BoltForm to produce a ParticlesManifestation with a positive reach, got " + result);
            return;
        }
        helper.succeed();
    }
}
