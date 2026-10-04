package com.abyssia.habitat.relay;

import com.abyssia.Abyssia;
import com.abyssia.habitat.build.BuildRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * WR01 wireless power relay: lower block (block entity) + upper antenna block, no items, no drops, and its build entry.
 * Links and transfer: {@link RelayNetwork}; client beams: client/RelayBeamRenderer.
 */
public final class RelayContent
{
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Abyssia.MODID);

    public static final DeferredBlock<Block> RELAY = BLOCKS.register("wireless_power_relay",
            () -> new WirelessPowerRelayBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0f, 6.0f)
                    .noLootTable().noOcclusion().sound(SoundType.METAL).pushReaction(PushReaction.BLOCK)
                    .lightLevel(s -> s.getValue(WirelessPowerRelayBlock.LINKED) ? 7 : 3)));
    public static final DeferredBlock<Block> RELAY_TOP = BLOCKS.register("wireless_power_relay_top",
            () -> new WirelessPowerRelayTopBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0f, 6.0f)
                    .noLootTable().noOcclusion().sound(SoundType.METAL).pushReaction(PushReaction.BLOCK)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessPowerRelayBlockEntity>> RELAY_ENTITY = BLOCK_ENTITIES.register(
            "wireless_power_relay", () -> BlockEntityType.Builder.of(WirelessPowerRelayBlockEntity::new, RELAY.get()).build(null));

    private RelayContent() {}

    public static void register(IEventBus bus)
    {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(RelayContent::registerCapabilities);
        BuildRegistry.register(new RelayEntry());
    }

    /**
     * FE (receive + extract) on every face; a base endpoint exposes the base FE and is hidden only for a null side
     * (see {@link WirelessPowerRelayBlockEntity#capability}).
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, RELAY_ENTITY.get(), (be, side) -> be.capability(side));
    }
}
