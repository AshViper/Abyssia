package com.abyssia.environment;

/**
 * Live counts of budgeted environment particles, so spawners can respect caps. Kept in common code because
 * blocks (whose animateTick runs only on the client) also check it before emitting spores.
 * Only ever touched from the client thread.
 */
public final class ParticleBudget
{
    /** Separate budgets so vent plumes, plant spores, current streaks and ambient marine snow never starve each other. */
    public enum Budget
    {
        AMBIENT(Integer.MAX_VALUE),
        VENT(500),
        PLANT(150),
        CURRENT(600),
        /** CU01 current stream streaks; capped by client config current_streams.particle_budget instead. */
        STREAM(Integer.MAX_VALUE);

        /** Hard cap; AMBIENT is capped per depth band by its spawner instead. */
        public final int limit;

        Budget(int limit)
        {
            this.limit = limit;
        }
    }

    private static final int[] ALIVE = new int[Budget.values().length];
    /** Particles that ticked since the last {@link #reconcile()}, and ones spawned since then (not ticked yet). */
    private static final int[] TICKED = new int[Budget.values().length];
    private static final int[] SPAWNED = new int[Budget.values().length];

    private ParticleBudget() {}

    public static int alive(Budget budget)
    {
        return ALIVE[budget.ordinal()];
    }

    public static boolean hasRoom(Budget budget)
    {
        return ALIVE[budget.ordinal()] < budget.limit;
    }

    public static void added(Budget budget)
    {
        ALIVE[budget.ordinal()]++;
        SPAWNED[budget.ordinal()]++;
    }

    /** Called from a budgeted particle's tick while it is alive. */
    public static void ticked(Budget budget)
    {
        TICKED[budget.ordinal()]++;
    }

    /**
     * Recounts from what actually ticked: particles the engine drops without {@code remove()} (cleared on a level
     * change, evicted from a full particle queue) would otherwise stay counted forever, until the cap blocks every new
     * one. Call once per client tick, before the particle engine ticks, and not while the game is paused.
     */
    public static void reconcile()
    {
        for (int i = 0; i < ALIVE.length; i++)
        {
            ALIVE[i] = TICKED[i] + SPAWNED[i];
            TICKED[i] = 0;
            SPAWNED[i] = 0;
        }
    }

    public static void removed(Budget budget)
    {
        ALIVE[budget.ordinal()] = Math.max(0, ALIVE[budget.ordinal()] - 1);
    }

    public static void reset()
    {
        java.util.Arrays.fill(ALIVE, 0);
        java.util.Arrays.fill(TICKED, 0);
        java.util.Arrays.fill(SPAWNED, 0);
    }
}
