package com.abyssia.worldgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * A number range in structure data, written {@code [min, max]} (or a single number for a fixed value). Sampled from
 * a candidate's own random, so the same structure instance always rolls the same size.
 */
public record Span(float min, float max)
{
    private static final Codec<Span> LIST = Codec.FLOAT.listOf().comapFlatMap(
            l -> l.size() == 2 && l.get(0) <= l.get(1) ? DataResult.success(new Span(l.get(0), l.get(1)))
                    : DataResult.error(() -> "Expected [min, max] with min <= max, got " + l),
            s -> List.of(s.min, s.max));
    public static final Codec<Span> CODEC = Codec.either(Codec.FLOAT, LIST).xmap(
            e -> e.map(v -> new Span(v, v), s -> s),
            s -> s.min == s.max ? com.mojang.datafixers.util.Either.left(s.min) : com.mojang.datafixers.util.Either.right(s));

    public static Span of(float min, float max)
    {
        return new Span(min, max);
    }

    public float sample(RandomSource random)
    {
        return min + random.nextFloat() * (max - min);
    }

    public int sampleInt(RandomSource random)
    {
        return Mth.floor(min + random.nextFloat() * (max - min + 1));
    }

    public float lerp(float t)
    {
        return min + (max - min) * t;
    }
}
