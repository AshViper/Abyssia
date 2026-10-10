package com.abyssia.client.armor;

import com.abyssia.Abyssia;
import com.abyssia.item.EntryDivingGear;
import com.abyssia.item.MaterialTools;
import com.abyssia.item.ModTools;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

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
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, name), "main");
    }

    private static IClientItemExtensions extensions(int tier)
    {
        return new IClientItemExtensions()
        {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original)
            {
                DivingSuitModel model = tier == 0 ? entryModel : tier == 1 ? deepModel : pressureModel;
                return model != null ? model : original;
            }
        };
    }

    @EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
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

        @SubscribeEvent
        public static void registerExtensions(RegisterClientExtensionsEvent event)
        {
            event.registerItem(extensions(0), EntryDivingGear.HELMET.get(), EntryDivingGear.TANK.get(), EntryDivingGear.FLIPPERS.get());
            event.registerItem(extensions(1), ModTools.DIVER_HELMET.get(), MaterialTools.DIVE_TANK.get(), ModTools.FLIPPERS.get());
            event.registerItem(extensions(2), MaterialTools.PRESSURE_DIVER_HELMET.get(), MaterialTools.PRESSURE_DIVE_TANK.get(),
                    MaterialTools.PRESSURE_FLIPPERS.get());
        }
    }
}
