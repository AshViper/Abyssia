package com.abyssia.client.armor;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/** Humanoid armor model whose parts carry the 3D diving-suit cubes of {@link DivingSuitMesh}. */
public final class DivingSuitModel extends HumanoidModel<LivingEntity>
{
    public DivingSuitModel(ModelPart root)
    {
        super(root);
    }
}
