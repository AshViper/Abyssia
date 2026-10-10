package com.abyssia.vehicle.client;

import com.abyssia.Abyssia;
import com.abyssia.ClientConfig;
import com.abyssia.vehicle.Submarine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * SUB07 Seamoth steering: the mouse sets a target, the hull (and the camera locked to it) eases there with the client
 * option's time constant. Done per frame so it is smooth; the hull entity copies the rider each tick (followRider).
 */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class SubmarineSteering
{
    private static boolean active;
    private static Submarine current;
    private static float hullYaw, hullPitch, targetYaw, targetPitch, lastYaw, lastPitch;
    private static long lastNanos;

    private SubmarineSteering() {}

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event)
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        double tau = ClientConfig.SUBMARINE_STEERING_SMOOTHING.get();
        if (tau <= 0.0 || player == null || mc.getCameraEntity() != player
                || !(player.getVehicle() instanceof Submarine sub) || sub.getControllingPassenger() != player || sub.getDock().isPresent())
        {
            active = false;
            current = null;
            return;
        }
        long now = System.nanoTime();
        if (!active || sub != current)
        {
            // start from the player's view, not the hull, so the camera never snaps
            active = true;
            current = sub;
            hullYaw = targetYaw = lastYaw = player.getYRot();
            hullPitch = targetPitch = lastPitch = Mth.clamp(player.getXRot(), -Submarine.MAX_PITCH, Submarine.MAX_PITCH);
            lastNanos = now;
        }
        float pt = (float) event.getPartialTick();
        float prevViewYaw = Mth.lerp(pt, player.yRotO, player.getYRot());
        float prevViewPitch = Mth.lerp(pt, player.xRotO, player.getXRot());
        double dt = mc.isPaused() ? 0.0 : Math.min(0.1, (now - lastNanos) / 1.0e9);
        lastNanos = now;
        targetYaw += player.getYRot() - lastYaw;
        targetPitch = Mth.clamp(targetPitch + player.getXRot() - lastPitch, -Submarine.MAX_PITCH, Submarine.MAX_PITCH);
        float a = (float) (1.0 - Math.exp(-dt / tau));
        hullYaw += (targetYaw - hullYaw) * a;
        hullPitch += (targetPitch - hullPitch) * a;
        player.setYRot(hullYaw);
        player.yRotO = hullYaw;
        player.setXRot(hullPitch);
        player.xRotO = hullPitch;
        lastYaw = hullYaw;
        lastPitch = hullPitch;
        float sign = mc.options.getCameraType().isMirrored() ? -1.0f : 1.0f;
        event.setYaw(event.getYaw() + (hullYaw - prevViewYaw));
        event.setPitch(event.getPitch() + sign * (hullPitch - prevViewPitch));
    }

    /** hull yaw for rendering: the eased angle for the local pilot's own submarine, else the entity's interpolated yaw */
    public static float yaw(Submarine sub, float pt)
    {
        return active && sub == current ? hullYaw : Mth.rotLerp(pt, sub.yRotO, sub.getYRot());
    }

    public static float pitch(Submarine sub, float pt)
    {
        return active && sub == current ? hullPitch : Mth.lerp(pt, sub.xRotO, sub.getXRot());
    }
}
