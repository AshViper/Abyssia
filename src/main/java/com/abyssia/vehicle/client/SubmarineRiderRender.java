package com.abyssia.vehicle.client;

import com.abyssia.Abyssia;
import com.abyssia.vehicle.Submarine;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * SUB05b: a player riding a {@link Submarine} is drawn tilted with the hull pitch about the eye point, so the body (and
 * the camera at the eye) stay rigid with the hull. Submarine.seatOffset puts that eye point on the rotated hull point.
 */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class SubmarineRiderRender
{
    private SubmarineRiderRender() {}

    @SubscribeEvent
    public static void pre(RenderPlayerEvent.Pre event)
    {
        Player player = event.getEntity();
        if (!(player.getVehicle() instanceof Submarine sub)) return;
        float pt = event.getPartialTick();
        float pitch = SubmarineSteering.pitch(sub, pt);
        if (Math.abs(pitch) < 0.01f) return;
        float yaw = 180.0f - SubmarineSteering.yaw(sub, pt);
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0, Submarine.SEAT_ANCHOR, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(-pitch));
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(0.0, -Submarine.SEAT_ANCHOR, 0.0);
    }

    @SubscribeEvent
    public static void post(RenderPlayerEvent.Post event)
    {
        Player player = event.getEntity();
        if (!(player.getVehicle() instanceof Submarine sub)) return;
        float pt = event.getPartialTick();
        if (Math.abs(SubmarineSteering.pitch(sub, pt)) < 0.01f) return;
        event.getPoseStack().popPose();
    }
}
