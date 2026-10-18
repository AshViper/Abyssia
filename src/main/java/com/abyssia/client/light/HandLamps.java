package com.abyssia.client.light;

import com.abyssia.Abyssia;
import com.abyssia.item.light.HandLampItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.function.Consumer;

/** Every player holding a switched-on hand lamp shines a {@link Spotlight} along their view; drawn on this client only. */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class HandLamps
{
    private static final float RANGE = 24.0f;
    private static final float HALF_ANGLE = 20.0f * Mth.DEG_TO_RAD;
    private static final int COLOR = 0xFFF0D0;
    private static final float BEAM_LENGTH = 12.0f;
    private static final float BEAM_RADIUS = 0.07f;

    private HandLamps() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> SpotlightProjector.addSource(HandLamps::collect));
    }

    private static void collect(ClientLevel level, float partialTick, Consumer<Spotlight> out)
    {
        for (Player player : level.players())
        {
            if (!player.isAlive() || player.isSpectator()) continue;
            boolean main = HandLampItem.isOn(player.getMainHandItem());
            if (!main && !HandLampItem.isOn(player.getOffhandItem())) continue;
            Vec3 direction = player.getViewVector(partialTick).normalize();
            // start at the lamp-holding hand: right/down of the eye, a little ahead
            boolean rightHand = (player.getMainArm() == HumanoidArm.RIGHT) == main;
            Vec3 right = direction.cross(new Vec3(0.0, 1.0, 0.0));
            right = right.lengthSqr() < 1.0e-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
            Vec3 origin = player.getEyePosition(partialTick).add(direction.scale(0.6))
                    .add(right.scale(rightHand ? 0.3 : -0.3)).add(0.0, -0.3, 0.0);
            out.accept(new Spotlight(origin, direction, RANGE, HALF_ANGLE, COLOR, 0.9f, BEAM_LENGTH, BEAM_RADIUS, player));
        }
    }
}
