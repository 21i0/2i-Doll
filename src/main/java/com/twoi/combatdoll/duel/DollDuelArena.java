package com.twoi.combatdoll.duel;

import com.twoi.combatdoll.TwoICombatDollMod;
import com.twoi.combatdoll.duel.network.DuelNetwork;
import com.customdoll.longhuan.content.DollOwnership;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Personal, source-independent Piston duel. Public begin/move are the story bridge. */
@EventBusSubscriber(modid=TwoICombatDollMod.MODID)
public final class DollDuelArena {
    public static final String DATA="two_i_combat_doll_duel", PROJECTION="two_i_duel_projection";
    private static final Map<UUID,Session> ACTIVE=new HashMap<>();
    private DollDuelArena(){}
    private static final class Session {
        final UUID id,owner;final ServerLevel level;final DuelRules rules;final Vec3 home,direction;
        LivingEntity dragon,piston;long nextAction,lastMoveAt=-100;
        Session(UUID id,ServerPlayer p,DuelRules rules){this.id=id;owner=p.getUUID();level=p.serverLevel();this.rules=rules;
            Vec3 dir=p.getLookAngle().multiply(1,0,1).normalize();direction=dir.lengthSqr()<.1?new Vec3(0,0,1):dir;home=p.position().add(direction.scale(4));}
    }
    /** Caller can restrict story progression; the standalone command remains playable. */
    public static boolean begin(ServerPlayer player){
        if(!player.isAlive()||player.isSpectator())return false;
        if(!storyAllows(player)){
            player.displayClientMessage(Component.literal("活塞正在等待龙幻路线的毕业挑战。请先完成你的旅程并抵达合流章节。"),false);return false;
        }
        Session current=ACTIVE.get(player.getUUID());
        if(current!=null){send(player,current,true);return true;}
        ItemStack doll=ownedDragon(player);
        if(doll.isEmpty()){player.displayClientMessage(Component.literal("请携带属于你的龙幻玩偶，或在身边放出她，再挑战活塞。"),false);return false;}
        CompoundTag row=player.getPersistentData().getCompound(DATA);
        DuelRules rules=row.getBoolean("active")?read(row):new DuelRules();
        if(!rules.valid()||rules.outcome!=0)rules=new DuelRules();
        UUID id=row.getBoolean("active")&&row.hasUUID("duel_id")?row.getUUID("duel_id"):UUID.randomUUID();
        Session s=new Session(id,player,rules);ACTIVE.put(player.getUUID(),s);
        try{
            s.dragon=com.customdoll.longhuan.init.ModEntities.TWO_I_DOLL.get().create(s.level);
            s.piston=com.customdoll.piston.init.ModEntities.TWO_I_DOLL.get().create(s.level);
            if(s.dragon==null||s.piston==null)throw new IllegalStateException("Duel actors unavailable");
            // Projections have no physical reward carrier. Even a third-party
            // capture or direct reclaim call cannot extract a duplicate doll.
            ((com.customdoll.longhuan.content.entity.DollEntity)s.dragon).initialize(player.getUUID(),ItemStack.EMPTY);
            ((com.customdoll.piston.content.entity.DollEntity)s.piston).initialize(player.getUUID(),ItemStack.EMPTY);
            initialize(s,s.dragon);initialize(s,s.piston);pose(s,0);
            if(!s.level.addFreshEntity(s.dragon)||!s.level.addFreshEntity(s.piston))throw new IllegalStateException("Duel actor spawn rejected");
            save(player,s);send(player,s,true);return true;
        }catch(RuntimeException failure){cleanup(s);ACTIVE.remove(player.getUUID());player.displayClientMessage(Component.literal("训练场暂时无法展开，请换一处空地重试。"),false);com.mojang.logging.LogUtils.getLogger().warn("Piston duel initialization failed",failure);return false;}
    }
    private static boolean storyAllows(ServerPlayer player){
        if(!net.neoforged.fml.ModList.get().isLoaded("doll_chronicle"))return true;
        try{
            var type=Class.forName("com.twoi.chronicle.server.RouteProgression");
            return Boolean.TRUE.equals(type.getMethod("canStartPistonDuel",ServerPlayer.class).invoke(null,player));
        }catch(ReflectiveOperationException|LinkageError failure){
            com.mojang.logging.LogUtils.getLogger().warn("Chronicle duel graduation gate unavailable; refusing to bypass it",failure);return false;
        }
    }
    private static ItemStack ownedDragon(ServerPlayer p){
        for(int slot=0;slot<p.getInventory().getContainerSize();slot++){
            ItemStack stack=p.getInventory().getItem(slot);
            if(stack.is(com.customdoll.longhuan.init.ModItems.TWO_I_DOLL.get())&&DollOwnership.mayUse(stack,p)){DollOwnership.claim(stack,p);p.getInventory().setChanged();return stack;}
        }
        for(var doll:p.serverLevel().getEntitiesOfClass(com.customdoll.longhuan.content.entity.DollEntity.class,p.getBoundingBox().inflate(24)))
            if(!isDuelActor(doll)&&p.getUUID().equals(doll.ownerId()))return doll.storedCopy();
        return ItemStack.EMPTY;
    }
    private static void initialize(Session s,LivingEntity actor){
        actor.getPersistentData().putUUID(PROJECTION,s.id);actor.addTag(PROJECTION);
        actor.setInvulnerable(true);actor.setNoGravity(true);actor.setSilent(true);
        if(actor instanceof Mob mob)mob.setNoAi(true);
    }
    /** Commands and trusted integration API use the same server rules as network input. */
    public static void move(ServerPlayer player,int move){Session s=ACTIVE.get(player.getUUID());if(s!=null)move(player,s.id,s.rules.turn,move);}
    public static void move(ServerPlayer player,UUID id,int expectedTurn,int move){
        Session s=ACTIVE.get(player.getUUID());
        if(s==null||!s.id.equals(id)||s.level!=player.level()||!player.isAlive())return;
        if(s.rules.turn!=expectedTurn||s.level.getGameTime()<s.nextAction){send(player,s);return;}
        if(move==-1){s.rules.outcome=3;s.rules.log="你让龙幻收起荆棘，本场挑战已退出。";save(player,s);send(player,s);cleanup(s);ACTIVE.remove(player.getUUID());return;}
        if(!s.rules.play(move)){send(player,s);return;}
        s.lastMoveAt=s.level.getGameTime();s.nextAction=s.lastMoveAt+28;
        save(player,s);
        if(s.rules.outcome==1){
            CompoundTag row=player.getPersistentData().getCompound(DATA);
            // No command/client can set this receipt. It follows a resolved, valid
            // server win against exactly Piston using this player's Longhuan.
            row.putBoolean("piston_win",true);row.putUUID("piston_win_duel",s.id);
            row.putUUID("piston_win_player",player.getUUID());row.putString("piston_opponent","piston:piston_doll");
            row.putInt("piston_win_rounds",s.rules.accepted);row.putLong("piston_win_time",s.level.getGameTime());
            player.getPersistentData().put(DATA,row);
            NeoForge.EVENT_BUS.post(new DollDuelWonEvent(player,s.id,s.rules.accepted));
        }
        s.level.playSound(null,player.blockPosition(),move==1?SoundEvents.SHIELD_BLOCK:SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.PLAYERS,.8F,move==3?.65F:1.2F);
        send(player,s);
    }
    public static boolean isDuelActor(Entity actor){return actor.getPersistentData().hasUUID(PROJECTION);}
    public static boolean hasPistonWin(ServerPlayer player){
        CompoundTag row=player.getPersistentData().getCompound(DATA);
        return row.getBoolean("piston_win")&&row.hasUUID("piston_win_player")&&player.getUUID().equals(row.getUUID("piston_win_player"))
                &&"piston:piston_doll".equals(row.getString("piston_opponent"))&&row.getInt("piston_win_rounds")>0;
    }
    private static void save(ServerPlayer p,Session s){
        CompoundTag row=p.getPersistentData().getCompound(DATA);DuelRules r=s.rules;
        row.putInt("schema",1);row.putUUID("duel_id",s.id);row.putBoolean("active",r.outcome==0);
        row.putInt("hp",r.hp);row.putInt("enemy_hp",r.enemy);row.putInt("energy",r.energy);row.putInt("turn",r.turn);
        row.putInt("weakened",r.weakened);row.putInt("outcome",r.outcome);row.putInt("accepted",r.accepted);row.putIntArray("pp",r.pp);
        row.putString("log",r.log);row.putString("opponent","piston:piston_doll");p.getPersistentData().put(DATA,row);
    }
    private static DuelRules read(CompoundTag row){
        DuelRules r=new DuelRules();if(row.getInt("schema")!=1||!"piston:piston_doll".equals(row.getString("opponent")))return r;
        r.hp=row.getInt("hp");r.enemy=row.getInt("enemy_hp");r.energy=row.getInt("energy");r.turn=row.getInt("turn");r.weakened=row.getInt("weakened");
        r.outcome=row.getInt("outcome");r.accepted=row.getInt("accepted");r.log=row.getString("log");int[] pp=row.getIntArray("pp");
        if(pp.length!=4){r.outcome=3;return r;}System.arraycopy(pp,0,r.pp,0,4);return r;
    }
    private static void send(ServerPlayer p,Session s){send(p,s,false);}
    private static void send(ServerPlayer p,Session s,boolean open){
        CompoundTag tag=new CompoundTag();DuelRules r=s.rules;
        tag.putUUID("id",s.id);tag.putInt("turn",r.turn);tag.putInt("hp",r.hp);tag.putInt("enemy",r.enemy);tag.putInt("energy",r.energy);
        tag.putIntArray("pp",r.pp);tag.putInt("outcome",r.outcome);tag.putString("plan",r.enemyPlan());tag.putString("log",r.log);
        tag.putBoolean("ready",s.level.getGameTime()>=s.nextAction);tag.putInt("dragon",s.dragon==null?-1:s.dragon.getId());tag.putInt("piston",s.piston==null?-1:s.piston.getId());
        tag.putBoolean("open",open);
        DuelNetwork.send(p,tag);
    }
    private static void pose(Session s,float elapsed){
        Vec3 side=new Vec3(s.direction.z,0,-s.direction.x);
        double hit=s.lastMoveAt<0?0:Math.sin(Math.min(1,Math.max(0,elapsed)/12)*Math.PI)*.55;
        Vec3 left=s.home.add(side.scale(-1.65+hit)),right=s.home.add(side.scale(1.65-hit*.35));
        place(s.dragon,left,right);place(s.piston,right,left);
    }
    private static void place(LivingEntity e,Vec3 pos,Vec3 look){
        if(e==null||e.isRemoved())return;e.setPos(pos);e.setDeltaMovement(Vec3.ZERO);
        float yaw=(float)(Math.toDegrees(Math.atan2(look.z-pos.z,look.x-pos.x))-90);
        e.setYRot(yaw);e.setYHeadRot(yaw);e.yBodyRot=yaw;e.setXRot(0);
        if(e instanceof Mob mob)mob.getNavigation().stop();
    }
    private static void cleanup(Session s){if(s.dragon!=null)s.dragon.discard();if(s.piston!=null)s.piston.discard();}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        for(Session s:List.copyOf(ACTIVE.values())){
            if(s.level.getServer()!=event.getServer())continue;
            ServerPlayer p=event.getServer().getPlayerList().getPlayer(s.owner);
            if(p==null||!p.isAlive()||p.level()!=s.level||p.distanceToSqr(s.home.x,s.home.y,s.home.z)>40*40){
                if(p!=null)save(p,s);cleanup(s);ACTIVE.remove(s.owner);continue;
            }
            long now=s.level.getGameTime();pose(s,now-s.lastMoveAt);
            if(now-s.lastMoveAt==5&&s.dragon!=null)s.level.sendParticles(ParticleTypes.CRIT,s.piston.getX(),s.piston.getY()+.6,s.piston.getZ(),12,.2,.25,.2,.08);
            if(now==s.nextAction){send(p,s);if(s.rules.outcome!=0){cleanup(s);ACTIVE.remove(s.owner);}}
        }
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract event){if(isDuelActor(event.getTarget())){event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);}}
    @SubscribeEvent public static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event){if(isDuelActor(event.getTarget())){event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);}}
    @SubscribeEvent public static void orphan(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide()||!isDuelActor(event.getEntity()))return;
        UUID id=event.getEntity().getPersistentData().getUUID(PROJECTION);
        if(ACTIVE.values().stream().noneMatch(s->s.id.equals(id)&&s.level==event.getLevel())){event.setCanceled(true);event.getEntity().discard();}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p){Session s=ACTIVE.remove(p.getUUID());if(s!=null){save(p,s);cleanup(s);}}}
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){
        CompoundTag row=event.getOriginal().getPersistentData().getCompound(DATA);
        if(!row.isEmpty())event.getEntity().getPersistentData().put(DATA,row.copy());
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event){for(Session s:List.copyOf(ACTIVE.values()))if(s.level.getServer()==event.getServer()){ServerPlayer p=event.getServer().getPlayerList().getPlayer(s.owner);if(p!=null)save(p,s);cleanup(s);ACTIVE.remove(s.owner);}}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("dollduel")
                .then(Commands.literal("piston").executes(c->begin(c.getSource().getPlayerOrException())?1:0))
                .then(Commands.literal("resume").executes(c->begin(c.getSource().getPlayerOrException())?1:0))
                .then(Commands.literal("surrender").executes(c->{move(c.getSource().getPlayerOrException(),-1);return 1;})));
    }
}
