package com.abyssia.habitat.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BT01a: every finished constructor build of one dimension (HabitatBuilder records it on completion), for BT01b
 * dismantling and refunds. {@code paid} = the stacks actually taken (empty in creative); {@code moduleId} = the
 * HabitatBases module registered by the build, -1 when none. Modules built before BT01 have no record.
 */
public class BuiltUnits extends SavedData
{
    public static final String NAME = "abyssia_built_units";

    public record Unit(int unitId, String entryId, BlockPos origin, int rot, BoundingBox box, List<ItemStack> paid, int moduleId)
    {
        CompoundTag save(HolderLookup.Provider registries)
        {
            CompoundTag t = new CompoundTag();
            t.putInt("Id", unitId);
            t.putString("Entry", entryId);
            t.put("Origin", NbtUtils.writeBlockPos(origin));   // 1.21: int array (Forge 1.20 saved a compound)
            t.putInt("Rot", rot);
            t.putIntArray("Box", new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
            ListTag items = new ListTag();
            for (ItemStack stack : paid) if (!stack.isEmpty()) items.add(stack.save(registries));
            t.put("Paid", items);
            t.putInt("Module", moduleId);
            return t;
        }

        static Unit load(CompoundTag t, HolderLookup.Provider registries)
        {
            int[] b = t.getIntArray("Box");
            if (b.length != 6) b = new int[6];
            List<ItemStack> paid = new ArrayList<>();
            ListTag items = t.getList("Paid", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++)
            {
                ItemStack stack = ItemStack.parseOptional(registries, items.getCompound(i));
                if (!stack.isEmpty()) paid.add(stack);
            }
            return new Unit(t.getInt("Id"), t.getString("Entry"), NbtUtils.readBlockPos(t, "Origin").orElse(BlockPos.ZERO), t.getInt("Rot"),
                    new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]), List.copyOf(paid),
                    t.contains("Module") ? t.getInt("Module") : -1);
        }
    }

    private final Map<Integer, Unit> units = new LinkedHashMap<>();
    private int nextId;

    public static BuiltUnits get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(BuiltUnits::new, BuiltUnits::load, null), NAME);
    }

    /** Records a finished build; returns the new unit id. */
    public int add(String entryId, BlockPos origin, int rot, BoundingBox box, List<ItemStack> paid, int moduleId)
    {
        int id = nextId++;
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : paid) copy.add(stack.copy());
        units.put(id, new Unit(id, entryId, origin.immutable(), Math.floorMod(rot, 4), box, List.copyOf(copy), moduleId));
        setDirty();
        return id;
    }

    @Nullable
    public Unit get(int unitId)
    {
        return units.get(unitId);
    }

    @Nullable
    public Unit remove(int unitId)
    {
        Unit removed = units.remove(unitId);
        if (removed != null) setDirty();
        return removed;
    }

    public Collection<Unit> units()
    {
        return Collections.unmodifiableCollection(units.values());
    }

    /** Units whose box contains pos, latest first (a fixture inside a room comes before the room). */
    public List<Unit> unitsAt(BlockPos pos)
    {
        List<Unit> out = new ArrayList<>();
        for (Unit unit : units.values())
            if (unit.box.isInside(pos)) out.add(0, unit);
        return out;
    }

    /** The unit that registered this HabitatBases module, or null (built before BT01). */
    @Nullable
    public Unit byModule(int moduleId)
    {
        if (moduleId < 0) return null;
        for (Unit unit : units.values())
            if (unit.moduleId == moduleId) return unit;
        return null;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        ListTag list = new ListTag();
        for (Unit unit : units.values()) list.add(unit.save(registries));
        tag.put("Units", list);
        tag.putInt("NextId", nextId);
        return tag;
    }

    public static BuiltUnits load(CompoundTag tag, HolderLookup.Provider registries)
    {
        BuiltUnits data = new BuiltUnits();
        ListTag list = tag.getList("Units", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            Unit unit = Unit.load(list.getCompound(i), registries);
            data.units.put(unit.unitId, unit);
            data.nextId = Math.max(data.nextId, unit.unitId + 1);
        }
        data.nextId = Math.max(data.nextId, tag.getInt("NextId"));
        return data;
    }
}
