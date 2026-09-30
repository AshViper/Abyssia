package com.abyssia.fauna.external;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A list of entity patterns from the config or a data file: {@code "mod:entity"}, {@code "mod:*"} (every entity of a
 * mod) or {@code "#namespace:tag"} (an entity type tag). Ids of mods that are not installed simply match nothing.
 */
public final class EntityPatterns
{
    public static final EntityPatterns EMPTY = new EntityPatterns(List.of());

    private final Set<ResourceLocation> ids = new HashSet<>();
    private final Set<String> namespaces = new HashSet<>();
    private final List<TagKey<EntityType<?>>> tags;

    public EntityPatterns(List<? extends String> patterns)
    {
        List<TagKey<EntityType<?>>> tagList = new ArrayList<>();
        for (String raw : patterns)
        {
            String p = raw.trim();
            if (p.isEmpty()) continue;
            if (p.startsWith("#"))
            {
                ResourceLocation tag = ResourceLocation.tryParse(p.substring(1));
                if (tag != null) tagList.add(TagKey.create(Registries.ENTITY_TYPE, tag));
            }
            else if (p.endsWith(":*"))
            {
                this.namespaces.add(p.substring(0, p.length() - 2));
            }
            else
            {
                ResourceLocation id = ResourceLocation.tryParse(p);
                if (id != null) this.ids.add(id);
            }
        }
        this.tags = List.copyOf(tagList);
    }

    public boolean isEmpty()
    {
        return this.ids.isEmpty() && this.namespaces.isEmpty() && this.tags.isEmpty();
    }

    public boolean matches(EntityType<?> type, ResourceLocation id)
    {
        if (this.ids.contains(id) || this.namespaces.contains(id.getNamespace())) return true;
        for (TagKey<EntityType<?>> tag : this.tags)
        {
            if (type.is(tag)) return true;
        }
        return false;
    }
}
