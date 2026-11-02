package com.twoi.combatdoll.api;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.LinkedHashMap;

/** Register additional definitions during mod construction on both sides. Duplicate IDs fail fast. */
public final class DollCatalog {
    private static final Map<ResourceLocation,DollDefinition> TYPES=new LinkedHashMap<>();
    public static final ResourceLocation TWO_I=ResourceLocation.fromNamespaceAndPath("two_i_combat_doll","two_i");
    static { register(new DollDefinition(TWO_I,ResourceLocation.fromNamespaceAndPath("two_i_combat_doll","textures/entity/two_i.png"),24,8)); }
    public static synchronized void register(DollDefinition definition) {
        if(TYPES.putIfAbsent(definition.id(),definition)!=null) throw new IllegalArgumentException("Duplicate doll "+definition.id());
    }
    public static DollDefinition get(ResourceLocation id) { return TYPES.getOrDefault(id,TYPES.get(TWO_I)); }
    public static java.util.Collection<DollDefinition> all() { return java.util.List.copyOf(TYPES.values()); }
}
