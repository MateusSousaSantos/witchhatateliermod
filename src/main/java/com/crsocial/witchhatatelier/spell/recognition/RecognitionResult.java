package com.crsocial.witchhatatelier.spell.recognition;

/**
 * Output of {@link PDollarPlusRecognizer#match}. Combines the matched
 * canonical spell name, the normalized confidence in {@code [0.0, 1.0]},
 * and the candidate sigil's indicative angle (radians).
 */
public record RecognitionResult(String spellName, float confidenceScore, float indicativeAngle) {

    public static final String UNKNOWN = "unknown";

    public static RecognitionResult unknown(float bestScore, float indicativeAngle) {
        return new RecognitionResult(UNKNOWN, bestScore, indicativeAngle);
    }
}
