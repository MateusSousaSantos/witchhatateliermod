package com.crsocial.witchhatatelier.blocks;

import com.crsocial.witchhatatelier.WitchHatAtelierMod;
import com.crsocial.witchhatatelier.items.PaperType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.EnumMap;

public class ModBlocks {

    // Silver wood's own BlockSetType/WoodType — both are open, registry-like records
    // (NeoForge ATs widen their register() methods to public). Must be static fields here
    // so they class-load well before any renderer/model bakes off WoodType.values().
    public static final BlockSetType SILVER_WOOD_SET = BlockSetType.register(new BlockSetType("silver_wood"));
    public static final WoodType SILVER_WOOD_TYPE = WoodType.register(new WoodType("silver_wood", SILVER_WOOD_SET));

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WitchHatAtelierMod.MODID);

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        modEventBus.addListener(ModBlocks::commonSetup);
        modEventBus.addListener(ModBlocks::onAddBlockEntityBlocks);
    }

    /**
     * Registers our sign blocks as valid holders of vanilla's shared {@code SIGN}
     * block-entity type. Without this, placing either sign block would fail to find
     * a valid block entity for it.
     */
    private static void onAddBlockEntityBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityType.SIGN, SILVER_WOOD_SIGN.get(), SILVER_WOOD_WALL_SIGN.get());
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
            fire.setFlammable(BUDDING_SILVER_WOOD.get(), 5, 5);
            fire.setFlammable(SILVER_WOOD_PLATE.get(), 5, 20);
            fire.setFlammable(SILVER_WOOD_BUTTON.get(), 5, 20);
            fire.setFlammable(SILVER_WOOD_SIGN.get(), 5, 20);
            fire.setFlammable(SILVER_WOOD_WALL_SIGN.get(), 5, 20);
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
            () -> new FenceGateBlock(SILVER_WOOD_TYPE, plankDerivedProps().forceSolidOn()));

    public static final DeferredBlock<LeavesBlock> SILVER_WOOD_LEAVES = BLOCKS.register("silver_wood_leaves",
            () -> new LeavesBlock(leavesProps()));

    public static final DeferredBlock<VineBlock> SILVER_WOOD_VINES = BLOCKS.register("silver_wood_vines",
            () -> new VineBlock(vineProps()));

    public static final DeferredBlock<PressurePlateBlock> SILVER_WOOD_PLATE = BLOCKS.register("silver_wood_plate",
            () -> new PressurePlateBlock(SILVER_WOOD_SET, plateProps()));

    public static final DeferredBlock<ButtonBlock> SILVER_WOOD_BUTTON = BLOCKS.register("silver_wood_button",
            () -> new ButtonBlock(SILVER_WOOD_SET, 30, buttonProps()));

    public static final DeferredBlock<StandingSignBlock> SILVER_WOOD_SIGN = BLOCKS.register("silver_wood_sign",
            () -> new StandingSignBlock(SILVER_WOOD_TYPE, signProps()));

    public static final DeferredBlock<WallSignBlock> SILVER_WOOD_WALL_SIGN = BLOCKS.register("silver_wood_wall_sign",
            () -> new WallSignBlock(SILVER_WOOD_TYPE, signProps().dropsLike(SILVER_WOOD_SIGN.get())));

    // ── Budding silver wood (amethyst-style trunk that grows branches) ─────────────

    public static final DeferredBlock<BuddingSilverWoodBlock> BUDDING_SILVER_WOOD = BLOCKS.register("budding_silver_wood",
            () -> new BuddingSilverWoodBlock(buddingProps()));

    public static final DeferredBlock<SilverTreeBranchBlock> SILVER_TREE_BRANCH_SMALL = BLOCKS.register("silver_tree_branch_small",
            () -> new SilverTreeBranchBlock(4, 2, branchProps()));

    public static final DeferredBlock<SilverTreeBranchBlock> SILVER_TREE_BRANCH_MEDIUM = BLOCKS.register("silver_tree_branch_medium",
            () -> new SilverTreeBranchBlock(6, 3, branchProps()));

    public static final DeferredBlock<SilverTreeBranchBlock> SILVER_TREE_BRANCH_LARGE = BLOCKS.register("silver_tree_branch_large",
            () -> new SilverTreeBranchBlock(9, 4, branchProps()));

    public static final DeferredBlock<SilverTreeBranchBlock> SILVER_TREE_BRANCH = BLOCKS.register("silver_tree_branch",
            () -> new SilverTreeBranchBlock(13, 5, branchProps()));

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

    private static BlockBehaviour.Properties buttonProps() {
        // Note: ButtonBlock forces the sound from the BlockSetType, so it is not set here.
        return BlockBehaviour.Properties.of()
                .noCollission()
                .strength(0.5f)
                .pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties signProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .forceSolidOn()
                .instrument(NoteBlockInstrument.BASS)
                .noCollission()
                .strength(1.0f)
                .ignitedByLava();
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

    private static BlockBehaviour.Properties buddingProps() {
        return BlockBehaviour.Properties.ofFullCopy(SILVER_WOOD_LOG.get()).randomTicks();
    }

    private static BlockBehaviour.Properties branchProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .noCollission()
                .strength(0.2f)
                .sound(SoundType.GRASS)
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
