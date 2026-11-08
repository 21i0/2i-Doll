import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.twoi.combatdoll.client.AstralStaffItemPose;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads the real production PoseStack helper only. Does not start a game, server or renderer. */
public final class StaffItemPoseProbe {
    static final float EPS = 0.000002F;
    static long checks;
    static long failures;
    static final List<String> errors = new ArrayList<>();
    static final Map<String,Object> report = new LinkedHashMap<>();
    static void check(boolean value,String message) {
        checks++;
        if(!value) { failures++; if(errors.size()<20) errors.add(message); }
    }
    static void near(float actual,float expected,String label) {
        check(Math.abs(actual-expected)<=EPS,label+": actual="+actual+" expected="+expected);
    }
    static String sha(Path p) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }
    static Matrix4f centered(ItemDisplayContext context) {
        PoseStack p=new PoseStack();
        p.translate(-.5,-.5,-.5);
        AstralStaffItemPose.apply(p,context);
        return new Matrix4f(p.last().pose());
    }
    static void old(PoseStack p,ItemDisplayContext context) {
        p.translate(.5,.47,.5);
        if(context==ItemDisplayContext.GUI) {
            p.translate(0,.08,0);
            p.mulPose(Axis.YP.rotationDegrees(-24)); p.mulPose(Axis.XP.rotationDegrees(8));
            p.mulPose(Axis.ZP.rotationDegrees(-13)); p.scale(.315F,.315F,.315F);
        } else if(context.firstPerson()) {
            p.translate(.04,-.19,-.02); p.mulPose(Axis.YP.rotationDegrees(158));
            p.mulPose(Axis.XP.rotationDegrees(-10)); p.mulPose(Axis.ZP.rotationDegrees(-11));
            p.scale(.52F,.52F,.52F);
        } else {
            p.translate(0,-.10,0); p.mulPose(Axis.YP.rotationDegrees(180));
            p.mulPose(Axis.XP.rotationDegrees(-10)); p.scale(.43F,.43F,.43F);
        }
    }
    static Matrix4f oldCentered(ItemDisplayContext context) {
        PoseStack p=new PoseStack(); p.translate(-.5,-.5,-.5); old(p,context);
        return new Matrix4f(p.last().pose());
    }
    static List<Float> vec(Vector3f v) { return List.of(v.x,v.y,v.z); }
    static List<Float> at(Matrix4f matrix,float x,float y,float z) {
        return vec(matrix.transformPosition(new Vector3f(x,y,z)));
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]), here=root.resolve("verification/staff-hold-ground-20261106");
        Path runtime=root.resolve("verification/staff-long-grip-20261029/runtime-b");
        List<float[]> vertices=new ArrayList<>();
        JsonObject manifest=JsonParser.parseString(Files.readString(runtime.resolve("manifest.json"))).getAsJsonObject();
        int frames=0;
        for(var frame:manifest.getAsJsonArray("frames")) {
            JsonObject entry=frame.getAsJsonObject();
            if(!entry.get("model").getAsString().equals("astral_edict")) continue;
            frames++;
            var data=JsonParser.parseString(Files.readString(runtime.resolve(entry.get("file").getAsString()))).getAsJsonObject();
            for(var group:data.getAsJsonArray("groups")) for(var vertex:group.getAsJsonObject().getAsJsonArray("vertices")) {
                var v=vertex.getAsJsonArray(); vertices.add(new float[]{v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat()});
            }
        }
        check(frames==18,"18 historical frame samples loaded");
        check(vertices.size()==161568,"161568 actual vertex positions loaded");
        report.put("loaded_historical_frames",frames); report.put("loaded_vertices",vertices.size());
        near(AstralStaffItemPose.GRIP_Y,-.51925F,"authored stretched grip centre");

        var fp=new LinkedHashMap<String,Object>();
        for(ItemDisplayContext context:List.of(ItemDisplayContext.FIRST_PERSON_LEFT_HAND,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)) {
            int side=context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND?-1:1;
            Matrix4f m=centered(context);
            Vector3f grip=m.transformPosition(new Vector3f(0,AstralStaffItemPose.GRIP_Y,0));
            near(grip.x,side*.04F,"first-person side grip x "+context);
            near(grip.y,-.22F,"first-person grip y "+context);
            near(grip.z,-.02F,"first-person grip z "+context);
            fp.put(context.name(),Map.of("new_grip",vec(grip),"old_grip",at(oldCentered(context),0,AstralStaffItemPose.GRIP_Y,0)));
        }
        report.put("first_person_grip_anchors",fp);

        var tp=new LinkedHashMap<String,Object>();
        for(ItemDisplayContext context:List.of(ItemDisplayContext.THIRD_PERSON_LEFT_HAND,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
            Matrix4f m=centered(context);
            Vector3f grip=m.transformPosition(new Vector3f(0,AstralStaffItemPose.GRIP_Y,0));
            near(grip.x,0,"third-person grip x "+context);near(grip.y,0,"third-person grip y "+context);near(grip.z,0,"third-person grip z "+context);
            PoseStack p=new PoseStack();
            // Player model outer flip and vanilla ItemInHandLayer rotations, with arm placement omitted.
            p.scale(1,-1,-1);p.mulPose(Axis.XP.rotationDegrees(-90));p.mulPose(Axis.YP.rotationDegrees(180));
            p.translate(-.5,-.5,-.5);AstralStaffItemPose.apply(p,context);
            var world=new Matrix4f(p.last().pose());
            var worldGrip=world.transformPosition(new Vector3f(0,AstralStaffItemPose.GRIP_Y,0));
            var head=world.transformPosition(new Vector3f(0,.63F,0));
            var shaftUp=head.sub(worldGrip,new Vector3f());
            check(shaftUp.y>.49F,"third-person head faces up "+context+": "+shaftUp);
            near(shaftUp.x,0,"third-person head vertical x "+context);
            near(shaftUp.z,0,"third-person head vertical z "+context);
            tp.put(context.name(),Map.of("grip",vec(grip),"post_character_flip_head_vector",vec(shaftUp)));
        }
        report.put("third_person_grip_and_up_axis",tp);

        var preserved=new LinkedHashMap<String,Object>();
        for(ItemDisplayContext context:List.of(ItemDisplayContext.GUI,ItemDisplayContext.NONE,ItemDisplayContext.FIXED,ItemDisplayContext.HEAD)) {
            Matrix4f a=centered(context), b=oldCentered(context);
            float maximumDelta=0;
            for(float[] v:vertices) {
                Vector3f x=a.transformPosition(new Vector3f(v[0],v[1],v[2])),y=b.transformPosition(new Vector3f(v[0],v[1],v[2]));
                maximumDelta=Math.max(maximumDelta,Math.max(Math.abs(x.x-y.x),Math.max(Math.abs(x.y-y.y),Math.abs(x.z-y.z))));
                check(x.equals(y),"preserved context vertex exactly identical "+context);
            }
            preserved.put(context.name(),Map.of("vertex_count",vertices.size(),"maximum_delta",maximumDelta,"exact_vector_comparison",true));
        }
        report.put("preserved_contexts",preserved);

        var ground=new ArrayList<Map<String,Object>>();
        for(int yaw=0;yaw<360;yaw+=15) {
            PoseStack p=new PoseStack();p.translate(0,.25,0);p.mulPose(Axis.YP.rotationDegrees(yaw));
            p.translate(-.5,-.5,-.5);AstralStaffItemPose.apply(p,ItemDisplayContext.GROUND);
            Matrix4f m=new Matrix4f(p.last().pose());
            PoseStack before=new PoseStack();before.translate(0,.25,0);before.mulPose(Axis.YP.rotationDegrees(yaw));
            before.translate(-.5,-.5,-.5);old(before,ItemDisplayContext.GROUND);
            Matrix4f oldM=new Matrix4f(before.last().pose());
            float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY,oldMin=Float.POSITIVE_INFINITY;
            for(float[] v:vertices) {
                Vector3f x=m.transformPosition(new Vector3f(v[0],v[1],v[2]));
                Vector3f previous=oldM.transformPosition(new Vector3f(v[0],v[1],v[2]));
                min=Math.min(min,x.y);max=Math.max(max,x.y);oldMin=Math.min(oldMin,previous.y);
                check(x.y>=.061F-EPS,"sampled ground vertex clear at yaw="+yaw+" y="+x.y);
            }
            // Complete continuous-time model enclosure from mesh-bounds-review; not only the 18 captured samples.
            for(float x:new float[]{-.663F,.663F}) for(float y:new float[]{-1.663F,1.293F}) for(float z:new float[]{-.54F,.54F}) {
                var corner=m.transformPosition(new Vector3f(x,y,z));
                check(corner.y>=.061F-EPS,"all-phase conservative support clear yaw="+yaw+" y="+corner.y);
            }
            check(oldMin<-.58F,"old shared held pose intersects ground yaw="+yaw);
            ground.add(Map.of("yaw",yaw,"new_min_y",min,"new_max_y",max,"old_min_y",oldMin));
        }
        report.put("ground_yaw_sweep",ground);
        report.put("continuous_ground_clearance_lower_bound",.25F-AstralStaffItemPose.GROUND_SCALE*.54F);
        report.put("ground_support_assumptions",List.of("Identity model GROUND display transform, verified separately from builtin/entity ancestry", "Native ItemEntity .25 * display scale lift, nonnegative native bob, item renderer -.5 centering", "Flat model X +90, yaw only from native drop rotation; no third-party render transforms", "All-phase model |z| <= .54 from independent source bound, with historical actual vertices as additional checks"));
        report.put("status",failures==0?"ACTUAL_PRODUCTION_POSE_PROBE_PASSED":"ACTUAL_PRODUCTION_POSE_PROBE_FAILED");
        report.put("checks",checks);report.put("failures",failures);report.put("failure_examples",errors);
        report.put("helper_source_sha256",sha(root.resolve("src/main/java/com/twoi/combatdoll/client/AstralStaffItemPose.java")));
        report.put("helper_class_sha256",sha(here.resolve("compile-classes/com/twoi/combatdoll/client/AstralStaffItemPose.class")));
        report.put("historical_mesh_report_sha256",sha(here.resolve("mesh-bounds-review.json")));
        report.put("minecraft_client_started",false);report.put("minecraft_server_started",false);
        report.put("visual_verification_this_round",false);
        report.put("scope","Actual production helper/PoseStack/Axis/ItemDisplayContext matrices executed in a plain JVM. Model mesh provenance linked independently. No game main, GL context, actual item entity, skin animation or screenshot ran.");
        Gson gson=new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(here.resolve("production-pose-probe.json"),gson.toJson(report));
        System.out.println(gson.toJson(Map.of("status",report.get("status"),"checks",checks,"failures",failures,"frames",frames,"vertices",vertices.size(),"continuous_ground_clearance_lower_bound",report.get("continuous_ground_clearance_lower_bound"))));
        if(failures>0) throw new AssertionError(errors.toString());
    }
}
