package com.abyssia.vehicle.client;

import com.abyssia.Abyssia;
import com.abyssia.vehicle.Exosuit;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** The pilot of an {@link Exosuit} is drawn with the same walk bob as the hull so the head stays inside the dome. */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class ExosuitRiderRender
{
    private ExosuitRiderRender() {}

    @SubscribeEvent
    public static void pre(RenderPlayerEvent.Pre event)
    {
        Player player = event.getEntity();
        if (!(player.getVehicle() instanceof Exosuit suit)) return;
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(0.0f, ExosuitModel.bob(ExosuitModel.swing(suit, event.getPartialTick())), 0.0f);
    }

    @SubscribeEvent
    public static void post(RenderPlayerEvent.Post event)
    {
        if (event.getEntity().getVehicle() instanceof Exosuit) event.getPoseStack().popPose();
    }
}
