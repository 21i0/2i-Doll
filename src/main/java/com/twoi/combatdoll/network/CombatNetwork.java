package com.twoi.combatdoll.network;

import net.minecraft.world.entity.LivingEntity;
import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.combat.DollCombatAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;

/** Cast boundaries plus a capped 5 Hz aim update during actual staff charging. */
@EventBusSubscriber(modid=TwoICombatDollMod.MODID)
public final class CombatNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("4");
        com.twoi.combatdoll.duel.network.DuelNetwork.register(registrar);
        registrar.playToClient(Freeze.TYPE, Freeze.CODEC, (payload, context) -> context.enqueueWork(() ->
                com.twoi.combatdoll.client.ClientTimeStop.receive(payload.active?payload.caster:null,payload.owner,payload.ticks)));
        registrar.playToClient(State.TYPE, State.CODEC, (payload, context) -> context.enqueueWork(() ->
                com.twoi.combatdoll.client.ClientCombatSync.receive(payload)));
    }
    private static State snapshot(LivingEntity doll) {
        var state = DollCombatAccess.state(doll);
        return new State(doll.level().dimension().location(), doll.getId(), doll.getUUID(), state.start(), state.targetDelta(),state.kind().ordinal(),state.armed(),com.twoi.combatdoll.combat.CombatWorldData.get(doll.getServer()).clock(),
                state.charging(),state.heldTicks(),state.sceneOrigin(doll),state.stageAnchor(doll),state.blackHole(doll));
    }
    public static void send(LivingEntity doll) {
        if(doll instanceof ServerPlayer player)PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,snapshot(doll));
        else PacketDistributor.sendToPlayersTrackingEntity(doll,snapshot(doll));
    }
    @SubscribeEvent public static void track(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity doll && (doll instanceof DollCombatAccess||doll instanceof net.minecraft.world.entity.player.Player) && event.getEntity() instanceof ServerPlayer player)
            PacketDistributor.sendToPlayer(player, snapshot(doll));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)com.twoi.combatdoll.combat.Chronostasis.joining(p);}
    public static void freeze(net.minecraft.server.MinecraftServer server,UUID caster,UUID owner,int ticks){
        PacketDistributor.sendToAllPlayers(new Freeze(caster!=null,caster==null?new UUID(0,0):caster,owner==null?new UUID(0,0):owner,ticks));
    }
    public static void freeze(ServerPlayer player,UUID caster,UUID owner,int ticks){PacketDistributor.sendToPlayer(player,new Freeze(true,caster,owner,ticks));}
    public record Freeze(boolean active,UUID caster,UUID owner,int ticks) implements CustomPacketPayload {
        public static final Type<Freeze> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID,"time_stop"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Freeze> CODEC=StreamCodec.of((b,p)->{b.writeBoolean(p.active);b.writeUUID(p.caster);b.writeUUID(p.owner);b.writeVarInt(p.ticks);},b->new Freeze(b.readBoolean(),b.readUUID(),b.readUUID(),b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record State(ResourceLocation dimension, int id, UUID uuid, long start, Vec3 delta,int kind,boolean armed,long clock,
                        boolean charging,int heldTicks,Vec3 origin,Vec3 anchor,Vec3 hole) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID,"cast"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p)->{
            b.writeResourceLocation(p.dimension); b.writeVarInt(p.id); b.writeUUID(p.uuid); b.writeLong(p.start);
            b.writeDouble(p.delta.x); b.writeDouble(p.delta.y); b.writeDouble(p.delta.z);
            b.writeVarInt(p.kind);b.writeBoolean(p.armed);b.writeLong(p.clock);
            b.writeBoolean(p.charging);b.writeVarInt(p.heldTicks);
            vector(b,p.origin);vector(b,p.anchor);vector(b,p.hole);
        }, b -> new State(b.readResourceLocation(), b.readVarInt(), b.readUUID(), b.readLong(),
                new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readVarInt(),b.readBoolean(),b.readLong(),
                b.readBoolean(),b.readVarInt(),vector(b),vector(b),vector(b)));
        private static void vector(RegistryFriendlyByteBuf b,Vec3 p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
        private static Vec3 vector(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
