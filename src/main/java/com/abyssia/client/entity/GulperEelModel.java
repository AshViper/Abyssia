package com.abyssia.client.entity;

import com.abyssia.client.entity.model.GulperEelAnimations;
import com.abyssia.client.entity.model.GulperEelGeometry;
import com.abyssia.entity.GulperEel;
import net.minecraft.client.model.geom.ModelPart;

import java.util.Map;
import java.util.Set;

/**
 * The generated gulper eel, plus the balloon display filmed by Nautilus in 2018: jaws shut, the pouch swells into a
 * black ball.
 */
public class GulperEelModel extends FaunaModel<GulperEel>
{
    private final ModelPart pouch;

    public GulperEelModel(ModelPart root)
    {
        super(root, GulperEelGeometry.GULPER_EEL, Map.of("idle", GulperEelAnimations.IDLE, "swim", GulperEelAnimations.SWIM,
                "hurt", GulperEelAnimations.HURT, "death", GulperEelAnimations.DEATH, "mouth_open", GulperEelAnimations.MOUTH_OPEN,
                "mouth_close", GulperEelAnimations.MOUTH_CLOSE, "glow", GulperEelAnimations.GLOW), Set.of(), true);
        this.pouch = this.bone(GulperEelGeometry.POUCH);
    }

    @Override
    protected void pose(GulperEel eel, float partialTick, float ageInTicks)
    {
        float b = eel.balloon(partialTick);
        if (b <= 0.001F) return;
        this.pouch.xScale *= 1.0F + b * 1.2F;
        this.pouch.yScale *= 1.0F + b * 1.6F;
        this.pouch.zScale *= 1.0F + b * 0.3F;
    }
}
