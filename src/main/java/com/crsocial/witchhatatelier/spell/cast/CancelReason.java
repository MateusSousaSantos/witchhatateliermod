package com.crsocial.witchhatatelier.spell.cast;

/** Why a channeled cast ended — drives logging and how the paper is consumed. */
public enum CancelReason {
    /** Spell's fuel supply ran out. */
    FUEL_EXHAUSTED,
    /** Player swapped the held paper out of the casting hand. */
    ITEM_CHANGED,
    /** Player logged out / could no longer be resolved. */
    LOGOUT,
    /** Player died or was removed. */
    DEATH,
    /** Superseded by a new cast or an internal error. */
    ERROR
}
