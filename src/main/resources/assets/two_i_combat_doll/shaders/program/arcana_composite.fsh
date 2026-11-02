#version 150
uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;
uniform vec2 InSize;
uniform float Domain;
uniform float Garden;
uniform float Impact;
uniform float Clock;
uniform float Mode;
uniform float Age;
uniform float ChargeExtra;
uniform vec2 FocalPoint;
in vec2 texCoord;
out vec4 fragColor;
const float PI=3.14159265359;
void main(){
    if(Mode>1.5){
        vec2 focus=clamp(FocalPoint,vec2(.1),vec2(.9));
        vec2 delta=texCoord-focus;
        float hit=step(160.,Age)*exp(-max(0.,Age-160.)*.28)*Domain;
        float roar=exp(-pow((Age-105.)/7.,2.))*Domain;
        float radius=length(delta);
        vec2 bend=normalize(delta+vec2(.0001))*sin(radius*52.-Age*.8)*hit*.005;
        vec3 scene=texture(DiffuseSampler,clamp(texCoord+bend,vec2(.001),vec2(.999))).rgb;
        scene+=texture(BloomSampler,texCoord).rgb*(.30*Domain+.20*Impact);
        scene=mix(scene,vec3(1.,.91,.96),hit*.23);
        scene+=roar*vec3(.020,.010,.025);
        scene*=1.-smoothstep(.4,.85,length(texCoord-.5))*.07*Domain;
        fragColor=vec4(clamp(scene,0.,1.),1.);return;
    }
    vec2 center=texCoord-.5;
    float radial=length(center),edge=smoothstep(.13,.72,radial);
    float entry=Mode>.5?step(44.,Age)*exp(-pow((Age-47.)/8.,2.)):exp(-pow((Age-23.)/9.,2.));
    float takeover=exp(-pow((Age-96.)/2.5,2.));
    float shock=Mode>.5?entry:takeover;
    float contact=Mode>.5?(Age<124.?0.:exp(-(Age-124.)*.42)):pow(Impact,1.45);
    float preAge=Mode>.5?124.:104.;
    float anticipation=smoothstep(preAge-7.,preAge-3.,Age)*(1.-step(preAge,Age));
    vec2 focus=clamp(FocalPoint,vec2(.08),vec2(.92));
    vec2 fromFocus=texCoord-focus;
    float holeFalloff=1.-smoothstep(.10,.56,length(fromFocus));
    float ripple=sin(radial*38.-Age*.15)*exp(-abs(radial-(Age-(Mode>.5?38.:16.))*.017)*13.)*entry;
    float reverse=max(0.,Age-172.);
    float motionTime=Mode>.5?(min(Age,44.)+ChargeExtra-reverse*reverse*(4./3.))*.05:Clock;
    vec2 bend=center*(ripple*.13+sin(radial*31.-motionTime*1.4)*.003*Domain*edge);
    bend+=fromFocus*anticipation*.011*Domain;
    vec2 uv=clamp(texCoord+bend,vec2(.001),vec2(.999));
    // A single short spatial recoil after the slow opening. Five bounded taps
    // converge on the actual spell, rather than shaking the vanilla camera.
    float zoom=(shock*.025+contact*.034)*Domain*holeFalloff;
    vec3 scene=texture(DiffuseSampler,uv).rgb*.38;
    scene+=texture(DiffuseSampler,clamp(uv-fromFocus*zoom,vec2(.001),vec2(.999))).rgb*.23;
    scene+=texture(DiffuseSampler,clamp(uv-fromFocus*zoom*2.,vec2(.001),vec2(.999))).rgb*.18;
    scene+=texture(DiffuseSampler,clamp(uv-fromFocus*zoom*3.,vec2(.001),vec2(.999))).rgb*.13;
    scene+=texture(DiffuseSampler,clamp(uv-fromFocus*zoom*4.,vec2(.001),vec2(.999))).rgb*.08;
    scene*=1.-anticipation*.12*Domain;
    vec2 fringe=normalize(fromFocus+vec2(.0001))*(1.+shock*4.+contact*5.)/InSize*Domain*holeFalloff;
    scene.r=mix(scene.r,texture(DiffuseSampler,clamp(uv+fringe,vec2(.001),vec2(.999))).r,.42*Domain*holeFalloff);
    scene.b=mix(scene.b,texture(DiffuseSampler,clamp(uv-fringe,vec2(.001),vec2(.999))).b,.42*Domain*holeFalloff);
    float luma=dot(scene,vec3(.2126,.7152,.0722));
    if(Mode>.5){
        float stopped=smoothstep(44.,49.,Age)*(1.-smoothstep(162.,172.,Age));
        // One outward negative-colour wave, followed by a still silver world.
        float wave=1.-smoothstep(.015,.08,abs(radial-max(0.,Age-44.)*.025));
        vec3 negative=vec3(1.)-scene.bgr;
        negative=mix(negative,vec3(dot(negative,vec3(.333)))*vec3(1.,.78,.96),.30);
        scene=mix(scene,negative,entry*wave*.82*Domain);
        // The world pass freezes terrain colours before luminous spell geometry.
        // Preserve the staff, gold rings and violet verdict in the composite.
        // No HUD clock: the world-space clock is physically inside the black hole.
        scene+=entry*vec3(.18,.12,.05)*Domain;
        scene+=contact*vec3(.16,.15,.24)*Domain;
        float bars=1.-smoothstep(.018,.033,min(texCoord.y,1.-texCoord.y));
        scene*=1.-bars*stopped*.82*Domain;
    }else{
        // Crimson covenant: neutral surfaces take on claret shadows, while the
        // carved ivory seal and organic red highlights retain their tonal range.
        vec3 enchanted=scene*vec3(1.04,.73,.78)+vec3(.010,.001,.004)*Domain;
        scene=mix(scene,enchanted,.62*Domain);
        scene+=takeover*vec3(.055,.002,.008)*Domain;
        // Contact gives one ivory-red edge, then immediately returns to the
        // readable claret domain. Repeated slams retain separate silhouettes.
        scene+=contact*vec3(.065,.013,.017)*(1.-smoothstep(.0,.8,length(fromFocus)))*Domain;
    }
    scene*=1.-edge*(Mode>.5?.24:.12)*Domain;
    vec3 bloom=texture(BloomSampler,texCoord).rgb;
    scene+=bloom*(.65*Domain+.30*Garden+.28*Impact);
    fragColor=vec4(clamp(scene,0.,1.),1.);
}
