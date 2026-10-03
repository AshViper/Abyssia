package com.abyssia.habitat.client;

import com.abyssia.Abyssia;
import com.abyssia.habitat.HabitatConstructorItem;
import com.abyssia.habitat.HabitatControlPacket;
import com.abyssia.habitat.HabitatPlan;
import com.abyssia.habitat.build.BuildEntry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

/**
 * H02 / BT01a controls while the constructor is in the main hand: right-click / G = build menu, left-click = build,
 * R = rotate +90 (Shift+R -90), mouse wheel = placement distance +-1 (3..12, component habitat_dist; Shift+wheel stays the hotbar).
 */
@EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class HabitatClient
{
    public static final String CATEGORY = "key.categories." + Abyssia.MODID;
    public static final KeyMapping MENU = new KeyMapping("key." + Abyssia.MODID + ".habitat_menu", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping ROTATE = new KeyMapping("key." + Abyssia.MODID + ".habitat_rotate", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    /** the attack button is still held since the last build request (no repeat while held) */
    private static boolean attackHeld;

    private HabitatClient() {}

    static ItemStack heldConstructor()
    {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof HabitatConstructorItem ? stack : null;
    }

    private static boolean shift()
    {
        return Minecraft.getInstance().options.keyShift.isDown();
    }

    public static void openMenu()
    {
        Minecraft mc = Minecraft.getInstance();
        ItemStack stack = heldConstructor();
        if (stack == null || mc.screen != null) return;
        click();
        mc.setScreen(new HabitatMenuScreen(HabitatConstructorItem.entry(stack)));
    }

    static void click()
    {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    static void select(BuildEntry entry)
    {
        ItemStack stack = heldConstructor();
        if (stack == null) return;
        HabitatConstructorItem.setEntryId(stack, entry.id());
        PacketDistributor.sendToServer(HabitatControlPacket.select(entry.id()));
    }

    private static void rotate(int steps)
    {
        ItemStack stack = heldConstructor();
        if (stack == null) return;
        int rot = Math.floorMod(HabitatConstructorItem.rotation(stack, Minecraft.getInstance().player) + steps, 4);
        HabitatConstructorItem.setRotation(stack, rot);
        PacketDistributor.sendToServer(HabitatControlPacket.rotate(rot));
    }

    /** BT01a: placement distance +-1, clamped 3..12, shown on the action bar. */
    private static void changeDistance(int by)
    {
        ItemStack stack = heldConstructor();
        Minecraft mc = Minecraft.getInstance();
        if (stack == null || mc.player == null) return;
        int old = HabitatConstructorItem.distance(stack);
        int dist = HabitatPlan.clampDistance(old + by);
        if (dist != old)
        {
            HabitatConstructorItem.setDistance(stack, dist);
            PacketDistributor.sendToServer(HabitatControlPacket.distance(dist));
        }
        mc.player.displayClientMessage(Component.translatable("message." + Abyssia.MODID + ".habitat.distance", dist), true);
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event)
    {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyAttack.isDown()) attackHeld = false;
        while (MENU.consumeClick()) openMenu();
        while (ROTATE.consumeClick()) rotate(shift() ? -1 : 1);
    }

    @SubscribeEvent
    public static void interaction(InputEvent.InteractionKeyMappingTriggered event)
    {
        ItemStack stack = heldConstructor();
        if (stack == null || Minecraft.getInstance().screen != null) return;
        if (event.isUseItem())
        {
            event.setCanceled(true);
            event.setSwingHand(false);
            openMenu();
        }
        else if (event.isAttack())
        {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (attackHeld) return;
            attackHeld = true;
            int rot = HabitatConstructorItem.rotation(stack, Minecraft.getInstance().player);
            PacketDistributor.sendToServer(HabitatControlPacket.build(HabitatConstructorItem.entry(stack).id(), rot,
                    HabitatConstructorItem.distance(stack)));
        }
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event)
    {
        if (heldConstructor() == null || Minecraft.getInstance().screen != null || shift() || event.getScrollDeltaY() == 0) return;
        event.setCanceled(true);
        changeDistance(event.getScrollDeltaY() > 0 ? 1 : -1);
    }

    @EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration
    {
        private Registration() {}

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event)
        {
            event.register(MENU);
            event.register(ROTATE);
        }
    }
}
