package com.abyssia.client.entity;

import com.abyssia.entity.FaunaAnimated;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A model from tools/bbmodel-generator: its generated geometry, and its generated keyframe clips played by name
 * whenever the entity has them running ({@link com.abyssia.entity.FaunaAnimations}). Swimmers pitch their root bone
 * with the direction they swim. Bones drawn by a translucent layer are left out of the opaque pass.
 * Subclasses add procedural touches in {@link #pose}.
 */
public class FaunaModel<T extends Mob & FaunaAnimated> extends HierarchicalModel<T>
{
    private final ModelPart root;
    private final ModelPart body;
    private final Map<String, AnimationDefinition> clips;
    private final Set<String> translucentBones;
    private final boolean pitch;
    private List<ModelPart> translucent;
    private List<ModelPart> allParts;
    private final Map<String, Optional<ModelPart>> boneCache = new HashMap<>();

    /**
     * @param rootBone    the creature's own root bone (the generator names it after the creature id)
     * @param clips       clip name ("idle", "swim", ...) -> its generated AnimationDefinition
     * @param translucent bones drawn by the translucent layer instead of the opaque pass
     * @param pitch       tilt the whole body with the entity's pitch (swimmers)
     */
    public FaunaModel(ModelPart root, String rootBone, Map<String, AnimationDefinition> clips, Set<String> translucent, boolean pitch)
    {
        this.root = root;
        this.body = root.getChild(rootBone);
        this.clips = clips;
        this.translucentBones = translucent;
        this.pitch = pitch;
    }

    @Override
    public ModelPart root()
    {
        return this.root;
    }

    /** Same result as vanilla, but memoised: vanilla re-streams every part per animated bone per frame. */
    @Override
    public Optional<ModelPart> getAnyDescendantWithName(String name)
    {
        return this.boneCache.computeIfAbsent(name, super::getAnyDescendantWithName);
    }

    public ModelPart bone(String name)
    {
        return this.getAnyDescendantWithName(name).orElseThrow(() -> new IllegalArgumentException("no bone " + name));
    }

    public Set<String> translucentBones()
    {
        return this.translucentBones;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {
        if (this.allParts == null) this.allParts = this.root.getAllParts().toList();
        for (ModelPart part : this.allParts) part.resetPose();
        for (Map.Entry<String, AnimationDefinition> clip : this.clips.entrySet())
        {
            AnimationState state = entity.animations().get(clip.getKey());
            if (state != null) this.animate(state, clip.getValue(), ageInTicks);
        }
        if (this.pitch) this.body.xRot += headPitch * Mth.DEG_TO_RAD;
        this.pose(entity, ageInTicks - entity.tickCount, ageInTicks);
        if (this.translucent == null) this.translucent = this.translucentBones.stream().map(this::bone).toList();
        this.translucent.forEach(p -> p.skipDraw = true);
    }

    /** Procedural touches on top of the clips (after them, before rendering). */
    protected void pose(T entity, float partialTick, float ageInTicks)
    {
    }
}
