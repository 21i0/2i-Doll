package com.twoi.combatdoll.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local visual preferences never change the server's damage or invitation rules. */
public final class TwoIVisualConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue POST, GARDEN;
    public static final ModConfigSpec.IntValue QUALITY;
    public static final ModConfigSpec.DoubleValue STRENGTH;
    static {
        var b = new ModConfigSpec.Builder();
        b.push("two_i_arcana");
        POST = b.comment("World colour grading and bloom. Geometry still renders when disabled.").define("post_processing", true);
        GARDEN = b.define("garden_decoration", true);
        QUALITY = b.comment("1 = light, 2 = balanced, 3 = detailed. Particle settings can lower this further.")
                .defineInRange("quality", 3, 1, 3);
        STRENGTH = b.comment("World takeover strength; zero uses world geometry without replacing the sky. Does not affect damage.")
                .defineInRange("domain_strength", 1.0, 0, 1);
        b.pop(); SPEC = b.build();
    }
    private TwoIVisualConfig() { }
}
