package com.abyssia.client.armor;

import com.abyssia.Abyssia;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Consumer;

/** Layer definitions, baked models and item extensions for the 3D diving-suit armor (helmet, tank, flippers) of the three tiers. */
public final class DivingSuitClient
{
    public static final ModelLayerLocation ENTRY = layer("diving_suit_entry");
    public static final ModelLayerLocation DEEP = layer("diving_suit_deep");
    public static final ModelLayerLocation PRESSURE = layer("diving_suit_pressure");

    private static DivingSuitModel entryModel, deepModel, pressureModel;

    private DivingSuitClient() {}

    private static ModelLayerLocation layer(String name)
    {
        return new ModelLayerLocation(new ResourceLocation(Abyssia.MODID, name), "main");
    }

    /**
     * Called from Item#initializeClient (client only). That runs inside the Item constructor, before ArmorItem has its
     * material, so the tier (0 entry, 1 deep = abyssal / diving alloy, 2 pressure) is read from the stack at render time.
     */
    public static void init(Consumer<IClientItemExtensions> consumer)
    {
        consumer.accept(EXTENSIONS);
    }

    private static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions()
    {
        @Override
        public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original)
        {
            if (!(stack.getItem() instanceof ArmorItem armor)) return original;
            String name = armor.getMaterial().getName();
            DivingSuitModel model = name.endsWith("entry_diving") ? entryModel : name.endsWith("pressure_alloy") ? pressureModel : deepModel;
            return model != null ? model : original;
        }
    };

    @Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup
    {
        private Setup() {}

        @SubscribeEvent
        public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event)
        {
            event.registerLayerDefinition(ENTRY, DivingSuitMesh::entry);
            event.registerLayerDefinition(DEEP, DivingSuitMesh::deep);
            event.registerLayerDefinition(PRESSURE, DivingSuitMesh::pressure);
        }

        @SubscribeEvent
        public static void bakeModels(EntityRenderersEvent.AddLayers event)
        {
            EntityModelSet models = event.getEntityModels();
            entryModel = new DivingSuitModel(models.bakeLayer(ENTRY));
            deepModel = new DivingSuitModel(models.bakeLayer(DEEP));
            pressureModel = new DivingSuitModel(models.bakeLayer(PRESSURE));
        }
    }
}
