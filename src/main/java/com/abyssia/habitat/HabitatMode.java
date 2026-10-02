package com.abyssia.habitat;

import com.abyssia.Abyssia;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** The habitat modules (spec inbox/specs/H01-habitat-modules.md): outer size, connectors and cost. */
public enum HabitatMode
{
    FOUNDATION("foundation", 13, 13, 1, EnumSet.noneOf(Face.class), EnumSet.noneOf(Face.class),
            List.of(vanilla(Items.IRON_INGOT, 12), vanilla(Items.COPPER_INGOT, 8), mod("iron_plate", 8))),
    ROOM("room", 13, 13, 5, EnumSet.allOf(Face.class), EnumSet.noneOf(Face.class),
            List.of(vanilla(Items.IRON_INGOT, 16), vanilla(Items.COPPER_INGOT, 12), mod("high_strength_alloy_ingot", 4),
                    mod("iron_plate", 12))),
    CORRIDOR("corridor", 5, 7, 5, EnumSet.of(Face.NEAR, Face.FAR), EnumSet.noneOf(Face.class),
            List.of(vanilla(Items.IRON_INGOT, 10), vanilla(Items.COPPER_INGOT, 6), mod("high_strength_alloy_ingot", 2),
                    mod("iron_plate", 6))),
    ENTRANCE("entrance", 5, 5, 5, EnumSet.of(Face.NEAR), EnumSet.of(Face.FAR),
            List.of(vanilla(Items.IRON_INGOT, 18), vanilla(Items.COPPER_INGOT, 12), mod("high_strength_alloy_ingot", 4),
                    mod("corrosion_alloy_ingot", 2), mod("iron_plate", 10))),
    MOON_POOL("moon_pool", 13, 13, 5, EnumSet.allOf(Face.class), EnumSet.noneOf(Face.class),
            List.of(vanilla(Items.IRON_INGOT, 16), vanilla(Items.COPPER_INGOT, 10), mod("corrosion_alloy_ingot", 4),
                    mod("high_strength_alloy_ingot", 4), mod("iron_plate", 8))),
    /** H07: 3 x 3 x 3 room with a scan console on the floor centre, a connector on every face, no windows */
    SCAN_ROOM("scan_room", 5, 5, 5, EnumSet.allOf(Face.class), EnumSet.noneOf(Face.class),
            List.of(vanilla(Items.IRON_INGOT, 14), vanilla(Items.COPPER_INGOT, 10), mod("conductive_alloy_ingot", 4),
                    mod("iron_plate", 8), mod("abyssal_crystal_shard", 4)));

    /** Module faces in local coordinates; connector panels (3 x 3) and doors (1 wide) sit at the centre of a face. */
    public enum Face { NEAR, FAR, LEFT, RIGHT }

    public record Cost(Supplier<Item> item, int count) {}

    public final String id;
    public final int width, depth, height;
    /** faces with a connector (a 3 x 3 hatch panel, or the door for doors that are also connectors) */
    public final Set<Face> connectors;
    /** faces with a door (2 tall + door frame) instead of a hatch */
    public final Set<Face> doors;
    public final List<Cost> cost;

    /** 13-wide modules whose shared wall opens completely when they meet (H03) */
    public boolean mergesWalls()
    {
        return this == ROOM || this == MOON_POOL;
    }

    HabitatMode(String id, int width, int depth, int height, Set<Face> connectors, Set<Face> doors, List<Cost> cost)
    {
        this.id = id;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.connectors = connectors;
        this.doors = doors;
        this.cost = cost;
    }

    public HabitatMode next()
    {
        HabitatMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static HabitatMode byId(String id)
    {
        for (HabitatMode mode : values())
            if (mode.id.equals(id)) return mode;
        return FOUNDATION;
    }

    public MutableComponent displayName()
    {
        return Component.translatable("habitat." + Abyssia.MODID + ".mode." + id);
    }

    private static Cost vanilla(Item item, int count)
    {
        return new Cost(() -> item, count);
    }

    private static Cost mod(String name, int count)
    {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name);
        return new Cost(() -> ForgeRegistries.ITEMS.getValue(id), count);
    }
}
