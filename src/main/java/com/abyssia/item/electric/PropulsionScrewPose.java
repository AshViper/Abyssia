package com.abyssia.item.electric;

import net.minecraft.client.model.HumanoidModel;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * Parameters of the extended HumanoidModel.ArmPose constant ABYSSIA_PROPULSION_SCREW (META-INF/enumextensions.json,
 * 1.20: HumanoidModel.ArmPose.create). Kept apart so the JSON can reference it before the client classes are touched.
 */
public final class PropulsionScrewPose
{
    public static final EnumProxy<HumanoidModel.ArmPose> PROXY = new EnumProxy<>(HumanoidModel.ArmPose.class, true,
            (IArmPoseTransformer) (model, entity, arm) -> PropulsionScrewClient.applyPose(model, entity));

    private PropulsionScrewPose() {}
}
