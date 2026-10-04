package com.abyssia.habitat.relay;

import com.abyssia.Abyssia;
import com.abyssia.habitat.build.BuildRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * WR01 wireless power relay: the 2-high relay (lower body with the block entity + upper antenna), no items, no drops,
 * and its EQUIPMENT build entry. Links / power transfer: {@link RelayNetwork}.
 */
public final class RelayContent
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Abyssia.MODID);

    public static final RegistryObject<Block> RELAY = BLOCKS.register("wireless_power_relay",
            () -> new WirelessPowerRelayBlock(props().lightLevel(s -> s.getValue(WirelessPowerRelayBlock.LINKED) ? 7 : 3)));
    public static final RegistryObject<Block> RELAY_TOP = BLOCKS.register("wireless_power_relay_top",
            () -> new WirelessPowerRelayTopBlock(props()));
    public static final RegistryObject<BlockEntityType<WirelessPowerRelayBlockEntity>> RELAY_ENTITY = BLOCK_ENTITIES.register("wireless_power_relay",
            () -> BlockEntityType.Builder.of(WirelessPowerRelayBlockEntity::new, RELAY.get()).build(null));

    private RelayContent() {}

    private static BlockBehaviour.Properties props()
    {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0f, 6.0f).noLootTable().noOcclusion()
                .sound(SoundType.METAL).pushReaction(PushReaction.BLOCK);
    }

    public static void register(IEventBus bus)
    {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        BuildRegistry.register(new RelayEntry());
    }
}
