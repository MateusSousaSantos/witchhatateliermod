package com.crsocial.witchhatatelier.gametest;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.spell.trigger.TriggerEvaluator;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Geometry gametests for the activation-ring trigger: a closed circle around
 * content must trigger, and an <b>unfinished</b> circle must not — but must be
 * detected as a ring-in-progress so the save pipeline keeps it out of sigil
 * clustering. Coordinates use the normalized unit square, like the server pass.
 */
@GameTestHolder(WitchHatAtelierMod.MODID)
public class TriggerGameTests {

    /** Circular arc around (0.5, 0.5), radius 0.3, from 0° to {@code sweepDeg} in 5° steps. */
    private static List<Vector2f> arc(float sweepDeg) {
        List<Vector2f> pts = new ArrayList<>();
        for (float a = 0f; a <= sweepDeg; a += 5f) {
            double rad = Math.toRadians(a);
            pts.add(new Vector2f(0.5f + 0.3f * (float) Math.cos(rad),
                                 0.5f + 0.3f * (float) Math.sin(rad)));
        }
        return pts;
    }

    /** A short horizontal stroke near the centre — the "sigil" the ring encloses. */
    private static List<Vector2f> innerStroke() {
        return List.of(new Vector2f(0.45f, 0.5f), new Vector2f(0.50f, 0.5f),
                       new Vector2f(0.55f, 0.5f));
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void unfinishedRingDetectedButDoesNotTrigger(GameTestHelper helper) {
        List<List<Vector2f>> strokes = List.of(arc(340f), innerStroke());

        if (TriggerEvaluator.evaluate(strokes, 0.01f, 1f, 1f).isPresent()) {
            helper.fail("A 340° arc must not count as a closed activation ring");
            return;
        }
        Optional<List<Integer>> inProgress =
                TriggerEvaluator.findRingInProgress(strokes, 0.01f, 1f, 1f);
        if (inProgress.isEmpty() || !inProgress.get().equals(List.of(0))) {
            helper.fail("A 340° arc enclosing content must be detected as a ring-in-progress, got "
                    + inProgress);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public void closedRingTriggers(GameTestHelper helper) {
        List<List<Vector2f>> strokes = List.of(arc(360f), innerStroke());

        Optional<TriggerEvaluator.TriggerResult> trig =
                TriggerEvaluator.evaluate(strokes, 0.01f, 1f, 1f);
        if (trig.isEmpty()) {
            helper.fail("A full circle around content must count as a closed activation ring");
            return;
        }
        if (!trig.get().ringStrokeIds().equals(List.of(0))
                || !trig.get().enclosedStrokeIds().equals(List.of(1))) {
            helper.fail("Unexpected trigger split: ring=" + trig.get().ringStrokeIds()
                    + " enclosed=" + trig.get().enclosedStrokeIds());
            return;
        }
        helper.succeed();
    }
}
