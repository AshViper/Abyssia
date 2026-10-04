package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * SUB03 "Submarine Systems": the 4 upgrade slots (hull, power, thrust, utility) in a row above the player inventory.
 * Opened by sneak + right-click on an unmanned submarine; valid while the submarine lives, is unmanned and within 8
 * blocks (re-checked on every click; a boarding closes the menu through stillValid). The client gets the entity id in
 * the open buffer and reads energy / hull from the synced entity.
 */
public class SubmarineUpgradeMenu extends AbstractContainerMenu
{
    public static final int SLOT_X = 52, SLOT_Y = 30, INV_Y = 61, HOTBAR_Y = 119;

    @Nullable
    private final Submarine sub;
    private final Container container;

    /** client */
    public SubmarineUpgradeMenu(int id, Inventory inventory, FriendlyByteBuf buf)
    {
        this(id, inventory, inventory.player.level().getEntity(buf.readVarInt()) instanceof Submarine s ? s : null);
    }

    public SubmarineUpgradeMenu(int id, Inventory inventory, @Nullable Submarine sub)
    {
        super(SubmarineUpgrades.MENU.get(), id);
        this.sub = sub;
        this.container = sub != null ? sub.upgrades() : new SimpleContainer(SubmarineUpgrades.SLOTS);
        for (int i = 0; i < SubmarineUpgrades.SLOTS; i++)
        {
            int slot = i;
            addSlot(new Slot(container, i, SLOT_X + i * 18, SLOT_Y)
            {
                @Override
                public boolean mayPlace(ItemStack stack)
                {
                    return SubmarineUpgrades.slotOf(stack) == slot;
                }

                @Override
                public int getMaxStackSize()
                {
                    return 1;
                }

                @Override
                public boolean mayPickup(Player player)
                {
                    return slot != SubmarineUpgrades.HULL || SubmarineUpgradeMenu.this.sub == null
                            || SubmarineUpgradeMenu.this.sub.getDamage() <= Submarine.MAX_DAMAGE;
                }
            });
        }
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

    public static boolean usable(@Nullable Submarine sub, Player player)
    {
        return sub != null && sub.isAlive() && !sub.isRemoved() && !sub.isVehicle() && player.distanceToSqr(sub) <= 64.0;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return usable(sub, player);
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player)
    {
        if (!player.level().isClientSide && !usable(sub, player)) return;
        if (slotId == SubmarineUpgrades.HULL && sub != null && !slots.get(slotId).getItem().isEmpty()
                && sub.getDamage() > Submarine.MAX_DAMAGE && !player.level().isClientSide)
            player.displayClientMessage(Component.translatable("message." + Abyssia.MODID + ".submarine.upgrade_hull_damaged"), true);
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
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

    /** server: opens the menu for this submarine */
    public static void open(Player player, Submarine sub)
    {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> new SubmarineUpgradeMenu(id, inv, sub),
                Component.translatable("container." + Abyssia.MODID + ".submarine.upgrades")), buf -> buf.writeVarInt(sub.getId()));
    }
}
