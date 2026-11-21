package com.abyssia.entity.giant.ability;

import com.abyssia.entity.giant.GiantFauna;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Lancejaw: the lance reaches 6 blocks in front of the head, 2 blocks wide, and hits a player or submarine inside that box. */
final class LancejawAbility extends GiantAbility
{
    static final double REACH = 6.0, HALF_WIDTH = 1.0;

    LancejawAbility(GiantFauna g) { super(g); }

    @Override
    public Boolean contact(Entity t)
    {
        Vec3 head = g.headPos();
        Vec3 end = head.add(g.heading().scale(REACH));
        var box = t.getBoundingBox().inflate(HALF_WIDTH);
        return box.contains(head) || box.clip(head, end).isPresent();
    }
}
