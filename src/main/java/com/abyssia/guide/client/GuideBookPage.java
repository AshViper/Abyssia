package com.abyssia.guide.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;

/** The four page types (data only; the layout is in GuideBookScreen). */
public interface GuideBookPage
{
    String id();

    String chapter();

    record Text(String id, String chapter, String title, String text, @Nullable String image, String imagePosition,
                int imageW, int imageH) implements GuideBookPage {}

    record Item(String id, String chapter, String item, @Nullable String title, String description, String obtaining,
                @Nullable String recipe, @Nullable String image) implements GuideBookPage {}

    record Image(String id, String chapter, String title, String image, @Nullable String caption) implements GuideBookPage {}

    record Recipe(String id, String chapter, String title, String recipe, @Nullable String description) implements GuideBookPage {}

    /** @throws IllegalArgumentException with a readable reason when the page is unusable */
    static GuideBookPage parse(JsonObject o, String fallbackChapter)
    {
        String type = str(o, "type", null);
        String id = str(o, "id", null);
        if (type == null || id == null) throw new IllegalArgumentException("missing type or id");
        String chapter = str(o, "chapter", fallbackChapter);
        switch (type)
        {
            case "text":
            {
                int[] size = size(o.get("image_size"));
                return new Text(id, chapter, str(o, "title", ""), req(o, "text"), str(o, "image", null),
                        str(o, "image_position", "top"), size[0], size[1]);
            }
            case "item":
                return new Item(id, chapter, req(o, "item"), str(o, "title", null), str(o, "description", ""),
                        str(o, "obtaining", ""), str(o, "recipe", null), str(o, "image", null));
            case "image":
                return new Image(id, chapter, str(o, "title", ""), req(o, "image"), str(o, "caption", null));
            case "recipe":
                return new Recipe(id, chapter, str(o, "title", ""), req(o, "recipe"), str(o, "description", null));
            default:
                throw new IllegalArgumentException("unknown page type " + type);
        }
    }

    private static String req(JsonObject o, String key)
    {
        String v = str(o, key, null);
        if (v == null) throw new IllegalArgumentException("missing " + key);
        return v;
    }

    private static String str(JsonObject o, String key, String def)
    {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    /** image_size: [w, h], {"w":..,"h":..}, "WxH" or one number (square); 0,0 = automatic. */
    private static int[] size(JsonElement e)
    {
        try
        {
            if (e == null) return new int[]{0, 0};
            if (e.isJsonArray())
            {
                JsonArray a = e.getAsJsonArray();
                return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()};
            }
            if (e.isJsonObject())
                return new int[]{e.getAsJsonObject().get("w").getAsInt(), e.getAsJsonObject().get("h").getAsInt()};
            String s = e.getAsString();
            if (s.contains("x"))
                return new int[]{Integer.parseInt(s.substring(0, s.indexOf('x')).trim()), Integer.parseInt(s.substring(s.indexOf('x') + 1).trim())};
            int n = Integer.parseInt(s.trim());
            return new int[]{n, n};
        }
        catch (RuntimeException ex)
        {
            return new int[]{0, 0};
        }
    }
}
