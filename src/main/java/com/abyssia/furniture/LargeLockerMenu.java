package com.abyssia.furniture;

import com.abyssia.registry.ModFurniture;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Large locker menu, side by side: 9 x 9 storage left, player inventory + hotbar right (texture tools/locker_gui.py,
 * 350 x 186, fits a 426 x 240 GUI).
 * Slots: 0-80 locker, 81-107 inventory, 108-116 hotbar. Shift-click works like a chest.
 */
public class LargeLockerMenu extends AbstractContainerMenu
{
    public static final int COLS = 9, ROWS = 9, SLOTS = COLS * ROWS;
    public static final int GRID_X = 8, GRID_Y = 18, INV_X = 181, INV_Y = 30, HOTBAR_Y = 88;
    /** "Inventory" label above the player inventory (right half) */
    public static final int INV_LABEL_X = 181, INV_LABEL_Y = 18;
    public static final int WIDTH = 350, HEIGHT = 186;

    private final Container container;

    /** Client side (IForgeMenuType): an empty local container, filled by the slot sync. */
    public LargeLockerMenu(int id, Inventory inventory, FriendlyByteBuf buf)
    {
        this(id, inventory, new SimpleContainer(SLOTS));
    }

    public LargeLockerMenu(int id, Inventory inventory, Container container)
    {
        super(ModFurniture.LARGE_LOCKER_MENU.get(), id);
        checkContainerSize(container, SLOTS);
        this.container = container;
        container.startOpen(inventory.player);
        for (int row = 0; row < ROWS; row++)
            for (int col = 0; col < COLS; col++)
                addSlot(new Slot(container, col + row * COLS, GRID_X + col * 18, GRID_Y + row * 18));
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, INV_X + col * 18, HOTBAR_Y));
    }

    public Container container()
    {
        return container;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return copy;
        ItemStack stack = slot.getItem();
        copy = stack.copy();
        if (index < SLOTS)
        {
            if (!moveItemStackTo(stack, SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        }
        else if (!moveItemStackTo(stack, 0, SLOTS, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player)
    {
        super.removed(player);
        container.stopOpen(player);
    }
}
