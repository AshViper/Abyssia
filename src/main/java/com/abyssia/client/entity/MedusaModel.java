package com.abyssia.client.entity;

import com.abyssia.entity.DriftingMedusa;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A generated medusa (template "medusa"): the bell is drawn translucent, and a jelly that has cast off its tentacles
 * (a silky medusa's escape) is drawn without them until they grow back.
 */
public class MedusaModel<T extends DriftingMedusa> extends FaunaModel<T>
{
    private final List<ModelPart> tentacles = new ArrayList<>();

    public MedusaModel(ModelPart root, String rootBone, Map<String, AnimationDefinition> clips, Set<String> translucent)
    {
        super(root, rootBone, clips, translucent, false);
        for (int i = 1; ; i++)
        {
            var part = this.getAnyDescendantWithName("tentacle_" + i);
            if (part.isEmpty()) break;
            this.tentacles.add(part.get());
        }
    }

    @Override
    protected void pose(T jelly, float partialTick, float ageInTicks)
    {
        boolean shown = !jelly.hasShedTentacles();
        for (ModelPart t : this.tentacles) t.visible = shown;
    }
}
