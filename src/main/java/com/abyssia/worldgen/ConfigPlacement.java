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
    private static final Map<String, DoubleSupplier> OPTIONS = Map.ofEntries(
            opt("vegetation", () -> Config.VEGETATION_ENABLED.get() ? Config.VEGETATION_DENSITY.get() : 0),
            opt("giant_plants", () -> Config.VEGETATION_ENABLED.get() ? Config.GIANT_PLANT_CHANCE.get() / 0.1 : 0),
            opt("glowing_plants", () -> Config.VEGETATION_ENABLED.get() ? Config.GLOWING_PLANT_CHANCE.get() / 0.03 : 0),
            opt("abyssal_forests", () -> Config.VEGETATION_ENABLED.get() && Config.ABYSSAL_FORESTS.get() ? 1 : 0),
            opt("crystals", () -> Config.CRYSTAL_FIELDS.get() ? 1 : 0),
            opt("thermal_vents", () -> Config.THERMAL_VENTS.get() ? 1 : 0),
            // AB02 crust ores: the tier multiplier times the global crust density (0 disables)
            opt("mk0_ore", () -> Config.CRUST_MK0_ORE.get() * Config.CRUST_ORE_DENSITY.get()),
            opt("mk1_ore", () -> Config.CRUST_MK1_ORE.get() * Config.CRUST_ORE_DENSITY.get()),
            opt("mk2_ore", () -> Config.CRUST_MK2_ORE.get() * Config.CRUST_ORE_DENSITY.get()));

    private static Map.Entry<String, DoubleSupplier> opt(String name, DoubleSupplier factor)
    {
        return Map.entry(name, factor);
    }

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
