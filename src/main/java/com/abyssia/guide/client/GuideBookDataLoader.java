package com.abyssia.guide.client;

import com.abyssia.Abyssia;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads assets/abyssia/guide/chapters.json, pages/&lt;chapter&gt;.json and lang/&lt;code&gt;.json (en_us underneath the
 * current language). Reloads with the resources (a language change reloads them too). Bad data is logged and skipped.
 */
public class GuideBookDataLoader implements ResourceManagerReloadListener
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile GuideBookData data;

    @Override
    public void onResourceManagerReload(ResourceManager manager)
    {
        data = load(manager);
    }

    /** The current data (loaded on demand, reloaded when the language no longer matches). */
    public static GuideBookData get()
    {
        GuideBookData d = data;
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        if (d == null || !d.langCode.equals(code))
        {
            d = load(Minecraft.getInstance().getResourceManager());
            data = d;
        }
        return d;
    }

    static GuideBookData load(ResourceManager rm)
    {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        Map<String, String> lang = new HashMap<>();
        readLang(rm, "en_us", lang);
        if (!"en_us".equals(code)) readLang(rm, code, lang);

        List<GuideBookData.Chapter> chapters = new ArrayList<>();
        Map<String, GuideBookPage> pages = new LinkedHashMap<>();
        JsonElement root = readElement(rm, rl("guide/chapters.json"));
        JsonArray list = root != null && root.isJsonObject() && root.getAsJsonObject().get("chapters") instanceof JsonArray a ? a : new JsonArray();
        for (JsonElement e : list)
        {
            try
            {
                JsonObject o = e.getAsJsonObject();
                String id = o.get("id").getAsString();
                List<String> ids = new ArrayList<>();
                if (o.get("pages") instanceof JsonArray pa) for (JsonElement p : pa) ids.add(p.getAsString());
                chapters.add(new GuideBookData.Chapter(id, o.has("title") ? o.get("title").getAsString() : "guide.abyssia." + id + ".title",
                        o.has("icon") ? o.get("icon").getAsString() : "", o.has("order") ? o.get("order").getAsInt() : chapters.size() + 1, ids));
                readPages(rm, id, pages);
            }
            catch (RuntimeException ex)
            {
                LOGGER.warn("Guide book: skipping bad chapter entry {}: {}", e, ex.toString());
            }
        }
        chapters.sort(Comparator.comparingInt(GuideBookData.Chapter::order));
        return new GuideBookData(code, chapters, pages, lang);
    }

    private static void readPages(ResourceManager rm, String chapter, Map<String, GuideBookPage> out)
    {
        JsonElement root = readElement(rm, rl("guide/pages/" + chapter + ".json"));
        JsonArray arr = root == null ? null : root.isJsonArray() ? root.getAsJsonArray()
                : root.isJsonObject() && root.getAsJsonObject().get("pages") instanceof JsonArray a ? a : null;
        if (arr == null) return;
        for (JsonElement e : arr)
        {
            try
            {
                GuideBookPage page = GuideBookPage.parse(e.getAsJsonObject(), chapter);
                if (out.putIfAbsent(page.id(), page) != null) LOGGER.warn("Guide book: duplicate page id {}", page.id());
            }
            catch (RuntimeException ex)
            {
                LOGGER.warn("Guide book: skipping bad page in {}: {}", chapter, ex.toString());
            }
        }
    }

    private static void readLang(ResourceManager rm, String code, Map<String, String> into)
    {
        for (Resource res : rm.getResourceStack(rl("guide/lang/" + code + ".json")))
        {
            try (Reader r = res.openAsReader())
            {
                for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(r).getAsJsonObject().entrySet())
                    if (e.getValue().isJsonPrimitive()) into.put(e.getKey(), e.getValue().getAsString());
            }
            catch (Exception ex)
            {
                LOGGER.warn("Guide book: bad lang file {} ({}): {}", code, res.sourcePackId(), ex.toString());
            }
        }
    }

    private static JsonElement readElement(ResourceManager rm, ResourceLocation loc)
    {
        try (Reader r = rm.getResource(loc).orElseThrow().openAsReader())
        {
            return JsonParser.parseReader(r);
        }
        catch (Exception ex)
        {
            LOGGER.warn("Guide book: cannot read {}: {}", loc, ex.toString());
            return null;
        }
    }

    private static ResourceLocation rl(String path)
    {
        return ResourceLocation.fromNamespaceAndPath(Abyssia.MODID, path);
    }
}
