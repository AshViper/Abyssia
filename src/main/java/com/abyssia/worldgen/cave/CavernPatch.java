package com.abyssia.worldgen.cave;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Ecology patches a cavern floor is divided into, so no two parts of one cavern look alike: plant life, minerals,
 * crystal, bare fallen rock, water (brine lakes), vent heat, and deliberately open ground.
 */
public enum CavernPatch implements StringRepresentable
{
    //       vegetation  crystals  minerals  speleothems
    PLANT("plant", 1.6, 0.5, 0.6, 0.8),
    MINERAL("mineral", 0.45, 0.8, 2.2, 1.1),
    CRYSTAL("crystal", 0.5, 1.8, 1.0, 1.0),
    ROCK("rock", 0.35, 0.4, 1.6, 1.5),
    WATER("water", 0.9, 0.6, 0.6, 0.8),
    THERMAL("thermal", 0.5, 0.8, 1.6, 1.0),
    OPEN("open", 0.12, 0.25, 0.6, 0.5);

    public static final Codec<CavernPatch> CODEC = StringRepresentable.fromEnum(CavernPatch::values);

    private final String name;
    /** Multipliers applied to the general cave decoration inside this patch. */
    public final double vegetation, crystals, minerals, speleothems;

    CavernPatch(String name, double vegetation, double crystals, double minerals, double speleothems)
    {
        this.name = name;
        this.vegetation = vegetation;
        this.crystals = crystals;
        this.minerals = minerals;
        this.speleothems = speleothems;
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }
}
