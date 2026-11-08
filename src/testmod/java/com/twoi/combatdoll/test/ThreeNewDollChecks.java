package com.twoi.combatdoll.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/** Opt-in real registry/deploy/recall/disk probes for all four 1.3.1/1.3.2 additions. Never shipped. */
public final class ThreeNewDollChecks {
    private static final List<String> IDS=List.of("magua","shuangshi","qiaotou","canmeng");
    private static final String FILE="four-new-dolls-witness.dat";
    private ThreeNewDollChecks(){}
    private static ResourceLocation id(String namespace,String suffix){return ResourceLocation.fromNamespaceAndPath(namespace,namespace+suffix);}
    private static Class<?> api(String namespace,String name)throws Exception{return Class.forName("com.customdoll."+namespace+"."+name);}
    private static UUID ownerId(String namespace){return UUID.nameUUIDFromBytes(("three-new-dolls-"+namespace).getBytes(StandardCharsets.UTF_8));}
    private static void check(boolean value,String why){if(!value)throw new AssertionError("Four new dolls: "+why);}
    private static ItemStack stored(Entity actor)throws Exception{return (ItemStack)actor.getClass().getMethod("storedCopy").invoke(actor);}
    private static boolean reclaim(Entity actor,Player player)throws Exception{return (boolean)actor.getClass().getMethod("reclaim",Player.class).invoke(actor,player);}

    public static void runServer(ServerPlayer anchor)throws Exception{
        var level=anchor.serverLevel();int index=0;
        for(String namespace:IDS){
            check(net.neoforged.fml.ModList.get().isLoaded(namespace),"runtime mod identity "+namespace);
            check(net.neoforged.fml.ModList.get().getModContainerById(namespace).orElseThrow().getModInfo().getVersion().toString().equals("1.21.1-1.0.0"),"preserved role version "+namespace);
            check(BuiltInRegistries.ITEM.containsKey(id(namespace,"_doll"))&&BuiltInRegistries.ENTITY_TYPE.containsKey(id(namespace,"_doll"))&&BuiltInRegistries.BLOCK.containsKey(id(namespace,"_display"))&&BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id(namespace,"_display")),"all four real registry entries "+namespace);
            var owner=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(ownerId(namespace),"NewDoll"+index));
            var stranger=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"DollVisitor"));
            owner.setGameMode(GameType.CREATIVE);owner.getInventory().clearContent();owner.setPos(32+index*8,150,32);owner.setYRot(0);owner.setXRot(0);stranger.setPos(owner.position());
            for(int x=-1;x<=1;x++)for(int z=-1;z<=2;z++)level.setBlock(owner.blockPosition().offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
            var source=new ItemStack(BuiltInRegistries.ITEM.get(id(namespace,"_doll")));
            source.set(DataComponents.CUSTOM_NAME,Component.literal(namespace+" complete carrier"));
            var extension=new CompoundTag();extension.putUUID("verification_identity",UUID.randomUUID());extension.putString("extension","preserve complete component carrier");source.set(DataComponents.CUSTOM_DATA,CustomData.of(extension));
            var ownership=api(namespace,"content.DollOwnership");ownership.getMethod("claim",ItemStack.class,Player.class).invoke(null,source,owner);
            check(owner.getUUID().equals(ownership.getMethod("get",ItemStack.class).invoke(null,source)),"item owner claim "+namespace);
            check(!(boolean)ownership.getMethod("mayUse",ItemStack.class,Player.class).invoke(null,source,stranger),"foreign item owner refusal "+namespace);
            check(source.getItem().getEquipmentSlot(source)==EquipmentSlot.HEAD&&source.getItem().canEquip(source,EquipmentSlot.HEAD,owner)&&!source.getItem().canEquip(source,EquipmentSlot.HEAD,stranger),"real owner-only head equipment "+namespace);
            owner.setItemInHand(InteractionHand.MAIN_HAND,source);
            check((boolean)api(namespace,"content.item.DollItem").getMethod("deploy",ServerPlayer.class,InteractionHand.class).invoke(null,owner,InteractionHand.MAIN_HAND),"actual item deploy "+namespace);
            check(owner.getMainHandItem().isEmpty(),"creative deploy moves rather than copies carrier "+namespace);
            var actors=level.getEntitiesOfClass(LivingEntity.class,owner.getBoundingBox().inflate(4),actor->BuiltInRegistries.ENTITY_TYPE.getKey(actor.getType()).equals(id(namespace,"_doll")));
            check(actors.size()==1,"one deployed registered actor "+namespace);var actor=actors.get(0);
            ItemStack deployed=stored(actor);check(owner.getUUID().equals(actor.getClass().getMethod("ownerId").invoke(actor)),"deployed owner "+namespace);
            CompoundTag saved=actor.saveWithoutId(new CompoundTag());var restored=actor.getType().create(level);check(restored!=null,"registered reload factory "+namespace);restored.load(saved);
            check(ItemStack.isSameItemSameComponents(deployed,stored(restored))&&owner.getUUID().equals(restored.getClass().getMethod("ownerId").invoke(restored)),"complete entity codec roundtrip "+namespace);
            check(!reclaim(actor,stranger)&&actor.isAlive(),"stranger cannot reclaim "+namespace);
            for(int slot=0;slot<owner.getInventory().items.size();slot++)owner.getInventory().items.set(slot,new ItemStack(Items.STONE,64));
            check(!reclaim(actor,owner)&&actor.isAlive()&&ItemStack.isSameItemSameComponents(deployed,stored(actor)),"full bag retains exact live carrier "+namespace);
            owner.getInventory().items.set(0,ItemStack.EMPTY);
            check(reclaim(actor,owner)&&actor.isRemoved()&&ItemStack.isSameItemSameComponents(deployed,owner.getInventory().getItem(0)),"free slot returns exact carrier "+namespace);
            check(!reclaim(actor,owner),"second recall cannot duplicate "+namespace);owner.getInventory().clearContent();
            LogUtils.getLogger().info("FOUR NEW DOLL SERVER ROLE PASSED: {} registry/version, actual creative deploy, owner/head/foreign checks, full carrier NBT, full-bag recall safety and once-only return",namespace);index++;
        }
        diskWitness(anchor);
        LogUtils.getLogger().info("FOUR NEW DOLLS SERVER CHECKS PASSED: magua/shuangshi/qiaotou/canmeng original registry identities and complete owner carriers");
    }

    private static void diskWitness(ServerPlayer anchor)throws Exception{
        var level=anchor.serverLevel();var file=anchor.getServer().getWorldPath(LevelResource.ROOT).resolve(FILE);
        if(Boolean.getBoolean("two_i_combat_doll.standalone_restart_test")){
            var evidence=NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap());check(evidence.getLong("pid")!=ProcessHandle.current().pid(),"disk witness uses genuinely different JVM");check(evidence.getInt("role_count")==IDS.size(),"disk witness includes all four current roles");
            for(String namespace:IDS){
                var row=evidence.getCompound(namespace);var pos=BlockPos.of(row.getLong("block"));level.getChunkAt(pos);
                var actor=level.getEntity(row.getUUID("entity"));check(actor!=null&&actor.isAlive(),"disk-loaded original entity "+namespace);
                var expected=ItemStack.parse(level.registryAccess(),row.getCompound("carrier")).orElseThrow();
                check(ItemStack.isSameItemSameComponents(expected,stored(actor))&&ownerId(namespace).equals(actor.getClass().getMethod("ownerId").invoke(actor)),"disk entity identity/owner/components "+namespace);
                var block=level.getBlockEntity(pos);check(block!=null&&ItemStack.isSameItemSameComponents(expected,(ItemStack)block.getClass().getMethod("copyDoll").invoke(block)),"disk display exact carrier "+namespace);
            }
            LogUtils.getLogger().info("FOUR NEW DOLLS REAL NEW-PROCESS DISK CHECKS PASSED: four original entity UUIDs and display carriers including canmeng, PID {} -> {}",evidence.getLong("pid"),ProcessHandle.current().pid());return;
        }
        check(!Files.exists(file),"fresh witness never overwrites prior evidence");var evidence=new CompoundTag();evidence.putLong("pid",ProcessHandle.current().pid());evidence.putInt("role_count",IDS.size());int index=0;
        for(String namespace:IDS){
            var owner=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new GameProfile(ownerId(namespace),"NewDoll"+index));
            ItemStack item=new ItemStack(BuiltInRegistries.ITEM.get(id(namespace,"_doll")));var data=new CompoundTag();data.putUUID("verification_identity",UUID.randomUUID());data.putString("extension","new-process witness");item.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            api(namespace,"content.DollOwnership").getMethod("claim",ItemStack.class,Player.class).invoke(null,item,owner);
            var pos=new BlockPos(64+index*8,150,64);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);level.setBlock(pos,BuiltInRegistries.BLOCK.get(id(namespace,"_display")).defaultBlockState(),3);
            var display=level.getBlockEntity(pos);check(display!=null,"real display factory "+namespace);display.getClass().getMethod("setDoll",ItemStack.class).invoke(display,item);
            var actor=BuiltInRegistries.ENTITY_TYPE.get(id(namespace,"_doll")).create(level);check(actor!=null,"witness entity factory "+namespace);actor.getClass().getMethod("initialize",UUID.class,ItemStack.class).invoke(actor,owner.getUUID(),item);actor.moveTo(pos.getX()+2.5,150,pos.getZ()+.5,180,0);check(level.addFreshEntity(actor),"witness actor joined "+namespace);
            level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);var row=new CompoundTag();row.putUUID("entity",actor.getUUID());row.putLong("block",pos.asLong());row.put("carrier",item.save(level.registryAccess()));evidence.put(namespace,row);index++;
        }
        anchor.getServer().saveEverything(true,true,true);NbtIo.writeCompressed(evidence,file);
        LogUtils.getLogger().info("FOUR NEW DOLLS FIRST-PROCESS DISK WITNESS SAVED: four original entities and displays including canmeng, PID {}",ProcessHandle.current().pid());
    }

    /** Client linkage is confined to this nested class; the dedicated entry above never loads it. */
    public static final class Client {
        private static boolean done,opened;
        private static Preview screen;
        public static boolean tick(net.minecraft.client.Minecraft client)throws Exception{
            if(done)return true;if(client.player==null||client.level==null)return false;
            if(!opened){if(client.screen!=null)return false;opened=true;screen=new Preview();client.setScreen(screen);return false;}
            if(!screen.done)return false;done=true;check(client.screen==null,"preview closes cleanly");
            LogUtils.getLogger().info("FOUR NEW DOLLS CLIENT CHECKS PASSED: magua/shuangshi/qiaotou/canmeng real registered actor/item/layer/resource, exported blink textures and production palm/head render APIs; two framebuffer pages saved");return true;
        }
        private static final class Preview extends net.minecraft.client.gui.screens.Screen {
            private final LivingEntity[] actors=new LivingEntity[IDS.size()];
            private final ItemStack[] items=new ItemStack[IDS.size()];
            private int frames;private boolean done;
            Preview(){super(Component.literal("Four new dolls / production renderers"));}
            @Override protected void init(){
                try{
                    for(int i=0;i<IDS.size();i++){
                        String namespace=IDS.get(i);items[i]=new ItemStack(BuiltInRegistries.ITEM.get(id(namespace,"_doll")));actors[i]=(LivingEntity)BuiltInRegistries.ENTITY_TYPE.get(id(namespace,"_doll")).create(minecraft.level);
                        check(actors[i]!=null&&minecraft.getEntityRenderDispatcher().getRenderer(actors[i])!=null,"registered production actor renderer "+namespace);
                        var renderer=api(namespace,"client.render.DollRenderer");var layer=(net.minecraft.client.model.geom.ModelLayerLocation)renderer.getField("LAYER").get(null);check(minecraft.getEntityModels().bakeLayer(layer)!=null,"registered baked model layer "+namespace);
                        var ext=net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(items[i]);check(ext.getCustomRenderer()!=null&&ext.getCustomRenderer().getClass().getName().startsWith("com.customdoll."+namespace),"actual item extension "+namespace);
                        for(String texture:List.of("skin.png","skin_blink_half.png","skin_blink_closed.png"))try(var input=minecraft.getResourceManager().open(ResourceLocation.fromNamespaceAndPath(namespace,"textures/entity/"+texture));var image=com.mojang.blaze3d.platform.NativeImage.read(input)){check(image.getWidth()>=64&&image.getWidth()==image.getHeight(),"valid actual skin/blink image "+namespace+"/"+texture);}
                    }
                }catch(Exception error){throw new AssertionError("Four new dolls client setup",error);}
            }
            @Override public void render(net.minecraft.client.gui.GuiGraphics gui,int mouseX,int mouseY,float partial){
                try{
                    int rowsPerPage=2,page=frames/18,totalPages=(IDS.size()+rowsPerPage-1)/rowsPerPage;
                    gui.fill(0,0,width,height,0xFF17151C);gui.drawCenteredString(font,"Production renderers: entity / palm / head ("+(page+1)+"/"+totalPages+")",width/2,8,0xFFEADDF5);
                    int rowHeight=(height-25)/rowsPerPage;
                    for(int row=0;row<rowsPerPage;row++){
                        int i=page*rowsPerPage+row;if(i>=IDS.size())break;
                        String namespace=IDS.get(i);int top=25+row*rowHeight;gui.drawString(font,namespace,8,top,0xFFFFFFFF);
                        net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(gui,20,top+10,width/3-5,top+rowHeight-2,Math.max(28,rowHeight-15),.0625F,width/6,top+rowHeight/2,actors[i]);
                        var pose=gui.pose();pose.pushPose();pose.translate(width*.50,top+rowHeight*.77,150);float size=Math.min(rowHeight*.75F,width*.15F);pose.scale(size,-size,size);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(155));
                        api(namespace,"client.render.DollHandRenderer").getMethod("renderOnPalm",ItemStack.class,net.minecraft.world.entity.HumanoidArm.class,net.minecraft.client.resources.PlayerSkin.class,boolean.class,boolean.class,com.mojang.blaze3d.vertex.PoseStack.class,net.minecraft.client.renderer.MultiBufferSource.class,int.class).invoke(null,items[i],net.minecraft.world.entity.HumanoidArm.RIGHT,minecraft.player.getSkin(),true,true,pose,gui.bufferSource(),15728880);gui.flush();pose.popPose();
                        pose.pushPose();pose.translate(width*.82,top+rowHeight*.81,150);pose.scale(size,size,size);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(165));
                        var playerModel=new net.minecraft.client.model.PlayerModel<LivingEntity>(minecraft.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER),false);playerModel.head.render(pose,gui.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.entitySolid(minecraft.player.getSkin().texture())),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                        api(namespace,"client.render.DollHeadLayer").getMethod("renderOnHead",ItemStack.class,net.minecraft.client.model.geom.ModelPart.class,com.mojang.blaze3d.vertex.PoseStack.class,net.minecraft.client.renderer.MultiBufferSource.class,int.class).invoke(null,items[i],playerModel.head,pose,gui.bufferSource(),15728880);gui.flush();pose.popPose();
                    }
                    gui.flush();check(org.lwjgl.opengl.GL11.glGetError()==org.lwjgl.opengl.GL11.GL_NO_ERROR,"production preview GL state");
                    if(++frames%18==0){var folder=minecraft.gameDirectory.toPath().resolve("four-new-dolls-screenshots");Files.createDirectories(folder);try(var shot=net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget())){shot.writeToFile(folder.resolve("four-new-dolls-production-forms-page-"+(page+1)+".png"));}if(frames==totalPages*18){done=true;minecraft.setScreen(null);}}
                }catch(Exception error){throw new AssertionError("Four new dolls production preview",error);}
            }
            @Override public boolean isPauseScreen(){return false;}
        }
    }
}
