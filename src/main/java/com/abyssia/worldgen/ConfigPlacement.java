package com.abyssia.worldgen;

import com.abyssia.Config;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.stream.Stream;

/**
 * Lets data-driven placed features obey the config: {@code {"type": "abyssia:config", "option": "<name>"}}.
 * Each option yields a multiplier for how many times the feature is attempted: toggles give 0 or 1, densities
 * and chances scale relative to their defaults (fractions are resolved randomly, so 1.5 means 1 or 2).
 */
public class ConfigPlacement extends PlacementModifier
{
    private static final Map<String, DoubleSupplier> OPTIONS = Map.of(
            "vegetation", () -> Config.VEGETATION_ENABLED.get() ? Config.VEGETATION_DENSITY.get() : 0,
            "giant_plants", () -> Config.VEGETATION_ENABLED.get() ? Config.GIANT_PLANT_CHANCE.get() / 0.1 : 0,
            "glowing_plants", () -> Config.VEGETATION_ENABLED.get() ? Config.GLOWING_PLANT_CHANCE.get() / 0.03 : 0,
            "abyssal_forests", () -> Config.VEGETATION_ENABLED.get() && Config.ABYSSAL_FORESTS.get() ? 1 : 0,
            "crystals", () -> Config.CRYSTAL_FIELDS.get() ? 1 : 0,
            "thermal_vents", () -> Config.THERMAL_VENTS.get() ? 1 : 0);

    public static final Codec<ConfigPlacement> CODEC = Codec.STRING.comapFlatMap(
            option -> OPTIONS.containsKey(option)
                    ? DataResult.success(new ConfigPlacement(option))
                    : DataResult.error(() -> "Unknown abyssia config option: " + option),
            p -> p.option).fieldOf("option").codec();

    private final String option;

    private ConfigPlacement(String option)
    {
        this.option = option;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos)
    {
        double factor = OPTIONS.get(option).getAsDouble();
        int whole = (int) factor;
        int count = whole + (random.nextDouble() < factor - whole ? 1 : 0);
        return count == 1 ? Stream.of(pos) : Stream.generate(() -> pos).limit(count);
    }

    @Override
    public PlacementModifierType<?> type()
    {
        return ModWorldgen.CONFIG_PLACEMENT.get();
    }
}
