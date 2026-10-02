package com.abyssia.compat.jei;

import com.abyssia.Abyssia;
import com.abyssia.industry.GuiLayout;
import com.abyssia.industry.MachineKind;
import com.abyssia.industry.client.IndustryScreen;
import com.abyssia.industry.recipe.MachineRecipes;
import com.abyssia.registry.ModIndustry;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Optional JEI integration. JEI finds this class through {@link JeiPlugin}; nothing else references it, so without
 * JEI it is never loaded and Abyssia runs unchanged (JEI is compileOnly in build.gradle, mandatory=false in mods.toml).
 * Crafting / smelting / blasting recipes are vanilla types and show up in JEI on their own; the processing machines
 * (MachineRecipes, derived from those at runtime) get one {@link MachineCategory} each.
 */
@JeiPlugin
public class AbyssiaJeiPlugin implements IModPlugin
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "jei");

    @Override
    public ResourceLocation getPluginUid()
    {
        return UID;
    }

    /** The processing machines and their blocks (the generators take fuel or heat, not recipes). */
    private static Map<MachineKind, RegistryObject<Block>> machines()
    {
        Map<MachineKind, RegistryObject<Block>> map = new EnumMap<>(MachineKind.class);
        map.put(MachineKind.CRUSHER, ModIndustry.CRUSHER);
        map.put(MachineKind.REFINERY_FURNACE, ModIndustry.REFINERY_FURNACE);
        map.put(MachineKind.ALLOY_FURNACE, ModIndustry.ALLOY_FURNACE);
        map.put(MachineKind.HIGH_TEMP_FURNACE, ModIndustry.HIGH_TEMP_FURNACE);
        map.put(MachineKind.SELECTIVE_LEACHING_SEPARATOR, ModIndustry.SELECTIVE_LEACHING_SEPARATOR);
        return map;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration)
    {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        machines().forEach((kind, block) -> registration.addRecipeCategories(new MachineCategory(guiHelper, kind, block.get())));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration)
    {
        machines().forEach((kind, block) -> registration.addRecipeCatalyst(block.get(), MachineCategory.type(kind)));
    }

    /** Clicking the progress arrow of a machine GUI opens that machine's recipes. */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration)
    {
        registration.addGuiContainerHandler(IndustryScreen.class, new IGuiContainerHandler<IndustryScreen>()
        {
            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(IndustryScreen screen, double mouseX, double mouseY)
            {
                MachineKind kind = screen.getMenu().kind();
                GuiLayout layout = kind.layout;
                if (!machines().containsKey(kind) || layout.arrowX < 0) return List.of();
                return List.of(IGuiClickableArea.createBasic(layout.arrowX, layout.arrowY, GuiLayout.ARROW_W, GuiLayout.ARROW_H,
                        MachineCategory.type(kind)));
            }
        });
    }

    /** Lang lines (description id + suffix) that become an Abyssia item's JEI info page, in this order. */
    private static final String[] INFO_SUFFIXES = {
            ".source",      // where to find it (plant materials, rare metals)
            ".dropped_by",  // which fauna drop it (tools/gen_fauna.py, from INFO["loot"])
    };

    /** Every Abyssia item with any of the {@link #INFO_SUFFIXES} lines gets them as one JEI info page. */
    @Override
    public void registerRecipes(IRecipeRegistration registration)
    {
        registerMachineRecipes(registration);
        Language language = Language.getInstance();
        int pages = 0;
        for (Item item : ForgeRegistries.ITEMS.getValues())
        {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null || !Abyssia.MODID.equals(id.getNamespace())) continue;
            ItemStack stack = new ItemStack(item);
            List<Component> lines = new ArrayList<>();
            for (String suffix : INFO_SUFFIXES)
            {
                String key = stack.getDescriptionId() + suffix;
                if (language.has(key)) lines.add(Component.translatable(key));
            }
            if (lines.isEmpty()) continue;
            registration.addItemStackInfo(stack, lines.toArray(Component[]::new));
            pages++;
        }
        LOGGER.debug("Abyssia JEI: {} item info pages", pages);
    }

    private static void registerMachineRecipes(IRecipeRegistration registration)
    {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null)
        {
            LOGGER.warn("Abyssia JEI: no client level, machine recipes not shown");
            return;
        }
        // JEI may run before IndustryClient's RecipesUpdatedEvent handler, and the client keeps one RecipeManager
        // across recipe syncs: rebuild so JEI never sees the previous recipe set.
        MachineRecipes.clearCache();
        MachineRecipes recipes = MachineRecipes.get(level);
        int total = 0;
        for (MachineKind kind : machines().keySet())
        {
            var list = recipes.forKind(kind);
            registration.addRecipes(MachineCategory.type(kind), list);
            total += list.size();
        }
        LOGGER.debug("Abyssia JEI: {} machine recipes", total);
    }
}
