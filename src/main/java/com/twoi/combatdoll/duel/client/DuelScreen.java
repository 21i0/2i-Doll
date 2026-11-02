package com.twoi.combatdoll.duel.client;

import com.twoi.combatdoll.duel.DuelRules;
import com.twoi.combatdoll.duel.network.DuelNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

/** Windowed personal command screen; HP, energy, turn and results are server snapshots. */
public final class DuelScreen extends Screen {
    private CompoundTag state;
    private boolean submitted;
    private LivingEntity dragon,piston;
    private final Button[] skills=new Button[4];
    private int left,top,panelWidth,panelHeight;
    public DuelScreen(CompoundTag tag){super(Component.literal("龙幻 × 活塞 · 绯棘试炼"));state=tag.copy();}
    public static void receive(CompoundTag tag){
        if(!tag.hasUUID("id"))return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof DuelScreen screen&&screen.state.getUUID("id").equals(tag.getUUID("id"))){screen.state=tag.copy();screen.submitted=false;screen.updateButtons();}
        else if(tag.getBoolean("open"))mc.setScreen(new DuelScreen(tag));
    }
    @Override protected void init(){
        panelWidth=Math.min(620,width-20);panelHeight=Math.min(400,height-16);left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        int gap=8,buttonWidth=(panelWidth-44-gap)/2;
        for(int i=0;i<4;i++){
            final int move=i;
            skills[i]=addRenderableWidget(Button.builder(Component.literal(DuelRules.MOVES[i]),b->choose(move))
                    .bounds(left+22+(i%2)*(buttonWidth+gap),top+panelHeight-90+(i/2)*28,buttonWidth,24).build());
        }
        addRenderableWidget(Button.builder(Component.literal("暂离"),b->onClose()).bounds(left+panelWidth-51,top+7,42,18).build());
        addRenderableWidget(Button.builder(Component.literal("认输"),b->choose(-1)).bounds(left+10,top+7,40,18).build());
        updateButtons();
    }
    private void choose(int move){
        if(submitted||!state.getBoolean("ready")||state.getInt("outcome")!=0)return;
        submitted=true;updateButtons();
        PacketDistributor.sendToServer(new DuelNetwork.Move(state.getUUID("id"),state.getInt("turn"),move));
    }
    private void updateButtons(){
        int[] pp=state.getIntArray("pp");
        for(int i=0;i<4;i++)if(skills[i]!=null){
            int remaining=pp.length==4?pp[i]:0;
            skills[i].setMessage(Component.literal(DuelRules.MOVES[i]+"  "+remaining+"次"));
            skills[i].active=!submitted&&state.getBoolean("ready")&&state.getInt("outcome")==0&&remaining>0&&(i!=3||state.getInt("energy")>=3);
        }
    }
    private LivingEntity actor(boolean first){
        if(minecraft.level==null)return null;
        var entity=minecraft.level.getEntity(state.getInt(first?"dragon":"piston"));
        if(entity instanceof LivingEntity live)return live;
        if(first){if(dragon==null)dragon=com.customdoll.longhuan.init.ModEntities.TWO_I_DOLL.get().create(minecraft.level);return dragon;}
        if(piston==null)piston=com.customdoll.piston.init.ModEntities.TWO_I_DOLL.get().create(minecraft.level);return piston;
    }
    private void drawActor(GuiGraphics g,boolean first,int mouseX,int mouseY){
        LivingEntity entity=actor(first);if(entity==null)return;
        int x=first?left+panelWidth/4:left+panelWidth*3/4;
        int baseline=top+Math.max(95,panelHeight-170);
        int size=Math.min(84,Math.max(36,panelHeight/5));
        InventoryScreen.renderEntityInInventoryFollowsMouse(g,x-54,top+52,x+54,baseline+9,size,.0625F,mouseX,mouseY,entity);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.fill(0,0,width,height,0xA5050811);
        g.fillGradient(left,top,left+panelWidth,top+panelHeight,0xFC211523,0xFC0A111B);
        g.fill(left,top,left+panelWidth,top+2,0xFFFF7192);
        g.fill(left,top+panelHeight-2,left+panelWidth,top+panelHeight,0xFF684C89);
        g.drawCenteredString(font,"绯棘试炼  /  第 "+state.getInt("turn")+" 回合",left+panelWidth/2,top+13,0xFFF0DFFF);
        int bars=top+40,barWidth=Math.max(80,panelWidth/2-50);
        health(g,left+24,bars,barWidth,state.getInt("hp"),DuelRules.MAX_HP,"龙幻",0xFFE275B5);
        health(g,left+panelWidth/2+24,bars,barWidth,state.getInt("enemy"),DuelRules.PISTON_HP,"活塞",0xFF7CD5DF);
        drawActor(g,true,mouseX,mouseY);drawActor(g,false,mouseX,mouseY);
        int details=top+panelHeight-154;
        int energy=Math.max(0,Math.min(4,state.getInt("energy")));
        g.drawCenteredString(font,"绯能  "+"◆".repeat(energy)+"◇".repeat(4-energy),left+panelWidth/4,details,0xFFFFA7C8);
        g.drawCenteredString(font,"预告："+state.getString("plan"),left+panelWidth*3/4,details,0xFFB4DAE3);
        int outcome=state.getInt("outcome");
        if(outcome!=0)g.drawCenteredString(font,outcome==1?"胜利 · 活塞认可了你与龙幻的默契":outcome==2?"本轮落败 · 可以再次挑战":"本场挑战已退出",left+panelWidth/2,details+17,outcome==1?0xFFFFD27A:0xFFDBBAC9);
        else if(submitted||!state.getBoolean("ready"))g.drawCenteredString(font,"双方行动中……",left+panelWidth/2,details+17,0xFFD2BEDB);
        var lines=font.split(Component.literal(state.getString("log")),panelWidth-44);
        for(int i=0;i<Math.min(3,lines.size());i++)g.drawString(font,lines.get(i),left+22,details+31+i*10,0xFFE6DEE8,false);
        super.render(g,mouseX,mouseY,partial);
        for(int i=0;i<4;i++)if(skills[i]!=null&&skills[i].isHoveredOrFocused())g.renderTooltip(font,Component.literal(DuelRules.DESCRIPTIONS[i]),mouseX,mouseY);
    }
    private void health(GuiGraphics g,int x,int y,int width,int value,int max,String name,int color){
        int clamped=Math.max(0,Math.min(max,value));
        g.drawString(font,name+"  "+clamped+" / "+max,x,y-11,0xFFF2E8F3,false);
        g.fill(x,y,x+width,y+5,0xFF34313F);g.fill(x,y,x+width*clamped/max,y+5,color);
    }
    @Override public boolean isPauseScreen(){return false;}
}
