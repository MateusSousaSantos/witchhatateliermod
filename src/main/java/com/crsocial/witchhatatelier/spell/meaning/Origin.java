package com.crsocial.witchhatatelier.spell.meaning;

/**
 * Where the spell emerges from. Resolved from the casting medium — placed
 * paper anchors to the block face, paper-item casts from the player's hand.
 * See {@code docs/magic_system/04_casting_medium.md}.
 */
public enum Origin {
    HAND,
    PAPER_SURFACE,
    INSCRIBED_BLOCK
}
