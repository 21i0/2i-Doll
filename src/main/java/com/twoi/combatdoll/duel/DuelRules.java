package com.twoi.combatdoll.duel;

/** Deterministic authoritative turn rules. No world damage, RNG or client result. */
public final class DuelRules {
    public static final int MAX_HP=120, PISTON_HP=176;
    public static final String[] MOVES={"绯棘穿刺","荆棘壁垒","记忆封缄","绯红终章"};
    public static final String[] DESCRIPTIONS={"24伤害 · 获得1绯能","回复10 · 本回合减伤；挡过载可反击","18伤害 · 削弱敌方两回合","消耗3绯能 · 58伤害"};
    public final int[] pp={16,8,8,3};
    public int hp=MAX_HP, enemy=PISTON_HP, energy, turn=1, weakened, outcome, accepted;
    public int lastMove=-1, lastDamage, lastIncoming;
    public String log="活塞启动液压核心。龙幻等待你的指令。";
    public String enemyPlan(){return switch((turn-1)%4){case 0->"蓄势冲程 · 12伤害";case 1->"过载冲击 · 38伤害（壁垒可反击）";case 2->"冷却回位 · 8伤害并回复8";default->"液压突刺 · 24伤害";};}
    public boolean canMove(int move){return outcome==0&&move>=0&&move<4&&pp[move]>0&&(move!=3||energy>=3);}
    public boolean play(int move){
        if(!canMove(move))return false;
        lastMove=move;pp[move]--;accepted++;
        int plan=(turn-1)%4;
        lastDamage=switch(move){case 0->24;case 2->18;case 3->58;default->0;};
        if(move==1){hp=Math.min(MAX_HP,hp+10);if(plan==1)lastDamage=18;}
        if(move==2)weakened=2;
        if(move==3)energy-=3;else energy=Math.min(4,energy+1);
        enemy=Math.max(0,enemy-lastDamage);
        if(enemy==0){outcome=1;lastIncoming=0;log="龙幻使出「"+MOVES[move]+"」，造成"+lastDamage+"伤害。活塞的核心停转。你赢得了这场对战！";turn++;return true;}
        lastIncoming=switch(plan){case 0->12;case 1->38;case 2->8;default->24;};
        if(weakened>0){lastIncoming=(lastIncoming+1)/2;weakened--;}
        if(move==1)lastIncoming=plan==1?4:Math.max(1,lastIncoming/4);
        hp=Math.max(0,hp-lastIncoming);
        if(plan==2)enemy=Math.min(PISTON_HP,enemy+8);
        log="龙幻使出「"+MOVES[move]+"」："+lastDamage+"伤害。活塞「"+enemyPlan().split(" · ")[0]+"」反击："+lastIncoming+"伤害。";
        if(move==1&&plan==1)log+=" 壁垒精准反制了过载！";
        if(hp==0){outcome=2;log+=" 龙幻暂时失去战斗能力，请调整技能顺序再来。";}
        turn++;
        if(turn>40&&outcome==0){outcome=2;log="回合达到上限，活塞保住了训练场。请重新挑战。";}
        return true;
    }
    public boolean valid(){
        if(hp<0||hp>MAX_HP||enemy<0||enemy>PISTON_HP||energy<0||energy>4||turn<1||turn>41||weakened<0||weakened>2||outcome<0||outcome>3||accepted<0||accepted>40)return false;
        int[] max={16,8,8,3};for(int i=0;i<4;i++)if(pp[i]<0||pp[i]>max[i])return false;
        return true;
    }
}
