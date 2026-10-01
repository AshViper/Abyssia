package com.abyssia.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

import java.util.List;
import java.util.stream.Stream;

/**
 * Biome source of the default (vanilla-land) world with the deep layer underneath (inbox/specs/M02-vanilla-default-deep-layer.md).
 * <ul>
 *   <li>At and above the bedrock band (quart Y &gt;= {@code QuartPos.fromBlock(DeepLayer.TOP_Y)}): the deep fissure where
 *   the router depth is below {@code fissure_max_depth} (the router pushes it down to about -10 there), else the
 *   {@code upper} source (vanilla's overworld preset).</li>
 *   <li>Below it: the {@code lower} source (the deep layer's biome list).</li>
 * </ul>
 * The climate is sampled once per call and handed to the chosen source.
 */
public class LayeredBiomeSource extends BiomeSource
{
    /** A {@code {"type": "minecraft:multi_noise", ...}} biome source, rejecting any other type. */
    private static final Codec<MultiNoiseBiomeSource> MULTI_NOISE = BiomeSource.CODEC.comapFlatMap(
            source -> source instanceof MultiNoiseBiomeSource multiNoise
                    ? DataResult.success(multiNoise)
                    : DataResult.error(() -> "abyssia:layered needs a minecraft:multi_noise biome source, got " + source),
            source -> source);

    public static final Codec<LayeredBiomeSource> CODEC = RecordCodecBuilder.create(i -> i.group(
            MULTI_NOISE.fieldOf("upper").forGetter(s -> s.upper),
            MULTI_NOISE.fieldOf("lower").forGetter(s -> s.lower),
            Biome.CODEC.fieldOf("fissure_biome").forGetter(s -> s.fissureBiome),
            Codec.FLOAT.fieldOf("fissure_max_depth").forGetter(s -> s.fissureMaxDepth)
    ).apply(i, i.stable(LayeredBiomeSource::new)));

    /** Quart Y of the bedrock band's bottom (-64 / 4 = -16): this and above is the upper world. */
    private static final int UPPER_MIN_QUART_Y = QuartPos.fromBlock(DeepLayer.TOP_Y);

    private final MultiNoiseBiomeSource upper;
    private final MultiNoiseBiomeSource lower;
    private final Holder<Biome> fissureBiome;
    private final float fissureMaxDepth;
    private final long fissureMaxDepthQuantized;

    public LayeredBiomeSource(MultiNoiseBiomeSource upper, MultiNoiseBiomeSource lower, Holder<Biome> fissureBiome, float fissureMaxDepth)
    {
        this.upper = upper;
        this.lower = lower;
        this.fissureBiome = fissureBiome;
        this.fissureMaxDepth = fissureMaxDepth;
        this.fissureMaxDepthQuantized = Climate.quantizeCoord(fissureMaxDepth);
    }

    public MultiNoiseBiomeSource upper()
    {
        return upper;
    }

    public MultiNoiseBiomeSource lower()
    {
        return lower;
    }

    public Holder<Biome> fissureBiome()
    {
        return fissureBiome;
    }

    @Override
    protected Codec<? extends BiomeSource> codec()
    {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes()
    {
        return Stream.concat(Stream.concat(upper.possibleBiomes().stream(), lower.possibleBiomes().stream()), Stream.of(fissureBiome)).distinct();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler)
    {
        Climate.TargetPoint target = sampler.sample(x, y, z);
        if (y < UPPER_MIN_QUART_Y) return lower.getNoiseBiome(target);
        return target.depth() < fissureMaxDepthQuantized ? fissureBiome : upper.getNoiseBiome(target);
    }

    /** F3: vanilla's biome-builder line above the deep layer; the deep layer's own climate does not fit that builder. */
    @Override
    public void addDebugInfo(List<String> info, BlockPos pos, Climate.Sampler sampler)
    {
        int qy = QuartPos.fromBlock(pos.getY());
        if (qy >= UPPER_MIN_QUART_Y)
        {
            upper.addDebugInfo(info, pos, sampler);
            Climate.TargetPoint target = sampler.sample(QuartPos.fromBlock(pos.getX()), qy, QuartPos.fromBlock(pos.getZ()));
            if (target.depth() < fissureMaxDepthQuantized) info.add("Abyssia: deep fissure (D " + Climate.unquantizeCoord(target.depth()) + " < " + fissureMaxDepth + ")");
        }
        else
        {
            info.add("Abyssia: deep layer (below Y " + DeepLayer.TOP_Y + ")");
        }
    }
}
