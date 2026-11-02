package com.twoi.combatdoll.duel.network;

import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.duel.DollDuelArena;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.util.UUID;

public final class DuelNetwork {
    private DuelNetwork(){}
    public static void register(PayloadRegistrar registrar){
        registrar.playToClient(State.TYPE,State.CODEC,(p,c)->c.enqueueWork(()->com.twoi.combatdoll.duel.client.DuelScreen.receive(p.tag)));
        registrar.playToServer(Move.TYPE,Move.CODEC,(p,c)->c.enqueueWork(()->{
            if(c.player() instanceof ServerPlayer player)DollDuelArena.move(player,p.duel,p.turn,p.move);
        }));
    }
    public static void send(ServerPlayer player,CompoundTag tag){PacketDistributor.sendToPlayer(player,new State(tag));}
    public record State(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID,"duel_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p)->b.writeNbt(p.tag),b->{CompoundTag tag=b.readNbt();return new State(tag==null?new CompoundTag():tag);});
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Move(UUID duel,int turn,int move) implements CustomPacketPayload {
        public static final Type<Move> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(TwoICombatDollMod.MODID,"duel_move"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Move> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.duel);b.writeVarInt(p.turn);b.writeVarInt(p.move);},b->new Move(b.readUUID(),b.readVarInt(),b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
}
