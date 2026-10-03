package com.abyssia.guide.client;

import java.util.List;
import java.util.Map;

/** Loaded guide content: chapters in order, pages by id, and the texts of one language (en_us fallback merged in). */
public final class GuideBookData
{
    public record Chapter(String id, String titleKey, String icon, int order, List<String> pageIds) {}

    public final String langCode;
    public final List<Chapter> chapters;
    public final Map<String, GuideBookPage> pages;
    private final Map<String, String> lang;

    public GuideBookData(String langCode, List<Chapter> chapters, Map<String, GuideBookPage> pages, Map<String, String> lang)
    {
        this.langCode = langCode;
        this.chapters = chapters;
        this.pages = pages;
        this.lang = lang;
    }

    /** The text for a key; a missing key shows the key itself. */
    public String tr(String key)
    {
        if (key == null) return "";
        return lang.getOrDefault(key, key);
    }
}
