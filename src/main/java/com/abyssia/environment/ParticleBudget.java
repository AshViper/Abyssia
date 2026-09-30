package com.abyssia.environment;

/**
 * Live counts of budgeted environment particles, so spawners can respect caps. Kept in common code because
 * blocks (whose animateTick runs only on the client) also check it before emitting spores.
 * Only ever touched from the client thread.
 */
public final class ParticleBudget
{
    /** Separate budgets so vent plumes, plant spores and ambient marine snow never starve each other. */
    public enum Budget
    {
        AMBIENT(Integer.MAX_VALUE),
        VENT(500),
        PLANT(150);

        /** Hard cap; AMBIENT is capped per depth band by its spawner instead. */
        public final int limit;

        Budget(int limit)
        {
            this.limit = limit;
        }
    }

    private static final int[] ALIVE = new int[Budget.values().length];

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
    }

    public static void removed(Budget budget)
    {
        ALIVE[budget.ordinal()] = Math.max(0, ALIVE[budget.ordinal()] - 1);
    }

    public static void reset()
    {
        java.util.Arrays.fill(ALIVE, 0);
    }
}
