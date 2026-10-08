package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * SUB03 "Submarine Systems" menu: 5 fixed-kind upgrade slots in a row (0 hull, 1 battery, 2 thruster, 3 utility, 4 depth),
 * then the player inventory (4-30) and hotbar (31-39); 1-row chest layout with a status line above the slots
 * (client: SubmarineUpgradeScreen). Valid while the submarine lives, is unmanned and within 8 blocks (vanilla closes
 * the menu and drops clicks once that fails, so boarding closes it).
 */
public class SubmarineUpgradeMenu extends AbstractContainerMenu
{
    public static final int SLOT_X = 44, SLOT_Y = 28, INV_Y = 59, HOTBAR_Y = 117;
    public static final int WIDTH = 176, HEIGHT = 141;

    private final Container container;
    @Nullable
    private final Submarine sub;

    /** Client side (IForgeMenuType): the submarine's entity id in the extra data; a local container filled by the slot sync. */
    public SubmarineUpgradeMenu(int id, Inventory inventory, FriendlyByteBuf buf)
    {
        this(id, inventory, new SimpleContainer(SubmarineUpgrades.SLOTS),
                inventory.player.level().getEntity(buf.readVarInt()) instanceof Submarine s ? s : null);
    }

    public SubmarineUpgradeMenu(int id, Inventory inventory, Container container, @Nullable Submarine sub)
    {
        super(VehicleContent.SUBMARINE_UPGRADE_MENU.get(), id);
        checkContainerSize(container, SubmarineUpgrades.SLOTS);
        this.container = container;
        this.sub = sub;
        container.startOpen(inventory.player);
        for (int i = 0; i < SubmarineUpgrades.SLOTS; i++)
            addSlot(new UpgradeSlot(container, i, SLOT_X + i * 18, SLOT_Y));
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
    }

    @Nullable
    public Submarine submarine()
    {
        return sub;
    }

    /** the hull upgrade cannot come off while the damage is above the plain hull's threshold */
    public boolean hullLocked()
    {
        return sub != null && sub.getDamage() > Submarine.MAX_DAMAGE;
    }

    private final class UpgradeSlot extends Slot
    {
        UpgradeSlot(Container container, int index, int x, int y)
        {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack)
        {
            return SubmarineUpgrades.slotOf(stack) == getContainerSlot();
        }

        @Override
        public int getMaxStackSize()
        {
            return 1;
        }

        @Override
        public boolean mayPickup(Player player)
        {
            if (!container.stillValid(player)) return false;
            if (getContainerSlot() == SubmarineUpgrades.Kind.HULL.slot() && hasItem() && hullLocked())
            {
                if (!player.level().isClientSide)
                    player.displayClientMessage(Component.translatable("message." + Abyssia.MODID + ".submarine.upgrade_hull_damaged"), true);
                return false;
            }
            return true;
        }
    }

    @Override
    public boolean stillValid(Player player)
    {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int n = SubmarineUpgrades.SLOTS;
        if (index < n)
        {
            if (!moveItemStackTo(stack, n, slots.size(), true)) return ItemStack.EMPTY;
        }
        else
        {
            int target = SubmarineUpgrades.slotOf(stack);
            if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        }
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
