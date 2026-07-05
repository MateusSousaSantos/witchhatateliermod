package com.crsocial.witchhatatelier.gametest;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.blocks.ModBlocks;
import com.crsocial.witchhatatelier.blocks.PlacedPaperBlockEntity;
import com.crsocial.witchhatatelier.items.PaperType;
import com.crsocial.witchhatatelier.spell.compiler.SigilType;
import com.crsocial.witchhatatelier.spell.compiler.SignType;
import com.crsocial.witchhatatelier.spell.feedback.InscriptionSummary;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Gametests for the inscription-summary feedback path: the NBT round-trip the
 * item stamp / tooltip relies on, and the block entity's sync-tag round-trip
 * the canvas status header reads through. Run via {@code gradlew runGameTestServer}
 * or the in-game {@code /test} command.
 */
@GameTestHolder(WitchHatAtelierMod.MODID)
public class InscriptionGameTests {

    private static InscriptionSummary sample() {
        return new InscriptionSummary(
                InscriptionSummary.InscriptionState.READY,
                SigilType.FIRE,
                List.of(new InscriptionSummary.SignEntry(SignType.COLUMN, 2),
                        new InscriptionSummary.SignEntry(SignType.LEVITATION, 1)),
                0.87f,
                new int[]{3, 5});
    }

    private static void assertSummariesEqual(GameTestHelper helper,
                                             InscriptionSummary expected, InscriptionSummary actual) {
        if (actual.state() != expected.state()
                || actual.element() != expected.element()
                || !actual.signs().equals(expected.signs())
                || actual.quality() != expected.quality()
                || !Arrays.equals(actual.unrecognizedStrokeIds(), expected.unrecognizedStrokeIds())) {
            helper.fail("Inscription summary changed across round-trip: expected "
                    + expected + " but got " + actual);
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void inscriptionSummaryNbtRoundTrip(GameTestHelper helper) {
        InscriptionSummary original = sample();

        CompoundTag root = new CompoundTag();
        root.put(InscriptionSummary.NBT_KEY, original.toNbt());
        Optional<InscriptionSummary> read = InscriptionSummary.fromNbt(root);
        if (read.isEmpty()) {
            helper.fail("fromNbt returned empty for a freshly stamped root tag");
            return;
        }
        assertSummariesEqual(helper, original, read.get());

        // Forward-safety: an unknown state (e.g. written by a newer version) reads as absent.
        CompoundTag unknown = original.toNbt();
        unknown.putString("state", "FROM_THE_FUTURE");
        if (InscriptionSummary.fromNbtCompound(unknown).isPresent()) {
            helper.fail("An unknown inscription state should read as empty, not throw or guess");
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void placedPaperSyncTagCarriesInscription(GameTestHelper helper) {
        InscriptionSummary original = sample();
        BlockState state = ModBlocks.placedFor(PaperType.MEDIUM_SQUARE).get().defaultBlockState();

        PlacedPaperBlockEntity source = new PlacedPaperBlockEntity(BlockPos.ZERO, state);
        source.setInscription(original);

        // getUpdateTag is what reaches the client; loadAdditional is how both sides read it.
        CompoundTag syncTag = source.getUpdateTag(helper.getLevel().registryAccess());
        PlacedPaperBlockEntity loaded = new PlacedPaperBlockEntity(BlockPos.ZERO, state);
        loaded.loadAdditional(syncTag, helper.getLevel().registryAccess());

        InscriptionSummary read = loaded.getInscription();
        if (read == null) {
            helper.fail("PlacedPaperBlockEntity sync tag dropped the inscription summary");
            return;
        }
        assertSummariesEqual(helper, original, read);
        helper.succeed();
    }
}
