package com.abyssia.industry.recipe;

import com.abyssia.Abyssia;
import com.abyssia.industry.MachineKind;
import com.abyssia.item.MaterialTools;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Machine processing, derived at runtime from the loaded vanilla recipes of namespace abyssia (no recipe JSON type
 * of its own, so datapack edits carry over and /reload rebuilds it: one instance per RecipeManager, weakly cached).
 * <ul>
 *   <li>crusher: shapeless crushing-hammer recipes with exactly 2 ingredients, output +1</li>
 *   <li>refinery furnace: blasting powder -> ingot (60 t, 2,400 FE); concentrate -> (powder) -> ingot (100 t, 4,000 FE)</li>
 *   <li>alloy furnace: fixed alloy table ({@link #ALLOY_FURNACE_ALLOYS}, no crafting-table recipe), 3 per craft (120 t, 6,000 FE)</li>
 *   <li>high-temperature furnace: tungsten powder -> ingot (60 t, 4,000 FE), thermal / tungsten alloy x3 (100 t, 8,000 FE)</li>
 *   <li>selective leaching separator: fixed table of spec I02 ({@link #leaching()}), not derived from recipes</li>
 * </ul>
 */
public final class MachineRecipes
{
    private static final Map<RecipeManager, MachineRecipes> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    /** alloy: result id, then its ingredient ids (no crafting-table recipe; each makes 2, the furnace gives x1.5) */
    private static final String[][] ALLOY_FURNACE_ALLOYS = {
            {"abyssal_alloy_ingot", "vanadium_ingot", "cobalt_ingot", "nickel_ingot"},
            {"corrosion_alloy_ingot", "nickel_ingot", "cobalt_powder", "zinc_ingot"},
            {"high_strength_alloy_ingot", "manganese_ingot", "vanadium_powder", "titanium_ingot"},
            {"heat_resistant_alloy_ingot", "molybdenum_ingot", "nickel_powder", "iron_powder"},
            {"conductive_alloy_ingot", "minecraft:copper_ingot", "tellurium_powder"},
            {"thermal_alloy_ingot", "thermal_reagent", "molybdenum_ingot", "tungsten_powder", "thorium_ingot"}};
    private static final String[][] HIGH_TEMP_ALLOYS = {
            {"thermal_alloy_ingot", "thermal_reagent", "molybdenum_ingot", "tungsten_powder", "thorium_ingot"},
            {"tungsten_alloy_ingot", "tungsten_ingot", "nickel_powder"}};
    /** powder smelted only by the high-temperature furnace */
    private static final String HIGH_TEMP_INGOT = "tungsten_ingot";

    private final Map<MachineKind, List<ProcessRecipe>> recipes = new EnumMap<>(MachineKind.class);

    public static MachineRecipes get(Level level)
    {
        RecipeManager manager = level.getRecipeManager();
        synchronized (CACHE)
        {
            return CACHE.computeIfAbsent(manager, m -> new MachineRecipes(m, level.registryAccess()));
        }
    }

    /** Client: the recipe manager instance is reused when the server sends new recipes. */
    public static void clearCache()
    {
        CACHE.clear();
    }

    private MachineRecipes(RecipeManager manager, RegistryAccess access)
    {
        List<ProcessRecipe> crusher = new ArrayList<>();
        List<ProcessRecipe> refinery = new ArrayList<>();
        List<ProcessRecipe> alloy = new ArrayList<>();
        List<ProcessRecipe> highTemp = new ArrayList<>();
        ItemStack hammer = new ItemStack(MaterialTools.CRUSHING_HAMMER.get());

        for (RecipeHolder<CraftingRecipe> holder : manager.getAllRecipesFor(RecipeType.CRAFTING))
        {
            if (!isOurs(holder.id())) continue;
            CraftingRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(access);
            if (result.isEmpty()) continue;
            List<Ingredient> ings = nonEmpty(recipe.getIngredients());

            if (ings.size() == 2)
            {
                int hammerAt = ings.get(0).test(hammer) ? 0 : ings.get(1).test(hammer) ? 1 : -1;
                Ingredient other = hammerAt < 0 ? null : ings.get(1 - hammerAt);
                if (other != null && !other.test(hammer))
                    crusher.add(new ProcessRecipe(List.of(other), withCount(result, result.getCount() + 1), 80, 3_200));
            }
        }

        addAlloys(alloy, ALLOY_FURNACE_ALLOYS, 120, 6_000);
        addAlloys(highTemp, HIGH_TEMP_ALLOYS, 100, 8_000);

        List<BlastingRecipe> blasting = new ArrayList<>();
        for (RecipeHolder<BlastingRecipe> holder : manager.getAllRecipesFor(RecipeType.BLASTING))
            if (isOurs(holder.id()) && holder.value().getIngredients().size() == 1) blasting.add(holder.value());
        // powder -> ingot
        List<BlastingRecipe> powderToIngot = new ArrayList<>();
        for (BlastingRecipe recipe : blasting)
        {
            ItemStack result = recipe.getResultItem(access);
            if (!result.isEmpty() && path(result.getItem()).endsWith("_ingot") && allEndWith(recipe.getIngredients().get(0), "_powder"))
            {
                powderToIngot.add(recipe);
                boolean hot = path(result.getItem()).equals(HIGH_TEMP_INGOT);
                if (hot) highTemp.add(new ProcessRecipe(List.of(recipe.getIngredients().get(0)), result.copy(), 60, 4_000));
                else refinery.add(new ProcessRecipe(List.of(recipe.getIngredients().get(0)), result.copy(), 60, 2_400));
            }
        }
        // concentrate -> powder -> ingot in one step
        for (BlastingRecipe recipe : blasting)
        {
            ItemStack powder = recipe.getResultItem(access);
            if (powder.isEmpty() || !path(powder.getItem()).endsWith("_powder")) continue;
            if (!allEndWith(recipe.getIngredients().get(0), "_concentrate")) continue;
            for (BlastingRecipe smelt : powderToIngot)
            {
                if (!smelt.getIngredients().get(0).test(powder)) continue;
                ItemStack ingot = smelt.getResultItem(access);
                if (path(ingot.getItem()).equals(HIGH_TEMP_INGOT)) break;
                refinery.add(new ProcessRecipe(List.of(recipe.getIngredients().get(0)), withCount(ingot, 1), 100, 4_000));
                break;
            }
        }

        recipes.put(MachineKind.CRUSHER, List.copyOf(crusher));
        recipes.put(MachineKind.REFINERY_FURNACE, List.copyOf(refinery));
        recipes.put(MachineKind.ALLOY_FURNACE, List.copyOf(alloy));
        recipes.put(MachineKind.HIGH_TEMP_FURNACE, List.copyOf(highTemp));
        recipes.put(MachineKind.SELECTIVE_LEACHING_SEPARATOR, leaching());
    }

    private static void addAlloys(List<ProcessRecipe> out, String[][] table, int ticks, int fe)
    {
        for (String[] row : table)
        {
            Item made = item(row[0]);
            List<Ingredient> ings = new ArrayList<>();
            for (int i = 1; i < row.length; i++)
            {
                Item in = item(row[i]);
                if (in != Items.AIR) ings.add(Ingredient.of(in));
            }
            if (made != Items.AIR && ings.size() == row.length - 1) out.add(new ProcessRecipe(ings, new ItemStack(made, 3), ticks, fe));
        }
    }

    /**
     * Spec I02: concentrate x3 (40 t, 4,000 FE) -> powder x3
     * plus rare raw metals, each rolled on its own. The reagent is paid by the machine.
     */
    private static List<ProcessRecipe> leaching()
    {
        List<ProcessRecipe> out = new ArrayList<>();
        // ECO02: every metal is its own deposit, so the separator no longer rolls rare raw metals
        List<ProcessRecipe.Rare> cobalt = List.of();
        List<ProcessRecipe.Rare> manganese = List.of();
        List<ProcessRecipe.Rare> nickel = List.of();
        leach(out, "cobalt_concentrate", 3, "cobalt_powder", 3, cobalt);
        leach(out, "manganese_concentrate", 3, "manganese_powder", 3, manganese);
        leach(out, "nickel_concentrate", 3, "nickel_powder", 3, nickel);
        return List.copyOf(out);
    }

    private static void leach(List<ProcessRecipe> out, String input, int count, String result, int resultCount, List<ProcessRecipe.Rare> rares)
    {
        Item in = item(input);
        Item made = item(result);
        if (in == Items.AIR || made == Items.AIR) return;
        boolean crust = count == 1;
        out.add(new ProcessRecipe(List.of(Ingredient.of(in)), count, new ItemStack(made, resultCount), rares,
                crust ? 60 : 40, crust ? 6_000 : 4_000));
    }

    /** Pairs of item id and chance. */
    private static List<ProcessRecipe.Rare> rares(Object... idAndChance)
    {
        List<ProcessRecipe.Rare> out = new ArrayList<>();
        for (int i = 0; i + 1 < idAndChance.length; i += 2)
        {
            Item item = item((String) idAndChance[i]);
            if (item != Items.AIR) out.add(new ProcessRecipe.Rare(new ItemStack(item), (Float) idAndChance[i + 1]));
        }
        return List.copyOf(out);
    }

    /** Item by id ("minecraft:..." or a bare abyssia path); AIR if missing. */
    private static Item item(String id)
    {
        ResourceLocation key = id.indexOf(':') >= 0 ? ResourceLocation.tryParse(id) : ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, id);
        return key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
    }

    public List<ProcessRecipe> forKind(MachineKind kind)
    {
        return recipes.getOrDefault(kind, List.of());
    }

    /** First recipe of the machine whose inputs are exactly the non-empty stacks given. */
    @Nullable
    public ProcessRecipe find(MachineKind kind, List<ItemStack> inputs)
    {
        for (ProcessRecipe recipe : forKind(kind))
            if (recipe.matches(inputs)) return recipe;
        return null;
    }

    /** Whether some recipe of the machine takes this item. */
    public boolean isInput(MachineKind kind, ItemStack stack)
    {
        for (ProcessRecipe recipe : forKind(kind))
            for (Ingredient ing : recipe.inputs())
                if (ing.test(stack)) return true;
        return false;
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isOurs(ResourceLocation id)
    {
        return id != null && Abyssia.MODID.equals(id.getNamespace());
    }

    private static List<Ingredient> nonEmpty(List<Ingredient> ingredients)
    {
        List<Ingredient> out = new ArrayList<>();
        for (Ingredient ing : ingredients)
            if (!ing.isEmpty()) out.add(ing);
        return out;
    }

    private static boolean allEndWith(Ingredient ing, String suffix)
    {
        ItemStack[] items = ing.getItems();
        if (items.length == 0) return false;
        for (ItemStack stack : items)
            if (!path(stack.getItem()).endsWith(suffix)) return false;
        return true;
    }

    private static String path(Item item)
    {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    private static ItemStack withCount(ItemStack stack, int count)
    {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }
}
