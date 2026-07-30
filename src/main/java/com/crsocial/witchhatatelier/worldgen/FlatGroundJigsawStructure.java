package com.crsocial.witchhatatelier.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

import java.util.List;
import java.util.Optional;

/**
 * Reimplements JigsawStructure's single-start placement (JigsawStructure itself is
 * {@code final}, so it can't be subclassed/decorated) with one added behavior: before
 * calling {@link JigsawPlacement#addPieces}, sample the world-surface heightmap around
 * the candidate point and reject the chunk if the terrain isn't flat enough, instead of
 * placing anywhere and letting {@code terrain_adaptation} patch the mismatch afterward.
 */
public class FlatGroundJigsawStructure extends Structure {

    public static final MapCodec<FlatGroundJigsawStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
                    Codec.intRange(0, JigsawStructure.MAX_DEPTH).fieldOf("size").forGetter(s -> s.maxDepth),
                    HeightProvider.CODEC.fieldOf("start_height").forGetter(s -> s.startHeight),
                    Codec.BOOL.fieldOf("use_expansion_hack").forGetter(s -> s.useExpansionHack),
                    Heightmap.Types.CODEC.fieldOf("project_start_to_heightmap").forGetter(s -> s.projectStartToHeightmap),
                    Codec.intRange(0, 32).fieldOf("footprint_radius").forGetter(s -> s.footprintRadius),
                    Codec.intRange(0, 32).fieldOf("max_height_difference").forGetter(s -> s.maxHeightDifference)
            )
            .apply(instance, FlatGroundJigsawStructure::new));

    private final Holder<StructureTemplatePool> startPool;
    private final int maxDepth;
    private final HeightProvider startHeight;
    private final boolean useExpansionHack;
    private final Heightmap.Types projectStartToHeightmap;
    private final int footprintRadius;
    private final int maxHeightDifference;

    public FlatGroundJigsawStructure(Structure.StructureSettings settings,
                                      Holder<StructureTemplatePool> startPool,
                                      int maxDepth,
                                      HeightProvider startHeight,
                                      boolean useExpansionHack,
                                      Heightmap.Types projectStartToHeightmap,
                                      int footprintRadius,
                                      int maxHeightDifference) {
        super(settings);
        this.startPool = startPool;
        this.maxDepth = maxDepth;
        this.startHeight = startHeight;
        this.useExpansionHack = useExpansionHack;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.footprintRadius = footprintRadius;
        this.maxHeightDifference = maxHeightDifference;
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        int startY = this.startHeight.sample(context.random(), new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor()));
        BlockPos pos = new BlockPos(chunkPos.getMinBlockX(), startY, chunkPos.getMinBlockZ());

        if (!isTerrainFlatEnough(context, pos)) {
            return Optional.empty();
        }

        return JigsawPlacement.addPieces(
                context,
                this.startPool,
                Optional.empty(),
                this.maxDepth,
                pos,
                this.useExpansionHack,
                Optional.of(this.projectStartToHeightmap),
                80,
                PoolAliasLookup.create(List.of(), pos, context.seed()),
                JigsawStructure.DEFAULT_DIMENSION_PADDING,
                JigsawStructure.DEFAULT_LIQUID_SETTINGS
        );
    }

    private boolean isTerrainFlatEnough(Structure.GenerationContext context, BlockPos pos) {
        // pos is the footprint's MIN corner (chunkPos.getMinBlockX/Z), not its center, so the
        // footprint spans [0, 2*footprintRadius] from pos, not [-footprintRadius, +footprintRadius].
        int[] offsets = {0, footprintRadius, footprintRadius * 2};

        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        for (int dx : offsets) {
            for (int dz : offsets) {
                int height = context.chunkGenerator().getFirstOccupiedHeight(
                        pos.getX() + dx,
                        pos.getZ() + dz,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(),
                        context.randomState());
                minHeight = Math.min(minHeight, height);
                maxHeight = Math.max(maxHeight, height);
            }
        }

        return (maxHeight - minHeight) <= maxHeightDifference;
    }

    @Override
    public StructureType<?> type() {
        return ModStructureTypes.FLAT_GROUND_JIGSAW.get();
    }
}
