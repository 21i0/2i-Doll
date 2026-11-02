package com.twoi.combatdoll.companion.content.entity;

import com.twoi.combatdoll.compat.StretcherCompanionBridge;
import net.minecraft.server.MinecraftServer;
import java.util.UUID;

/** Local facade; all optional implementation access stays in the compat package. */
public final class SharedLegacyTwoI {
    private SharedLegacyTwoI() { }
    public static Object data(MinecraftServer server, String name) { return StretcherCompanionBridge.data(server, name); }
    public static <T> T field(Object source, String name) { return StretcherCompanionBridge.field(source, name); }
    public static int[] story(MinecraftServer server, UUID player) { return StretcherCompanionBridge.story(server, player); }
    public static void complete(MinecraftServer server, UUID player, int chapter) { StretcherCompanionBridge.complete(server, player, chapter); }
    public static boolean acquisition(MinecraftServer server, UUID doll) { return StretcherCompanionBridge.acquisition(server, doll); }
    public static TwoICompanionshipData.Sample clock(MinecraftServer server, UUID doll, TwoICompanionshipData.Progress local, long time, boolean sample) {
        return StretcherCompanionBridge.clock(server, doll, local, time, sample);
    }
}