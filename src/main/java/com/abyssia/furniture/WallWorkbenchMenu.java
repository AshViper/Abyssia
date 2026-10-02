package com.abyssia.furniture;

import com.abyssia.registry.ModFurniture;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.Optional;

import static com.abyssia.furniture.WallWorkbenchBlockEntity.DATA_CAPACITY;
import static com.abyssia.furniture.WallWorkbenchBlockEntity.DATA_COUNT;
import static com.abyssia.furniture.WallWorkbenchBlockEntity.DATA_ENERGY;

/**
 * Wall workbench menu: the vanilla crafting table (same slot positions, normal crafting recipes, the grid goes back to
 * the player on close) plus the charge slot right of the result and the FE data for the gauge.
 * Slots: 0 result, 1-9 grid, 10 charge, 11-37 inventory, 38-46 hotbar.
 */
public class WallWorkbenchMenu extends AbstractContainerMenu
{
    public static final int RESULT = 0, GRID_START = 1, CHARGE = 10, INV_START = 11, HOTBAR_START = 38, END = 47;
    public static final int CHARGE_X = 152, CHARGE_Y = 53;

    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final Player player;

    /** Client side (IForgeMenuType): a local charge slot, data synced by the server. */
    public WallWorkbenchMenu(int id, Inventory inventory, FriendlyByteBuf buf)
    {
        this(id, inventory, new ChargeSlotHandler(() -> {}), new SimpleContainerData(DATA_COUNT), ContainerLevelAccess.NULL);
    }

    public WallWorkbenchMenu(int id, Inventory inventory, IItemHandler charge, ContainerData data, ContainerLevelAccess access)
    {
        super(ModFurniture.WALL_WORKBENCH_MENU.get(), id);
        this.access = access;
        this.data = data;
        this.player = inventory.player;

        addSlot(new ResultSlot(inventory.player, craftSlots, resultSlots, 0, 124, 35));
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++)
                addSlot(new Slot(craftSlots, col + row * 3, 30 + col * 18, 17 + row * 18));
        addSlot(new SlotItemHandler(charge, 0, CHARGE_X, CHARGE_Y));
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        addDataSlots(data);
    }

    public int energy()
    {
        return (data.get(DATA_ENERGY) & 0xFFFF) | (data.get(DATA_ENERGY + 1) & 0xFFFF) << 16;
    }

    public int capacity()
    {
        return (data.get(DATA_CAPACITY) & 0xFFFF) | (data.get(DATA_CAPACITY + 1) & 0xFFFF) << 16;
    }

    // ---------------------------------------------------------------- crafting (as vanilla CraftingMenu)

    @Override
    public void slotsChanged(Container container)
    {
        if (container == craftSlots) access.execute((level, pos) -> updateResult(level));
    }

    private void updateResult(Level level)
    {
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer) || level.getServer() == null) return;
        ItemStack result = ItemStack.EMPTY;
        Optional<CraftingRecipe> recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftSlots, level);
        if (recipe.isPresent() && resultSlots.setRecipeUsed(level, serverPlayer, recipe.get()))
        {
            ItemStack crafted = recipe.get().assemble(craftSlots, level.registryAccess());
            if (crafted.isItemEnabled(level.enabledFeatures())) result = crafted;
        }
        resultSlots.setItem(0, result);
        setRemoteSlot(RESULT, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT, result));
    }

    @Override
    public void removed(Player player)
    {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, craftSlots));
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot)
    {
        return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean stillValid(Player player)
    {
        return stillValid(access, player, ModFurniture.WALL_WORKBENCH.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return copy;
        ItemStack stack = slot.getItem();
        copy = stack.copy();
        if (index == RESULT)
        {
            access.execute((level, pos) -> stack.getItem().onCraftedBy(stack, level, player));
            if (!moveItemStackTo(stack, INV_START, END, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, copy);
        }
        else if (index >= INV_START)
        {
            boolean moved = ChargeSlotHandler.chargeable(stack) && moveItemStackTo(stack, CHARGE, CHARGE + 1, false);
            if (!moved && !moveItemStackTo(stack, GRID_START, CHARGE, false))
            {
                if (index < HOTBAR_START)
                {
                    if (!moveItemStackTo(stack, HOTBAR_START, END, false)) return ItemStack.EMPTY;
                }
                else if (!moveItemStackTo(stack, INV_START, HOTBAR_START, false)) return ItemStack.EMPTY;
            }
        }
        else if (!moveItemStackTo(stack, INV_START, END, false)) return ItemStack.EMPTY;

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        if (index == RESULT) player.drop(stack, false);
        return copy;
    }
}
