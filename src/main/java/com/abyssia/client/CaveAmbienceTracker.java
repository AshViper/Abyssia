package com.abyssia.client;

import com.abyssia.Abyssia;
import com.abyssia.Config;
import com.abyssia.environment.CaveAmbience;
import com.abyssia.environment.OceanCurrentManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Measures the camera's surroundings four times a second with ten short ray casts (eight horizontal, up, down):
 * a roof overhead plus walls on most sides means a cave; the average distance gives the cavity size; the narrowest
 * pair of opposite rays gives the passage width, and the longest pair its axis. The eased result goes to
 * {@link CaveAmbience} for marine snow, particle drift and spores. No block scan, no per-tick work beyond a counter.
 * <p>
 * Water squeezing through a narrow passage runs faster: the flow follows the passage axis, in whichever sense the
 * regional ocean current (or up/downwelling, in a shaft) pushes it.
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CaveAmbienceTracker
{
    private static final int INTERVAL = 5;
    private static final int RAY_LENGTH = 40;
    private static final int ROOF_RANGE = 32;
    /** Passages narrower than this (wall to wall) start channelling the flow. */
    private static final float NARROW_WIDTH = 12f;
    private static final double MAX_PASSAGE_FLOW = 0.04;
    /** The cavern probe: once a second, rays long enough to span a colossal cavern from its middle. */
    private static final int CAVERN_INTERVAL = 20;
    private static final int CAVERN_RAY = 96;
    private static final Vec3 UP = new Vec3(0, 1, 0), DOWN = new Vec3(0, -1, 0);
    private static final Vec3[] HORIZONTAL = new Vec3[8];

    static
    {
        for (int i = 0; i < 8; i++) HORIZONTAL[i] = new Vec3(Math.cos(i * Math.PI / 4), 0, Math.sin(i * Math.PI / 4));
    }

    private static int tick;
    private static float enclosure, cavity = RAY_LENGTH, narrowness;
    private static float cavern, cavernTarget;
    private static Vec3 flow = Vec3.ZERO;

    private CaveAmbienceTracker() {}

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event)
    {
        if (event.getLevel().isClientSide())
        {
            CaveAmbience.reset();
            enclosure = 0;
            narrowness = 0;
            cavity = RAY_LENGTH;
            flow = Vec3.ZERO;
            cavern = cavernTarget = 0;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused() || tick++ % INTERVAL != 0) return;

        Vec3 eye = player.getEyePosition();
        if ((tick - 1) % CAVERN_INTERVAL == 0) cavernTarget = measureCavern(level, eye);
        float[] h = new float[8];
        int walls = 0;
        float sum = 0;
        for (int i = 0; i < 8; i++)
        {
            h[i] = ray(level, eye, HORIZONTAL[i], RAY_LENGTH);
            if (h[i] < RAY_LENGTH) walls++;
            sum += h[i];
        }
        float up = ray(level, eye, UP, RAY_LENGTH), down = ray(level, eye, DOWN, RAY_LENGTH);

        float roof = up <= ROOF_RANGE ? 1f : 0f;
        float targetEnclosure = roof * Mth.clamp((walls / 8f - 0.25f) / 0.6f, 0f, 1f);
        float targetCavity = (sum / 8f + (up + down) * 0.5f) * 0.5f;

        // Narrowest cross-section and the passage axis (longest open line through the viewer).
        float narrowest = up + down;
        float longest = up + down;
        Vec3 axis = new Vec3(0, 1, 0);
        for (int i = 0; i < 4; i++)
        {
            float width = h[i] + h[i + 4];
            narrowest = Math.min(narrowest, width);
            if (width > longest)
            {
                longest = width;
                axis = HORIZONTAL[i];
            }
        }
        float targetNarrow = targetEnclosure * Mth.clamp((NARROW_WIDTH - narrowest) / (NARROW_WIDTH - 4f), 0f, 1f);
        Vec3 targetFlow = Vec3.ZERO;
        if (targetNarrow > 0 && Config.CAVE_CURRENT_EFFECTS.get())
        {
            BlockPos pos = player.blockPosition();
            Vec3 current = OceanCurrentManager.getCurrent(level, pos);
            // Follow the regional current's sense along the passage; in a shaft, the up/downwelling (default: up).
            double sense = axis.y > 0.5 ? (current.y < 0 ? -1 : 1) : Math.signum(current.x * axis.x + current.z * axis.z);
            if (sense == 0) sense = 1;
            double strength = MAX_PASSAGE_FLOW * targetNarrow * (Config.CURRENT_STRENGTH.get() / 0.25);
            targetFlow = axis.scale(sense * strength);
        }

        enclosure += (targetEnclosure - enclosure) * 0.35f;
        cavity += (targetCavity - cavity) * 0.35f;
        narrowness += (targetNarrow - narrowness) * 0.35f;
        flow = flow.lerp(targetFlow, 0.35);
        cavern += (cavernTarget - cavern) * 0.35f;
        CaveAmbience.update(eye.x, eye.y, eye.z, enclosure, cavity, narrowness, flow);
        CaveAmbience.updateCavern(cavern);
    }

    /**
     * How deep inside a large cavern the camera is (0..1): roof and floor in reach, walls in most directions, and the
     * space around it wide and tall. Plants pass the rays, so a kelp forest never counts as a wall.
     */
    private static float measureCavern(ClientLevel level, Vec3 eye)
    {
        float up = ray(level, eye, UP, CAVERN_RAY), down = ray(level, eye, DOWN, CAVERN_RAY);
        if (up >= CAVERN_RAY || down >= CAVERN_RAY) return 0f;
        int walls = 0;
        float sum = 0;
        for (Vec3 dir : HORIZONTAL)
        {
            float d = ray(level, eye, dir, CAVERN_RAY);
            if (d < CAVERN_RAY) walls++;
            sum += d;
        }
        if (walls < 5) return 0f;
        // Large caves (walls ~15-25 away) get a little, massive halls (25+ away, 30+ tall) the full effect.
        float size = Mth.clamp((Math.min(sum / 8f * 2f, (up + down) * 1.5f) - 28f) / 40f, 0f, 1f);
        return size * size * (3f - 2f * size) * Mth.clamp((walls - 4) / 3f, 0f, 1f);
    }

    /** Distance to the first collidable block along a ray (plants and water pass), or {@code length}. */
    private static float ray(ClientLevel level, Vec3 from, Vec3 dir, int length)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int t = 1; t <= length; t++)
        {
            pos.set(from.x + dir.x * t, from.y + dir.y * t, from.z + dir.z * t);
            if (!level.isLoaded(pos)) return length;
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return t;
        }
        return length;
    }
}
