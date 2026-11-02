package com.twoi.combatdoll.client.compat;

import com.twoi.combatdoll.mixin.StarfieldClockAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/** Optional presentation adapter for visits to the old Stretcher garden. */
public final class LegacyStarfieldVisuals {
    private static final ResourceLocation GARDEN = ResourceLocation.parse("useless_stretcher:two_i_starfield");
    private LegacyStarfieldVisuals() { }

    public static boolean isGarden(ClientLevel level) {
        return level != null && ModList.get().isLoaded("useless_stretcher")
                && level.dimension().location().equals(GARDEN);
    }

    public static float showAge(ClientLevel level, float partial) {
        if (!isGarden(level)) return -1;
        long end = StarfieldClockAccessor.twoi$showEnd();
        long remaining = end - level.getGameTime();
        return remaining <= 0 || remaining > 600 ? -1 : 600 - remaining + partial;
    }
}
