package com.twoi.combatdoll.companion.content.entity;
import net.minecraft.server.level.ServerPlayer;
public final class TimeStopManager {
 public static boolean start(ServerPlayer player) {
  if(!com.twoi.combatdoll.companion.config.StretcherConfig.serverTimeStop())return false;
  com.twoi.combatdoll.combat.Chronostasis.start(player,100);return true;
 }
 public static boolean startPanic(ServerPlayer player) {
  if(!com.twoi.combatdoll.companion.config.StretcherConfig.serverTimeStop())return false;
  com.twoi.combatdoll.combat.Chronostasis.start(player,60);return true;
 }
}
