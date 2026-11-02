"""Original astral declaration v2: numerical synthesis, no reference samples.

Inputs: equations, explicitly seeded Gaussian noise, and our own four auxiliary
sounds for the optional timeline preview. The game declaration is synthesized
without reading, sampling, decoding, or embedding any supplied reference video.
"""
from pathlib import Path
import hashlib
import json
import math
import subprocess
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/two_i_combat_doll"
OUT = ROOT / "verification/astral-20261027/audio"
FFMPEG = ROOT.parent / "DollSkinBlinkTool/verification/media_tools/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe"
RATE, DURATION, SEED = 48000, 5.90, 27102102
OUT.mkdir(parents=True, exist_ok=True)
t = np.arange(round(RATE * DURATION), dtype=np.float64) / RATE


def tone(hz, phase=0):
    return np.sin(math.tau * hz * t + phase)


def noise(low, high, seed):
    rng = np.random.default_rng(seed)
    samples = rng.normal(0, 1, len(t))
    freq = np.fft.rfftfreq(len(t), 1 / RATE)
    mask = 1 / (1 + (low / np.maximum(freq, .0001)) ** 10)
    mask *= 1 / (1 + (freq / high) ** 10)
    result = np.fft.irfft(np.fft.rfft(samples) * mask, len(t))
    return result / max(np.std(result), 1e-12)


def drop(start, end, decay):
    phase = math.tau * (end * t + (start - end) * decay * (1 - np.exp(-t / decay)))
    return np.sin(phase)


def edge(signal, attack=.002, release=.22):
    return signal * np.sin(np.clip(t / attack, 0, 1) * math.pi / 2) ** 2 * np.sin(np.clip((DURATION-t) / release, 0, 1) * math.pi / 2) ** 2


def delayed(signal, seconds):
    offset = round(seconds * RATE)
    result = np.zeros_like(signal)
    if offset < len(result):
        result[offset:] = signal[:len(result)-offset]
    return result


def mono_metrics(signal):
    freq = np.fft.rfftfreq(len(signal), 1 / RATE)
    power = np.abs(np.fft.rfft(signal-signal.mean())) ** 2
    total = max(float(power.sum()), 1e-12)
    return {
        "duration_seconds": len(signal)/RATE, "sample_rate": RATE,
        "peak_dbfs": round(20*math.log10(max(float(np.abs(signal).max()),1e-12)),3),
        "rms_dbfs": round(20*math.log10(max(float(np.sqrt(np.mean(signal**2))),1e-12)),3),
        "20_160_hz_energy_fraction": round(float(power[(freq>=20)&(freq<160)].sum())/total,5),
        "20_320_hz_energy_fraction": round(float(power[(freq>=20)&(freq<320)].sum())/total,5),
        "peak_samples_at_or_above_0_999": int(np.count_nonzero(np.abs(signal)>=.999)),
        "dc_offset": round(float(signal.mean()),8),
        "rms_100ms": [round(float(np.sqrt(np.mean(signal[i:i+4800]**2))),6) for i in range(0,len(signal),4800)],
    }


def write_wave(path, samples):
    with wave.open(str(path),"wb") as out:
        out.setnchannels(1 if samples.ndim==1 else samples.shape[1])
        out.setsampwidth(2);out.setframerate(RATE)
        out.writeframes(np.round(np.clip(samples,-.999,.999)*32767).astype("<i2").tobytes())


def decode(path, channels=1):
    result=subprocess.run([str(FFMPEG),"-v","error","-i",str(path),"-f","f32le","-ac",str(channels),"-ar",str(RATE),"pipe:1"],capture_output=True,check=True)
    data=np.frombuffer(result.stdout,dtype="<f4").astype(np.float64)
    return data if channels==1 else data.reshape(-1,channels)


# 1. Sudden pressure declaration: two phase-continuous falling fundamentals,
# their harmonics, and a short broad-band implosion, all generated equations.
shock=(drop(157,37,.19)*.90+drop(314,74,.19)*.28)*np.exp(-t*1.48)
shock*=1-np.exp(-t*500)
shock+=noise(29,350,SEED)*.20*np.exp(-t*8)*(1-np.exp(-t*400))
sub=(tone(39)*.16+tone(78,.12)*.054)*np.exp(-t*.56)*(1-np.exp(-t*16))

# 2. A mechanical formant roar: 24 moving harmonics with three shifting
# resonance windows. This is an oscillator bank, not a speech or animal sample.
f0=44+92*np.exp(-t/.55)
phase=math.tau*(44*t+92*.55*(1-np.exp(-t/.55)))
roar=np.zeros_like(t)
for n in range(1,25):
    hz=n*f0
    formant=(np.exp(-((hz-(360+380*np.exp(-t/.23)))/135)**2)*.75
             +np.exp(-((hz-(860+560*np.exp(-t/.48)))/240)**2)*.63
             +np.exp(-((hz-(1620+740*np.exp(-t/.76)))/340)**2)*.40)
    amplitude=(.13/n**.9+formant/n**.45)
    roar+=np.sin(n*phase+.42*np.sin(phase*.497+n*.21))*amplitude
roar=np.tanh(roar*.8)*.34*np.exp(-t*1.30)*(1-np.exp(-t*180))
roar*=.88+.12*np.sin(math.tau*(19*t-4*np.minimum(t,1)**2))

# 3. Inharmonic descending plates and a separate rising pitch scrape. Their
# independent, uneven partials create changing spectral detail rather than
# one plain sine swoop followed by a drone.
metal=np.zeros_like(t)
for i,f in enumerate((417,683,1117,1469,2389,3703,5189)):
    end=f*(.58+i*.018)
    theta=math.tau*(end*t+(f-end)*.26*(1-np.exp(-t/.26)))
    theta+=(1.4-.11*i)*np.sin(math.tau*(31+i*7.3)*t)*np.exp(-t*2)
    metal+=np.sin(theta)*(.11/(1+i*.24))*np.exp(-t*(1.0+i*.29))
metal*=1-np.exp(-t*360)
scrape=noise(650,6700,SEED+1)*.12*np.exp(-t*2.2)*(1-np.exp(-t*100))
scrape*=.52+.48*np.sin(math.tau*(39*t+15*t*t))**2
glass=(np.sin(math.tau*(850*t+810*np.minimum(t,1.1)**2))*.045
       +np.sin(math.tau*(1590*t+407*np.minimum(t,1.1)**2))*.026)
glass*=np.exp(-t*2.4)*(1-np.exp(-t*80))

# 4. The clock survives the frozen silence: original synthesized escapement
# strikes become more widely spaced and deeper as the pressure tail expands.
clock=np.zeros_like(t)
for j,seconds in enumerate((.64,1.46,2.43,3.57,4.88)):
    local=np.maximum(t-seconds,0)
    pitch=1.04-j*.055
    face=(np.sin(math.tau*1423*pitch*local)*.10*np.exp(-local*42)
          +np.sin(math.tau*2701*pitch*local)*.065*np.exp(-local*75)
          +np.sin(math.tau*197*pitch*local)*.115*np.exp(-local*28))
    face*=1-np.exp(-local*1800)
    face[t<seconds]=0
    clock+=face

dry=edge(shock+sub+roar+metal+scrape+glass+clock)
# Stereo is intentional for this globally played, relative/UI declaration;
# bass stays centred. The four world-positioned auxiliary sounds remain mono.
air=metal+scrape+glass+roar*.30
left=dry.copy();right=dry.copy()
for delay,gain in ((.071,.18),(.193,.13),(.347,.10),(.611,.075),(1.031,.052)):
    left+=delayed(air,delay)*gain
    right+=delayed(air,delay*1.117+.013)*gain
left+=delayed(clock,.117)*.11
right+=delayed(clock,.179)*.11
stereo=np.column_stack((edge(left),edge(right)))
stereo-=stereo.mean(axis=0)
# Original soft-knee shaping keeps a dense, loud attack while retaining 1.5 dB
# of PCM headroom. There is no imported waveform or reference spectrogram.
stereo=np.tanh(stereo*1.25)/1.25
stereo*=.80/max(float(np.abs(stereo).max()),1e-12)
wav=OUT/"time_stop.wav"
write_wave(wav,stereo)
write_wave(OUT/"time-stop-original-v2.wav",stereo)
ogg=ASSETS/"sounds/time_stop.ogg"
subprocess.run([str(FFMPEG),"-hide_banner","-loglevel","error","-i",str(wav),"-c:a","libvorbis","-q:a","6","-y",str(ogg)],check=True)
actual=decode(ogg,2)
mono=actual.mean(axis=1)
stats=mono_metrics(mono)
stats["channels"]=2
stats["per_channel_peak_dbfs"]=[round(20*math.log10(float(np.abs(actual[:,i]).max())),3) for i in range(2)]
stats["peak_samples_at_or_above_0_999"]=int(np.count_nonzero(np.abs(actual)>=.999))
stats.update({
    "wav":str(wav.relative_to(ROOT)),"ogg":str(ogg.relative_to(ROOT)),
    "sha256":hashlib.sha256(ogg.read_bytes()).hexdigest(),
    "origin":"Original numerical synthesis v2. No video/audio reference samples are read or incorporated.",
    "generator":str(Path(__file__).relative_to(ROOT)),
    "generator_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    "seed":SEED,
    "layers":["157->37 Hz exponential pressure drop and octave", "24-harmonic moving three-formant mechanical roar", "seven inharmonic frequency-modulated falling metallic plates", "seeded 650..6700 Hz gated scrape and independent rising glass oscillators", "five individually generated clock escapement strikes", "five unequal stereo reflection taps; bass centred"],
    "comparison":"Replaces the v1 simple sub-bass/drone with moving formants, metallic partials, gated scrape, clock detail and asymmetric reflections. The intervening user-video excerpt was rejected and removed from production.",
    "license":"MIT; original procedural sound generation. No third-party audio excerpt in this game resource.",
    "limits":"Numerical and real-source runtime checks do not substitute for subjective listening on the target audio system.",
})
assert stats["peak_samples_at_or_above_0_999"]==0,stats
assert max(abs(actual.mean(axis=0)))<.001,stats
report_path=OUT/"audio-audit.json"
report=json.loads(report_path.read_text(encoding="utf8"))
report["synthesis"]="All five final game sounds are original procedural synthesis. time_stop is the layered v2 numerical declaration, generated without reading any reference recording. The temporary user-video candidate is historical evidence only and is excluded from game resources."
report["encoded_assets"]["time_stop"]=stats
report["timeline_20tps"]["time_stop"]="Original v2 declaration played with freeze onset at age 44/full-charge release, volume/pitch 1.0. 5.9 seconds = 118 ticks."
report["historical_reference"]="Reference measurements and a rejected excerpt candidate are retained outside src/main/resources for history; no extracted reference waveform is part of the final five game assets."
report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
(OUT/"original-declaration-provenance.json").write_text(json.dumps(stats,ensure_ascii=False,indent=2)+"\n",encoding="utf8")

preview=np.zeros((round(RATE*11.2),2))
def add(signal,seconds,gain=1):
    if signal.ndim==1:signal=np.column_stack((signal,signal))
    start=round(RATE*seconds);count=min(len(signal),len(preview)-start)
    preview[start:start+count]+=signal[:count]*gain
add(decode(ASSETS/"sounds/astral_charge.ogg"),0,.62)
for sec in (0,1,2):add(decode(ASSETS/"sounds/astral_tick.ogg"),sec,.85)
add(actual,2.2)
add(decode(ASSETS/"sounds/astral_reverse.ogg"),8.6,.9)
preview*=min(1,.84/max(float(np.abs(preview).max()),1e-12))
write_wave(OUT/"astral-timeline-preview.wav",preview)

image=Image.new("RGB",(1400,700),"#0b1020")
draw=ImageDraw.Draw(image);font=ImageFont.truetype(r"C:\Windows\Fonts\consola.ttf",20)
draw.text((30,20),"ORIGINAL DECLARATION V2 / NO REFERENCE SAMPLES / LAYERED NUMERICAL SYNTHESIS",fill="#e9f2ff",font=font)
for row,(name,signal,color) in enumerate((("PRESSURE + MOVING FORMANT ROAR",shock+sub+roar,"#ffb08c"),("INHARMONIC PLATES + SCRAPE + CLOCK",metal+scrape+glass+clock,"#c3a0ff"),("FINAL ENCODED MONO SUM (STEREO GAME RESOURCE)",mono,"#80dcff"))):
    top=70+row*195;draw.text((30,top),name,fill=color,font=font)
    normalized=signal/max(float(np.abs(signal).max()),1e-12);y=top+85
    draw.line((40,y,1360,y),fill="#334363")
    for x in range(1320):
        a=math.floor(x*len(signal)/1320);b=max(a+1,math.floor((x+1)*len(signal)/1320))
        mag=float(np.max(np.abs(normalized[a:b])))
        draw.line((40+x,y-mag*54,40+x,y+mag*54),fill=color)
draw.text((30,660),f"peak {stats['peak_dbfs']} dBFS / RMS {stats['rms_dbfs']} dBFS / LF20-160 {stats['20_160_hz_energy_fraction']:.1%} / clipping 0",fill="#b7d6e2",font=font)
image.save(OUT/"original-declaration-v2-waveforms.png")
print(json.dumps({k:v for k,v in stats.items() if k!="rms_100ms"},ensure_ascii=False,indent=2))
