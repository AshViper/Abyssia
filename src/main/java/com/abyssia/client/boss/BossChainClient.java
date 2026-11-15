package com.abyssia.client.boss;

import com.abyssia.entity.boss.AbyssBoss;
import com.abyssia.entity.boss.BossChainPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class BossChainClient
{
    private BossChainClient() {}

    public static void apply(BossChainPayload msg)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(msg.entityId());
        if (e instanceof AbyssBoss boss)
        {
            var c = boss.chainAt(msg.chain());
            if (c != null) c.receive(msg.rel());
        }
    }
}
