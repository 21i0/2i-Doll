import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Production geometry bytecode unchanged; omits only global client RenderType registration. */
public final class BookRideMeshProbe {
    static long checks;
    static Class<?> meshClass;
    static void check(boolean okay,String message){checks++;if(!okay)throw new AssertionError(message);}
    static final class Capture implements VertexConsumer {
        final List<float[]> rows=new ArrayList<>();
        public VertexConsumer addVertex(float x,float y,float z){rows.add(new float[]{x,y,z});return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    static Object mesh(PoseStack pose,Capture output)throws Exception{
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");
        Field singleton=unsafeType.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        Object unsafe=singleton.get(null);
        var result=unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,meshClass);
        for(var entry:Map.<String,Object>of("pose",pose,"out",output,"scarlet",true,"camera",Vec3.ZERO).entrySet()){
            Field field=meshClass.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(result,entry.getValue());
        }
        return result;
    }
    static void emit(Object mesh,float time,float size,boolean explicit,float degrees)throws Exception{
        if(explicit)meshClass.getMethod("book",float.class,float.class,float.class,float.class,float.class).invoke(mesh,time,1F,size,1F,degrees);
        else meshClass.getMethod("book",float.class,float.class,float.class,float.class).invoke(mesh,time,1F,size,1F);
    }
    static final class GeometryLoader extends ClassLoader {
        final Path classes;
        GeometryLoader(Path classes){super(BookRideMeshProbe.class.getClassLoader());this.classes=classes;}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
            if(!name.equals("com.twoi.combatdoll.client.TwoIArcanaRenderer")&&!name.equals("com.twoi.combatdoll.client.TwoIArcanaRenderer$Mesh"))return super.loadClass(name,resolve);
            Class<?> value=findLoadedClass(name);
            if(value==null)try{
                byte[] input=Files.readAllBytes(classes.resolve(name.replace('.','/')+".class"));
                if(!name.endsWith("$Mesh")){
                    ClassWriter writer=new ClassWriter(0);
                    new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9,writer){
                        @Override public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] exceptions){
                            return method.equals("<clinit>")?null:super.visitMethod(access,method,desc,signature,exceptions);
                        }
                        @Override public FieldVisitor visitField(int access,String field,String desc,String signature,Object constant){
                            return super.visitField(desc.equals("[F")?access&~Opcodes.ACC_FINAL:access,field,desc,signature,constant);
                        }
                    },0);
                    input=writer.toByteArray();
                }
                value=defineClass(name,input,0,input.length);
            }catch(Exception failure){throw new ClassNotFoundException(name,failure);}
            if(resolve)resolveClass(value);return value;
        }
    }
    public static void main(String[] args)throws Exception{
        GeometryLoader loader=new GeometryLoader(Path.of(args[1]));
        Class<?> renderer=Class.forName("com.twoi.combatdoll.client.TwoIArcanaRenderer",true,loader);
        meshClass=Class.forName("com.twoi.combatdoll.client.TwoIArcanaRenderer$Mesh",true,loader);
        String source=Files.readString(Path.of(args[2]).resolve("src/main/java/com/twoi/combatdoll/client/TwoIArcanaRenderer.java"));
        // Read atlas constants from the production source instead of duplicating them.
        for(String name:List.of("RED_COVER","PARCHMENT","HEX_EYE","IRIS","RED_SOLID")){
            Matcher matcher=Pattern.compile("float\\[\\]\\s+"+name+"\\s*=\\s*\\{([^}]+)\\}").matcher(source);
            check(matcher.find(),"missing production atlas constant "+name);
            String[] entries=matcher.group(1).split(",");float[] values=new float[entries.length];
            for(int i=0;i<values.length;i++){
                String[] fraction=entries[i].trim().replace("F","").split("/");
                values[i]=Float.parseFloat(fraction[0]);if(fraction.length==2)values[i]/=Float.parseFloat(fraction[1]);
            }
            Field field=renderer.getDeclaredField(name);field.setAccessible(true);field.set(null,values);
        }
        int flatVertices=0;float top=Float.NEGATIVE_INFINITY;
        for(int frame=0;frame<120;frame++){
            float time=frame*2.0F;
            Capture original=new Capture(),explicit=new Capture();
            emit(mesh(new PoseStack(),original),time,1.35F,false,155);
            emit(mesh(new PoseStack(),explicit),time,1.35F,true,155);
            check(original.rows.size()==explicit.rows.size(),"ordinary book vertex count changed");
            for(int i=0;i<original.rows.size();i++)for(int axis=0;axis<3;axis++)
                check(Float.floatToIntBits(original.rows.get(i)[axis])==Float.floatToIntBits(explicit.rows.get(i)[axis]),"ordinary book shape changed");
            for(int yaw=0;yaw<360;yaw+=15){
                PoseStack pose=new PoseStack();pose.translate(0,-.079*1.15-.01,0);
                pose.mulPose(Axis.YP.rotationDegrees(yaw));pose.mulPose(Axis.XP.rotationDegrees(-90));pose.translate(.205F*1.15F,0,0);
                Capture flat=new Capture();emit(mesh(pose,flat),time,1.15F,true,180);
                flatVertices=flat.rows.size();
                for(float[] vertex:flat.rows){
                    for(float coordinate:vertex)check(Float.isFinite(coordinate),"nonfinite flat-book coordinate");
                    check(vertex[1]<=-.00999F,"supporting book clips above seat");
                    top=Math.max(top,vertex[1]);
                }
            }
        }
        String json="{\"status\":\"passed\",\"timeFrames\":120,\"yaws\":24,\"checks\":"+checks+",\"verticesPerBook\":"+flatVertices+",\"topRelativeToSeat\":"+top+",\"usesProductionMeshMethodBytecode\":true,\"isolatesClientStaticRegistration\":true,\"startsMinecraft\":false}\n";
        Files.writeString(Path.of(args[0]),json);System.out.print(json);
    }
}
