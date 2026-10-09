package com.abyssia.compat.jei;

import com.abyssia.Config;
import com.abyssia.industry.MachineKind;
import com.abyssia.industry.recipe.ProcessRecipe;
import com.abyssia.research.ClientResearch;
import com.abyssia.research.ResearchManager;
import com.abyssia.research.gate.MachineGate;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * AB05: hides, in JEI only, the recipes the local player has not unlocked yet: recipes whose result item is gated by an
 * {@code abyssia:recipe/<item path>} key, and the recipes of a machine category whose machine block is locked. Re-evaluated
 * whenever the research state arrives (ClientResearch refresh). Nothing is enforced here; config {@code research.jei_hide_locked}.
 */
final class LockedRecipeFilter
{
    private static IJeiRuntime runtime;
    private static final Map<RecipeType<?>, List<?>> HIDDEN = new HashMap<>();
    private static boolean listening;

    private LockedRecipeFilter() {}

    static void start(IJeiRuntime jei)
    {
        runtime = jei;
        HIDDEN.clear();
        if (!listening)
        {
            listening = true;
            ClientResearch.addRefreshListener(LockedRecipeFilter::refresh);
        }
        refresh();
    }

    static void stop()
    {
        runtime = null;
        HIDDEN.clear();
    }

    static void refresh()
    {
        IJeiRuntime jei = runtime;
        if (jei == null) return;
        IRecipeManager manager = jei.getRecipeManager();
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        ClientLevel level = mc.level;
        boolean hide = player != null && level != null && Config.RESEARCH_JEI_HIDE_LOCKED.get();
        RegistryAccess access = level == null ? null : level.registryAccess();
        vanilla(manager, RecipeTypes.CRAFTING, hide, player, access);
        vanilla(manager, RecipeTypes.SMELTING, hide, player, access);
        vanilla(manager, RecipeTypes.BLASTING, hide, player, access);
        vanilla(manager, RecipeTypes.SMOKING, hide, player, access);
        vanilla(manager, RecipeTypes.CAMPFIRE_COOKING, hide, player, access);
        vanilla(manager, RecipeTypes.STONECUTTING, hide, player, access);
        vanilla(manager, RecipeTypes.SMITHING, hide, player, access);
        for (var entry : AbyssiaJeiPlugin.machines().entrySet())
        {
            MachineKind kind = entry.getKey();
            boolean machineLocked = hide && MachineGate.lockedBy(player, entry.getValue().get()).isPresent();
            apply(manager, MachineCategory.type(kind), hide,
                    (ProcessRecipe r) -> machineLocked || resultLocked(player, r.result()));
        }
    }

    private static <R extends Recipe<?>> void vanilla(IRecipeManager manager, RecipeType<R> type, boolean hide,
                                                      Player player, RegistryAccess access)
    {
        apply(manager, type, hide, recipe -> resultLocked(player, recipe.getResultItem(access)));
    }

    @SuppressWarnings("unchecked")
    private static <T> void apply(IRecipeManager manager, RecipeType<T> type, boolean hide, Predicate<T> locked)
    {
        List<T> previous = (List<T>) HIDDEN.remove(type);
        if (previous != null && !previous.isEmpty()) manager.unhideRecipes(type, previous);
        if (!hide) return;
        List<T> now = manager.createRecipeLookup(type).includeHidden().get().filter(locked).toList();
        if (now.isEmpty()) return;
        manager.hideRecipes(type, now);
        HIDDEN.put(type, now);
    }

    private static boolean resultLocked(Player player, ItemStack result)
    {
        if (result.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(result.getItem());
        return !ResearchManager.isKeyUnlocked(player, ResearchManager.key("recipe", id.getPath()));
    }
}
