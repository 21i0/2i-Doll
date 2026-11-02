package com.twoi.combatdoll.companion.client.render;
/** Shared item singleton: hands, head, Curios and display use the canonical combat rig. */
public final class TwoIDollItemRenderer {
 private TwoIDollItemRenderer() { }
 public static com.twoi.combatdoll.client.TwoIDollItemRenderer get(){return com.twoi.combatdoll.client.TwoIDollItemRenderer.get();}
 public static float animationTime(){return com.twoi.combatdoll.client.TwoIDollItemRenderer.animationTime();}
}
