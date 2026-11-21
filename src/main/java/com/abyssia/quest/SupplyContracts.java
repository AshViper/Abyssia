package com.abyssia.quest;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * QOL01 daily supply contracts. One request per player per day: {@code day = gameTime / 24000},
 * {@code index = floorMod(hash(day, uuid), POOL.size())}; client and server compute it the same way, nothing is synced.
 * The day a player last delivered lives in PERSISTED_NBT_TAG "abyssia_qol01" / "contract_day".
 */
public final class SupplyContracts
{
    public static final String TAG = "abyssia_qol01";
    public static final String KEY_DAY = "contract_day";
    public static final long DAY_TICKS = 24000L;

    public record Contract(String item, int count, String reward, int rewardCount) {}

    /** request = a renewable material; reward = something a little rarer */
    public static final List<Contract> POOL = List.of(
            new Contract("cooked_abyssal_fish", 16, "cobalt_ingot", 4),
            new Contract("deep_fiber", 32, "nickel_ingot", 4),
            new Contract("organic_matter", 32, "manganese_ingot", 4),
            new Contract("plant_resin", 16, "titanium_ingot", 2),
            new Contract("crystal_sap", 8, "deep_crystal_shard", 2),
            new Contract("lumen_gel", 8, "pressure_crystal_shard", 2),
            new Contract("bio_oil", 8, "molybdenum_ingot", 2),
            new Contract("thermal_fiber", 16, "vanadium_ingot", 2),
            new Contract("fish_pie", 4, "abyssal_alloy_ingot", 1),
            new Contract("mushroom_cap", 32, "abyssal_crystal_shard", 2),
            new Contract("kelp_leaf", 32, "thermal_crystal_shard", 2),
            new Contract("hard_stalk", 24, "titanium_ingot", 2));

    public enum Result { OK, DISABLED, DONE_TODAY, MISSING }

    private SupplyContracts() {}

    public static long dayOf(long gameTime)
    {
        return Math.floorDiv(gameTime, DAY_TICKS);
    }

    public static int indexOf(long day, UUID uuid)
    {
        return Math.floorMod(Objects.hash(day, uuid), POOL.size());
    }

    public static Contract of(long day, UUID uuid)
    {
        return POOL.get(indexOf(day, uuid));
    }

    public static Item itemOf(String id)
    {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(Abyssia.MODID, id));
    }

    public static boolean doneOn(Player player, long day)
    {
        CompoundTag mine = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(TAG);
        return mine.contains(KEY_DAY) && mine.getLong(KEY_DAY) == day;
    }

    public static int count(Player player, Item item)
    {
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : player.getInventory().offhand) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** takes exactly {@code amount} of item from the inventory (the caller checked there is enough) */
    private static void take(Player player, Item item, int amount)
    {
        for (ItemStack s : player.getInventory().items)
        {
            if (amount <= 0) return;
            if (!s.is(item)) continue;
            int n = Math.min(amount, s.getCount());
            s.shrink(n);
            amount -= n;
        }
        for (ItemStack s : player.getInventory().offhand)
        {
            if (amount <= 0) return;
            if (!s.is(item)) continue;
            int n = Math.min(amount, s.getCount());
            s.shrink(n);
            amount -= n;
        }
    }

    /** Delivers today's request if the inventory holds it; the reward goes to the inventory or the ground. */
    public static Result redeem(ServerPlayer player)
    {
        if (!Config.QOL01_CONTRACTS_ENABLED.get()) return Result.DISABLED;
        long day = dayOf(player.level().getGameTime());
        if (doneOn(player, day)) return Result.DONE_TODAY;
        Contract c = of(day, player.getUUID());
        Item want = itemOf(c.item());
        if (count(player, want) < c.count()) return Result.MISSING;
        take(player, want, c.count());
        ItemStack reward = new ItemStack(itemOf(c.reward()), c.rewardCount());
        if (!player.getInventory().add(reward)) player.drop(reward, false);
        player.giveExperiencePoints(Config.QOL01_CONTRACT_XP.get());
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag mine = persisted.getCompound(TAG);
        mine.putLong(KEY_DAY, day);
        persisted.put(TAG, mine);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        return Result.OK;
    }
}
