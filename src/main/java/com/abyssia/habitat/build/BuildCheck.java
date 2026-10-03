package com.abyssia.habitat.build;

import com.abyssia.Abyssia;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Result of {@link BuildEntry#check}: {@code problem} is null when buildable, else the suffix of the action-bar key
 * {@code message.abyssia.habitat.<problem>} (existing: not_water, entity, permission, not_interior, no_target).
 */
public record BuildCheck(@Nullable String problem)
{
    public static final BuildCheck OK = new BuildCheck(null);
    public static final BuildCheck NOT_WATER = new BuildCheck("not_water");
    public static final BuildCheck ENTITY = new BuildCheck("entity");
    public static final BuildCheck PERMISSION = new BuildCheck("permission");
    public static final BuildCheck NOT_INTERIOR = new BuildCheck("not_interior");
    public static final BuildCheck NO_TARGET = new BuildCheck("no_target");

    public static BuildCheck fail(String problem)
    {
        return new BuildCheck(problem);
    }

    public boolean ok()
    {
        return problem == null;
    }

    public Component message()
    {
        return Component.translatable("message." + Abyssia.MODID + ".habitat." + (problem == null ? "ok" : problem));
    }
}
