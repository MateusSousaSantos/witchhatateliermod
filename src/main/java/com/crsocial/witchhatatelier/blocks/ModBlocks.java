package com.crsocial.witchhatatelier.blocks;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.items.PaperType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.EnumMap;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WitchHatAtelierMod.MODID);

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    // ── Legacy generic blocks (kept for backward compatibility) ─────────────────

    public static final DeferredBlock<PlacedPaper> PLACED_PAPER = BLOCKS.register("placed_paper",
            () -> new PlacedPaper(paperProps()));

    public static final DeferredBlock<PlacedPaper> PLACED_SQUARE_PAPER = BLOCKS.register("placed_square_paper",
            () -> new PlacedPaper(paperProps()));

    public static final DeferredBlock<PlacedPaper> PLACED_ROUND_PAPER = BLOCKS.register("placed_round_paper",
            () -> new PlacedPaper(paperProps()));

    // ── Per-type placed paper blocks ─────────────────────────────────────────────

    public static final DeferredBlock<PlacedPaper> PLACED_SMALL_SQUARE_PAPER = BLOCKS.register("placed_small_square_paper",
            () -> new PlacedPaper(paperProps()));

    public static final DeferredBlock<PlacedPaper> PLACED_MEDIUM_SQUARE_PAPER = BLOCKS.register("placed_medium_square_paper",
            () -> new PlacedPaper(paperProps()));

    public static final DeferredBlock<PlacedPaper> PLACED_LARGE_SQUARE_PAPER = BLOCKS.register("placed_large_square_paper",
            () -> new PlacedLargePaper(paperProps()));

    public static final DeferredBlock<PlacedPaper> PLACED_SMALL_ROUND_PAPER = BLOCKS.register("placed_small_round_paper",
            () -> new PlacedPaper(paperProps()));

    public static final DeferredBlock<PlacedPaper> PLACED_MEDIUM_ROUND_PAPER = BLOCKS.register("placed_medium_round_paper",
            () -> new PlacedPaper(paperProps()));

    public static final DeferredBlock<PlacedPaper> PLACED_LARGE_ROUND_PAPER = BLOCKS.register("placed_large_round_paper",
            () -> new PlacedLargePaper(paperProps()));

    // ── PaperType → block lookup ─────────────────────────────────────────────────

    private static final EnumMap<PaperType, DeferredBlock<PlacedPaper>> PLACED_MAP = new EnumMap<>(PaperType.class);

    static {
        PLACED_MAP.put(PaperType.SMALL_SQUARE,  PLACED_SMALL_SQUARE_PAPER);
        PLACED_MAP.put(PaperType.MEDIUM_SQUARE, PLACED_MEDIUM_SQUARE_PAPER);
        PLACED_MAP.put(PaperType.LARGE_SQUARE,  PLACED_LARGE_SQUARE_PAPER);
        PLACED_MAP.put(PaperType.SMALL_ROUND,   PLACED_SMALL_ROUND_PAPER);
        PLACED_MAP.put(PaperType.MEDIUM_ROUND,  PLACED_MEDIUM_ROUND_PAPER);
        PLACED_MAP.put(PaperType.LARGE_ROUND,   PLACED_LARGE_ROUND_PAPER);
    }

    /** Returns the {@link PlacedPaper} block registered for the given {@link PaperType}. */
    public static DeferredBlock<PlacedPaper> placedFor(PaperType type) {
        return PLACED_MAP.get(type);
    }

    // ── Shared properties ────────────────────────────────────────────────────────

    private static BlockBehaviour.Properties paperProps() {
        return BlockBehaviour.Properties.of()
                .noOcclusion()
                .strength(0.2f)
                .sound(SoundType.WOOL);
    }
}
