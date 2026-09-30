package com.abyssia.entity;

import net.minecraft.world.phys.Vec3;

/** An animal with a bioluminescent lure that draws small fish (anglerfish esca, viperfish dorsal ray). */
public interface LureBearer
{
    /** Lit and worth swimming to (not fleeing, not sated). */
    boolean isLureLit();

    /** Sated after a meal: the lure still glows, but no strike follows, so prey is not drawn in. */
    boolean isDigesting();

    Vec3 lurePosition();
}
