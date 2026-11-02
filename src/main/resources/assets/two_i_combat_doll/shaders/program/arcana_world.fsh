#version 150
uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D ClockSampler;
uniform sampler2D GalaxySampler;
uniform sampler2D LandscapeSampler;
uniform sampler2D CrimsonSampler;
uniform sampler2D CrimsonGroundSampler;
uniform mat4 InvProjection;
uniform mat4 CameraRotation;
uniform vec3 Eye;
uniform vec3 PlanetAnchor;
uniform mat4 Planets0;
uniform mat4 Planets1;
uniform mat4 Planets2;
uniform mat4 Planets3;
uniform mat4 Planets4;
uniform mat4 Planets5;
uniform mat4 Planets6;
uniform mat4 Planets7;
uniform mat4 SystemCenters0;
uniform mat4 SystemCenters1;
uniform mat4 HoleTrail0;
uniform mat4 HoleTrail1;
uniform float TrailCount;
uniform vec3 Ground;
uniform vec3 Lens;
uniform vec2 OutSize;
uniform float Domain;
uniform float Mode;
uniform float TerrainPass;
uniform float FloorStyle;
uniform float Takeover;
uniform float Age;
uniform float Impact;
uniform float Clock;
uniform float ChargeExtra;
uniform float VisualQuality;
in vec2 texCoord;
out vec4 fragColor;
const float PI=3.14159265359;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+1.),f.x),f.y);}
float fbm(vec2 p){float s=0.,w=.55;for(int i=0;i<4;i++){s+=noise(p)*w;p=mat2(1.6,-1.2,1.2,1.6)*p+7.1;w*=.48;}return s;}
mat2 rot(float a){return mat2(cos(a),sin(a),-sin(a),cos(a));}
float sdLine(vec2 p,vec2 a,vec2 b){vec2 d=b-a;return length(p-a-d*clamp(dot(p-a,d)/dot(d,d),0.,1.));}
// One clock drives the clockwork, bodies, star field and their reflections.
// A held charge can continue ticking; ChargeExtra is latched when it is released.
float astralAge(){float reverse=max(0.,Age-172.);return min(Age,44.)+ChargeExtra-reverse*reverse*(4./3.);}
float astralAngle(){return astralAge()*.0045;}
float starsAt(vec2 p,float scale,float time){
    vec2 cell=floor(p*scale),q=fract(p*scale)-.5;
    vec2 j=vec2(hash(cell),hash(cell+77.3))-.5;q-=j*.64;
    float d=length(q),aa=max(.018,min(.07,length(fwidth(p))*scale*.55));
    float core=1.-smoothstep(aa*.25,aa,d);
    float halo=exp(-d*32.)*.22;
    float sparkle=exp(-abs(q.x)*210.-abs(q.y)*14.)+exp(-abs(q.y)*210.-abs(q.x)*14.);
    float bright=step(.992,hash(cell+5.));
    return step(.92,hash(cell+39.))*(core*1.7+halo+sparkle*bright*.45)*(.8+.2*sin(time*.6+hash(cell)*80.));
}
float stars(vec2 p,float scale){return starsAt(p,scale,Clock);}
// Stable polar cells move exponentially inward, making curved accelerating star trails.
vec3 stellarInfall(vec2 p,float horizon){
    float time=astralAge()*.05;
    float r=length(p),a=atan(p.y,p.x),lr=log(max(.05,r));
    vec2 field=vec2((a-lr*.83-time*.032)*37.,(lr+time*.14)*11.);
    vec2 cell=floor(field),q=fract(field)-.5;
    q-=vec2(hash(cell+5.),hash(cell+71.))*.48-.24;
    float seed=hash(cell+103.);
    float core=exp(-q.x*q.x*2200.-q.y*q.y*180.);
    float trail=exp(-q.x*q.x*2100.-q.y*q.y*19.)*.34;
    float close=1.-smoothstep(horizon*1.1,horizon*3.,r);
    vec3 color=mix(vec3(.48,.69,1.),vec3(1.,.64,.37),close*.65);
    float mask=smoothstep(horizon*1.008,horizon*1.18,r)*(1.-smoothstep(1.5,2.8,r));
    return (core+trail)*step(.77,seed)*color*mask*(1.+close*.65);
}
vec3 galacticSky(vec3 ray){
    float time=astralAge()*.05;
    // The original baked panorama carries costly multi-scale cloud/dust detail.
    // Sample a celestial direction, never screen UV: mirror and sky share it.
    vec2 turned=rot(astralAngle()*.08)*ray.xz;
    ray=vec3(turned.x,ray.y,turned.y);
    vec3 pole=normalize(vec3(.52,.74,.43));
    vec3 east=normalize(cross(vec3(0.,1.,0.),pole));
    vec3 north=cross(pole,east);
    float latitude=asin(clamp(dot(ray,pole),-1.,1.));
    float longitude=atan(dot(ray,north),dot(ray,east));
    vec2 uv=vec2(fract(longitude/(2.*PI)+.5),.5-latitude/PI);
    // Leave headroom for the shared bloom pass so the warm core retains dust detail.
    vec3 sky=texture(GalaxySampler,uv).rgb*.78;
    // A sparse near-star layer remains crisp at any framebuffer size.
    vec2 starUV=vec2(atan(ray.z,ray.x)/PI,asin(clamp(ray.y,-1.,1.))/PI);
    sky+=starsAt(starUV,145.,time)*vec3(.66,.78,1.)*.64;
    sky+=starsAt(starUV+vec2(19.3,7.7),290.,time)*vec3(.75,.79,.89)*.16;
    return sky;
}
vec3 cosmos(vec3 ray,bool astral){
    if(astral)return galacticSky(ray);
    float time=astral?astralAge()*.05:Clock;
    vec2 p=vec2(atan(ray.z,ray.x)/PI,asin(clamp(ray.y,-1.,1.))/PI);
    float n=fbm(p*9.+vec2(time*.004,0.));float cloud=pow(max(0.,n-.22),2.2);
    float s=starsAt(p,150.,time)+starsAt(p+19.3,260.,time)*.48;
    vec3 nebula=astral?mix(vec3(.08,.015,.24),vec3(.02,.14,.23),noise(p*16.)):mix(vec3(.16,.005,.025),vec3(.055,.008,.038),noise(p*16.));
    return vec3(.002,.003,.009)+cloud*nebula*(astral?1.8:1.0)+s*(astral?vec3(.88,.9,1.):vec3(1.,.52,.48));
}
vec3 gear(vec2 p,vec2 center,float radius,float angle,float teeth){
    vec2 q=rot(angle)*(p-center);float r=length(q),a=atan(q.y,q.x);
    float outer=radius*(.92+.08*smoothstep(-.25,.25,cos(a*teeth)));
    float mask=(1.-smoothstep(outer-.001,outer+.001,r))*smoothstep(radius*.68,radius*.70,r);
    float axle=exp(-abs(r-radius*.22)*900.);
    float spokes=pow(max(0.,cos(a*6.)),22.)*step(radius*.23,r)*(1.-step(radius*.72,r));
    float bevel=exp(-abs(r-outer+.002)*1400.)+exp(-abs(r-radius*.70)*1000.);
    return (mask*.43+spokes*.36+axle*.8)*vec3(.62,.34,.09)+bevel*vec3(.9,.70,.30)*.6;
}
vec3 clockFace(vec2 p,float reveal){
    // Slow clockwise movement changes continuously into a fast counter-clockwise rewind.
    float angle=astralAngle();
    vec2 q=rot(angle)*p/.355;float r=length(q);
    if(r>1.03)return vec3(0.);
    vec2 uv=vec2(q.x,-q.y)*.5+.5;
    vec4 art=texture(ClockSampler,uv);
    vec3 color=art.rgb*(1.05+.20*sin(atan(q.y,q.x)+.7));
    // Actual independently moving gears live behind the engraved face.
    color+=gear(q,vec2(-.15,.10),.27,angle*3.,18.);
    color+=gear(q,vec2(.20,.03),.23,-angle*4.+.1,16.);
    color+=gear(q,vec2(.04,-.23),.19,angle*5.,14.);
    vec2 h=rot(-angle*5.-.45)*q;
    float hour=1.-smoothstep(.010,.018,sdLine(h,vec2(0,-.12),vec2(0,.49)));
    vec2 m=rot(-angle*13.+1.55)*q;
    float minute=1.-smoothstep(.006,.012,sdLine(m,vec2(0,-.16),vec2(0,.72)));
    float ornate=exp(-abs(length((h-vec2(0,.37))*vec2(1.,.65))-.065)*110.);
    float axle=exp(-abs(r-.037)*150.);
    color+=(hour*.80+minute*.96+ornate*.52+axle)*vec3(1.,.77,.33);
    // A restrained amber halo keeps the clock recognisable inside a genuinely black horizon.
    return color*reveal*(1.-smoothstep(.98,1.03,r));
}
const float BODY_SEED[30]=float[30](
    .41,.12,.85,.27,.72,.52,.94,.18,.63,.37,
    .75,.24,.56,.89,.32,.68,.09,.45,.81,.16,
    .58,.91,.35,.14,.77,.49,.22,.66,.98,.04);
vec3 orbitBaseU(){return normalize(vec3(.985,.09,.15));}
vec3 orbitBaseV(){vec3 u=orbitBaseU(),raw=vec3(-.04,.98,-.21);return normalize(raw-u*dot(raw,u));}
vec3 primaryOrbitV(float index){
    vec3 u=orbitBaseU(),v=orbitBaseV();float incline=(index-2.5)*.006;
    return v*cos(incline)+cross(u,v)*sin(incline);
}
vec3 satelliteOrbitU(float system){return orbitBaseU()*cos(system*.31)+orbitBaseV()*sin(system*.31);}
vec3 satelliteOrbitV(float system,float order){
    vec3 u=orbitBaseU(),v=orbitBaseV();
    vec3 turned=v*cos(system*.31)-u*sin(system*.31);
    float incline=.12*sin(system*1.1)+(order-1.5)*.012;
    return turned*cos(incline)+cross(u,v)*sin(incline);
}
vec4 celestialSystem(int index){return index<4?SystemCenters0[index]:SystemCenters1[index-4];}
vec4 celestialBody(int index){
    if(index<4)return Planets0[index];
    if(index<8)return Planets1[index-4];
    if(index<12)return Planets2[index-8];
    if(index<16)return Planets3[index-12];
    if(index<20)return Planets4[index-16];
    if(index<24)return Planets5[index-20];
    if(index<28)return Planets6[index-24];
    return Planets7[index-28];
}
vec3 planetPalette(float seed){
    vec3 blue=mix(vec3(.035,.13,.31),vec3(.10,.51,.62),seed);
    vec3 violet=mix(vec3(.12,.035,.28),vec3(.56,.16,.43),seed);
    vec3 gold=mix(vec3(.32,.14,.045),vec3(.75,.47,.19),seed);
    return seed<.35?blue:(seed<.70?violet:gold);
}
const vec3 SYSTEM_TINT[6]=vec3[6](vec3(.64,.29,.09),vec3(.045,.31,.66),
    vec3(.055,.47,.37),vec3(.72,.51,.26),vec3(.39,.14,.60),vec3(.09,.23,.48));
vec3 celestialTint(int index){
    int system=index<6?index:(index-6)%6;
    vec3 base=SYSTEM_TINT[system];
    if(index<6)return base;
    float order=float((index-6)/6);
    return mix(base,vec3(.40,.46,.56),.52+order*.055);
}
// Exactly thirty cheap sphere bounds are tested. Only the nearest actual body
// gets noise, lighting and surface detail; no ray marching or texture arrays.
vec3 celestialBodies(vec3 background,vec3 ray,vec3 origin){
    float nearest=1.e6,bodyIndex=-1.,bodySeed=0.,ringHit=0.;
    vec3 hit=vec3(0.),normal=vec3(0.);vec4 nearestBody=vec4(0.);
    vec3 halo=vec3(0.);
    for(int id=0;id<30;id++){
        float index=float(id);
        if(index>=(VisualQuality<1.5?20.:(VisualQuality<2.5?26.:30.)))break;
        float seed=BODY_SEED[id];
        vec4 body=celestialBody(id);float radius=body.w;
        if(radius<.001)continue;
        vec3 delta=body.xyz-origin;float forward=dot(ray,delta);
        if(forward<.01)continue;
        float d2=max(0.,dot(delta,delta)-forward*forward);
        bool hasRing=id==1||id==4;
        float bound=radius*(hasRing?2.20:1.16);
        if(d2>bound*bound)continue;
        float edge=sqrt(d2)/radius;
        if(edge>1.)halo+=celestialTint(id)*exp(-(edge-1.)*30.)*(id<6?.20:.12);
        if(d2<radius*radius){
            float travel=forward-sqrt(max(0.,radius*radius-d2));
            if(travel>.01&&travel<nearest){
                nearest=travel;bodyIndex=index;bodySeed=seed;nearestBody=body;ringHit=0.;
                hit=origin+ray*travel;normal=(hit-body.xyz)/radius;
            }
        }
        if(hasRing){
            vec3 ringNormal=normalize(cross(orbitBaseU(),primaryOrbitV(index))+orbitBaseU()*.18);
            float denominator=dot(ray,ringNormal);
            if(abs(denominator)>.001){
                float travel=dot(delta,ringNormal)/denominator;
                vec3 offset=origin+ray*travel-body.xyz;
                float radial=length(offset)/radius;
                if(travel>.01&&travel<nearest&&radial>1.27&&radial<2.12){
                    float stripe=.52+.48*sin(radial*130.+noise(offset.xz*4.)*.8);
                    float gaps=smoothstep(.025,.07,abs(radial-1.71));
                    if(stripe*gaps>.16){
                        nearest=travel;bodyIndex=index;bodySeed=seed;nearestBody=body;ringHit=radial;
                        hit=origin+ray*travel;normal=ringNormal*sign(-denominator);
                    }
                }
            }
        }
    }
    background+=halo;
    if(bodyIndex<0.)return background;
    vec4 body=nearestBody;
    float seed=bodySeed;
    vec3 tint=celestialTint(int(bodyIndex));
    vec3 lightDirection=normalize(Eye-hit);
    float diffuse=max(0.,dot(normal,lightDirection));
    float rim=pow(1.-max(0.,dot(normal,-ray)),3.);
    float proximity=exp(-length(body.xyz-Eye)/24.);
    // Tick energy pulses pass from the clock into the orbital bodies. The pulse
    // itself freezes, and runs backward with everything else during the finale.
    float tickPhase=astralAge()/11.;
    float pulse=pow(max(0.,cos(tickPhase*2.*PI-length(body.xyz-PlanetAnchor)*.25)),18.);
    vec3 color;
    if(ringHit>.5){
        float bands=.55+.45*sin(ringHit*147.);
        color=mix(tint,vec3(.70,.56,.37),.42)*bands*(.19+.75*diffuse);
        color+=vec3(.20,.35,.65)*pulse*.18;
    }else{
        float spin=astralAge()*(bodyIndex<6?.024:.013)*(.65+seed*1.4);
        float longitude=atan(normal.z,normal.x)+spin;
        float latitude=asin(clamp(normal.y,-1.,1.));
        vec2 map=vec2(longitude*1.25,latitude*2.7);
        float terrain=fbm(map*3.3+seed*17.);
        float gas=.5+.5*sin(latitude*(20.+seed*22.)+terrain*4.);
        float mineral=pow(noise(map*31.),5.);
        float detail=bodyIndex<6&&bodyIndex>2.5?mix(.45,1.30,gas):mix(.35,1.22,terrain);
        color=tint*detail*(.07+.92*diffuse);
        float terminator=exp(-abs(dot(normal,lightDirection))/.09);
        color+=terminator*tint*.14+mineral*vec3(.35,.40,.55)*diffuse;
        float specular=pow(max(0.,dot(reflect(-lightDirection,normal),-ray)),36.);
        color+=specular*vec3(.34,.46,.65)*(.2+proximity);
        color+=rim*mix(tint,vec3(.31,.50,1.),.6)*(.22+proximity*.90);
        color+=rim*pulse*vec3(.66,.55,.23)*.32;
        // A coherent system colour makes the primary and its moving moons read
        // as a local group, while retaining rocky/icy satellite surface detail.
        color+=tint*(bodyIndex<6?.035:.018);
    }
    // Near the independently controlled black hole, the atmosphere is pulled
    // into a bright cold edge; the lensed ray gives the body its stretched image.
    color+=rim*proximity*vec3(.18,.34,.85)*.38;
    return color;
}
vec3 orbitalTraces(vec3 ray,vec3 origin){
    vec3 light=vec3(0.);
    for(int i=0;i<6;i++){
        float index=float(i);vec3 u=orbitBaseU(),v=primaryOrbitV(index);
        vec3 normal=cross(u,v);float denominator=dot(ray,normal);
        if(abs(denominator)<.001)continue;
        float travel=dot(PlanetAnchor-origin,normal)/denominator;
        if(travel<.01)continue;
        vec3 delta=origin+ray*travel-PlanetAnchor;
        vec2 p=vec2(dot(delta,u),dot(delta,v));
        float radius=18.+index*13.2,eccentricity=.035+index*.003;
        vec2 ellipse=vec2((p.x+radius*eccentricity)/radius,p.y/(radius*sqrt(1.-eccentricity*eccentricity)));
        float distance=abs(length(ellipse)-1.)*radius;
        if(distance>.40)continue;
        float aa=max(.025,travel/OutSize.y*.7);
        float line=exp(-distance/max(.035,aa));
        light+=line*vec3(.018,.033,.065);
    }
    // Each primary has a small, visibly separate satellite system. Bounds are
    // rejected before tracing its two representative real moon orbits. These
    // centres come from the same CPU Kepler solution as the rendered bodies.
    for(int system=0;system<6;system++){
        vec4 parent=celestialSystem(system);vec3 delta=parent.xyz-origin;
        float forward=dot(delta,ray);if(forward<.01)continue;
        bool rings=system==1||system==4;
        float first=rings?parent.w*2.55:parent.w+.95;
        float spacing=rings?.82:1.10;
        float outer=first+spacing*3.;
        if(dot(delta,delta)-forward*forward>outer*outer*1.08)continue;
        for(int orbit=0;orbit<2;orbit++){
            float order=float(orbit)*3.,radius=first+spacing*order;
            vec3 u=satelliteOrbitU(float(system)),v=satelliteOrbitV(float(system),order);
            vec3 normal=cross(u,v);float denominator=dot(ray,normal);
            if(abs(denominator)<.001)continue;
            float travel=dot(delta,normal)/denominator;if(travel<.01)continue;
            vec3 local=origin+ray*travel-parent.xyz;
            vec2 p=vec2(dot(local,u),dot(local,v));
            float e=.018+order*.006;
            vec2 ellipse=vec2((p.x+radius*e)/radius,p.y/(radius*sqrt(1.-e*e)));
            float distance=abs(length(ellipse)-1.)*radius;
            if(distance>.32)continue;
            float aa=max(.020,travel/OutSize.y*.65);
            float line=exp(-distance/max(.027,aa));
            light+=line*(SYSTEM_TINT[system]*.23+vec3(.055,.065,.09));
        }
    }
    return light;
}
vec4 holeTrailPoint(int index){return index<4?HoleTrail0[index]:HoleTrail1[index-4];}
// At most six finite analytic segments, and zero cost while the hole is still.
// Camera-relative world positions also give the real water its matching trail.
vec3 holeMotionTrail(vec3 ray,vec3 origin){
    if(TrailCount<2.)return vec3(0.);
    vec3 light=vec3(0.);
    for(int i=0;i<6;i++){
        if(float(i+1)>=TrailCount)break;
        vec4 a=holeTrailPoint(i),b=holeTrailPoint(i+1);
        if(max(a.w,b.w)<.002)continue;
        vec3 segment=b.xyz-a.xyz,w=origin-a.xyz;
        float length2=dot(segment,segment),along=dot(ray,segment);
        if(length2<.0001)continue;
        float denominator=max(.0001,length2-along*along);
        float u=clamp((dot(segment,w)-along*dot(ray,w))/denominator,0.,1.);
        vec3 closest=a.xyz+segment*u;
        float travel=dot(closest-origin,ray);
        if(travel<0.)continue;
        float distance=length(origin+ray*travel-closest);
        float strength=mix(a.w,b.w,u)*(1.-float(i)*.095);
        float width=.68+strength*1.42;
        if(distance>width*3.0)continue;
        // Wide gravitational sheath and a compact, bright spine make the wake
        // read as displaced mass rather than a thin laser line. Slow travelling
        // ripples reinforce its viscous, sticky motion.
        float sheath=exp(-distance*distance/(width*width*3.8));
        float ribbon=exp(-distance*distance/(width*width));
        float core=exp(-distance*distance/(width*width*.075));
        float thread=.62+.38*sin(u*17.+float(i)*2.7-astralAge()*.035);
        float viscous=.72+.28*sin(u*31.-astralAge()*.075+float(i)*1.8);
        vec3 tint=mix(vec3(.19,.035,.46),vec3(.055,.34,.59),u);
        light+=(sheath*tint*.14+ribbon*tint*.36+core*vec3(.32,.43,.62)*.32)*strength*thread*viscous;
    }
    return light;
}
// Both the sky and the water reflection trace this exact same scene. World
// positions are camera-relative; a reflected ray starts at the real water face.
vec3 astralSky(vec3 ray,vec3 origin){
    vec3 right=normalize(mat3(CameraRotation)*vec3(1,0,0));
    vec3 up=normalize(mat3(CameraRotation)*vec3(0,1,0));
    vec3 facing=normalize(cross(right,up));
    float denominator=dot(ray,facing);
    float travel=dot(Eye-origin,facing)/(abs(denominator)<.0001?.0001:denominator);
    vec3 offset=origin+ray*travel-Eye;
    vec2 p=vec2(dot(offset,right),dot(offset,up))/20.;
    float r=length(p),grow=smoothstep(3.,44.,Age),horizon=.42*mix(.018,1.,grow);
    bool visible=travel>0.&&abs(denominator)>.001;
    vec3 bent=ray;
    if(visible){
        float deflect=.13*grow/max(.06,r*r)*(1.-smoothstep(1.5,3.2,r));
        vec2 source=p*(1.+deflect);
        bent=normalize(Eye-origin+(right*source.x+up*source.y)*20.);
    }
    vec3 result=cosmos(bent,true);
    // Keep luminous dust on both sides; the local backdrop leaves contrast for
    // the lens and flowing material instead of washing the disc into the Milky Way.
    if(visible)result*=1.-.22*exp(-r*r*.74)*grow;
    result+=orbitalTraces(bent,origin);
    result=celestialBodies(result,bent,origin);
    if(!visible)return result;
    float time=astralAge()*.05;
    float inside=1.-smoothstep(horizon-.003,horizon+.003,r);
    float angle=atan(p.y,p.x);
    vec3 spectral=mix(vec3(.34,.10,1.),vec3(.08,.82,1.),.5+.5*cos(angle));
    float photon=exp(-abs(r-horizon-.007)*310.);
    float corona=exp(-abs(r-horizon-.014)*30.)*(.72+.28*noise(vec2(angle*5.,r*9.-time*.1)));
    vec3 light=photon*vec3(.88,.92,1.)*1.9+corona*spectral*.9;
    // A broad, finite annulus has dark lanes and bright filaments. Its outer
    // flow extends past three horizon radii, without enlarging the hole itself.
    // Every phase uses the shared astronomical clock: stop and rewind together.
    vec2 disc=rot(.10)*p;float discR=length(vec2(disc.x,disc.y*4.65));
    float discAngle=atan(disc.y*4.65,disc.x);
    float scaledR=discR/max(.008,horizon);
    float flow=fbm(vec2(discAngle*5.-time*.65,scaledR*15.));
    float annulus=smoothstep(1.42,1.82,scaledR)*(1.-smoothstep(3.18,3.62,scaledR));
    float streamR=scaledR+.075*sin(discAngle*3.-time*.28+flow*3.);
    vec3 bandDistance=(vec3(streamR)-vec3(2.72,2.16,3.34))/vec3(.31,.20,.09);
    vec3 bands=exp(-bandDistance*bandDistance);
    float band=dot(bands,vec3(.72,.39,.22));
    float lane=sin(streamR*43.+flow*7.+discAngle*3.);
    float aaLane=max(.12,fwidth(streamR*43.));
    float threads=.20+.80*smoothstep(-.34-aaLane,.52+aaLane,lane);
    float clumps=.5+.5*sin(discAngle*4.-streamR*2.7-time*.44+flow*4.);
    threads*=.22+.78*smoothstep(.23,.84,clumps);
    threads*=.64+.36*noise(vec2(discAngle*31.-time*.31,streamR*83.));
    // The rear half disappears behind the horizon; the lower front ribbon
    // crosses its edge while leaving the central clock readable.
    float front=(1.-inside)+inside*(1.-smoothstep(-.018,.018,disc.y))
        *smoothstep(horizon*.57,horizon*.82,r)*.88;
    float doppler=.57+.43*(.5+.5*cos(discAngle-.3));
    vec3 diskColor=mix(vec3(.34,.075,.61),vec3(.27,.69,.88),.5+.5*cos(discAngle));
    light+=annulus*band*threads*diskColor*front*doppler*1.02;
    // The distant disc folds above the hole as two separated lensed arcs.
    float arcR=length(vec2(p.x,p.y*.76))/max(.008,horizon);
    vec2 arcDistance=(vec2(arcR)-vec2(1.18,1.095))/vec2(.028,.016);
    vec2 arcs=exp(-arcDistance*arcDistance);
    float arc=dot(arcs,vec2(1.,.30));
    arc*=smoothstep(.005,.055,p.y)*(1.-smoothstep(horizon*.83,horizon*1.10,abs(p.x)));
    arc*=.52+.48*flow;
    light+=arc*mix(vec3(.40,.14,.74),vec3(.44,.76,.91),flow)*.68;
    light+=(exp(-abs(r-horizon*1.30)*400.)*.10+exp(-abs(r-horizon*1.47)*500.)*.045)*spectral;
    result=mix(result,vec3(.00015,.0001,.0005),inside);
    result+=holeMotionTrail(ray,origin)*(1.-inside);
    result+=clockFace(p/max(.018,grow),smoothstep(10.,40.,Age))*inside;
    float dawn=smoothstep(0.,40.,Age);
    float dawnGlow=exp(-abs(r-.18*(1.-grow))*18.)*dawn*(1.-grow)*.55;
    result+=dawnGlow*vec3(.20,.38,1.);
    result+=light*(.25+.75*grow);
    result+=stellarInfall(p,horizon)*grow*(1.-inside);
    result+=Impact*exp(-abs(r-horizon)*9.)*vec3(.18,.13,.25);
    return result;
}
vec3 surfaceAt(vec2 uv){
    float d=texture(DepthSampler,uv).r;
    vec4 v=InvProjection*vec4(uv*2.-1.,d*2.-1.,1.);
    return (CameraRotation*vec4(v.xyz/v.w,0.)).xyz;
}
// Two nearest feature points define closed, irregular stone plates. Their
// bisector distance yields a branching fracture network fixed in world space.
vec2 fracture(vec2 p){
    vec2 cell=floor(p),f=fract(p);float first=10.,second=10.,seed=0.;
    for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
        vec2 g=vec2(x,y),id=cell+g;
        vec2 jitter=vec2(hash(id),hash(id+49.7));
        float d=length(g+jitter-f);
        if(d<first){second=first;first=d;seed=hash(id+101.);}else second=min(second,d);
    }
    return vec2(second-first,seed);
}
vec3 crackedGround(vec3 at,vec3 ray){
    vec2 p=at.xz-Ground.xz;
    vec2 base=p/18.;
    vec2 view=ray.xz/max(.18,abs(ray.y));
    view*=min(1.,3.8/max(.001,length(view)));
    vec2 fullShift=view*(.38/18.);
    float steps=VisualQuality<1.5?6.:10.;
    float low=0.,high=1.,depth=0.;
    // Bounded relief intersection: at most ten march + two refinement samples.
    // The height map is baked; there is no per-step procedural cellular noise.
    for(int i=0;i<10;i++){
        if(float(i)>=steps)break;
        depth=(float(i)+1.)/steps;
        float surface=1.-texture(CrimsonGroundSampler,fract(base+fullShift*depth)).a;
        if(depth>=surface){high=depth;break;}
        low=depth;
    }
    for(int i=0;i<2;i++){
        float middle=(low+high)*.5;
        float surface=1.-texture(CrimsonGroundSampler,fract(base+fullShift*middle)).a;
        if(middle>=surface)high=middle;else low=middle;
    }
    depth=(low+high)*.5;
    vec2 uv=base+fullShift*depth;
    vec4 stone=texture(CrimsonGroundSampler,fract(uv));
    vec2 texel=vec2(1./1024.,0.);
    float hx=texture(CrimsonGroundSampler,fract(uv+texel)).a-texture(CrimsonGroundSampler,fract(uv-texel)).a;
    float hz=texture(CrimsonGroundSampler,fract(uv+texel.yx)).a-texture(CrimsonGroundSampler,fract(uv-texel.yx)).a;
    vec3 normal=normalize(vec3(-hx*(.38*1024./36.),1.,-hz*(.38*1024./36.)));
    vec3 light=normalize(vec3(-.48,.77,-.42));
    float diffuse=.29+.71*max(0.,dot(normal,light));
    float shade=1.;
    // Three short light probes shade the lee of the broken rim.
    for(int i=1;i<=3;i++){
        float travel=float(i)*.035;
        float blocker=texture(CrimsonGroundSampler,fract(uv+light.xz*travel)).a;
        shade*=mix(1.,.66,smoothstep(.015,.08,blocker-stone.a-travel*3.));
    }
    float cavity=1.-smoothstep(.13,.58,stone.a);
    vec3 rock=stone.rgb*diffuse*shade*(.50+.50*stone.a);
    float vein=.5+.5*sin(Clock*.60+noise(p*.23)*8.);
    // Deep red underlight sits beneath dark cliff faces, never outlines every
    // plate in an equally bright neon line. Pale rims retain directional light.
    rock+=cavity*vec3(.30,.009,.018)*(.65+.35*vein);
    rock+=pow(max(0.,dot(reflect(ray,normal),light)),22.)*vec3(.045,.025,.022)*(1.-cavity);
    float farMist=smoothstep(18.,82.,length(p))*.36;
    rock=mix(rock,vec3(.115,.044,.057),farMist);
    return rock;
}
vec3 waterGround(vec3 at,vec3 ray){
    vec2 p=at.xz-Ground.xz;float distance=length(p);
    float time=Mode>.5?astralAge()*.05:Clock;
    float radial=distance*3.9-time*1.9;
    float a=sin(p.x*2.1+p.y*3.3-time*1.5);
    float b=sin(p.x*-.9+p.y*6.1+time*1.1);
    float wave=sin(radial+a*.16);
    vec2 gradient=vec2(2.1,3.3)*cos(p.x*2.1+p.y*3.3-time*1.5)*.018
        +vec2(-.9,6.1)*cos(p.x*-.9+p.y*6.1+time*1.1)*.008
        +p/max(.1,distance)*cos(radial)*.025;
    vec3 normal=normalize(vec3(-gradient.x,1.,-gradient.y));
    vec3 reflected=reflect(ray,normal);
    vec3 water=vec3(.008,.016,.050)+vec3(.008,.013,.045)*(.5+.5*wave);
    float fresnel=.20+.80*pow(1.-max(0.,dot(-ray,normal)),3.);
    // Reflection starts at this exact water face, so every orbit, moving lens,
    // clock gear and star uses the same spatial and temporal sky definition.
    vec3 reflection=astralSky(reflected,at+normal*.006);
    water+=reflection*(.64+fresnel*.32)*vec3(.90,.94,1.);
    // Perspective-correct ripples and their stretched specular glints replace
    // the old stone colour entirely; no DiffuseSampler term is retained here.
    float aa=max(.035,fwidth(radial)*.8);
    float ripple=1.-smoothstep(.025,.025+aa,abs(sin(radial+a*.13)));
    float glint=pow(max(0.,sin(p.x*.72+p.y*1.8+time*.3)),14.);
    water+=ripple*vec3(.050,.110,.34)*(.22+glint*.78);
    water+=pow(max(0.,wave),22.)*vec3(.035,.070,.20)*fresnel;
    return water;
}
// The crimson covenant is authored from geometric edges, carved thorns and a
// living eye. No reference image, landscape bitmap or original green/data motif
// is sampled. All loops have a fixed small bound; no screen-space limbs remain.
float tracedTriangle(vec2 p,float radius,float turn,float progress){
    float distance=10.;
    for(int i=0;i<3;i++){
        float a=turn+float(i)*(2.*PI/3.);
        float b=a+2.*PI/3.;
        vec2 from=vec2(sin(a),cos(a))*radius;
        vec2 to=vec2(sin(b),cos(b))*radius;
        float drawn=clamp(progress*3.-float(i),0.,1.);
        if(drawn>.0001)distance=min(distance,sdLine(p,from,mix(from,to,max(.0001,drawn))));
    }
    return distance;
}
float scarletBlink(float age,float middle){
    return smoothstep(middle-3.,middle,age)*(1.-smoothstep(middle,middle+5.,age));
}
vec2 softCubic(vec2 a,vec2 b,vec2 c,vec2 d,float t){
    float u=1.-t;
    return u*u*u*a+3.*u*u*t*b+3.*u*t*t*c+t*t*t*d;
}
// The former screen-space eyelid limbs are removed. Physical limbs now
// emerge only from separate world-space black portal cavities.
float thornCrown(vec2 p){
    float distance=10.;
    // Six symmetric, closed leaf tips and split thorn stems at the star points.
    // The ornaments are deliberately sparse enough to read from the ground.
    for(int i=0;i<6;i++){
        vec2 q=rot(float(i)*PI/3.)*p-vec2(0.,.94);
        distance=min(distance,sdLine(q,vec2(0.,-.16),vec2(0.,.095)));
        for(int j=-1;j<=1;j+=2){
            float signSide=float(j);
            vec2 v=q*vec2(signSide,1.);
            distance=min(distance,sdLine(v,vec2(0.,-.10),vec2(.055,-.045)));
            distance=min(distance,sdLine(v,vec2(.055,-.045),vec2(.035,.018)));
            distance=min(distance,sdLine(v,vec2(.035,.018),vec2(0.,.06)));
            vec2 coil=v-vec2(.057,-.085);
            float arc=abs(length(coil)-.031);
            arc=max(arc,-coil.y-.014);
            distance=min(distance,arc);
        }
    }
    return distance;
}
float bloodVeins(vec2 eye,float amount){
    if(amount<.001)return 0.;
    float u=clamp((.57-abs(eye.x))/.57,0.,1.);
    float distance=10.;
    for(int i=0;i<5;i++){
        float index=float(i),target=-.22+index*.11;
        float side=eye.x<0.?1.:-1.;
        float seed=index*1.71+side*.9;
        float jitter=(sin(u*31.+seed)*.005+sin(u*57.-seed)*.002)*u;
        float path=target*pow(u,.82)+jitter;
        float grown=smoothstep(.05+index*.035,.66+index*.045,amount);
        if(u<grown){
            distance=min(distance,abs(eye.y-path));
            // Secondary forks join their parent exactly; they never form a grid.
            for(int j=0;j<2;j++){
                float fork=.24+float(j)*.23+hash(vec2(index,side))*.04;
                float travel=max(0.,u-fork);
                float branch=path+travel*(j==0?.22:-.25)*side;
                if(u>fork&&u<min(grown,fork+.28))distance=min(distance,abs(eye.y-branch));
            }
        }
    }
    float aa=max(.0008,1.0/OutSize.y);
    return 1.-smoothstep(.0013,.0033+aa,distance);
}
vec3 crimsonCovenant(vec3 background,vec2 p,vec3 ray,float sky){
    float drawn=smoothstep(12.,60.,Age);
    float wrath=smoothstep(155.,200.,Age);
    float presence=sky*Domain*Takeover;
    if(presence<.0001)return background;
    float radius=length(p);
    float aa=max(.0013,1.3/OutSize.y);
    vec3 result=background;
    // A restrained claret nebula silhouettes the seal; the centre remains dark.
    float cloud=fbm(p*3.1+vec2(Clock*.012,-Clock*.007));
    float mist=exp(-radius*.95)*pow(max(0.,cloud-.23),1.7);
    result+=mist*vec3(.19,.005,.028)*presence*drawn;
    float first=tracedTriangle(p,1.06,0.,smoothstep(12.,54.,Age));
    float second=tracedTriangle(p,1.06,PI,smoothstep(26.,70.,Age));
    float edge=min(first,second);
    float ghost=min(tracedTriangle(p,1.11,0.,smoothstep(18.,62.,Age)),
        tracedTriangle(p,1.11,PI,smoothstep(32.,78.,Age)));
    // Dark raised metal has a pale inner bevel and an outer carmine filament.
    float body=1.-smoothstep(.044-aa,.044+aa,edge);
    float bevel=exp(-abs(edge-.034)*280.)+exp(-abs(edge-.006)*390.)*.48;
    float groove=exp(-abs(edge-.019)*540.);
    float engraved=sin(p.x*127.+p.y*81.)*sin(p.x*61.-p.y*113.);
    float grain=noise(p*160.);
    vec3 metal=mix(vec3(.12,.039,.046),vec3(.42,.285,.25),grain*.65+.20);
    metal+=bevel*vec3(.34,.245,.215);
    metal*=1.-groove*.64;
    metal*=.82+.18*engraved;
    result=mix(result,metal,body*presence);
    result+=exp(-edge*210.)*vec3(.46,.033,.043)*presence;
    float ember=.72+.28*sin(p.x*31.-p.y*43.+Clock*1.7);
    result+=exp(-ghost*300.)*vec3(.95,.025,.055)*presence*ember;
    result+=exp(-ghost*65.)*vec3(.085,.001,.006)*presence;
    float thorns=thornCrown(p);
    float thornBody=(1.-smoothstep(.006,.010+aa,thorns))*drawn;
    vec3 thornMetal=vec3(.42,.27,.24)*(.6+.4*noise(p*51.));
    result=mix(result,thornMetal,thornBody*presence);
    result+=exp(-thorns*180.)*vec3(.14,.017,.023)*drawn*presence;
    // Eye opening, gaze and two purposeful blinks are driven by cast age rather
    // than wall-clock phase, making the scene coherent for every observing client.
    float blink=max(scarletBlink(Age,104.),scarletBlink(Age,148.));
    float opening=smoothstep(56.,96.,Age)*(1.-.988*blink);
    float halfWidth=.58;
    float x=p.x/halfWidth;
    float almond=pow(max(0.,1.-x*x),.72);
    float angry=wrath*.055*(1.-abs(clamp(x,-1.,1.)));
    float upper=.295*almond*opening-angry;
    float lower=-.235*almond*opening;
    float centre=.024*(1.-x*x);
    float upperDistance=p.y-centre-upper;
    float lowerDistance=lower+centre-p.y;
    float eyeDistance=max(upperDistance,lowerDistance);
    float extent=1.-smoothstep(.982,1.025,abs(x));
    float eyeMask=(1.-smoothstep(-aa,aa,eyeDistance))*extent*drawn;
    float lidDistance=min(abs(upperDistance),abs(lowerDistance));
    float lid=exp(-lidDistance*190.)*extent;
    float rim=exp(-abs(lidDistance-.013)*160.)*extent;
    vec2 gaze=vec2(.018*sin(Clock*.21),.009*sin(Clock*.14));
    gaze*=1.-wrath;
    vec2 irisPoint=(p-vec2(gaze.x,.024+gaze.y))/.197;
    float irisRadius=length(irisPoint),irisAngle=atan(irisPoint.y,irisPoint.x);
    float scleraShade=clamp(almond*.74+(.18-p.y)*.4,0.,1.);
    vec3 sclera=mix(vec3(.075,.018,.029),vec3(.63,.445,.42),scleraShade);
    sclera*=.90+.10*noise(p*61.);
    float fibre=.5+.5*sin(irisAngle*121.+irisRadius*33.+sin(irisAngle*47.)*2.7);
    fibre=pow(fibre,3.);
    float irisBand=pow(max(0.,1.-abs(irisRadius-.64)*1.5),.6);
    vec3 iris=mix(vec3(.18,.012,.034),vec3(.72,.32,.29),fibre)*irisBand;
    iris+=exp(-abs(irisRadius-.94)*70.)*vec3(.78,.59,.53);
    float pupilRadius=mix(.32,.43,wrath);
    float pupil=1.-smoothstep(pupilRadius-.025,pupilRadius+.025,irisRadius);
    iris=mix(iris,vec3(.001,.0003,.002),pupil);
    iris+=exp(-length((irisPoint-vec2(-.26,.30))*vec2(1.2,1.7))*30.)*vec3(.98,.87,.80);
    iris+=exp(-length(irisPoint-vec2(.28,-.30))*53.)*vec3(.25,.09,.09);
    vec3 livingEye=mix(sclera,iris,1.-smoothstep(.985,1.02,irisRadius));
    float veins=bloodVeins(p-vec2(0.,.024),wrath)*smoothstep(1.02,1.10,irisRadius);
    livingEye=mix(livingEye,vec3(.46,.003,.019),veins*(.72+.28*wrath));
    result=mix(result,livingEye,eyeMask*presence);
    result+=(lid*vec3(.50,.065,.082)+rim*vec3(.055,.012,.023))*presence*drawn;
    // Engraved brow ridges draw down in the final act; no concentric data rings.
    vec2 brow=vec2(abs(p.x),p.y);
    float browDistance=sdLine(brow,vec2(.09,.405-wrath*.07),vec2(.54,.245));
    result+=exp(-browDistance*160.)*vec3(.29,.022,.038)*presence*smoothstep(78.,110.,Age);
    // Discrete ember filaments drift between star points, never Matrix columns.
    vec2 fireUV=p*vec2(4.0,2.8)+vec2(0.,-Clock*.018);
    float dust=stars(fireUV,43.);
    float dustMask=smoothstep(.72,1.0,radius)*(1.-smoothstep(1.55,2.10,radius));
    result+=dust*vec3(.44,.09,.065)*dustMask*presence*drawn*.32;
    result+=Impact*vec3(.14,.008,.025)*sky;
    return result;
}
float bookWaveAge(){
    if(Age<104.||Age>=220.)return -1000.;
    return Age-(104.+12.*min(7.,floor((Age-104.)/12.)));
}
// The validated original rock stage returns in the crimson covenant palette.
// Three bounded analytic layers give solid faces, shoulders and a central valley;
// they never march rays, create terrain/entities or affect the astral staff sky.
vec4 mountainLayer(float x,float y,float layer){
    float seed=layer*31.7+7.3;
    float base=mix(.008,-.055,layer*.5);
    float peak=base,center=0.,summit=.1,width=.4;
    for(int i=-5;i<=5;i++){
        float id=float(i),c=id*.63+(hash(vec2(id,seed))-.5)*.23;
        float valley=.16+.84*smoothstep(.10,.76,abs(c));
        float height=(.095+layer*.13)*(.65+.55*hash(vec2(id+9.,seed)))*valley;
        float w=.28+hash(vec2(id+37.,seed))*.30;
        float dx=mod(x-c+PI,2.*PI)-PI;
        float flank=dx<0.?(1.05+.38*hash(vec2(id,seed+4.))):(.78+.42*hash(vec2(id,seed+8.)));
        float h=base+height*(1.-abs(dx)*flank/w);
        if(h>peak){peak=h;center=x-dx;summit=base+height;width=w/flank;}
        // Independent shoulder faces preserve a broken rocky silhouette.
        for(int j=-1;j<=1;j+=2){
            float side=float(j);
            float offset=side*w*(.37+.15*hash(vec2(id+side*13.,seed+21.)));
            float childHeight=height*(.61+.15*hash(vec2(id+side*31.,seed+34.)));
            float childWidth=w*(.22+.12*hash(vec2(id+side*7.,seed+11.)));
            float childDx=mod(x-c-offset+PI,2.*PI)-PI;
            float child=base+childHeight*(1.-abs(childDx)/childWidth);
            if(child>peak){peak=child;center=x-childDx;summit=base+childHeight;width=childWidth;}
        }
    }
    peak+=(noise(vec2(x*91.+seed,3.1))-.5)*(.012+layer*.005)
        +(noise(vec2(x*217.+seed,17.))- .5)*.005;
    float aa=1.8/OutSize.y;
    float cover=1.-smoothstep(peak-aa,peak+aa,y);
    if(cover<.001)return vec4(0.);
    float h=clamp((y-base)/max(.035,summit-base),0.,1.);
    float spine=center+(1.-h)*(.025+.030*layer)*sin(center*9.+seed);
    float side=x-spine;
    float inward=-sign(center);
    float flankLight=.25+.65*smoothstep(-.15,.15,side*inward/max(.05,width));
    vec2 rockUV=vec2(x*(75.+layer*21.)+y*11.,y*63.);
    float slabs=noise(floor(rockUV*1.5)*.67+seed);
    float erosion=noise(rockUV*.38)*.58+noise(rockUV*.89+19.)*.28+noise(rockUV*2.13)*.14;
    float ribs=pow(1.-abs(noise(vec2((x-spine)*180.+erosion*2.,y*73.))*2.-1.),8.);
    float relief=.55+.45*erosion;
    vec3 shadow=vec3(.022,.014,.021);
    vec3 lit=vec3(.22,.185,.177);
    vec3 rock=mix(shadow,lit,flankLight*relief);
    rock*=.58+.42*slabs;
    rock+=ribs*flankLight*vec3(.023,.015,.018)*(1.-h*.4);
    // Warm silver faces remain matte: the covenant illuminates real rock area,
    // rather than outlining every ridge with the earlier green/gold emission.
    float snow=smoothstep(.63,.94,h+erosion*.22)*smoothstep(.40,.78,flankLight)
        *smoothstep(.30,.66,slabs+erosion*.28);
    rock=mix(rock,vec3(.49,.435,.415)*(.65+.35*erosion),snow*.88);
    float seam=pow(max(0.,1.-abs(sin(rockUV.x*1.8+floor(rockUV.y)*.37))),20.);
    seam*=step(.75,slabs)*(1.-smoothstep(.12,.66,h));
    rock+=seam*vec3(.19,.014,.032)*(.45+.55*erosion);
    float warm=pow(max(0.,1.-abs(sin(x*59.+y*87.))),24.)*step(.87,slabs);
    rock+=warm*vec3(.20,.055,.040)*(1.-h)*.45;
    float haze=(1.-layer*.5)*.27;
    rock=mix(rock,vec3(.066,.035,.048),haze);
    return vec4(rock,cover);
}
vec4 longhuanMountains(vec3 ray){
    vec2 forward=normalize((Eye-Ground).xz);
    vec2 direction=normalize(ray.xz);
    float x=atan(direction.x*forward.y-direction.y*forward.x,dot(direction,forward));
    float y=ray.y/max(.12,length(ray.xz));
    // A distant, low horizon backdrop leaves the eye and seal as the subject.
    // Compress angular elevation, keeping the authored rock silhouette intact.
    float atlasY=1.-(y/.26+.10)/1.25;
    if(atlasY<0.||atlasY>1.)return vec4(0.);
    // An original baked valley replaces the former repeated narrow peaks.
    // Small overlapping ranges sit beneath the seal and lead to a low centre.
    vec4 range=texture(CrimsonSampler,vec2(x/(2.*PI)+.5,atlasY));
    range.rgb*=vec3(.72,.58,.66);
    float delayed=bookWaveAge()-(1.25+abs(x)*.70);
    float response=step(0.,delayed)*exp(-max(0.,delayed)*.65);
    range.rgb+=response*vec3(.065,.011,.016)*(.25+range.rgb*2.);
    range.rgb+=exp(-abs(y-.009)*110.)*vec3(.16,.041,.052)
        *(.25+.75*noise(vec2(x*21.,3.)));
    return range;
}
// Cangsheng's day realm is independent of both the crimson and stellar scenes.
// Bounded analytical ridges, no ray marching and no generated world chunks.
vec4 crystalRange(float azimuth,float elevation,float layer){
    float rise=smoothstep(5.+layer*5.,38.+layer*5.,Age);
    float peak=-.07,center=0.,summit=.25,base=-.07;
    for(int i=-7;i<=7;i++){
        float id=float(i),seed=layer*39.+11.;
        float c=id*.43+(hash(vec2(id,seed))-.5)*.18;
        float central=exp(-c*c*12.);
        float h=(.12+layer*.05+hash(vec2(id+4.,seed))*.20+central*.30)*rise;
        float w=.11+hash(vec2(id+7.,seed))*.16;
        float dx=mod(azimuth-c+PI,2.*PI)-PI;
        float taper=pow(abs(dx)/w,1.15);
        float shoulder=max(0.,1.-abs(dx-w*.34)/(w*.50))*.64;
        float profile=max(1.-taper,shoulder);
        float top=base+h*profile;
        if(top>peak){peak=top;center=azimuth-dx;summit=h;}
    }
    float erosion=noise(vec2(azimuth*110.+layer*17.,elevation*77.));
    peak+=(noise(vec2(azimuth*171.,layer*3.))-.5)*.012*rise;
    float mask=1.-smoothstep(peak-.002,peak+.002,elevation);
    float height=clamp((elevation-base)/max(.03,summit),0.,1.);
    float ridge=sin((azimuth-center)*86.+elevation*12.);
    float light=.38+.48*smoothstep(-.015,.035,azimuth-center)+erosion*.14;
    vec3 lavender=mix(vec3(.28,.29,.47),vec3(.57,.57,.73),light);
    float flutes=pow(.5+.5*sin(azimuth*255.+erosion*3.+elevation*41.),8.);
    lavender*=.88+.12*ridge;lavender+=flutes*vec3(.06,.07,.10);
    float snow=smoothstep(.48,.88,height+erosion*.24);
    vec3 stone=mix(lavender,vec3(.92,.88,.97)*(.86+.14*light),snow*.89);
    float ledges=pow(max(0.,sin(height*28.+azimuth*53.+erosion*2.)),11.);
    float blossom=smoothstep(.40,.73,noise(vec2(azimuth*163.+9.,elevation*121.)))*ledges;
    stone=mix(stone,vec3(.99,.64,.82),blossom*.88);
    // Slim streams cling to the rock face instead of an all-over noise texture.
    float fall=pow(max(0.,1.-abs(sin(azimuth*139.+layer*9.))),65.);
    fall*=smoothstep(.13,.35,height)*(1.-smoothstep(.72,.91,height));
    stone=mix(stone,vec3(.80,.91,1.),fall*.70);
    float facets=pow(max(0.,1.-abs(sin(azimuth*71.+elevation*91.))),30.);
    stone+=facets*vec3(.04,.09,.13)*smoothstep(.50,.90,erosion);
    float mist=(1.-layer*.22)*.43+exp(-pow((elevation+.015)*21.,2.))*.25;
    stone=mix(stone,vec3(.89,.83,.94),mist);
    return vec4(stone,mask);
}
vec3 cangshengSky(vec3 ray){
    float h=clamp(ray.y*.72+.34,0.,1.);
    vec3 color=mix(vec3(.98,.84,.91),vec3(.59,.73,.94),pow(h,.72));
    vec3 lightDir=normalize(vec3(-.45,.41,-.78));
    float sun=max(0.,dot(ray,lightDir));
    color+=pow(sun,14.)*vec3(.14,.09,.075);
    color=mix(color,vec3(1.,.96,.91),pow(sun,240.)*.9);
    vec2 cloudUV=ray.xz/max(.13,abs(ray.y))*.44;
    float clouds=fbm(cloudUV+vec2(Clock*.008,0.));
    float puff=smoothstep(.38,.72,clouds)*smoothstep(-.04,.34,ray.y);
    color=mix(color,vec3(.99,.93,.98),puff*.78);
    vec2 forward=normalize((Eye-Ground).xz);
    vec2 direction=normalize(ray.xz);
    float azimuth=atan(direction.x*forward.y-direction.y*forward.x,dot(direction,forward));
    float elevation=ray.y/max(.15,length(ray.xz));
    // Original Blender dream realm: near gardens, middle cliff arcades and
    // distant sanctuary islands. Linear slope spans +1.15 to -.35; preserve
    // its authored elevation instead of flattening the architecture.
    float mountainRise=max(.025,smoothstep(8.,52.,Age));
    float atlasY=1.-(elevation/mountainRise+.35)/1.50;
    if(atlasY>=0.&&atlasY<=1.){
        vec4 mountains=texture(LandscapeSampler,vec2(azimuth/(2.*PI)+.5,atlasY));
        vec3 hazed=mix(mountains.rgb,vec3(.89,.86,.96),.055);
        color=mix(color,hazed,mountains.a*smoothstep(8.,22.,Age));
    }
    // Sparse moving petals and tiny sun glints; no star field or black filter.
    vec2 cellUV=vec2(azimuth*18.,elevation*18.+Clock*.06);
    vec2 cell=floor(cellUV),q=fract(cellUV)-.5;
    float drift=sin(Clock*.7+hash(cell)*6.28)*.18;
    q.x+=drift;
    float petal=exp(-dot(q*vec2(36.,22.),q*vec2(36.,22.)))*step(.82,hash(cell+31.));
    color+=petal*vec3(.13,.07,.11);
    return color;
}
vec3 cangshengGround(vec3 at,vec3 ray){
    vec2 p=at.xz-Ground.xz;
    float r=length(p);
    // The dream takes over solid ground as weathered ivory plates over a deep
    // violet fault. A fixed height atlas bounds the relief work; there is no
    // reflected sky, animated water or per-pixel unbounded ray march here.
    vec2 base=rot(.37)*(p/24.);
    vec2 view=rot(.37)*ray.xz/max(.22,abs(ray.y));
    view*=min(1.,4.0/max(.001,length(view)));
    float rupture=smoothstep(82.,154.,Age);
    float relief=mix(.45,1.65,rupture);
    vec2 shift=view*(relief/24.);
    float steps=VisualQuality<1.5?5.:8.;
    float low=0.,high=1.;
    for(int i=0;i<8;i++){
        if(float(i)>=steps)break;
        float d=(float(i)+1.)/steps;
        float h=texture(CrimsonGroundSampler,fract(base+shift*d)).a;
        float surface=1.-smoothstep(.02,.96,h);
        if(d>=surface){high=d;break;}low=d;
    }
    for(int i=0;i<2;i++){
        float d=(low+high)*.5;
        float h=texture(CrimsonGroundSampler,fract(base+shift*d)).a;
        if(d>=1.-smoothstep(.02,.96,h))high=d;else low=d;
    }
    vec2 uv=base+shift*((low+high)*.5);
    vec4 stone=texture(CrimsonGroundSampler,fract(uv));
    vec2 texel=vec2(1./1024.,0.);
    float hx=texture(CrimsonGroundSampler,fract(uv+texel)).a-texture(CrimsonGroundSampler,fract(uv-texel)).a;
    float hz=texture(CrimsonGroundSampler,fract(uv+texel.yx)).a-texture(CrimsonGroundSampler,fract(uv-texel.yx)).a;
    vec3 normal=normalize(vec3(-hx*(relief*1024./48.),1.,-hz*(relief*1024./48.)));
    vec3 light=normalize(vec3(-.48,.78,-.42));
    float diffuse=.36+.64*max(0.,dot(normal,light));
    float strata=dot(stone.rgb,vec3(.24,.55,.21));
    vec3 ivory=mix(vec3(.44,.39,.56),vec3(.90,.84,.87),smoothstep(.07,.57,strata));
    // Muted lichen and exposed pale edges link this material to the cliffs.
    float moss=smoothstep(.64,.82,noise(p*.19+11.))*smoothstep(.58,.88,stone.a);
    ivory=mix(ivory,vec3(.48,.58,.47),moss*.38);
    vec3 rock=ivory*diffuse*(.45+.55*stone.a);
    float cavity=1.-smoothstep(.08,.48+rupture*.10,stone.a);
    float face=smoothstep(.045,.19,stone.a)*(1.-smoothstep(.43,.69,stone.a));
    rock=mix(rock,vec3(.085,.055,.145)+face*vec3(.13,.10,.20),cavity*.94);
    float deep=1.-smoothstep(.02,.16,stone.a);
    float pulse=step(160.,Age)*exp(-max(0.,Age-160.)*.055);
    rock+=deep*vec3(.15,.095,.24)*(.35+.65*rupture+pulse*.55);
    float front=(Age-160.)*3.2;
    float wave=step(160.,Age)*exp(-pow((r-front)/1.8,2.))*(1.-smoothstep(180.,204.,Age));
    rock+=wave*(.10+.35*cavity)*vec3(.30,.23,.38);
    float dust=stars(p*.16+vec2(Clock*.003,0.),47.);
    rock+=dust*deep*vec3(.15,.12,.20)*rupture;
    return mix(rock,vec3(.62,.57,.71),smoothstep(32.,102.,r)*.35);
}
void main(){
    vec2 uv=texCoord;bool astral=Mode>.5;
    vec4 local=InvProjection*vec4(uv*2.-1.,1.,1.);
    vec3 ray=normalize(mat3(CameraRotation)*local.xyz);
    vec3 right=normalize(mat3(CameraRotation)*vec3(1,0,0));
    vec3 up=normalize(mat3(CameraRotation)*vec3(0,1,0));
    float originalDepth=texture(DepthSampler,uv).r;
    vec3 surface=surfaceAt(uv);
    vec3 surfaceNormal=normalize(cross(dFdx(surface),dFdy(surface)));
    if(dot(surfaceNormal,ray)>0.)surfaceNormal=-surfaceNormal;
    float topFace=smoothstep(.55,.90,surfaceNormal.y);
    float opaque=1.-step(.999995,originalDepth);
    if(Mode>1.5){
        vec3 original=texture(DiffuseSampler,uv).rgb;
        float reveal=clamp(Takeover*Domain,0.,1.);
        if(TerrainPass>.5){
            float extent=1.-smoothstep(80.,112.,length(surface.xz-Ground.xz));
            float height=1.-smoothstep(8.,22.,abs(surface.y-Ground.y));
            float mask=topFace*opaque*height*extent*reveal;
            if(mask>.0001)original=mix(original,cangshengGround(surface,ray),mask);
        }else{
            float sky=smoothstep(.99996,.999995,originalDepth);
            if(sky*reveal>.0001)original=mix(original,cangshengSky(ray),sky*reveal);
            vec3 pastel=original*vec3(1.025,.98,1.015)+vec3(.022,.014,.024);
            original=mix(original,pastel,reveal*.18*(1.-topFace*opaque));
        }
        fragColor=vec4(clamp(original,0.,1.),1.);return;
    }
    if(TerrainPass>.5){
        vec3 original=texture(DiffuseSampler,uv).rgb;
        float range=length(surface.xz-Ground.xz);
        float extent=1.-smoothstep(80.,112.,range);
        float height=1.-smoothstep(8.,22.,abs(surface.y-Ground.y));
        float takeover=astral?max(Takeover,smoothstep(3.,38.,Age)):Takeover;
        float reveal=clamp(takeover*Domain,0.,1.);
        float mask=topFace*opaque*height*extent*reveal;
        if(mask<.0001){fragColor=vec4(original,1.);return;}
        vec3 material=FloorStyle>.5?waterGround(surface,ray):crackedGround(surface,ray);
        if(!astral&&FloorStyle<.5){
            float after=bookWaveAge();
            float front=after*2.8;
            float ring=exp(-pow((range-front)/.9,2.))*step(0.,after)*(1.-smoothstep(6.,10.,after));
            material+=ring*vec3(.13,.010,.021);
        }
        // At full takeover mask=1: no original block texture survives.
        fragColor=vec4(mix(original,material,mask),1.);return;
    }
    vec3 billboardNormal=normalize(cross(right,up));
    float denom=dot(ray,billboardNormal);float plane=dot(Eye,billboardNormal)/denom;
    bool visible=plane>0.&&abs(denom)>.001&&length(Eye)>1.;
    vec3 at=ray*plane-Eye;
    vec2 p=vec2(dot(at,right),dot(at,up))/20.;
    float r=length(p),angle=atan(p.y,p.x);
    float grow=astral?smoothstep(3.,44.,Age):smoothstep(42.,122.,Age);
    float horizon=.42*mix(.018,1.,grow);
    // Analytic thin-lens deflection of the actual framebuffer, with a smooth finite support.
    vec2 warped=uv;
    if(visible&&Lens.z>.0001){
        vec2 dv=uv-Lens.xy;vec2 metric=dv*vec2(OutSize.x/OutSize.y,1.);
        float rr=length(metric)/Lens.z;
        float deflect=(astral?.22:.035)*grow/max(.18,rr*rr)*(1.-smoothstep(1.2,3.2,rr));
        float torque=astral?smoothstep(172.,210.,Age)*.12*exp(-abs(rr-1.3)*2.):.018*sin(Clock*.18)*exp(-abs(rr-1.)*2.);
        warped=Lens.xy+rot(torque)*(dv*(1.+deflect*Domain));
    }
    vec3 scene=texture(DiffuseSampler,clamp(warped,vec2(.001),vec2(.999))).rgb;
    float depth=texture(DepthSampler,uv).r;
    float sky=smoothstep(.99996,.999995,depth);
    float luma=dot(scene,vec3(.2126,.7152,.0722));
    float stopped=smoothstep(43.5,44.,Age)*(1.-smoothstep(162.,172.,Age));
    vec3 graded=astral?mix(scene,vec3(luma)*vec3(.62,.64,.76),stopped*.94):mix(scene,scene*vec3(.92,.26,.33)+vec3(luma*.11,.002,.008),.64);
    graded*=1.-(astral?.30:.08)*Takeover*Domain;
    // Preserve the saturated floor materials produced before entity rendering.
    scene=mix(scene,graded,Domain*(1.-topFace*opaque*Takeover));
    float skyPresence=astral?max(Takeover,smoothstep(3.,38.,Age)):Takeover;
    // Reject terrain before the celestial query. The floor pass already traced
    // its reflected sky; it must not pay for a second invisible forward query.
    if(sky*skyPresence*Domain>.0001){
        vec3 background=astral?astralSky(ray,vec3(0.)):cosmos(ray,false);
        scene=mix(scene,background,sky*skyPresence*Domain);
    }
    if(!astral&&visible){
        scene=crimsonCovenant(scene,p,ray,sky);
    }
    // Solid BOOK mountains sit before the sky seal, behind real terrain and
    // entities. Keeping this after the covenant prevents it glazing the cliffs.
    if(!astral&&sky*Takeover*Domain>.001){
        vec4 mountains=longhuanMountains(ray);
        scene=mix(scene,mountains.rgb,mountains.a*sky*Takeover*Domain);
    }
    float vignette=smoothstep(.3,.78,length(uv-.5));scene*=1.-vignette*Domain*Takeover*.16;
    fragColor=vec4(clamp(scene,0.,1.),1.);
}
