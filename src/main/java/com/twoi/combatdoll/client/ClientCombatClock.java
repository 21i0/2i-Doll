package com.twoi.combatdoll.client;
/** Client effect time advances independently of frozen world gameTime. */
public final class ClientCombatClock {
    private static long ticks;
    public static long now(){return ticks;}
    public static void tick(){ticks++;}
}
