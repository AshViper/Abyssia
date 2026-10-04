package com.abyssia.vehicle.client;

import com.abyssia.client.anim.AnimMeshRenderer;
import com.abyssia.vehicle.SubmarineDockBlockEntity.State;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

/**
 * SUB04 dock poses from the generated animation block ({@link DockMesh#ANIMATION_JSON}, source
 * tools/vehicle_models/dock/submarine_dock.parts.json). Angles (Z rotation, degrees, absolute) come from the JSON
 * (IDLE / CAPTURE / DOCKED); the timing windows are the spec's (see SubmarineDockBlockEntity) and easing is cubic.
 */
public final class DockAnim
{
    private static final Map<String, float[]> ANGLES = new HashMap<>();   // part -> {IDLE, CAPTURE, DOCKED, RELEASE} z

    static
    {
        JsonObject root = JsonParser.parseString(DockMesh.ANIMATION_JSON).getAsJsonObject();
        String[] names = {"IDLE", "CAPTURE", "DOCKED", "RELEASE"};
        for (int i = 0; i < names.length; i++)
        {
            JsonObject pose = root.getAsJsonObject(names[i]);
            if (pose == null) continue;
            for (Map.Entry<String, JsonElement> e : pose.entrySet())
            {
                float z = e.getValue().getAsJsonObject().getAsJsonArray("rotation").get(2).getAsFloat();
                ANGLES.computeIfAbsent(e.getKey(), k -> new float[4])[i] = z;
            }
        }
    }

    private DockAnim() {}

    /** Pose provider for {@code state}, {@code t} ticks into it (partial tick included). */
    public static AnimMeshRenderer.PoseProvider poses(State state, float t)
    {
        return (index, part, out) ->
        {
            float[] a = ANGLES.get(part.name());
            if (a == null) return;
            String n = part.name();
            float target;   // absolute Z angle
            if (n.equals("gangway_bridge"))
            {
                float g = switch (state)   // 0 = raised (IDLE), 1 = down (DOCKED)
                {
                    case IDLE, CAPTURE -> 0.0f;
                    case DOCKED -> easeOut(clamp((t - 6.0f) / 14.0f));
                    case RELEASE -> 1.0f - easeIn(clamp(t / 14.0f));
                };
                target = a[0] + (a[2] - a[0]) * g;
            }
            else if (n.endsWith("_upper") || n.endsWith("_lower"))
            {
                boolean upper = n.endsWith("_upper");
                float c = switch (state)   // 0 = open (IDLE), 1 = closed (CAPTURE / DOCKED)
                {
                    case IDLE -> 0.0f;
                    case CAPTURE -> easeInOut(clamp((t - (upper ? 0.0f : 6.0f)) / 18.0f));
                    case DOCKED -> 1.0f;
                    case RELEASE -> 1.0f - easeInOut(clamp((t - (upper ? 20.0f : 14.0f)) / 14.0f));
                };
                target = a[0] + (a[1] - a[0]) * c;
            }
            else return;
            out.rz = target - part.rz();
        };
    }

    private static float clamp(float v)
    {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private static float easeInOut(float x)
    {
        return x < 0.5f ? 4.0f * x * x * x : 1.0f - (float) Math.pow(-2.0 * x + 2.0, 3.0) / 2.0f;
    }

    private static float easeOut(float x)
    {
        return 1.0f - (float) Math.pow(1.0 - x, 3.0);
    }

    private static float easeIn(float x)
    {
        return x * x * x;
    }
}
