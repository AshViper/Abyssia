package com.abyssia.habitat.build;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * BT01a: one item of the constructor's build menu. Registered once in {@link BuildContent} (common) and listed by
 * {@link BuildRegistry} in category order. The server runs every entry through the same timed job as the modules
 * (HabitatBuilder: check, pay up front, {@link #duration()} ticks of {@link #layout} steps, refund on cancel, then
 * {@link #complete} and a {@link BuiltUnits} record).
 * <p>
 * {@link Mode#PLACE}: a new footprint at the hologram (eye + look x distance, hatch snap within 2 blocks).
 * {@link Mode#TARGET}: acts on an existing block the player aims at (glass wall, radar upgrade); {@link #plan} picks the
 * target itself (distance = reach) and returns null when nothing valid is aimed at.
 */
public interface BuildEntry
{
    enum Mode { PLACE, TARGET }

    /** Result of a finished build: the HabitatBases module it registered (-1 = none) and the action-bar text. */
    record Completion(int moduleId, @Nullable Component message)
    {
        public static final Completion NONE = new Completion(-1, null);
    }

    /** unique, saved in the constructor NBT {@code Mode} and in BuiltUnits (modules keep their HabitatMode ids) */
    String id();

    BuildCategory category();

    default Mode mode()
    {
        return Mode.PLACE;
    }

    default Component displayName()
    {
        return Component.translatable("habitat." + Abyssia.MODID + ".mode." + id());
    }

    /** second line in the menu / tooltip (size...), null = none */
    @Nullable
    default Component detail()
    {
        return null;
    }

    /** menu icon (16 x 16); the constructor item is drawn when the texture is missing */
    default ResourceLocation icon()
    {
        return ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/item/habitat_icon_" + id() + ".png");
    }

    /** nominal cost shown in the menu / tooltip (fresh stacks each call) */
    List<ItemStack> cost();

    /** what this placement really costs (TARGET entries that scale with the target); default {@link #cost()} */
    default List<ItemStack> cost(Level level, BuildPlacement placement)
    {
        return cost();
    }

    /**
     * Placement for the current aim, client (hologram, every frame / tick) and server (on BUILD, re-planned with the
     * clamped distance). {@code rot} 0-3 (HabitatPlan.facing), {@code distance} 3..12. Null = nothing to show / build.
     */
    @Nullable
    BuildPlacement plan(Player player, int rot, int distance, float partialTick);

    /** Placement rules (both sides; the client only tints, the server decides). Materials are checked by the caller. */
    BuildCheck check(Level level, Player player, BuildPlacement placement);

    /** Server, once at start (after payment): the block steps of the timed build. */
    BuildLayout layout(ServerLevel level, BuildPlacement placement);

    /** Server, after the last tick (layout air / water applied): connect, register power... */
    default Completion complete(ServerLevel level, ServerPlayer player, BuildPlacement placement)
    {
        return Completion.NONE;
    }

    default int duration()
    {
        return HabitatBuilder.DURATION;
    }

    /** whether a finished build is recorded in {@link BuiltUnits} (dismantle needs it) */
    default boolean recordsUnit()
    {
        return true;
    }

    /** BT01b: whether {@link #dismantle} is implemented */
    default boolean canDismantle()
    {
        return false;
    }

    /**
     * BT01b hook: remove a recorded unit of this entry (blocks, power, refund) - the BuiltUnits record is removed by the
     * caller when this returns true. Default: unsupported.
     */
    default boolean dismantle(ServerLevel level, ServerPlayer player, BuiltUnits.Unit unit)
    {
        throw new UnsupportedOperationException("dismantle not supported by " + id());
    }
}
