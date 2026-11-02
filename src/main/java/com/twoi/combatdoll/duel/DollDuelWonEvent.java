package com.twoi.combatdoll.duel;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import java.util.UUID;

/** Emitted only after the server has resolved the winning round and saved its receipt. */
public final class DollDuelWonEvent extends Event {
    private final ServerPlayer player;
    private final UUID duelId;
    private final int rounds;
    public DollDuelWonEvent(ServerPlayer player,UUID duelId,int rounds){this.player=player;this.duelId=duelId;this.rounds=rounds;}
    public ServerPlayer player(){return player;}
    public UUID duelId(){return duelId;}
    public int rounds(){return rounds;}
    public String opponent(){return "piston:piston_doll";}
}
