package com.abyssia.furniture;

import com.abyssia.registry.ModFurniture;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Hydro planter menu (feature PL01): the seedling slot (slot 0) and the player inventory (texture
 * tools/planter_gui.py, 176 x 166 on a 256 x 256 sheet). Data: growth ticks and total ticks for the progress arrow.
 */
public class HydroPlanterMenu extends AbstractContainerMenu
{
    public static final int SEED_X = 44, SEED_Y = 35, ARROW_X = 70, ARROW_Y = 34, ARROW_W = 24, ARROW_H = 17;
    public static final int SPRITE_X = 176, ARROW_V = 14;
    public static final int INV_START = 1, HOTBAR_START = 28, END = 37;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Client side (IMenuTypeExtension): a local slot, data synced by the server. */
    public HydroPlanterMenu(int id, Inventory inventory, FriendlyByteBuf buf)
    {
        this(id, inventory, new ItemStackHandler(1), new SimpleContainerData(HydroPlanterBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL);
    }

    public HydroPlanterMenu(int id, Inventory inventory, IItemHandler seed, ContainerData data, ContainerLevelAccess access)
    {
        super(ModFurniture.HYDRO_PLANTER_MENU.get(), id);
        this.access = access;
        this.data = data;
        addSlot(new SlotItemHandler(seed, 0, SEED_X, SEED_Y)
        {
            @Override
            public int getMaxStackSize()
            {
                return 1;
            }

            @Override
            public int getMaxStackSize(ItemStack stack)
            {
                return 1;
            }

            @Override
            public boolean mayPlace(ItemStack stack)
            {
                return PlanterCrop.of(stack) != PlanterCrop.NONE && super.mayPlace(stack);
            }
        });
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        addDataSlots(data);
    }

    public int growth()
    {
        return data.get(HydroPlanterBlockEntity.DATA_GROWTH);
    }

    public int total()
    {
        return data.get(HydroPlanterBlockEntity.DATA_TOTAL);
    }

    @Override
    public boolean stillValid(Player player)
    {
        return stillValid(access, player, ModFurniture.HYDRO_PLANTER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return copy;
        ItemStack stack = slot.getItem();
        copy = stack.copy();
        if (index == 0)
        {
            if (!moveItemStackTo(stack, INV_START, END, true)) return ItemStack.EMPTY;
        }
        else if (!moveItemStackTo(stack, 0, 1, false))
        {
            if (index < HOTBAR_START)
            {
                if (!moveItemStackTo(stack, HOTBAR_START, END, false)) return ItemStack.EMPTY;
            }
            else if (!moveItemStackTo(stack, INV_START, HOTBAR_START, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }
}
