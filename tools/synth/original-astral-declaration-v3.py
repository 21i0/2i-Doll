"""Original world-fracture declaration v3. No imported recording or sample.

The declaration itself reads no input audio: exponential oscillators, explicit
Gaussian-noise seeds, inharmonic metal modes, procedural debris and reflections.
The optional timeline preview reads only our four original auxiliary game cues.
"""
from pathlib import Path
import hashlib
import json
import math
import subprocess
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[2]
A=ROOT/"src/main/resources/assets/two_i_combat_doll"
OUT=ROOT/"verification/astral-20261027/audio"
FFMPEG=ROOT.parent/"DollSkinBlinkTool/verification/media_tools/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe"
RATE,DURATION,SEED=48000,5.90,27102103
t=np.arange(round(RATE*DURATION))/RATE
OUT.mkdir(parents=True,exist_ok=True)


def noise(low,high,seed):
    x=np.random.default_rng(seed).normal(0,1,len(t));f=np.fft.rfftfreq(len(t),1/RATE)
    shape=1/(1+(low/np.maximum(f,.0001))**10)/(1+(f/high)**10)
    y=np.fft.irfft(np.fft.rfft(x)*shape,len(t))
    return y/max(np.std(y),1e-12)


def filter_band(x,low,high):
    f=np.fft.rfftfreq(len(x),1/RATE)
    shape=1/(1+(low/np.maximum(f,.0001))**10)/(1+(f/high)**10)
    return np.fft.irfft(np.fft.rfft(x)*shape,len(x))


def slide(start,end,decay):
    return np.sin(math.tau*(end*t+(start-end)*decay*(1-np.exp(-t/decay))))


def delay(x,seconds):
    n=round(seconds*RATE);out=np.zeros_like(x)
    if n<len(x):out[n:]=x[:len(x)-n]
    return out


def edge(x):
    env=np.sin(np.clip(t/.0008,0,1)*math.pi/2)**2
    env*=np.sin(np.clip((DURATION-t)/.55,0,1)*math.pi/2)**2
    return x*env if x.ndim==1 else x*env[:,None]


def write_wave(path,x):
    with wave.open(str(path),"wb") as out:
        out.setnchannels(1 if x.ndim==1 else x.shape[1]);out.setsampwidth(2);out.setframerate(RATE)
        out.writeframes(np.round(np.clip(x,-.999,.999)*32767).astype("<i2").tobytes())


def decode(path,channels=2):
    raw=subprocess.run([str(FFMPEG),"-v","error","-i",str(path),"-f","f32le","-ac",str(channels),"-ar",str(RATE),"pipe:1"],capture_output=True,check=True).stdout
    x=np.frombuffer(raw,dtype="<f4").astype(np.float64)
    return x if channels==1 else x.reshape(-1,channels)


def db(x):return 20*math.log10(max(float(x),1e-12))
def rms(x):return float(np.sqrt(np.mean(x*x)))
def true_peak(x):
    # Four-times bandlimited interpolation of the actual encoded waveform.
    peaks=[]
    for channel in range(x.shape[1]):
        y=np.fft.irfft(np.fft.rfft(x[:,channel]),len(x)*4)*4
        peaks.append(float(np.abs(y).max()))
    return max(peaks)


# Centre impact: a physical low-frequency slam, distorted upper harmonics and
# a broad dark-thunder body. It starts immediately; there is no anticipatory
# rise, dialogue, bright arpeggio or delayed melodic "swoop".
slam=slide(178,43,.075)*np.exp(-t/1.15)*1.10
slam+=slide(356,86,.075)*np.exp(-t/.63)*.23
sub=np.tanh((slide(58,34,2.3)+.22*slide(116,68,2.3))*2.2)*.44
sub*=np.exp(-t/3.3)*(.90+.10*np.sin(math.tau*7.1*t))
thunder=noise(28,780,SEED)*(.47*np.exp(-t/1.1)+.10*np.exp(-t/3.1))

# Millisecond world fracture: separate brittle crack, heavy tearing pressure
# and non-periodic shards. Noise is created here, never read from a recording.
crack=noise(1100,12500,SEED+1)*.60*np.exp(-t/.055)
tear=noise(260,6900,SEED+2)*(.40*np.exp(-t/.28)+.15*np.exp(-t/1.15))
tear*=.64+.36*np.sin(math.tau*(31*t+9*t*t))**2
debris=np.zeros_like(t)
rng=np.random.default_rng(SEED+3)
for i,seconds in enumerate((0,.009,.026,.051,.083,.128,.188,.271,.382,.529,.733,1.017)):
    local=np.maximum(t-seconds,0)
    grain=noise(170+i*19,5700-i*240,SEED+10+i)
    env=(1-np.exp(-local*2400))*np.exp(-local/(.023+i*.008))
    env[t<seconds]=0
    debris+=grain*env*(.12/(1+i*.12))

# Deep metal sheets ripping apart: unequally spaced modes and random phases,
# ring modulation and slow drag. Short high modes die while low creaks remain.
metal=np.zeros_like(t)
for i,f in enumerate((83,137,211,319,487,733,1091,1559,2203,3089,4217)):
    end=f*(.61+i*.014)
    phase=math.tau*(end*t+(f-end)*.32*(1-np.exp(-t/.32)))
    phase+=.75*np.sin(math.tau*(17+i*3.7)*t)*np.exp(-t/.8)
    part=np.sin(phase+float(rng.uniform(-math.pi,math.pi)))
    part*=np.exp(-t/(1.7-i*.12))*(.15/(1+i*.28))
    metal+=part
metal=np.tanh(metal*1.3)*.55

# Sustained impending collapse: dark 25..310 Hz pressure has uneven movement,
# not a long pure note. The distant tail retains weight after the first slam.
doom=noise(25,310,SEED+40)*(.19*np.exp(-t/4.4))
doom*=.72+.28*np.sin(math.tau*(.43*t+.021*t*t)+.7)**2
doom+=np.sin(math.tau*31.7*t)*.082*np.exp(-t/3.5)
clock=np.zeros_like(t)
for i,seconds in enumerate((.81,1.69,2.71,3.87,5.13)):
    local=np.maximum(t-seconds,0)
    pitch=1-i*.047
    hit=(np.sin(math.tau*1279*pitch*local)*.083*np.exp(-local*50)
         +np.sin(math.tau*2573*pitch*local)*.035*np.exp(-local*87)
         +np.sin(math.tau*163*pitch*local)*.075*np.exp(-local*31))
    hit*=1-np.exp(-local*1700);hit[t<seconds]=0;clock+=hit

direct=edge(slam+sub+thunder+crack+tear+debris+metal+doom+clock)
# Far, dark, huge-space reflections; sub remains centred. More high-frequency
# material is removed at each arrival, avoiding a shiny or cartoon reverb.
reflection_source=filter_band(thunder+tear+debris+metal,38,1500)
left=direct.copy();right=direct.copy()
for i,(seconds,gain) in enumerate(((.043,.27),(.119,.23),(.257,.19),(.481,.16),(.823,.13),(1.379,.095),(2.073,.065))):
    dark=filter_band(reflection_source,28,1500/(1+i*.39))
    left+=delay(dark,seconds)*gain
    right+=delay(dark,seconds*1.123+.017)*gain
left+=noise(22,240,SEED+50)*.055*np.exp(-t/3.5)
right+=noise(22,240,SEED+51)*.055*np.exp(-t/3.5)
raw=edge(np.column_stack((left,right)))
raw-=raw.mean(axis=0)

# Shape density over time before mastering: the opening has a dense crack and
# tactile body, then a sustained pressure room. The final half second exhales.
window=round(RATE*.09)
power=np.mean(raw*raw,axis=1)
c=np.r_[0,np.cumsum(np.pad(power,(window//2,window-window//2),mode="edge"))]
local_rms=np.sqrt((c[window:]-c[:-window])[:len(t)]/window+1e-9)
target=np.interp(t,(0,.12,.8,1.7,3.3,5.15,5.9),(.40,.40,.36,.29,.235,.18,0))
gain=np.clip(target/np.maximum(local_rms,.025),.30,3.2)
shaped=raw*gain[:,None]
# Solve one soft-knee drive for roughly -12 dBFS programme RMS. This is deliberate
# nonlinear sound design with headroom, not hard sample clipping or an unsafe
# external volume multiplier. Dynamics/spectrum were redesigned before gain.
lo,hi=.1,8.0
for _ in range(35):
    drive=(lo+hi)/2
    test=edge(.80*np.tanh(shaped*drive/.80))
    if rms(test)<10**(-12/20):lo=drive
    else:hi=drive
master=edge(.80*np.tanh(shaped*((lo+hi)/2)/.80))
candidate=OUT/"time-stop-original-v3-candidate.ogg"
wav=OUT/"time-stop-original-v3.wav"
for attempt in range(3):
    write_wave(wav,master)
    subprocess.run([str(FFMPEG),"-hide_banner","-loglevel","error","-i",str(wav),"-c:a","libvorbis","-q:a","7","-y",str(candidate)],check=True)
    actual=decode(candidate)
    peak=true_peak(actual)
    if db(peak)<=-1.2:break
    master*=10**(-1.3/20)/peak
mono=actual.mean(axis=1)
f=np.fft.rfftfreq(len(mono),1/RATE);p=np.abs(np.fft.rfft(mono-mono.mean()))**2;total=float(p.sum())
stats={
    "version":"original-world-fracture-v3","duration_seconds":len(actual)/RATE,"sample_rate":RATE,"channels":2,
    "peak_dbfs":round(db(np.abs(actual).max()),3),"true_peak_4x_dbfs":round(db(true_peak(actual)),3),
    "rms_dbfs":round(db(rms(actual)),3),"first_0_8_seconds_rms_dbfs":round(db(rms(actual[:round(.8*RATE)])),3),
    "first_0_1_seconds_rms_dbfs":round(db(rms(actual[:round(.1*RATE)])),3),
    "20_160_hz_energy_fraction":round(float(p[(f>=20)&(f<160)].sum())/total,5),
    "20_320_hz_energy_fraction":round(float(p[(f>=20)&(f<320)].sum())/total,5),
    "320_3000_hz_energy_fraction":round(float(p[(f>=320)&(f<3000)].sum())/total,5),
    "3000_12000_hz_energy_fraction":round(float(p[(f>=3000)&(f<12000)].sum())/total,5),
    "peak_samples_at_or_above_0_999":int(np.count_nonzero(np.abs(actual)>=.999)),
    "dc_offset":round(float(actual.mean()),8),
    "rms_100ms":[round(rms(actual[i:i+4800]),6) for i in range(0,len(actual),4800)],
    "origin":"100% original mathematical synthesis; no reference waveform/sample/spectrogram input.",
    "generator":str(Path(__file__).relative_to(ROOT)),"generator_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),"seed":SEED,
    "layers":["instant broadband fracture and twelve irregular procedural debris grains","centred 178->43 Hz slam, distorted 58->34 Hz sub and upper harmonics","seeded dark-thunder pressure body","eleven inharmonic metal-tearing modes with nonperiodic drag","sustained low-frequency collapsing room","five procedural clock escapement hits","seven increasingly dark asymmetric far-reflection taps"],
    "comparison":"v2 was dominated by a descending oscillator/formant gesture; v3 rebuilds the onset as dense broadband fracture, thunder and physical sub slam, adds audible low/mid tearing and keeps a weighted dark pressure tail. Not merely increased gain.",
    "license":"MIT; original procedural synthesis.","limits":"Objective signal QA and runtime audio-source checks do not replace subjective listening on target equipment.",
}
assert stats["peak_samples_at_or_above_0_999"]==0,stats
assert stats["true_peak_4x_dbfs"]<=-1.19,stats
assert -10.5<=stats["first_0_8_seconds_rms_dbfs"]<=-7.0,stats
assert -13.6<=stats["rms_dbfs"]<=-11.0,stats
# Only a fully checked candidate replaces the game asset.
ogg=A/"sounds/time_stop.ogg";ogg.write_bytes(candidate.read_bytes())
stats.update({"wav":str(wav.relative_to(ROOT)),"ogg":str(ogg.relative_to(ROOT)),"sha256":hashlib.sha256(ogg.read_bytes()).hexdigest()})
write_wave(OUT/"time_stop.wav",actual)
report_path=OUT/"audio-audit.json";report=json.loads(report_path.read_text(encoding="utf8"))
report["synthesis"]="All five final game sounds are original procedural synthesis. time_stop is original world-fracture v3, with no reference recording input."
report["encoded_assets"]["time_stop"]=stats
report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
(OUT/"original-declaration-v3-provenance.json").write_text(json.dumps(stats,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
(OUT/"original-declaration-provenance.json").write_text(json.dumps(stats,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
preview=np.zeros((round(RATE*11.2),2))
def add(x,sec,gain=1):
    if x.ndim==1:x=np.column_stack((x,x))
    start=round(RATE*sec);n=min(len(x),len(preview)-start);preview[start:start+n]+=x[:n]*gain
add(decode(A/"sounds/astral_charge.ogg",1),0,.62)
for sec in (0,1,2):add(decode(A/"sounds/astral_tick.ogg",1),sec,.85)
add(actual,2.2);add(decode(A/"sounds/astral_reverse.ogg",1),8.6,.9)
preview*=min(1,.90/max(float(np.abs(preview).max()),1e-12))
write_wave(OUT/"astral-timeline-preview.wav",preview)
image=Image.new("RGB",(1400,710),"#0b1020");draw=ImageDraw.Draw(image);font=ImageFont.truetype(r"C:\Windows\Fonts\consola.ttf",19)
draw.text((30,20),"ORIGINAL WORLD-FRACTURE V3 / NO IMPORTED AUDIO / ACTUAL ENCODED WAVEFORM",fill="#e9f2ff",font=font)
for row,(name,x,color) in enumerate((("BROADBAND FRACTURE + PROCEDURAL DEBRIS",crack+tear+debris,"#ff9988"),("CENTRED SUB SLAM + DARK THUNDER + METAL",slam+sub+thunder+metal+doom,"#bd9fff"),("FINAL ENCODED MONO SUM / STEREO GAME ASSET",mono,"#80dcff"))):
    top=70+row*195;draw.text((30,top),name,fill=color,font=font);y=top+85;x=x/max(float(np.abs(x).max()),1e-12)
    for col in range(1320):
        a=math.floor(col*len(x)/1320);b=max(a+1,math.floor((col+1)*len(x)/1320));mag=float(np.abs(x[a:b]).max())
        draw.line((40+col,y-mag*54,40+col,y+mag*54),fill=color)
draw.text((30,660),f"RMS {stats['rms_dbfs']} dBFS / first0.8s {stats['first_0_8_seconds_rms_dbfs']} dBFS / 4x truepeak {stats['true_peak_4x_dbfs']} dBFS",fill="#b7d6e2",font=font)
image.save(OUT/"original-declaration-v3-waveforms.png");image.save(OUT/"audio-waveforms.png")
print(json.dumps({k:v for k,v in stats.items() if k!="rms_100ms"},ensure_ascii=False,indent=2))
