package com.abyssia.thermal;

import com.abyssia.Config;
import com.abyssia.block.StackingPlantBlock;
import com.abyssia.block.ThermalVentBlock;
import com.abyssia.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

/**
 * Paints the parts of nearby vent fields that fall inside the current chunk: mineral zoning and mounds on the
 * seabed, tapering chimneys topped by vent cores, rubble from collapsed chimneys, and a hydrothermal cave under
 * large fields. Every shape derives from the field layout plus noise-based terrain heights, so neighbouring
 * chunks agree without ever writing outside their own columns.
 */
public class ThermalVentGenerator extends Feature<NoneFeatureConfiguration>
{
    private static final int CEILING_MARGIN = 16;

    public ThermalVentGenerator()
    {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context)
    {
        if (!Config.THERMAL_VENTS.get()) return false;
        WorldGenLevel level = context.level();
        ChunkGenerator generator = context.chunkGenerator();
        RandomState randomState = level.getLevel().getChunkSource().randomState();
        BiomeSource biomes = generator.getBiomeSource();
        ChunkPos chunk = new ChunkPos(context.origin());

        List<ThermalVentField> fields = ThermalVentField.near(level.getSeed(), chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), chunk.getMaxBlockZ(),
                (x, z) -> biomes.getNoiseBiome(QuartPos.fromBlock(x), 0, QuartPos.fromBlock(z), randomState.sampler()).unwrapKey().orElse(null));
        for (ThermalVentField field : fields) new Painter(level, generator, randomState, field, chunk).paint();
        return !fields.isEmpty();
    }

    private static final class Painter
    {
        private final WorldGenLevel level;
        private final ChunkGenerator generator;
        private final RandomState randomState;
        private final ThermalVentField field;
        private final int minX, minZ, maxX, maxZ;
        private final SimplexNoise noise;
        private final int[] baseHeights;
        private final boolean minerals = Config.MINERAL_GENERATION.get();
        private final boolean crystals = Config.CRYSTAL_GENERATION.get();
        private final boolean thermalPlants = Config.THERMAL_VEGETATION.get() && Config.VEGETATION_ENABLED.get();
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        Painter(WorldGenLevel level, ChunkGenerator generator, RandomState randomState, ThermalVentField field, ChunkPos chunk)
        {
            this.level = level;
            this.generator = generator;
            this.randomState = randomState;
            this.field = field;
            this.minX = chunk.getMinBlockX();
            this.minZ = chunk.getMinBlockZ();
            this.maxX = chunk.getMaxBlockX();
            this.maxZ = chunk.getMaxBlockZ();
            this.noise = new SimplexNoise(new XoroshiroRandomSource(field.seed()));
            this.baseHeights = new int[field.vents().size()];
            java.util.Arrays.fill(baseHeights, Integer.MIN_VALUE);
        }

        void paint()
        {
            paintSeabed();
            for (int i = 0; i < field.vents().size(); i++) paintChimney(i);
            if (field.kind() != ThermalVentField.Kind.NORMAL) paintCave();
            // Cores last, so a neighbouring chimney painted afterwards can never bury one.
            for (Core core : cores) set(core.pos(), core.state());
        }

        private record Core(BlockPos pos, BlockState state) {}

        private final List<Core> cores = new ArrayList<>();

        private void addCore(int x, int y, int z, ThermalVentType type, VentActivity activity)
        {
            cores.add(new Core(new BlockPos(x, y, z), ModBlocks.THERMAL_VENT.get().defaultBlockState()
                    .setValue(ThermalVentBlock.TYPE, type).setValue(ThermalVentBlock.ACTIVITY, activity)));
        }

        // ---------- helpers ----------

        private boolean inChunk(int x, int z)
        {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        private boolean near(int x, int z, double reach)
        {
            double dx = Math.max(0, Math.max(minX - x, x - maxX)), dz = Math.max(0, Math.max(minZ - z, z - maxZ));
            return dx * dx + dz * dz <= reach * reach;
        }

        /** Deterministic 0..1 value per position and purpose. */
        private double hash(int x, int y, int z, int salt)
        {
            long h = RandomSupport.mixStafford13(field.seed() ^ x * 3129871L ^ z * 116129781L ^ y * 0x5DEECE66DL ^ salt * 0x9E3779B97F4A7C15L);
            return (h >>> 11) * 0x1.0p-53;
        }

        /** Terrain height at a vent from the generator's noise, identical from every chunk. */
        private int baseY(int index)
        {
            if (baseHeights[index] == Integer.MIN_VALUE)
            {
                ThermalVentField.Vent v = field.vents().get(index);
                baseHeights[index] = generator.getBaseHeight(v.x(), v.z(), Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
            }
            return baseHeights[index];
        }

        private boolean isWater(BlockPos p)
        {
            return level.getBlockState(p).is(Blocks.WATER);
        }

        private void set(BlockPos p, BlockState state)
        {
            if (level.isOutsideBuildHeight(p) || level.getBlockState(p).is(Blocks.BEDROCK)) return;
            level.setBlock(p, state, 2);
        }

        private static BlockState crystal(Block block)
        {
            return block.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP).setValue(AmethystClusterBlock.WATERLOGGED, true);
        }

        private float moundRadius(ThermalVentField.Vent v)
        {
            return 2f + v.width() * 2f + v.moundHeight() * 1.5f;
        }

        // ---------- seabed: mounds and mineral zones ----------

        private void paintSeabed()
        {
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    double distCenter = Math.sqrt(Mth.square(x - field.centerX()) + Mth.square(z - field.centerZ()));
                    if (distCenter > field.radius() + 6) continue;
                    int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                    if (!isWater(pos.set(x, floor, z))) continue;

                    // Mineral zoning follows the hottest vent's footprint, independent of its current activity:
                    // deposits outlive the vents that made them.
                    float zoneHeat = 0f, mound = 0f;
                    ThermalVentType hottest = null;
                    for (ThermalVentField.Vent v : field.vents())
                    {
                        double d = Math.sqrt(Mth.square(x - v.x()) + Mth.square(z - v.z()));
                        float heat = v.type().maxTemperature * ThermalTemperature.falloff(d, v.type().radius * (field.kind() == ThermalVentField.Kind.ANCIENT ? 2.2 : 1.6));
                        if (heat > zoneHeat)
                        {
                            zoneHeat = heat;
                            hottest = v.type();
                        }
                        mound = Math.max(mound, v.moundHeight() * ThermalTemperature.falloff(d, moundRadius(v)));
                    }
                    float coverage = ThermalTemperature.falloff(distCenter, field.radius());
                    double n = noise.getValue(x * 0.12, z * 0.12);
                    double n2 = noise.getValue(x * 0.05 + 100, z * 0.05);

                    BlockState surface = zoneBlock(zoneHeat, hottest, n, n2, coverage, hash(x, 0, z, 1));
                    int moundHeight = Math.max(0, Mth.floor(mound + n * 0.6));
                    if (surface == null && moundHeight > 0) surface = ModBlocks.VENT_ROCK.get().defaultBlockState();
                    if (surface != null)
                    {
                        set(pos.set(x, floor - 1, z), surface);
                        if (zoneHeat > 0.3f) set(pos.set(x, floor - 2, z), surface);
                    }
                    for (int i = 0; i < moundHeight; i++)
                    {
                        set(pos.set(x, floor + i, z), i == moundHeight - 1 ? surface : moundInterior(hottest, x, floor + i, z));
                    }

                    int top = floor + moundHeight;
                    if (crystals && isWater(pos.set(x, top, z)))
                    {
                        BlockState crystal = crystalFor(zoneHeat, coverage, hash(x, 0, z, 2));
                        if (crystal != null) set(pos, crystal);
                    }
                    if (thermalPlants && isWater(pos.set(x, top, z))) plantThermalRing(x, top, z, zoneHeat, hash(x, 0, z, 9));
                }
            }
        }

        private BlockState zoneBlock(float heat, ThermalVentType hottest, double n, double n2, float coverage, double r)
        {
            if (heat > 0.7f)
            {
                return switch (hottest)
                {
                    case BLACK_SMOKER -> ModBlocks.BLACK_MINERAL_DEPOSIT.get().defaultBlockState();
                    case WHITE_SMOKER -> (n > 0 ? ModBlocks.SULFUR_VENT_ROCK : ModBlocks.THERMAL_ROCK).get().defaultBlockState();
                    case MINERAL -> ModBlocks.MINERAL_VENT_ROCK.get().defaultBlockState();
                    case SUPERHEATED -> n > 0.3 ? ModBlocks.MOLTEN_VOLCANIC_ROCK.get().defaultBlockState() : n < -0.4 ? ModBlocks.VOLCANIC_GLASS.get().defaultBlockState()
                            : ModBlocks.THERMAL_ROCK.get().defaultBlockState();
                };
            }
            if (heat > 0.3f)
            {
                if (!minerals) return ModBlocks.VENT_ROCK.get().defaultBlockState();
                if (n > 0.35) return ModBlocks.SULFUR_DEPOSIT.get().defaultBlockState();
                if (n > 0.1) return ModBlocks.SULFUR_ORE.get().defaultBlockState();
                if (n > -0.1 && r < 0.35) return ModBlocks.DEEP_COPPER_ORE.get().defaultBlockState();
                if (n > -0.2 && r < 0.2) return ModBlocks.THERMAL_CRYSTAL_ORE.get().defaultBlockState();
                if (n < -0.3 && hottest == ThermalVentType.BLACK_SMOKER) return ModBlocks.BLACK_MINERAL_DEPOSIT.get().defaultBlockState();
                return ModBlocks.MINERAL_SEDIMENT.get().defaultBlockState();
            }
            // Outer sediment zone: patchy thermal sediment thinning toward the field edge.
            if (r < coverage * 0.8 + heat)
            {
                return n2 > 0.2 ? ModBlocks.MINERAL_SEDIMENT.get().defaultBlockState()
                        : n2 > -0.3 ? ModBlocks.SULFUR_DEPOSIT.get().defaultBlockState() : null;
            }
            return null;
        }

        private BlockState moundInterior(ThermalVentType hottest, int x, int y, int z)
        {
            if (minerals && hash(x, y, z, 3) < 0.08) return ModBlocks.SULFUR_ORE.get().defaultBlockState();
            return (hottest == ThermalVentType.BLACK_SMOKER ? ModBlocks.BLACK_VENT_ROCK : ModBlocks.VENT_ROCK).get().defaultBlockState();
        }

        /**
         * Concentric vent ecology: nothing survives at the core, heat moss clings to hot rock, heat-adapted
         * tubes and vent grass crowd the warm ring, and mineral vines climb the mineral zone. Beyond that
         * ordinary deep-sea vegetation takes over (placed later by the biome's own features).
         */
        private void plantThermalRing(int x, int y, int z, float heat, double r)
        {
            if (heat > 0.75f) return;
            if (heat > 0.45f)
            {
                if (r < 0.45) set(pos.set(x, y, z), ModBlocks.HEAT_MOSS.get().defaultBlockState());
                else if (r < 0.55) column(x, y, z, ModBlocks.MINERAL_VINE.get(), 1 + (int) (hash(x, 1, z, 10) * 3));
                return;
            }
            if (heat > 0.25f)
            {
                if (r < 0.35) column(x, y, z, ModBlocks.THERMAL_TUBE.get(), 1 + (int) (hash(x, 1, z, 10) * 4));
                else if (r < 0.6) set(pos.set(x, y, z), ModBlocks.VENT_GRASS.get().defaultBlockState());
                return;
            }
            if (heat > 0.1f && r < 0.4) set(pos.set(x, y, z), ModBlocks.VENT_GRASS.get().defaultBlockState());
        }

        /** A stacking plant column, stopping at the first non-water block. */
        private void column(int x, int y, int z, Block plant, int height)
        {
            for (int i = 0; i < height; i++)
            {
                if (!isWater(pos.set(x, y + i, z))) height = i;
            }
            for (int i = 0; i < height; i++)
            {
                set(pos.set(x, y + i, z), plant.defaultBlockState().setValue(StackingPlantBlock.TOP, i == height - 1));
            }
        }

        /** Crystals grow larger the hotter (closer to a vent) they are. */
        private BlockState crystalFor(float heat, float coverage, double r)
        {
            if (heat > 0.15f && heat < 0.9f && r < 0.12)
            {
                return crystal(heat > 0.6f ? ModBlocks.THERMAL_CRYSTAL_CLUSTER.get()
                        : heat > 0.35f ? ModBlocks.MEDIUM_THERMAL_CRYSTAL_BUD.get() : ModBlocks.SMALL_THERMAL_CRYSTAL_BUD.get());
            }
            if (heat <= 0.15f && coverage > 0.2f && r < 0.02)
            {
                return crystal(field.kind() == ThermalVentField.Kind.ANCIENT ? ModBlocks.PRESSURE_CRYSTAL_CLUSTER.get() : ModBlocks.DEEP_CRYSTAL_CLUSTER.get());
            }
            return null;
        }

        // ---------- chimneys ----------

        private void paintChimney(int index)
        {
            ThermalVentField.Vent v = field.vents().get(index);
            float reach = v.width() * 2f + v.height() * 0.4f + 6f;
            if (!near(v.x(), v.z(), reach)) return;

            int base = baseY(index) + Math.max(0, Math.round(v.moundHeight()) - 1);
            // Seabed right under the world ceiling (unreachable: divers surface back to the ocean world there)
            // has no room for a chimney and its core.
            if (base + v.height() + 1 >= level.getMaxBuildHeight() - CEILING_MARGIN) return;
            int standing = Math.max(1, Math.round(v.height() * (1f - v.age().collapse * (0.6f + 0.4f * (float) hash(v.x(), 0, v.z(), 4)))));
            double cx = v.x() + 0.5, cz = v.z() + 0.5;
            // Start slightly below the base so the chimney is always rooted in the mound, never floating.
            for (int h = -2; h < standing; h++)
            {
                float taper = 1f - Math.max(0, h) / (float) (v.height() + 1);
                double radius = v.width() * Math.pow(taper, 0.9) + 0.45;
                cx = v.x() + 0.5 + v.leanX() * h + noise.getValue(v.x() * 0.1, h * 0.25) * 0.35;
                cz = v.z() + 0.5 + v.leanZ() * h + noise.getValue(v.z() * 0.1 + 50, h * 0.25) * 0.35;
                int r = Mth.ceil(radius + 1);
                for (int bx = Mth.floor(cx) - r; bx <= Mth.floor(cx) + r; bx++)
                {
                    for (int bz = Mth.floor(cz) - r; bz <= Mth.floor(cz) + r; bz++)
                    {
                        if (!inChunk(bx, bz)) continue;
                        // Irregular outline: never a perfect cylinder.
                        double rr = radius * (1.0 + 0.18 * noise.getValue(bx * 0.4, bz * 0.4 + h * 0.3));
                        if (Mth.square(bx + 0.5 - cx) + Mth.square(bz + 0.5 - cz) > rr * rr) continue;
                        set(pos.set(bx, base + h, bz), chimneyBlock(v, Math.max(0, h), standing, bx, base + h, bz));
                    }
                }
            }

            // The core sits on the top, or on the broken stump of a collapsed chimney.
            int coreX = Mth.floor(cx), coreZ = Mth.floor(cz);
            if (inChunk(coreX, coreZ))
            {
                VentActivity activity = v.type() == ThermalVentType.SUPERHEATED && v.age() != VentAge.DEAD ? VentActivity.SUPERHEATED : v.age().activity;
                addCore(coreX, base + standing, coreZ, v.type(), activity);
            }

            // Rubble from the collapsed part, scattered around the base.
            int rubble = Math.round((v.height() - standing) * v.width());
            for (int i = 0; i < rubble; i++)
            {
                double angle = hash(v.x(), i, v.z(), 5) * Math.PI * 2;
                double dist = v.width() + 1 + hash(v.x(), i, v.z(), 6) * 4;
                int rx = Mth.floor(v.x() + Math.cos(angle) * dist), rz = Mth.floor(v.z() + Math.sin(angle) * dist);
                if (!inChunk(rx, rz)) continue;
                int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, rx, rz);
                if (isWater(pos.set(rx, y, rz))) set(pos, chimneyBlock(v, 0, standing, rx, y, rz));
            }
        }

        private BlockState chimneyBlock(ThermalVentField.Vent v, int h, int standing, int x, int y, int z)
        {
            double r = hash(x, y, z, 7);
            boolean nearTop = h >= standing - 2;
            return switch (v.type())
            {
                case BLACK_SMOKER -> nearTop && v.age() != VentAge.DEAD ? ModBlocks.BLACK_MINERAL_DEPOSIT.get().defaultBlockState()
                        : minerals && r < 0.1 ? ModBlocks.SULFUR_ORE.get().defaultBlockState()
                        : minerals && r < 0.15 ? ModBlocks.DEEP_COPPER_ORE.get().defaultBlockState()
                        : ModBlocks.BLACK_VENT_ROCK.get().defaultBlockState();
                case WHITE_SMOKER -> nearTop && minerals && r < 0.5 ? ModBlocks.SULFUR_DEPOSIT.get().defaultBlockState()
                        : noise.getValue(x * 0.5, y * 0.5 + z * 0.3) > 0.3 ? ModBlocks.SULFUR_VENT_ROCK.get().defaultBlockState()
                        : ModBlocks.VENT_ROCK.get().defaultBlockState();
                case MINERAL -> r < 0.35 ? ModBlocks.MINERAL_SEDIMENT.get().defaultBlockState() : ModBlocks.MINERAL_VENT_ROCK.get().defaultBlockState();
                case SUPERHEATED -> h < 2 ? ModBlocks.VOLCANIC_GLASS.get().defaultBlockState()
                        : r < 0.15 ? ModBlocks.MOLTEN_VOLCANIC_ROCK.get().defaultBlockState() : ModBlocks.THERMAL_ROCK.get().defaultBlockState();
            };
        }

        // ---------- hydrothermal cave under large fields ----------

        private void paintCave()
        {
            ThermalVentField.Vent main = field.vents().get(0);
            boolean ancient = field.kind() == ThermalVentField.Kind.ANCIENT;
            double rx = Mth.clamp(field.radius() * 0.12, 6, 12);
            double ry = ancient ? 6 : 4.5;
            if (!near(main.x(), main.z(), rx + 4)) return;
            int cy = baseY(0) - (ancient ? 16 : 12);
            if (cy - ry < level.getMinBuildHeight() + 6) return;
            double cx = main.x() + 0.5, cz = main.z() + 0.5;
            // A shaft connects the cave to the seabed so it can be found.
            double shaftX = cx + rx * 0.6, shaftZ = cz;

            for (int x = Mth.floor(cx - rx - 2); x <= Mth.ceil(cx + rx + 2); x++)
            {
                for (int z = Mth.floor(cz - rx - 2); z <= Mth.ceil(cz + rx + 2); z++)
                {
                    if (!inChunk(x, z)) continue;
                    double ex = (x + 0.5 - cx) / rx, ez = (z + 0.5 - cz) / rx;
                    int caveFloor = Integer.MAX_VALUE;
                    for (int y = Mth.floor(cy - ry - 2); y <= Mth.ceil(cy + ry + 2); y++)
                    {
                        double ey = (y + 0.5 - cy) / ry;
                        double e = ex * ex + ey * ey + ez * ez + noise.getValue(x * 0.2, y * 0.2 + z * 0.1) * 0.15;
                        pos.set(x, y, z);
                        if (e < 1.0)
                        {
                            set(pos, Blocks.WATER.defaultBlockState());
                            caveFloor = Math.min(caveFloor, y);
                        }
                        else if (e < 1.4 && !isWater(pos))
                        {
                            set(pos, minerals && noise.getValue(x * 0.3, y * 0.3 + z * 0.2) > 0.4 ? ModBlocks.SULFUR_DEPOSIT.get().defaultBlockState()
                                    : ModBlocks.THERMAL_ROCK.get().defaultBlockState());
                        }
                    }
                    if (caveFloor != Integer.MAX_VALUE && crystals && hash(x, caveFloor, z, 8) < 0.15)
                    {
                        set(pos.set(x, caveFloor, z), crystal(ModBlocks.THERMAL_CRYSTAL_CLUSTER.get()));
                    }
                    if (Mth.square(x + 0.5 - shaftX) + Mth.square(z + 0.5 - shaftZ) <= 2.25)
                    {
                        for (int y = cy; y < baseY(0) + 1; y++) set(pos.set(x, y, z), Blocks.WATER.defaultBlockState());
                    }
                }
            }
            int coreX = Mth.floor(cx), coreZ = Mth.floor(cz);
            if (inChunk(coreX, coreZ))
            {
                int bottom = Mth.floor(cy - ry + 0.5);
                addCore(coreX, bottom, coreZ, main.type(), VentActivity.ACTIVE);
            }
        }
    }
}
