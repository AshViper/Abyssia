package com.abyssia.client;

import com.abyssia.Abyssia;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Whether an Iris / Oculus shader pack is drawing the world. Looked up by reflection, so neither mod is a dependency,
 * and read once per frame, since particles ask for every one of them. A shader pack replaces the vanilla fog and
 * lighting maths with its own, so effects that rely on them need a fallback.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShaderCompat
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Object api;
    private static Method inUse;
    private static boolean resolved;
    private static boolean active;

    private ShaderCompat() {}

    public static boolean shaderPackInUse()
    {
        return active;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event)
    {
        if (event.phase == TickEvent.Phase.START) active = query();
    }

    private static boolean query()
    {
        if (!resolved)
        {
            resolved = true;
            try
            {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = type.getMethod("getInstance").invoke(null);
                inUse = type.getMethod("isShaderPackInUse");
                LOGGER.info("[{}] Iris/Oculus found: shader pack fallbacks enabled", Abyssia.MODID);
            }
            catch (ReflectiveOperationException | LinkageError e)
            {
                inUse = null;
            }
        }
        if (inUse == null) return false;
        try
        {
            return (boolean) inUse.invoke(api);
        }
        catch (ReflectiveOperationException e)
        {
            inUse = null;
            return false;
        }
    }
}
