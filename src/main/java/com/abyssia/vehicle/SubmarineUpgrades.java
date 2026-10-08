package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * SUB03 submarine upgrades (spec inbox/specs/SUB03-submarine-upgrades.md): four slot-bound items, the "Submarine
 * Systems" menu and the save format. Slot i takes only ITEM_IDS[i] (slot 4, DEPTH, takes the three Depth Hull items
 * submarine_upgrade_depth_mk1..3); the entity saves {@code Upgrades} =
 * {Hull, Battery, Thruster, Utility, Depth} item id strings (empty slots omitted), the submarine item carries the same compound
 * in the abyssia:submarine_upgrades data component. Effects: {@link Submarine} (bit mask {@link #bit}; bits 5-6 = Depth Hull tier).
 * Rated depth (metres, DepthZone): 300 without a DEPTH upgrade, Mk1 600, Mk2 1000, Mk3 2000 (Config SUBMARINE_DEPTH_*);
 * below it the hull takes crush damage once a second (Submarine.crush).
 */
public final class SubmarineUpgrades
{
    public static final int HULL = 0, BATTERY = 1, THRUSTER = 2, SONAR = 3, DEPTH = 4, SLOTS = 5;
    /** bits 5-6 of the synced mask hold the Depth Hull tier (0 none, 1..3 = Mk1..Mk3) */
    public static final int DEPTH_SHIFT = 5;
    public static final int DEPTH_TIERS = 3;
    public static final String[] ITEM_IDS = {"submarine_upgrade_pressure_hull", "submarine_upgrade_high_capacity_battery",
            "submarine_upgrade_maneuver_thruster", "submarine_upgrade_sonar_scanner",
            DepthItem.PREFIX};   // DEPTH: only the prefix of the three Depth Hull ids
    /** save keys per slot (entity NBT "Upgrades" / item component) */
    public static final String[] KEYS = {"Hull", "Battery", "Thruster", "Utility", "Depth"};
    public static final String TAG = "Upgrades";

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Abyssia.MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<SubmarineUpgradeMenu>> MENU = MENUS.register("submarine_upgrades",
            () -> IMenuTypeExtension.create(SubmarineUpgradeMenu::new));

    @SuppressWarnings("unchecked")
    public static final DeferredItem<Item>[] ITEMS = new DeferredItem[SLOTS];

    /** Depth Hull Mk1..Mk3 (index = tier - 1) */
    public static final List<DeferredItem<Item>> DEPTH_ITEMS = new ArrayList<>();

    private SubmarineUpgrades() {}

    public static void register(IEventBus bus)
    {
        MENUS.register(bus);
    }

    public static void registerItems(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab)
    {
        for (int i = 0; i < DEPTH; i++)
        {
            String id = ITEM_IDS[i];
            ITEMS[i] = items.register(id, () -> new UpgradeItem(new Item.Properties().stacksTo(1), id));
            tab.add(ITEMS[i]);
        }
        for (int tier = 1; tier <= DEPTH_TIERS; tier++)
        {
            int t = tier;
            String id = DepthItem.idOf(t);
            DeferredItem<Item> item = items.register(id, () -> new DepthItem(t, new Item.Properties().stacksTo(1), id));
            DEPTH_ITEMS.add(item);
            tab.add(item);
        }
    }

    public static int bit(int slot)
    {
        return 1 << slot;
    }

    /** the slot this stack belongs in, or -1 */
    public static int slotOf(ItemStack stack)
    {
        if (stack.isEmpty()) return -1;
        if (stack.getItem() instanceof DepthItem) return DEPTH;
        for (int i = 0; i < DEPTH; i++)
            if (ITEMS[i] != null && stack.is(ITEMS[i].get())) return i;
        return -1;
    }

    /** bit mask of the installed upgrades */
    public static int mask(Container container)
    {
        int mask = 0;
        for (int i = 0; i < SLOTS; i++)
            if (slotOf(container.getItem(i)) == i) mask |= bit(i);
        if (container.getItem(DEPTH).getItem() instanceof DepthItem depth) mask |= depth.tier << DEPTH_SHIFT;
        return mask;
    }

    /** {Hull, Battery, Thruster, Utility} = item id strings, empty slots omitted */
    public static CompoundTag save(Container container)
    {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < SLOTS; i++)
        {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) tag.putString(KEYS[i], BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return tag;
    }

    /** fills the container from {@link #save}; unknown ids or ids in the wrong slot are skipped */
    public static void load(CompoundTag tag, Container container)
    {
        for (int i = 0; i < SLOTS; i++)
        {
            ItemStack stack = ItemStack.EMPTY;
            if (tag.contains(KEYS[i]))
            {
                ResourceLocation id = ResourceLocation.tryParse(tag.getString(KEYS[i]));
                if (id != null)
                {
                    ItemStack s = new ItemStack(BuiltInRegistries.ITEM.get(id));
                    if (slotOf(s) == i) stack = s;
                }
            }
            container.setItem(i, stack);
        }
    }

    /** bit mask of an {@link #save} compound */
    public static int mask(CompoundTag tag)
    {
        int mask = 0;
        for (int i = 0; i < DEPTH; i++)
            if (tag.getString(KEYS[i]).equals(Abyssia.MODID + ":" + ITEM_IDS[i])) mask |= bit(i);
        int tier = DepthItem.tierOf(tag.getString(KEYS[DEPTH]));
        if (tier > 0) mask |= bit(DEPTH) | tier << DEPTH_SHIFT;
        return mask;
    }

    /** Depth Hull tier in a mask, 0 (none) .. 3 */
    public static int depthTier(int mask)
    {
        return (mask >> DEPTH_SHIFT) & DEPTH_TIERS;
    }

    /** rated depth in metres: crush damage starts below it */
    public static int ratedDepth(int mask)
    {
        return switch (depthTier(mask))
        {
            case 1 -> Config.SUBMARINE_DEPTH_MK1.get();
            case 2 -> Config.SUBMARINE_DEPTH_MK2.get();
            case 3 -> Config.SUBMARINE_DEPTH_MK3.get();
            default -> Config.SUBMARINE_DEPTH_BASE.get();
        };
    }

    /** an upgrade: stack 1, effect + cost lines in the tooltip */
    public static class UpgradeItem extends Item
    {
        private final String id;

        public UpgradeItem(Properties properties, String id)
        {
            super(properties);
            this.id = id;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag)
        {
            lines.add(Component.translatable("tooltip." + Abyssia.MODID + "." + id + ".effect").withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("tooltip." + Abyssia.MODID + "." + id + ".cost").withStyle(ChatFormatting.RED));
        }
    }

    /** Depth Hull Mk{@code tier} (1..3), all in the {@link #DEPTH} slot */
    public static class DepthItem extends UpgradeItem
    {
        public static final String PREFIX = "submarine_upgrade_depth_mk";
        public final int tier;

        public DepthItem(int tier, Properties properties, String id)
        {
            super(properties, id);
            this.tier = tier;
        }

        public static String idOf(int tier)
        {
            return PREFIX + tier;
        }

        /** tier of an item id string ("abyssia:submarine_upgrade_depth_mk2" -> 2), 0 when it is not a Depth Hull */
        public static int tierOf(String id)
        {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            String path = rl == null ? "" : rl.getPath();
            if (!path.startsWith(PREFIX) || path.length() != PREFIX.length() + 1) return 0;
            int tier = path.charAt(PREFIX.length()) - '0';
            return tier >= 1 && tier <= DEPTH_TIERS ? tier : 0;
        }
    }
}
