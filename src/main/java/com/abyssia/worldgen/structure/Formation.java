package com.abyssia.worldgen.structure;

import com.abyssia.worldgen.structure.formation.ArtifactFieldFormation;
import com.abyssia.worldgen.structure.formation.CargoContainerFormation;
import com.abyssia.worldgen.structure.formation.CaveFormation;
import com.abyssia.worldgen.structure.formation.CraterFormation;
import com.abyssia.worldgen.structure.formation.CrystalFormation;
import com.abyssia.worldgen.structure.formation.FallenLogFormation;
import com.abyssia.worldgen.structure.formation.PillarFormation;
import com.abyssia.worldgen.structure.formation.RottenTreeFormation;
import com.abyssia.worldgen.structure.formation.RockFormation;
import com.abyssia.worldgen.structure.formation.SedimentFormation;
import com.abyssia.worldgen.structure.formation.SkeletonFormation;
import com.abyssia.worldgen.structure.formation.TrenchFormation;
import com.abyssia.worldgen.structure.formation.VegetationFormation;
import com.abyssia.worldgen.structure.formation.VentFormation;
import com.abyssia.worldgen.structure.formation.VolcanoFormation;
import com.abyssia.worldgen.structure.formation.WreckFormation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * A generic structure template (pillar, rock, crystal, vent, volcano, crater, trench, sediment, vegetation, cave),
 * given its parameters by a {@link SeabedStructure} definition ({@code "formation": {"type": "pillar", ...}}).
 * <p>
 * A formation never keeps state: each chunk re-derives the instance's layout from {@link Site#random()} (same
 * seed, same draws in the same order) and paints only its own columns through the {@link Painter}, so a structure
 * spanning many chunks comes out whole and seamless in any generation order.
 */
public interface Formation
{
    Codec<Formation> CODEC = Codec.STRING.partialDispatch("type", f -> DataResult.success(f.type()), Formation::codec);

    private static DataResult<Codec<? extends Formation>> codec(String type)
    {
        Codec<? extends Formation> codec = switch (type)
        {
            case PillarFormation.TYPE -> PillarFormation.CODEC;
            case RockFormation.TYPE -> RockFormation.CODEC;
            case CrystalFormation.TYPE -> CrystalFormation.CODEC;
            case VentFormation.TYPE -> VentFormation.CODEC;
            case VolcanoFormation.TYPE -> VolcanoFormation.CODEC;
            case CraterFormation.TYPE -> CraterFormation.CODEC;
            case TrenchFormation.TYPE -> TrenchFormation.CODEC;
            case SedimentFormation.TYPE -> SedimentFormation.CODEC;
            case VegetationFormation.TYPE -> VegetationFormation.CODEC;
            case CaveFormation.TYPE -> CaveFormation.CODEC;
            case RottenTreeFormation.TYPE -> RottenTreeFormation.CODEC;
            case FallenLogFormation.TYPE -> FallenLogFormation.CODEC;
            case CargoContainerFormation.TYPE -> CargoContainerFormation.CODEC;
            case SkeletonFormation.TYPE -> SkeletonFormation.CODEC;
            case ArtifactFieldFormation.TYPE -> ArtifactFieldFormation.CODEC;
            case WreckFormation.TYPE -> WreckFormation.CODEC;
            default -> null;
        };
        return codec != null ? DataResult.success(codec) : DataResult.error(() -> "Unknown seabed structure formation: " + type);
    }

    String type();

    /** Builds the structure body (and reshapes the terrain around it) in the painter's chunk. */
    void paint(Site site, Painter painter);

    /** Formation-specific living parts (a kelp forest's kelp), painted in the dressing pass with the plants. */
    default void dress(Site site, Painter painter) {}

    /**
     * How far below its base this formation digs (0 if it only adds material). Carving formations are kept away
     * from cave volumes, so they never open a cave's gas pocket or leave a hanging wall over a void.
     */
    default int carveDepth(SeabedStructure definition)
    {
        return 0;
    }

    /** How far from the centre digging can reach (the whole footprint unless the formation knows better). */
    default int carveReach(SeabedStructure definition)
    {
        return definition.footprintRadius();
    }
}
