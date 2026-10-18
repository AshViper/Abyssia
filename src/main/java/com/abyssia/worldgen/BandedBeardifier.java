package com.abyssia.worldgen;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla's {@link Beardifier} walks every structure piece near the chunk for every block of the column. With a 1328-block
 * world and a trial chamber nearby (dozens of encapsulated pieces) that was most of the noise fill. Every piece and junction
 * contributes exactly 0 more than 12 blocks above or below its box (and ground level), so each height here only walks the
 * pieces and junctions that reach it, in vanilla's order and with vanilla's formulas: the same values.
 */
public final class BandedBeardifier extends Beardifier
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Kernel radius 12 (beard and bury), plus one block of margin. */
    private static final int REACH = 13;
    private static final int[] NONE = new int[0];
    private static final Field PIECES, JUNCTIONS;

    static
    {
        Field pieces = null, junctions = null;
        try
        {
            pieces = Beardifier.class.getDeclaredField("pieceIterator");
            junctions = Beardifier.class.getDeclaredField("junctionIterator");
            pieces.setAccessible(true);
            junctions.setAccessible(true);
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            LOGGER.warn("[Abyssia] Beardifier fields not accessible; structure terrain adaptation stays vanilla", e);
            pieces = junctions = null;
        }
        PIECES = pieces;
        JUNCTIONS = junctions;
    }

    private final Rigid[] rigids;
    private final JigsawJunction[] junctions;
    private final int y0;
    /** Per height from y0: indices of the pieces / junctions that can contribute there, ascending. */
    private final int[][] rigidsAt, junctionsAt;

    private BandedBeardifier(List<Rigid> rigids, List<JigsawJunction> junctions)
    {
        super(new ObjectArrayList<>(rigids).iterator(), new ObjectArrayList<>(junctions).iterator());
        this.rigids = rigids.toArray(new Rigid[0]);
        this.junctions = junctions.toArray(new JigsawJunction[0]);
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        int[] rLo = new int[this.rigids.length], rHi = new int[this.rigids.length];
        for (int i = 0; i < this.rigids.length; i++)
        {
            BoundingBox box = this.rigids[i].box();
            int ground = box.minY() + this.rigids[i].groundLevelDelta();
            rLo[i] = Math.min(box.minY(), ground) - REACH;
            rHi[i] = Math.max(box.maxY(), ground) + REACH;
            lo = Math.min(lo, rLo[i]);
            hi = Math.max(hi, rHi[i]);
        }
        for (JigsawJunction junction : this.junctions)
        {
            lo = Math.min(lo, junction.getSourceGroundY() - REACH);
            hi = Math.max(hi, junction.getSourceGroundY() + REACH);
        }
        if (lo > hi)
        {
            y0 = 0;
            rigidsAt = junctionsAt = new int[0][];
            return;
        }
        y0 = lo;
        int n = hi - lo + 1;
        rigidsAt = new int[n][];
        junctionsAt = new int[n][];
        IntArrayList r = new IntArrayList(), j = new IntArrayList();
        for (int y = lo; y <= hi; y++)
        {
            r.clear();
            j.clear();
            for (int i = 0; i < this.rigids.length; i++) if (y >= rLo[i] && y <= rHi[i]) r.add(i);
            for (int i = 0; i < this.junctions.length; i++)
            {
                int ground = this.junctions[i].getSourceGroundY();
                if (y >= ground - REACH && y <= ground + REACH) j.add(i);
            }
            rigidsAt[y - lo] = r.isEmpty() ? NONE : r.toIntArray();
            junctionsAt[y - lo] = j.isEmpty() ? NONE : j.toIntArray();
        }
    }

    /** Vanilla's beardifier for the chunk, sorted by height (vanilla's own if its pieces cannot be read). */
    @SuppressWarnings("unchecked")
    public static Beardifier forStructuresInChunk(StructureManager structureManager, ChunkPos pos)
    {
        Beardifier vanilla = Beardifier.forStructuresInChunk(structureManager, pos);
        // Another mod's beardifier (a subclass) keeps its own compute.
        if (PIECES == null || vanilla.getClass() != Beardifier.class) return vanilla;
        List<Rigid> rigids = new ArrayList<>();
        List<JigsawJunction> junctions = new ArrayList<>();
        try
        {
            ObjectListIterator<Rigid> pieces = (ObjectListIterator<Rigid>) PIECES.get(vanilla);
            while (pieces.hasNext()) rigids.add(pieces.next());
            pieces.back(Integer.MAX_VALUE);
            ObjectListIterator<JigsawJunction> joints = (ObjectListIterator<JigsawJunction>) JUNCTIONS.get(vanilla);
            while (joints.hasNext()) junctions.add(joints.next());
            joints.back(Integer.MAX_VALUE);
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            return vanilla;
        }
        return new BandedBeardifier(rigids, junctions);
    }

    /** {@link Beardifier#compute} restricted to the pieces and junctions listed for this height (the others add exactly 0). */
    @Override
    public double compute(DensityFunction.FunctionContext context)
    {
        int y = context.blockY();
        int band = y - y0;
        if (band < 0 || band >= rigidsAt.length) return 0.0;
        int x = context.blockX(), z = context.blockZ();
        double d = 0.0;
        for (int index : rigidsAt[band])
        {
            Rigid rigid = rigids[index];
            BoundingBox box = rigid.box();
            int ground = rigid.groundLevelDelta();
            int dx = Math.max(0, Math.max(box.minX() - x, x - box.maxX()));
            int dz = Math.max(0, Math.max(box.minZ() - z, z - box.maxZ()));
            int base = box.minY() + ground;
            int above = y - base;
            int dy = switch (rigid.terrainAdjustment())
            {
                case NONE -> 0;
                case BURY, BEARD_THIN -> above;
                case BEARD_BOX -> Math.max(0, Math.max(base - y, y - box.maxY()));
                case ENCAPSULATE -> Math.max(0, Math.max(box.minY() - y, y - box.maxY()));
            };
            d += switch (rigid.terrainAdjustment())
            {
                case NONE -> 0.0;
                case BURY -> getBuryContribution(dx, dy / 2.0, dz);
                case BEARD_THIN, BEARD_BOX -> getBeardContribution(dx, dy, dz, above) * 0.8;
                case ENCAPSULATE -> getBuryContribution(dx / 2.0, dy / 2.0, dz / 2.0) * 0.8;
            };
        }
        for (int index : junctionsAt[band])
        {
            JigsawJunction junction = junctions[index];
            int dx = x - junction.getSourceX(), dy = y - junction.getSourceGroundY(), dz = z - junction.getSourceZ();
            d += getBeardContribution(dx, dy, dz, dy) * 0.4;
        }
        return d;
    }
}
