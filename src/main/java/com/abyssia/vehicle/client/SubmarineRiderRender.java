package com.abyssia.vehicle.client;

import com.abyssia.Abyssia;
import com.abyssia.vehicle.Submarine;
import com.mojang.math.Axis;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * SUB05b rider tilt. The hull pitches about (0, PIVOT_Y, 0) (xRot + = nose down). The rider's EYE (Submarine.SEAT_ANCHOR above its
 * feet) is the point that follows the seat's head point (Submarine.positionRider), so the rider model is tilted about that same
 * eye by the interpolated hull pitch: head stays put, body / legs swing with the hull and stay inside it. The tilt axis is the
 * hull's horizontal right axis (rotation done in hull yaw space), sign identical to SubmarineRenderer (XP(-pitch) in model space).
 * The view itself (xRot / yRot) is never touched. Any LivingEntity riding a Submarine is tilted (only players can ride).
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SubmarineRiderRender
{
    private SubmarineRiderRender() {}

    @SubscribeEvent
    public static void pre(RenderLivingEvent.Pre<?, ?> event)
    {
        LivingEntity rider = event.getEntity();
        if (!(rider.getVehicle() instanceof Submarine sub)) return;
        float t = event.getPartialTick();
        float pitch = SubmarineSteering.pitch(sub, t);
        float yaw = 180.0f - SubmarineSteering.yaw(sub, t);
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0, Submarine.SEAT_ANCHOR, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(-pitch));
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(0.0, -Submarine.SEAT_ANCHOR, 0.0);
    }

    @SubscribeEvent
    public static void post(RenderLivingEvent.Post<?, ?> event)
    {
        if (event.getEntity().getVehicle() instanceof Submarine) event.getPoseStack().popPose();
    }
}
