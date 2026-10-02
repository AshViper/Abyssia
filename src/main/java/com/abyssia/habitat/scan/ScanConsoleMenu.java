package com.abyssia.habitat.scan;

import com.abyssia.registry.ModHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Terminal of the scan console: no slots, energy / progress through ContainerData, actions through menu buttons
 * ({@link #BUTTON_SCAN}, {@link #BUTTON_ALL}, {@link #BUTTON_KIND} + palette index of the last result).
 */
public class ScanConsoleMenu extends AbstractContainerMenu
{
    public static final int BUTTON_SCAN = 0, BUTTON_ALL = 1, BUTTON_KIND = 2;

    private final BlockPos pos;
    private final ContainerData data;

    public ScanConsoleMenu(int id, BlockPos pos, ContainerData data)
    {
        super(ModHabitat.SCAN_CONSOLE_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);
    }

    /** client side */
    public ScanConsoleMenu(int id, Inventory inventory, FriendlyByteBuf buf)
    {
        this(id, buf.readBlockPos(), new SimpleContainerData(ScanConsoleBlockEntity.DATA_COUNT));
    }

    public BlockPos pos()
    {
        return pos;
    }

    public int energy()
    {
        return (data.get(ScanConsoleBlockEntity.DATA_ENERGY) & 0xFFFF) | (data.get(ScanConsoleBlockEntity.DATA_ENERGY + 1) & 0xFFFF) << 16;
    }

    public int progress()
    {
        return data.get(ScanConsoleBlockEntity.DATA_PROGRESS);
    }

    public boolean scanning()
    {
        return data.get(ScanConsoleBlockEntity.DATA_SCANNING) != 0;
    }

    @Nullable
    public ScanConsoleBlockEntity console(Player player)
    {
        return player.level().getBlockEntity(pos) instanceof ScanConsoleBlockEntity be ? be : null;
    }

    @Override
    public boolean clickMenuButton(Player player, int id)
    {
        ScanConsoleBlockEntity be = console(player);
        if (be == null) return false;
        if (id == BUTTON_SCAN) return be.startScan();
        if (id == BUTTON_ALL)
        {
            be.setTarget(null);
            return true;
        }
        int kind = id - BUTTON_KIND;
        if (kind < 0 || kind >= be.result().palette.size()) return false;
        be.setTarget(be.result().palette.get(kind));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return player.level().getBlockState(pos).is(ModHabitat.SCAN_CONSOLE.get())
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
