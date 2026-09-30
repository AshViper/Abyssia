package com.abyssia.fauna;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * A source of spawn rules for {@link FaunaSpawner}. Every spawn attempt asks each provider for the rules that may apply
 * at the sampled position, evaluates them all the same way (placement, depth, cover, habitat, caps) and picks one.
 * <p>
 * {@link #NATIVE} is Abyssia's own fauna (data/&lt;namespace&gt;/fauna_spawns); other providers only add candidates
 * and never change the native rules.
 */
@FunctionalInterface
public interface FaunaSpawnProvider
{
    /** Abyssia's own species, unbounded and with no extra checks. */
    FaunaSpawnProvider NATIVE = (level, pos) -> FaunaSpawnRules.rules();

    /** The rules that may apply around {@code pos} (evaluated in full by the spawner). */
    List<FaunaSpawnRule> rules(ServerLevel level, BlockPos pos);

    /**
     * Largest share of the spawn weight at a site this provider may take, compared with the native fauna there
     * (1 = unbounded). Keeps added animals from crowding out the native ones however many there are.
     */
    default double maxShare()
    {
        return 1.0;
    }

    /** Extra checks on a positioned, not yet added mob before the Forge spawn checks; false discards it. */
    default boolean accepts(ServerLevel level, FaunaSpawnRule rule, Mob mob, RandomSource random)
    {
        return true;
    }

    /** The result of the Forge spawn checks for a mob this provider proposed (before it is added when {@code ok}). */
    default void checked(FaunaSpawnRule rule, Mob mob, boolean ok) {}
}
