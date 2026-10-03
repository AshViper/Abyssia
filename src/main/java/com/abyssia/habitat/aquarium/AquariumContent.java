package com.abyssia.habitat.aquarium;

import com.abyssia.Abyssia;
import com.abyssia.habitat.build.BuildRegistry;
import com.abyssia.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * BT01g aquarium, capture and breeding: own registers (blocks without items / loot, the controller block entity, the
 * two capture canister items) and the build entry. Assets: tools/bt01/aquarium_assets.py.
 */
public final class AquariumContent
{
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Abyssia.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Abyssia.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Abyssia.MODID);

    /** entities the canister can catch */
    public static final TagKey<EntityType<?>> CAPTURABLE =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "aquarium_capturable"));

    /** controller: the floor centre of the tank, holds the creatures */
    public static final DeferredBlock<Block> AQUARIUM = BLOCKS.register("aquarium", () -> new AquariumBlock(props().lightLevel(s -> 6)));
    /** every other cell of the tank (frame, glass, floor, water volume) */
    public static final DeferredBlock<Block> AQUARIUM_PART = BLOCKS.register("aquarium_part", () -> new AquariumPartBlock(props()
            .lightLevel(s -> s.getValue(AquariumPartBlock.KIND) == AquariumPartBlock.Kind.CORNER ? 7 : 0)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AquariumBlockEntity>> AQUARIUM_ENTITY = BLOCK_ENTITIES.register("aquarium",
            () -> BlockEntityType.Builder.of(AquariumBlockEntity::new, AQUARIUM.get()).build(null));

    public static final DeferredItem<Item> CANISTER = ITEMS.register("creature_capture_canister",
            () -> new CreatureCaptureCanisterItem(false, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> CANISTER_FILLED = ITEMS.register("creature_capture_canister_filled",
            () -> new CreatureCaptureCanisterItem(true, new Item.Properties().stacksTo(1)));

    private AquariumContent() {}

    /** BT01g anchor in BuildContent. */
    public static void register(IEventBus bus)
    {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ITEMS.register(bus);
        bus.addListener(AquariumContent::onTabContents);
        NeoForge.EVENT_BUS.addListener(CreatureCaptureCanisterItem::onEntityInteract);
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
