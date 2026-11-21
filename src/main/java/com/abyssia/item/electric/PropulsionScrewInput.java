package com.abyssia.item.electric;

import com.abyssia.Abyssia;
import com.abyssia.client.light.Spotlight;
import com.abyssia.client.light.SpotlightProjector;
import com.abyssia.network.AbyssiaNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForgeMod;

import java.util.function.Consumer;

/**
 * Client input of the propulsion screw: left-click held with the screw in the main hand thrusts (the attack never
 * reaches blocks or entities), the state is sent to the server on change; and every player whose screw has its light on
 * shines a {@link Spotlight} (drawn on this client only, like HandLamps).
 */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class PropulsionScrewInput
{
    private static final float RANGE = 50.0f;
    private static final float HALF_ANGLE = 50.0f * Mth.DEG_TO_RAD;
    private static final int COLOR = 0xFFF0D0;
    private static final float BEAM_LENGTH = 12.0f;
    private static final float BEAM_RADIUS = 0.07f;

    private static boolean thrusting;

    private PropulsionScrewInput() {}

    public static boolean localThrusting() { return thrusting; }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event)
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean now = false;
        if (player != null && mc.screen == null && mc.options.keyAttack.isDown()
                && player.getMainHandItem().getItem() instanceof PropulsionScrewItem
                && !player.isPassenger()
                && player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value()))
            now = player.getAbilities().instabuild || ElectricTools.getEnergy(player.getMainHandItem()) > 0;
        if (now)
        {
            PropulsionScrewItem.thrust(player);
            Vec3 back = player.getLookAngle().scale(-0.9);
            Vec3 at = player.getEyePosition().add(back).add(0, -0.3, 0);
            player.level().addParticle(ParticleTypes.BUBBLE, at.x, at.y, at.z, back.x * 0.1, back.y * 0.1, back.z * 0.1);
            player.level().addParticle(ParticleTypes.BUBBLE_COLUMN_UP, at.x, at.y, at.z, 0.0, 0.05, 0.0);
        }
        if (now != thrusting)
        {
            thrusting = now;
            if (player != null) AbyssiaNetwork.sendToServer(new ScrewThrustPacket(now));
        }
    }

    /** The screw in the main hand swallows the attack key: it never breaks blocks or hits entities. */
    @SubscribeEvent
    public static void interaction(InputEvent.InteractionKeyMappingTriggered event)
    {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!event.isAttack() || player == null || !(player.getMainHandItem().getItem() instanceof PropulsionScrewItem)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> SpotlightProjector.addSource(PropulsionScrewInput::collect));
    }

    private static void collect(ClientLevel level, float partialTick, Consumer<Spotlight> out)
    {
        for (Player player : level.players())
        {
            if (!player.isAlive() || player.isSpectator()) continue;
            if (!PropulsionScrewItem.isLightOn(player.getMainHandItem()) && !PropulsionScrewItem.isLightOn(player.getOffhandItem())) continue;
            Vec3 direction = player.getViewVector(partialTick).normalize();
            // at the screw: centred in front of the eyes, slightly low
            Vec3 origin = player.getEyePosition(partialTick).add(direction.scale(0.6)).add(0.0, -0.3, 0.0);
            out.accept(new Spotlight(origin, direction, RANGE, HALF_ANGLE, COLOR, 0.9f, BEAM_LENGTH, BEAM_RADIUS, player));
        }
    }
}
