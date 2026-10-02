package com.abyssia.environment;

/**
 * For entities that hold their own against currents: 0 = carried fully (the default for everything without this),
 * 0.5 = half the push, 1 = immune. The entity type tag {@code abyssia:ignores_ocean_current} is full immunity.
 */
public interface CurrentResistant
{
    float getCurrentResistance();
}
