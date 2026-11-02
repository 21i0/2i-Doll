package com.twoi.combatdoll.config;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class DollServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue LEAF_CHANCE;
    static {
        var b=new ModConfigSpec.Builder();
        LEAF_CHANCE=b.comment("Standalone 2i leaf reward chance, once per player per save. Disabled automatically when Useless Stretcher supplies its existing 2i reward.")
                .defineInRange("two_i_leaf_chance",.0001,0,1);
        SPEC=b.build();
    }
}
