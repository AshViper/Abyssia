package com.abyssia.research;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Result of {@link ResearchManager#scan}. {@code fragments}/{@code needed}: counted discoveries of the target and how many
 * any technology asks of it (at least 1); {@code unlocked}: technologies this scan unlocked.
 */
public record ScanResult(ScanOutcome outcome, int fragments, int needed, List<ResourceLocation> unlocked) {}
