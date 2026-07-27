package com.crsocial.witchhatatelier.blocks;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.items.PaperType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.EnumMap;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WitchHatAtelierMod.MODID);

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        modEventBus.addListener(ModBlocks::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FireBlock fire = (FireBlock) Blocks.FIRE;
            fire.setFlammable(SILVER_WOOD_PLANKS.get(), 5, 20);
            fire.setFlammable(SILVER_WOOD_STAIRS.get(), 5, 20);
            fire.setFlammable(SILVER_WOOD_SLAB.get(), 5, 20);
            fire.setFlammable(SILVER_WOOD_FENCE.get(), 5, 20);
            fire.setFlammable(SILVER_WOOD_FENCE_GATE.get(), 5, 20);
            fire.setFlammable(SILVER_WOOD_LOG.get(), 5, 5);
            fire.setFlammable(SILVER_WOOD_WOOD.get(), 5, 5);
            fire.setFlammable(STRIPPED_SILVER_LOG.get(), 5, 5);
            fire.setFlammable(STRIPPED_SILVER_WOOD.get(), 5, 5);
            fire.setFlammable(SILVER_WOOD_LEAVES.get(), 30, 60);
        });
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

    public static final DeferredBlock<PlacedPaper> PLACED_SMALL_ROUND_PAPER = BLOCKS.register("placed_small_round_paper",
            () -> new PlacedPaper(paperProps()));

    public static final DeferredBlock<PlacedPaper> PLACED_MEDIUM_ROUND_PAPER = BLOCKS.register("placed_medium_round_paper",
            () -> new PlacedPaper(paperProps()));

    // ── Canvas pressure plate ────────────────────────────────────────────────────

    public static final DeferredBlock<CanvasPressurePlate> CANVAS_PLATE = BLOCKS.register("canvas_plate",
            () -> new CanvasPressurePlate(BlockSetType.OAK, plateProps()));

    // ── Silver wood ───────────────────────────────────────────────────────────────

    public static final DeferredBlock<RotatedPillarBlock> SILVER_WOOD_LOG = BLOCKS.register("silver_wood_log",
            () -> new RotatedPillarBlock(logProps()));

    public static final DeferredBlock<RotatedPillarBlock> SILVER_WOOD_WOOD = BLOCKS.register("silver_wood_wood",
            () -> new RotatedPillarBlock(logProps()));

    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_SILVER_LOG = BLOCKS.register("stripped_silver_log",
            () -> new RotatedPillarBlock(logProps()));

    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_SILVER_WOOD = BLOCKS.register("stripped_silver_wood",
            () -> new RotatedPillarBlock(logProps()));

    public static final DeferredBlock<Block> SILVER_WOOD_PLANKS = BLOCKS.register("silver_wood_planks",
            () -> new Block(planksProps()));

    public static final DeferredBlock<StairBlock> SILVER_WOOD_STAIRS = BLOCKS.register("silver_wood_stairs",
            () -> new StairBlock(SILVER_WOOD_PLANKS.get().defaultBlockState(), plankDerivedProps()));

    public static final DeferredBlock<SlabBlock> SILVER_WOOD_SLAB = BLOCKS.register("silver_wood_slab",
            () -> new SlabBlock(plankDerivedProps()));

    public static final DeferredBlock<FenceBlock> SILVER_WOOD_FENCE = BLOCKS.register("silver_wood_fence",
            () -> new FenceBlock(plankDerivedProps().forceSolidOn()));

    public static final DeferredBlock<FenceGateBlock> SILVER_WOOD_FENCE_GATE = BLOCKS.register("silver_wood_fence_gate",
            () -> new FenceGateBlock(WoodType.OAK, plankDerivedProps().forceSolidOn()));

    public static final DeferredBlock<LeavesBlock> SILVER_WOOD_LEAVES = BLOCKS.register("silver_wood_leaves",
            () -> new LeavesBlock(leavesProps()));

    public static final DeferredBlock<VineBlock> SILVER_WOOD_VINES = BLOCKS.register("silver_wood_vines",
            () -> new VineBlock(vineProps()));

    // ── PaperType → block lookup ─────────────────────────────────────────────────

    private static final EnumMap<PaperType, DeferredBlock<PlacedPaper>> PLACED_MAP = new EnumMap<>(PaperType.class);

    static {
        PLACED_MAP.put(PaperType.SMALL_SQUARE,  PLACED_SMALL_SQUARE_PAPER);
        PLACED_MAP.put(PaperType.MEDIUM_SQUARE, PLACED_MEDIUM_SQUARE_PAPER);
        PLACED_MAP.put(PaperType.SMALL_ROUND,   PLACED_SMALL_ROUND_PAPER);
        PLACED_MAP.put(PaperType.MEDIUM_ROUND,  PLACED_MEDIUM_ROUND_PAPER);
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

    private static BlockBehaviour.Properties plateProps() {
        // Note: BasePressurePlateBlock forces the sound from the BlockSetType, so it is not set here.
        return BlockBehaviour.Properties.of()
                .forceSolidOn()
                .noCollission()
                .strength(0.5f)
                .pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties logProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .instrument(NoteBlockInstrument.BASS)
                .strength(2.0f)
                .sound(SoundType.WOOD)
                .ignitedByLava();
    }

    private static BlockBehaviour.Properties planksProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .instrument(NoteBlockInstrument.BASS)
                .strength(2.0f, 3.0f)
                .sound(SoundType.WOOD)
                .ignitedByLava();
    }

    private static BlockBehaviour.Properties plankDerivedProps() {
        return BlockBehaviour.Properties.ofFullCopy(SILVER_WOOD_PLANKS.get());
    }

    private static BlockBehaviour.Properties leavesProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT)
                .strength(0.2f)
                .randomTicks()
                .sound(SoundType.GRASS)
                .noOcclusion()
                .ignitedByLava()
                .pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties vineProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT)
                .replaceable()
                .noCollission()
                .randomTicks()
                .strength(0.2f)
                .sound(SoundType.VINE)
                .ignitedByLava()
                .pushReaction(PushReaction.DESTROY);
    }
}
