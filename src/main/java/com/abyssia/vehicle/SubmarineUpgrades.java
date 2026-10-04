package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * SUB03 submarine upgrades (spec inbox/specs/SUB03-submarine-upgrades.md). Port reference (keep identical on NeoForge):
 * <pre>
 * slot  kind      item id                                         NBT key   bit  slot-hint lang suffix
 * 0     HULL      abyssia:submarine_upgrade_pressure_hull         Hull      1    hull
 * 1     BATTERY   abyssia:submarine_upgrade_high_capacity_battery Battery   2    battery
 * 2     THRUSTER  abyssia:submarine_upgrade_maneuver_thruster     Thruster  4    thruster
 * 3     UTILITY   abyssia:submarine_upgrade_sonar_scanner         Utility   8    utility
 *
 * Entity NBT:  "Upgrades" = {Hull:"abyssia:...", Battery:..., Thruster:..., Utility:...}  (item id strings, empty slots omitted)
 * Item NBT:    "Upgrades" (same form) + "Energy" (int) on the submarine item (NeoForge: data components); hull damage is
 *              NOT kept (a placed submarine starts at 0 damage, as in SUB02)
 * Synced:      Submarine DATA_UPGRADES (byte bit mask above), set by the server, read by the pilot's client for speeds
 * Menu:        menu type abyssia:submarine_upgrades, extra data = entity id (varint); slots 0-3 upgrades, 4-30 inventory, 31-39 hotbar
 * Lang:        container.abyssia.submarine.upgrades (title), container.abyssia.submarine.upgrades.status (%s energy, %s cap, %s hull %),
 *              container.abyssia.submarine.upgrades.slot.&lt;suffix&gt;, tooltip.abyssia.&lt;item id path&gt;.effect / .cost,
 *              tooltip.abyssia.submarine_upgrade.battery_warning (%s FE), tooltip.abyssia.submarine.upgrade (%s name),
 *              message.abyssia.submarine.upgrade_hull_damaged, message.abyssia.submarine.sonar.* (see vehicle_assets.py)
 * Config:      [submarine.upgrades] in abyssia-common.toml (Config.SUBMARINE_HULL_* ... SUBMARINE_SONAR_*)
 * </pre>
 * Speed order (fixed): base (Config max_speed / 0.22 / 0.18) -> thruster replaces (0.56 / 0.30 / 0.23) -> x hull 0.92
 * -> (vertical only) x battery 0.95. Thrust FE is flat per moving tick: 8 (Config thrust_fe_per_tick) or 12 with the thruster.
 */
public final class SubmarineUpgrades
{
    public static final String TAG = "Upgrades";
    public static final int SLOTS = 4;

    public enum Kind
    {
        HULL("submarine_upgrade_pressure_hull", "Hull", "hull"),
        BATTERY("submarine_upgrade_high_capacity_battery", "Battery", "battery"),
        THRUSTER("submarine_upgrade_maneuver_thruster", "Thruster", "thruster"),
        UTILITY("submarine_upgrade_sonar_scanner", "Utility", "utility");

        public final String id, key, slotName;
        public final int bit;

        Kind(String id, String key, String slotName)
        {
            this.id = id;
            this.key = key;
            this.slotName = slotName;
            this.bit = 1 << ordinal();
        }

        public int slot()
        {
            return ordinal();
        }

        public ResourceLocation itemId()
        {
            return ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, id);
        }

        public boolean in(int mask)
        {
            return (mask & bit) != 0;
        }
    }

    private SubmarineUpgrades() {}

    /** the kind of an upgrade item, or null */
    @Nullable
    public static Kind kindOf(ItemStack stack)
    {
        return stack.getItem() instanceof UpgradeItem item ? item.kind : null;
    }

    /** the only slot {@code stack} fits, or -1 */
    public static int slotOf(ItemStack stack)
    {
        Kind kind = kindOf(stack);
        return kind == null ? -1 : kind.slot();
    }

    public static int maskOf(Container upgrades)
    {
        int mask = 0;
        for (Kind kind : Kind.values())
            if (!upgrades.getItem(kind.slot()).isEmpty()) mask |= kind.bit;
        return mask;
    }

    /** the "Upgrades" compound for a 4-slot container (item id strings, empty slots omitted) */
    public static CompoundTag write(Container upgrades)
    {
        CompoundTag tag = new CompoundTag();
        for (Kind kind : Kind.values())
        {
            ItemStack stack = upgrades.getItem(kind.slot());
            if (!stack.isEmpty()) tag.putString(kind.key, kind.itemId().toString());
        }
        return tag;
    }

    /** fills a 4-slot container from an "Upgrades" compound (unknown / mismatched ids are skipped) */
    public static void read(CompoundTag tag, Container upgrades)
    {
        for (Kind kind : Kind.values())
        {
            ItemStack stack = ItemStack.EMPTY;
            if (tag.contains(kind.key))
            {
                ResourceLocation id = ResourceLocation.tryParse(tag.getString(kind.key));
                Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
                if (item instanceof UpgradeItem upgrade && upgrade.kind == kind) stack = new ItemStack(item);
            }
            upgrades.setItem(kind.slot(), stack);
        }
    }

    public static int maskOf(CompoundTag tag)
    {
        int mask = 0;
        for (Kind kind : Kind.values())
            if (tag.contains(kind.key)) mask |= kind.bit;
        return mask;
    }

    // ---------------------------------------------------------------- stats

    public static int capacity(int mask)
    {
        return Kind.BATTERY.in(mask) ? Config.SUBMARINE_BATTERY_CAPACITY.get() : Submarine.capacity();
    }

    public static float maxDamage(int mask)
    {
        return Kind.HULL.in(mask) ? Config.SUBMARINE_HULL_MAX_DAMAGE.get().floatValue() : Submarine.MAX_DAMAGE;
    }

    private static double hull(int mask)
    {
        return Kind.HULL.in(mask) ? Config.SUBMARINE_HULL_SPEED_MULT.get() : 1.0;
    }

    public static double forwardSpeed(int mask)
    {
        return (Kind.THRUSTER.in(mask) ? Config.SUBMARINE_THRUSTER_FORWARD.get() : Config.SUBMARINE_MAX_SPEED.get()) * hull(mask);
    }

    /** reverse and sideways */
    public static double sideSpeed(int mask)
    {
        return (Kind.THRUSTER.in(mask) ? Config.SUBMARINE_THRUSTER_SIDE.get() : Submarine.SIDE_SPEED) * hull(mask);
    }

    public static double verticalSpeed(int mask)
    {
        double v = (Kind.THRUSTER.in(mask) ? Config.SUBMARINE_THRUSTER_VERTICAL.get() : Submarine.VERTICAL_SPEED) * hull(mask);
        return Kind.BATTERY.in(mask) ? v * Config.SUBMARINE_BATTERY_VERTICAL_MULT.get() : v;
    }

    public static double accel(int mask)
    {
        return Kind.THRUSTER.in(mask) ? Config.SUBMARINE_THRUSTER_ACCEL.get() : Submarine.ACCEL;
    }

    public static int thrustFe(int mask)
    {
        return Kind.THRUSTER.in(mask) ? Config.SUBMARINE_THRUSTER_FE.get() : Config.SUBMARINE_THRUST_FE.get();
    }

    // ---------------------------------------------------------------- item

    /** An upgrade item: stack 1, tooltip = effect (aqua) + cost (red) lines from lang. */
    public static class UpgradeItem extends Item
    {
        public final Kind kind;

        public UpgradeItem(Kind kind, Properties properties)
        {
            super(properties);
            this.kind = kind;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag)
        {
            String base = "tooltip." + Abyssia.MODID + "." + kind.id;
            lines.add(Component.translatable(base + ".effect").withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable(base + ".cost").withStyle(ChatFormatting.RED));
        }
    }
}
