package com.twoi.combatdoll.companion.config;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class StretcherConfig {
 private static final ModConfigSpec.Builder B=new ModConfigSpec.Builder();
 private static final ModConfigSpec.BooleanValue INVITATION=B.comment("Allow 2i's private starfield invitation.").define("starfieldInvitation",true);
 private static final ModConfigSpec.BooleanValue TIME_STOP=B.comment("Allow 2i's rare companion time-stop interaction.").define("companionTimeStop",true);
 private static final ModConfigSpec.BooleanValue LEAF_DROP=B.comment("Standalone only: one lifetime leaf reward per player per world. With Stretcher installed its existing shared roll is used.").define("enableDollLeafDrop",true);
 private static final ModConfigSpec.DoubleValue LEAF_PROBABILITY=B.comment("Chance per broken leaf; 0.0001 is one in ten thousand.").defineInRange("dollLeafDropProbability",0.0001D,0D,1D);
 public static final ModConfigSpec SPEC=B.build();
 public static boolean starfieldInvitationEnabled(){return INVITATION.get();}
 public static boolean serverTimeStop(){return TIME_STOP.get();}
 public static boolean enableDollLeafDrop(){return LEAF_DROP.get();}
 public static double dollLeafDropProbability(){return LEAF_PROBABILITY.get();}
}
