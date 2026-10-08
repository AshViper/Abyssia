package com.abyssia.industry;

import com.abyssia.Abyssia;
import net.minecraft.resources.ResourceLocation;

/**
 * Where the slots and gauges of an industrial GUI sit (pixels inside the 176x166 panel). tools/industrial_gui.py
 * draws textures/gui/&lt;texture&gt;.png with the same numbers; its sprites sit at x=176: flame 14x14 at y=0,
 * progress arrow 24x17 at y=14, energy bar fill 12x52 at y=31.
 */
public enum GuiLayout
{
    /** one input, arrow, output */
    MACHINE("industrial_machine", new int[][] {{56, 35}}, 116, 35, 80, 34, -1, -1, 28),
    /** three inputs, arrow, output */
    ALLOY("industrial_alloy", new int[][] {{30, 35}, {48, 35}, {66, 35}}, 124, 35, 90, 34, -1, -1, 28),
    /** fuel slot under a flame, text on the right */
    GENERATOR("industrial_generator", new int[][] {{40, 53}}, -1, -1, -1, -1, 41, 35, 64),
    /** no slots: energy bar and text only */
    ENERGY("industrial_energy", new int[0][], -1, -1, -1, -1, -1, -1, 30),
    /** input over the reagent slot (droplet icon on its left), arrow, big main output, a column of three rare outputs */
    LEACHING("industrial_leaching", new int[][] {{36, 21}}, 94, 30, 60, 30, -1, -1, 28, 36, 41, new int[][] {{126, 17}, {126, 35}, {126, 53}}),
    /** no inputs, energy bar, progress bar, target deposit info, output slot + by-product slot (Mk2) */
    EXCAVATOR("industrial_excavator", new int[0][], 116, 53, 80, 34, -1, -1, 28, -1, -1, new int[][] {{142, 53}});

    public static final int ENERGY_X = 9, ENERGY_Y = 17, ENERGY_W = 12, ENERGY_H = 52;
    public static final int SPRITE_X = 176, FLAME_V = 0, ARROW_V = 14, ENERGY_V = 31;
    public static final int ARROW_W = 24, ARROW_H = 17, FLAME_SIZE = 14;

    public final ResourceLocation texture;
    public final int[][] inputs;
    public final int outputX, outputY;
    public final int arrowX, arrowY;
    public final int flameX, flameY;
    /** left edge of the status text */
    public final int textX;
    /** reagent slot (-1 if none) */
    public final int reagentX, reagentY;
    /** rare output slots after the main output */
    public final int[][] rares;

    GuiLayout(String texture, int[][] inputs, int outputX, int outputY, int arrowX, int arrowY, int flameX, int flameY, int textX)
    {
        this(texture, inputs, outputX, outputY, arrowX, arrowY, flameX, flameY, textX, -1, -1, new int[0][]);
    }

    GuiLayout(String texture, int[][] inputs, int outputX, int outputY, int arrowX, int arrowY, int flameX, int flameY, int textX,
              int reagentX, int reagentY, int[][] rares)
    {
        this.texture = ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, "textures/gui/" + texture + ".png");
        this.inputs = inputs;
        this.outputX = outputX;
        this.outputY = outputY;
        this.arrowX = arrowX;
        this.arrowY = arrowY;
        this.flameX = flameX;
        this.flameY = flameY;
        this.textX = textX;
        this.reagentX = reagentX;
        this.reagentY = reagentY;
        this.rares = rares;
    }
}
