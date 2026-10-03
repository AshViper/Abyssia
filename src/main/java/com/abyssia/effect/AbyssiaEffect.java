package com.abyssia.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Plain colour-only MobEffect; behaviour comes from attribute modifiers or from {@link EffectEvents} / the fog code. */
public class AbyssiaEffect extends MobEffect
{
    public AbyssiaEffect(MobEffectCategory category, int color)
    {
        super(category, color);
    }
}
