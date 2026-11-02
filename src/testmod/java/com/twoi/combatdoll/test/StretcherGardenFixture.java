package com.twoi.combatdoll.test;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import com.twoi.combatdoll.combat.DollCombatAccess;

/** Called only by the opt-in compatibility run, never by standalone tests or production. */
final class StretcherGardenFixture {
    static net.minecraft.world.entity.PathfinderMob spawn(ServerPlayer player){
        var doll=(PathfinderMob)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("useless_stretcher:two_i_doll")).create(player.serverLevel());
        try { doll.getClass().getMethod("setOwnerId",java.util.UUID.class).invoke(doll,player.getUUID()); }
        catch(ReflectiveOperationException failure){throw new AssertionError("Original owner API changed",failure);}
        return doll;
    }
    static void transfer(ServerPlayer player){
        var original=spawn(player);
        original.moveTo(player.getX()+1,player.getY(),player.getZ(),0,0);
        try { original.getClass().getMethod("restoreBond",int.class,boolean.class).invoke(original,100,false); }
        catch(ReflectiveOperationException failure){throw new AssertionError("Original bond API changed",failure);}
        player.serverLevel().addFreshEntity(original);
        if(!((Object)original instanceof DollCombatAccess))throw new AssertionError("Optional original 2i bridge missing");
        try {
            var service=Class.forName("com.sorrowmist.useless.stretcher.content.entity.TwoIDollService");
            if(!Boolean.TRUE.equals(service.getMethod("sendPlayerToStarfield",ServerPlayer.class,boolean.class,original.getClass()).invoke(null,player,false,original)))
                throw new AssertionError("Real garden transfer failed");
        }catch(ReflectiveOperationException failure){throw new AssertionError("Original garden API changed",failure);}
        player.connection.teleport(8.5,64,32.5,180,-24);
    }
    static void show(ServerPlayer player){
        try { Class.forName("com.sorrowmist.useless.stretcher.network.TwoIStarfieldShowNetwork")
                .getMethod("send",ServerPlayer.class,BlockPos.class,long.class)
                .invoke(null,player,new BlockPos(8,64,8),player.serverLevel().getGameTime()+600); }
        catch(ReflectiveOperationException failure){throw new AssertionError("Original show API changed",failure);}
    }
}
