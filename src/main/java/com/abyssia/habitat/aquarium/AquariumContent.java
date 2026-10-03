package com.abyssia.habitat.aquarium;

import com.abyssia.Abyssia;
import com.abyssia.habitat.build.BuildRegistry;
import com.abyssia.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * BT01g aquarium, capture and breeding: own registers (blocks without items / loot, the controller block entity, the
 * two capture canister items) and the build entry. Assets: tools/bt01/aquarium_assets.py.
 */
public final class AquariumContent
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Abyssia.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Abyssia.MODID);

    /** entities the canister can catch */
    public static final TagKey<EntityType<?>> CAPTURABLE =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "aquarium_capturable"));

    /** controller: the floor centre of the tank, holds the creatures */
    public static final RegistryObject<Block> AQUARIUM = BLOCKS.register("aquarium", () -> new AquariumBlock(props().lightLevel(s -> 6)));
    /** every other cell of the tank (frame, glass, floor, water volume) */
    public static final RegistryObject<Block> AQUARIUM_PART = BLOCKS.register("aquarium_part", () -> new AquariumPartBlock(props()
            .lightLevel(s -> s.getValue(AquariumPartBlock.KIND) == AquariumPartBlock.Kind.CORNER ? 7 : 0)));
    public static final RegistryObject<BlockEntityType<AquariumBlockEntity>> AQUARIUM_ENTITY = BLOCK_ENTITIES.register("aquarium",
            () -> BlockEntityType.Builder.of(AquariumBlockEntity::new, AQUARIUM.get()).build(null));

    public static final RegistryObject<Item> CANISTER = ITEMS.register("creature_capture_canister",
            () -> new CreatureCaptureCanisterItem(false, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> CANISTER_FILLED = ITEMS.register("creature_capture_canister_filled",
            () -> new CreatureCaptureCanisterItem(true, new Item.Properties().stacksTo(1)));

    private AquariumContent() {}

    /** BT01g anchor in BuildContent. */
    public static void register(IEventBus bus)
    {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ITEMS.register(bus);
        bus.addListener(AquariumContent::onTabContents);
        MinecraftForge.EVENT_BUS.addListener(CreatureCaptureCanisterItem::onEntityInteract);
        BuildRegistry.register(new AquariumEntry());
    }

    private static void onTabContents(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) event.accept(CANISTER.get());
    }

    private static BlockBehaviour.Properties props()
    {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(5.0f, 6.0f).requiresCorrectToolForDrops()
                .sound(SoundType.METAL).noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)
                .isValidSpawn((s, l, p, e) -> false);
    }
}
