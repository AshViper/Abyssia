package com.abyssia.waypoint;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** One waypoint beacon: an empty name means the default (the translated block name). owner is server-side only. */
public record WaypointEntry(BlockPos pos, String name, int color, @Nullable UUID owner) {}
