package com.abyssia.client.giant;

import com.abyssia.entity.giant.GiantChainPayload;
import com.abyssia.entity.giant.GiantFauna;
import net.minecraft.client.Minecraft;

public final class GiantChainClient
{
    private GiantChainClient() {}

    public static void apply(GiantChainPayload msg)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.level.getEntity(msg.entityId()) instanceof GiantFauna g) g.chain().receive(msg.rel());
    }
}
