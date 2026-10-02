package com.abyssia.compat.jei;

import com.abyssia.Abyssia;
import com.abyssia.industry.GuiLayout;
import com.abyssia.industry.MachineKind;
import com.abyssia.industry.recipe.ProcessRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One processing machine (crusher, refinery / alloy / high-temperature furnace, selective leaching separator) as a JEI
 * category. It draws the part of the machine's own GUI texture (GuiLayout) that holds its slots and arrow, and shows
 * time and energy under it. Recipes are the {@link ProcessRecipe}s of MachineRecipes: input stacks carry the recipe's
 * count, the reagent slot shows the reagent, and rare outputs show their chance in the tooltip.
 */
public class MachineCategory implements IRecipeCategory<ProcessRecipe>
{
    private static final int MIN_WIDTH = 130, LINE = 10, TEXT_COLOR = 0xFF404040;

    private final MachineKind kind;
    private final RecipeType<ProcessRecipe> type;
    private final Component title;
    private final IDrawable icon;
    private final IDrawableStatic background;
    private final IGuiHelper guiHelper;
    /** arrows by recipe duration, so each one fills in the recipe's own time */
    private final Map<Integer, IDrawableAnimated> arrows = new HashMap<>();
    /** part of the GUI texture drawn as the background: every slot frame and the arrow, plus a small margin */
    private final int cropLeft, cropTop, cropHeight, offsetX, width, height;

    public MachineCategory(IGuiHelper guiHelper, MachineKind kind, ItemLike machine)
    {
        this.guiHelper = guiHelper;
        this.kind = kind;
        this.type = type(kind);
        this.title = Component.translatable("container." + Abyssia.MODID + "." + kind.id);
        this.icon = guiHelper.createDrawableItemLike(machine);
        GuiLayout layout = kind.layout;
        // bounds of slot frames (item at x,y: frame x-1..x+16, big output x-5..x+20) and the arrow
        int x0 = layout.outputX - 5, y0 = layout.outputY - 5, x1 = layout.outputX + 21, y1 = layout.outputY + 21;
        int[][] smalls = slotsOf(layout);
        for (int[] slot : smalls)
        {
            x0 = Math.min(x0, slot[0] - 1);
            y0 = Math.min(y0, slot[1] - 1);
            x1 = Math.max(x1, slot[0] + 17);
            y1 = Math.max(y1, slot[1] + 17);
        }
        y0 = Math.min(y0, layout.arrowY);
        y1 = Math.max(y1, layout.arrowY + GuiLayout.ARROW_H);
        cropLeft = x0 - 4;
        cropTop = y0 - 1;
        cropHeight = y1 + 1 - cropTop;
        int cropWidth = x1 + 4 - cropLeft;
        background = guiHelper.createDrawable(layout.texture, cropLeft, cropTop, cropWidth, cropHeight);
        int textWidth = kind.needsVent() ? Minecraft.getInstance().font.width(Component.translatable("jei.abyssia.needs_vent")) + 4 : 0;
        width = Math.max(Math.max(MIN_WIDTH, cropWidth), textWidth);
        offsetX = (width - cropWidth) / 2;
        height = cropHeight + 2 + LINE * (kind.needsVent() ? 2 : 1);
    }

    public static RecipeType<ProcessRecipe> type(MachineKind kind)
    {
        return RecipeType.create(Abyssia.MODID, kind.id, ProcessRecipe.class);
    }

    @Override
    public RecipeType<ProcessRecipe> getRecipeType()
    {
        return type;
    }

    @Override
    public Component getTitle()
    {
        return title;
    }

    @Override
    public IDrawable getIcon()
    {
        return icon;
    }

    @Override
    public int getWidth()
    {
        return width;
    }

    @Override
    public int getHeight()
    {
        return height;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ProcessRecipe recipe, IFocusGroup focuses)
    {
        GuiLayout layout = kind.layout;
        for (int i = 0; i < recipe.inputs().size() && i < layout.inputs.length; i++)
            builder.addSlot(RecipeIngredientRole.INPUT, x(layout.inputs[i][0]), y(layout.inputs[i][1]))
                    .addItemStacks(withCount(recipe.inputs().get(i), recipe.count()));
        if (kind.reagent && layout.reagentX >= 0)
            builder.addSlot(RecipeIngredientRole.INPUT, x(layout.reagentX), y(layout.reagentY)).addItemStack(kind.reagentStack());
        builder.addSlot(RecipeIngredientRole.OUTPUT, x(layout.outputX), y(layout.outputY)).addItemStack(recipe.result());
        for (int i = 0; i < recipe.rares().size() && i < layout.rares.length; i++)
        {
            ProcessRecipe.Rare rare = recipe.rares().get(i);
            Component chance = Component.translatable("jei.abyssia.chance", String.format("%.0f", rare.chance() * 100))
                    .withStyle(ChatFormatting.GOLD);
            builder.addSlot(RecipeIngredientRole.OUTPUT, x(layout.rares[i][0]), y(layout.rares[i][1]))
                    .addItemStack(rare.stack())
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(chance));
        }
        if (recipe.inputs().size() > 1) builder.setShapeless(width - 10, cropHeight + 1);
    }

    @Override
    public void draw(ProcessRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY)
    {
        GuiLayout layout = kind.layout;
        background.draw(graphics, offsetX, 0);
        arrows.computeIfAbsent(Math.max(1, recipe.ticks()), ticks -> guiHelper
                        .drawableBuilder(layout.texture, GuiLayout.SPRITE_X, GuiLayout.ARROW_V, GuiLayout.ARROW_W, GuiLayout.ARROW_H)
                        .buildAnimated(ticks, IDrawableAnimated.StartDirection.LEFT, false))
                .draw(graphics, x(layout.arrowX), y(layout.arrowY));

        var font = Minecraft.getInstance().font;
        int textY = cropHeight + 2;
        Component process = Component.translatable("jei.abyssia.process",
                String.format("%.1f", recipe.ticks() / 20.0f), String.format("%,d", recipe.energy()));
        graphics.drawString(font, process, (width - font.width(process)) / 2, textY, TEXT_COLOR, false);
        if (kind.needsVent())
        {
            Component vent = Component.translatable("jei.abyssia.needs_vent");
            graphics.drawString(font, vent, (width - font.width(vent)) / 2, textY + LINE, TEXT_COLOR, false);
        }
    }

    private int x(int guiX)
    {
        return guiX - cropLeft + offsetX;
    }

    private int y(int guiY)
    {
        return guiY - cropTop;
    }

    /** Small slots of the layout: inputs, reagent and rare outputs. */
    private static int[][] slotsOf(GuiLayout layout)
    {
        List<int[]> slots = new ArrayList<>(List.of(layout.inputs));
        if (layout.reagentX >= 0) slots.add(new int[] {layout.reagentX, layout.reagentY});
        slots.addAll(List.of(layout.rares));
        return slots.toArray(int[][]::new);
    }

    /** The ingredient's stacks, each holding the recipe's count (concentrate ×3 in the leaching separator). */
    private static List<ItemStack> withCount(Ingredient ingredient, int count)
    {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : ingredient.getItems())
        {
            ItemStack copy = stack.copy();
            copy.setCount(count);
            stacks.add(copy);
        }
        return stacks;
    }
}
