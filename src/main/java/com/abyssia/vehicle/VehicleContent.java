package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import com.abyssia.habitat.build.BuildRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.entity.living.LivingBreatheEvent;
import net.minecraftforge.event.entity.living.LivingDrownEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * SUB02 submarine (spec inbox/specs/SUB02-submarine-dock.md): the entity, its item, and the moon pool dock block +
 * block entity (built with the habitat constructor: {@link SubmarineDockEntry}). Mesh / textures come from tools/vehicle_model.py; models, recipes, loot and lang from
 * tools/vehicle_assets.py.
 */
public final class VehicleContent
{
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Abyssia.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Abyssia.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Abyssia.MODID);

    public static final RegistryObject<EntityType<Submarine>> SUBMARINE = ENTITIES.register("submarine",
            () -> EntityType.Builder.<Submarine>of(Submarine::new, MobCategory.MISC).sized(3.0F, 2.2F)
                    .clientTrackingRange(10).updateInterval(1).build("submarine"));

    public static final RegistryObject<Block> SUBMARINE_DOCK = BLOCKS.register("submarine_dock",
            () -> new SubmarineDockBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0f, 6.0f)
                    .noLootTable().noOcclusion().sound(SoundType.METAL).lightLevel(s -> 12)));
    /** SUB04 invisible step-collision helper under the dock gangway (placed by the dock block entity; no item / drops) */
    public static final RegistryObject<Block> SUBMARINE_DOCK_GANGWAY = BLOCKS.register("submarine_dock_gangway",
            () -> new SubmarineDockGangwayBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(-1.0f, 3600000.0f)
                    .noLootTable().noOcclusion().sound(SoundType.METAL)));
    public static final RegistryObject<BlockEntityType<SubmarineDockBlockEntity>> SUBMARINE_DOCK_ENTITY = BLOCK_ENTITIES.register("submarine_dock",
            () -> BlockEntityType.Builder.of(SubmarineDockBlockEntity::new, SUBMARINE_DOCK.get()).build(null));

    /** SUB03 "Submarine Systems" upgrade screen (sneak + right-click an unmanned submarine) */
    public static final RegistryObject<MenuType<SubmarineUpgradeMenu>> SUBMARINE_UPGRADE_MENU = MENUS.register("submarine_upgrades",
            () -> IForgeMenuType.create(SubmarineUpgradeMenu::new));

    public static RegistryObject<Item> SUBMARINE_ITEM;
    /** SUB03 upgrade items, one per {@link SubmarineUpgrades.Kind} */
    public static final Map<SubmarineUpgrades.Kind, RegistryObject<Item>> UPGRADE_ITEMS = new EnumMap<>(SubmarineUpgrades.Kind.class);

    private VehicleContent() {}

    public static void register(IEventBus bus)
    {
        ENTITIES.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        // the dock is built with the habitat constructor (no item, no drops), like the charging station
        BuildRegistry.register(new SubmarineDockEntry());
    }

    /** The item in the Abyssia tab (called from ModItems.register like ModHabitat). */
    public static void registerItems(DeferredRegister<Item> items, List<RegistryObject<? extends Item>> tab)
    {
        SUBMARINE_ITEM = items.register("submarine", () -> new SubmarineItem(new Item.Properties().stacksTo(1)));
        tab.add(SUBMARINE_ITEM);
        for (SubmarineUpgrades.Kind kind : SubmarineUpgrades.Kind.values())
        {
            RegistryObject<Item> item = items.register(kind.id, () -> new SubmarineUpgrades.UpgradeItem(kind, new Item.Properties().stacksTo(1)));
            UPGRADE_ITEMS.put(kind, item);
            tab.add(item);
        }
    }

    /** The pilot breathes inside the hull (both sides, so the air bar never shows). */
    @Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events
    {
        private Events() {}

        @SubscribeEvent
        public static void breathe(LivingBreatheEvent event)
        {
            if (event.getEntity() instanceof Player && event.getEntity().getVehicle() instanceof Submarine)
            {
                event.setCanBreathe(true);
                event.setCanRefillAir(true);
                event.setRefillAirAmount(event.getEntity().getMaxAirSupply());
            }
        }

        @SubscribeEvent
        public static void drown(LivingDrownEvent event)
        {
            if (event.getEntity().getVehicle() instanceof Submarine) event.setCanceled(true);
        }
    }
}
