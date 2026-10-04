package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import com.abyssia.habitat.build.BuildRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * SUB02 submarine (spec inbox/specs/SUB02-submarine-dock.md): the entity, its item, and the moon pool dock block +
 * block entity (built with the habitat constructor: {@link SubmarineDockEntry}). Mesh / textures come from
 * tools/vehicle_model.py; models, recipes and lang from tools/vehicle_assets.py. NeoForge: the dock's FE is a block capability (Forge: BlockEntity#getCapability).
 */
public final class VehicleContent
{
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Abyssia.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Abyssia.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<Submarine>> SUBMARINE = ENTITIES.register("submarine",
            () -> EntityType.Builder.<Submarine>of(Submarine::new, MobCategory.MISC).sized(3.0F, 2.2F)
                    .clientTrackingRange(10).updateInterval(1).build("submarine"));

    public static final DeferredBlock<Block> SUBMARINE_DOCK = BLOCKS.register("submarine_dock",
            () -> new SubmarineDockBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0f, 6.0f)
                    .noLootTable().noOcclusion().sound(SoundType.METAL).lightLevel(s -> 12)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SubmarineDockBlockEntity>> SUBMARINE_DOCK_ENTITY = BLOCK_ENTITIES.register(
            "submarine_dock", () -> BlockEntityType.Builder.of(SubmarineDockBlockEntity::new, SUBMARINE_DOCK.get()).build(null));

    public static DeferredItem<Item> SUBMARINE_ITEM;

    private VehicleContent() {}

    public static void register(IEventBus bus)
    {
        ENTITIES.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(VehicleContent::registerCapabilities);
        // the dock is built with the habitat constructor (no item, no drops), like the charging station
        BuildRegistry.register(new SubmarineDockEntry());
    }

    /** The item in the Abyssia tab (called from ModItems.register like ModHabitat). */
    public static void registerItems(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab)
    {
        SUBMARINE_ITEM = items.register("submarine", () -> new SubmarineItem(new Item.Properties().stacksTo(1)));
        tab.add(SUBMARINE_ITEM);
    }

    /** the dock's buffer takes FE on every face (HabitatPower wireless distribution and cables) */
    private static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SUBMARINE_DOCK_ENTITY.get(), (be, side) -> be.energy());
    }

    /** The pilot breathes inside the hull (both sides, so the air bar never shows). */
    @EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.GAME)
    public static final class Events
    {
        private Events() {}

        @SubscribeEvent
        public static void breathe(LivingBreatheEvent event)
        {
            if (event.getEntity() instanceof Player && event.getEntity().getVehicle() instanceof Submarine)
            {
                event.setCanBreathe(true);
                event.setRefillAirAmount(event.getEntity().getMaxAirSupply());
            }
        }

        @SubscribeEvent
        public static void drown(LivingDrownEvent event)
        {
            if (event.getEntity().getVehicle() instanceof Submarine) event.setCanceled(true);
        }

        /** no suffocation for the pilot either (spec: neither drowning nor suffocation) */
        @SubscribeEvent
        public static void damage(LivingIncomingDamageEvent event)
        {
            if (event.getEntity().getVehicle() instanceof Submarine && event.getSource().is(DamageTypes.IN_WALL)) event.setCanceled(true);
        }
    }
}
