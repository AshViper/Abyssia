package com.abyssia.client.giant;

import com.abyssia.Abyssia;
import com.abyssia.entity.giant.GiantFauna;
import com.abyssia.entity.giant.GiantSpec;
import com.abyssia.entity.giant.GiantSpecies;
import com.abyssia.registry.ModGiants;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** GNT01: every giant species gets the shared {@link GiantRenderer}; species models plug in through {@link GiantRenderer#registerModel}. */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GiantClientSetup
{
    private GiantClientSetup() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        // species models: texture textures/entity/boss/<id>_geo.png, glow <id>_geo_glow.png, model scale 2.0, segments = joints - 1;
        // lumenwing and glassback have translucent *_skin bones
        for (GiantSpec spec : GiantSpecies.SPECS)
        {
            String id = spec.id(), tex = "textures/entity/boss/" + id + "_geo";
            boolean skin = id.equals("lumenwing") || id.equals("glassback");
            GiantRenderer.registerGeo(id, id, tex + ".png", 2.0F, spec.joints() - 1, tex + "_glow.png", skin);
        }
        for (ModGiants.Species sp : ModGiants.all()) event.<GiantFauna>registerEntityRenderer(sp.type().get(), GiantRenderer::new);
    }
}
