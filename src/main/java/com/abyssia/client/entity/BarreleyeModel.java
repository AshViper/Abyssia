package com.abyssia.client.entity;

import com.abyssia.client.entity.model.BarreleyeAnimations;
import com.abyssia.client.entity.model.BarreleyeGeometry;
import com.abyssia.entity.Barreleye;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.Set;

/**
 * The generated barreleye with its see-through head shield, plus the trick of its eyes: as the body tilts upright to
 * feed, the tubular eyes rotate the other way inside the shield and stay on the prey above.
 */
public class BarreleyeModel extends FaunaModel<Barreleye>
{
    private final ModelPart rightEye;
    private final ModelPart leftEye;

    public BarreleyeModel(ModelPart root)
    {
        super(root, BarreleyeGeometry.BARRELEYE, Map.of("idle", BarreleyeAnimations.IDLE, "swim", BarreleyeAnimations.SWIM,
                "hurt", BarreleyeAnimations.HURT, "death", BarreleyeAnimations.DEATH, "mouth_open", BarreleyeAnimations.MOUTH_OPEN,
                "mouth_close", BarreleyeAnimations.MOUTH_CLOSE), Set.of(BarreleyeGeometry.DOME), true);
        this.rightEye = this.bone(BarreleyeGeometry.RIGHT_EYE);
        this.leftEye = this.bone(BarreleyeGeometry.LEFT_EYE);
    }

    @Override
    protected void pose(Barreleye fish, float partialTick, float ageInTicks)
    {
        float pitch = fish.getViewXRot(partialTick) * Mth.DEG_TO_RAD;
        this.rightEye.xRot -= pitch;
        this.leftEye.xRot -= pitch;
    }
}
