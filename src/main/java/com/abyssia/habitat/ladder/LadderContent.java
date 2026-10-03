package com.abyssia.habitat.ladder;

import com.abyssia.Abyssia;
import com.abyssia.habitat.build.BuildRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** BT01c: habitat ladder block + the ladder / vertical hatch build entries (BuildContent anchor line). */
public final class LadderContent
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Abyssia.MODID);

    /** no BlockItem, no loot table (generated habitat block) */
    public static final RegistryObject<Block> LADDER = BLOCKS.register("habitat_ladder", () -> new HabitatLadderBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()));

    private LadderContent() {}

    public static void register(IEventBus bus)
    {
        BLOCKS.register(bus);
        BuildRegistry.register(new LadderEntry());
        BuildRegistry.register(new VerticalHatchEntry());
    }
}
