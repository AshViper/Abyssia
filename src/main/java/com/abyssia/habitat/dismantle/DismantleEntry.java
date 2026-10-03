package com.abyssia.habitat.dismantle;

import com.abyssia.Abyssia;
import com.abyssia.habitat.build.BuildCategory;
import com.abyssia.habitat.build.BuildCheck;
import com.abyssia.habitat.build.BuildEntry;
import com.abyssia.habitat.build.BuildLayout;
import com.abyssia.habitat.build.BuildPlacement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * BT01b: the constructor's dismantle mode. A TARGET entry: aims at a built unit (module or fixture), costs nothing,
 * runs as HabitatBuilder's 20-tick job (blocks back to water) and refunds 80 % on completion (see {@link Dismantler}).
 * The orange preview comes from the server ({@link DismantleSync} writes the target box into the held constructor's
 * NBT; client/DismantleClient draws it through HabitatHologram.setDismantlePreview).
 */
public final class DismantleEntry implements BuildEntry
{
    public static final String ID = "dismantle";

    @Override
    public String id()
    {
        return ID;
    }

    @Override
    public BuildCategory category()
    {
        return BuildCategory.CUSTOMIZE;
    }

    @Override
    public Mode mode()
    {
        return Mode.TARGET;
    }

    @Override
    public Component detail()
    {
        return Component.translatable("habitat." + Abyssia.MODID + ".dismantle.detail");
    }

    @Override
    public List<ItemStack> cost()
    {
        return List.of();
    }

    /** server only (the client preview comes from the held stack's NBT) */
    @Nullable
    @Override
    public BuildPlacement plan(Player player, int rot, int distance, float partialTick)
    {
        if (!(player.level() instanceof ServerLevel level)) return null;
        return Dismantler.find(level, player, partialTick, true);
    }

    @Override
    public BuildCheck check(Level level, Player player, BuildPlacement placement)
    {
        if (!(level instanceof ServerLevel server) || !(placement instanceof DismantleTarget t)) return BuildCheck.NO_TARGET;
        return Dismantler.check(server, player, t);
    }

    @Override
    public BuildLayout layout(ServerLevel level, BuildPlacement placement)
    {
        return Dismantler.layout(level, (DismantleTarget) placement);
    }

    @Override
    public Completion complete(ServerLevel level, ServerPlayer player, BuildPlacement placement)
    {
        return Dismantler.finish(level, player, (DismantleTarget) placement);
    }

    @Override
    public boolean recordsUnit()
    {
        return false;
    }
}
