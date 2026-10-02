package com.abyssia.industry.menu;

import com.abyssia.industry.GuiLayout;
import com.abyssia.industry.MachineKind;
import com.abyssia.industry.blockentity.IndustryBlockEntity;
import com.abyssia.industry.blockentity.MachineInventory;
import com.abyssia.registry.ModIndustry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.abyssia.industry.blockentity.IndustryBlockEntity.*;

/**
 * One menu type for every industrial block: the kind (sent with the block position when it opens) picks the slots
 * and the GUI layout. Machine slots come first, then the player's inventory and hotbar.
 */
public class IndustryMenu extends AbstractContainerMenu
{
    private final MachineKind kind;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    @Nullable
    private final Block block;

    /** Server side: the block entity's own inventory and data. */
    public IndustryMenu(int id, Inventory inventory, IndustryBlockEntity be)
    {
        this(id, inventory, be.kind(), be.items(), be.data(),
                ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()), be.getBlockState().getBlock());
    }

    /** Client side (IForgeMenuType): position and kind from the open packet, data synced by the server. */
    public IndustryMenu(int id, Inventory inventory, FriendlyByteBuf buf)
    {
        this(id, inventory, readKind(buf), inventory.player.level());
    }

    private IndustryMenu(int id, Inventory inventory, MachineKind kind, Level level)
    {
        this(id, inventory, kind, new MachineInventory(kind, () -> level, () -> {}), new SimpleContainerData(DATA_COUNT),
                ContainerLevelAccess.NULL, null);
    }

    private static MachineKind readKind(FriendlyByteBuf buf)
    {
        buf.readBlockPos();
        return MachineKind.byOrdinal(buf.readByte());
    }

    private IndustryMenu(int id, Inventory inventory, MachineKind kind, MachineInventory items, ContainerData data,
                         ContainerLevelAccess access, @Nullable Block block)
    {
        super(ModIndustry.MACHINE_MENU.get(), id);
        this.kind = kind;
        this.data = data;
        this.access = access;
        this.block = block;

        GuiLayout layout = kind.layout;
        for (int i = 0; i < kind.inputs && i < layout.inputs.length; i++)
            addSlot(new SlotItemHandler(items, i, layout.inputs[i][0], layout.inputs[i][1]));
        if (kind.reagent) addSlot(new SlotItemHandler(items, kind.reagentSlot(), layout.reagentX, layout.reagentY));
        if (kind.hasOutput) addSlot(new OutputSlot(items, kind.outputSlot(), layout.outputX, layout.outputY));
        for (int i = 0; i < kind.outputs - 1 && i < layout.rares.length; i++)
            addSlot(new OutputSlot(items, kind.outputSlot() + 1 + i, layout.rares[i][0], layout.rares[i][1]));

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));

        addDataSlots(data);
    }

    /** Output slot: take only. */
    private static class OutputSlot extends SlotItemHandler
    {
        OutputSlot(MachineInventory items, int index, int x, int y)
        {
            super(items, index, x, y);
        }

        @Override
        public boolean mayPlace(@Nonnull ItemStack stack)
        {
            return false;
        }
    }

    public MachineKind kind()
    {
        return kind;
    }

    private int wide(int index)
    {
        return ((data.get(index + 1) & 0xFFFF) << 16) | (data.get(index) & 0xFFFF);
    }

    public int energy()
    {
        return wide(DATA_ENERGY);
    }

    public int capacity()
    {
        return wide(DATA_CAPACITY);
    }

    public int burn()
    {
        return wide(DATA_BURN);
    }

    public int burnMax()
    {
        return wide(DATA_BURN_MAX);
    }

    public int progress()
    {
        return data.get(DATA_PROGRESS);
    }

    public int maxProgress()
    {
        return data.get(DATA_MAX_PROGRESS);
    }

    public int rate()
    {
        return data.get(DATA_RATE);
    }

    public int status()
    {
        return data.get(DATA_STATUS);
    }

    @Override
    public boolean stillValid(Player player)
    {
        return block == null || stillValid(access, player, block);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = kind.slotCount();
        int playerStart = machineSlots;
        int hotbarStart = playerStart + 27;
        int end = hotbarStart + 9;

        if (index < machineSlots)
        {
            if (!moveItemStackTo(stack, playerStart, end, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, copy);
        }
        else if (kind.insertSlots() == 0 || !moveItemStackTo(stack, 0, kind.insertSlots(), false))
        {
            if (index < hotbarStart)
            {
                if (!moveItemStackTo(stack, hotbarStart, end, false)) return ItemStack.EMPTY;
            }
            else if (!moveItemStackTo(stack, playerStart, hotbarStart, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }
}
