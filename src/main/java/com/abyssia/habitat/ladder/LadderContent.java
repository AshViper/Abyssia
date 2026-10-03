package com.abyssia.habitat.ladder;

import com.abyssia.Abyssia;
import com.abyssia.habitat.build.BuildRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** BT01c: habitat ladder block + the ladder / vertical hatch build entries (BuildContent anchor line). */
public final class LadderContent
{
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Abyssia.MODID);

    /** no BlockItem, no loot table (generated habitat block) */
    public static final DeferredBlock<Block> LADDER = BLOCKS.register("habitat_ladder", () -> new HabitatLadderBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion()));

    private LadderContent() {}

    public static void register(IEventBus bus)
    {
        BLOCKS.register(bus);
        BuildRegistry.register(new LadderEntry());
        BuildRegistry.register(new VerticalHatchEntry());
    }
}
